package havis.llrpservice.server.service;

import havis.device.io.IODevice;
import havis.device.rf.RFDevice;
import havis.llrpservice.common.concurrent.EventPipe;
import havis.llrpservice.common.io.PathHandler;
import havis.llrpservice.common.io.XMLFile;
import havis.llrpservice.common.tcp.TCPServerMultiplexed;
import havis.llrpservice.sbc.service.ServiceFactory;
import havis.llrpservice.server.configuration.ConfigurationException;
import havis.llrpservice.server.configuration.ConfigurationValidator;
import havis.llrpservice.server.configuration.ServerConfiguration;
import havis.llrpservice.server.management.Management;
import havis.llrpservice.server.management.ManagementFactory;
import havis.llrpservice.server.management.bean.Server;
import havis.llrpservice.server.management.bean.ServiceInstance;
import havis.llrpservice.server.service.LLRPServiceInstance.LLRPServiceInstanceListener;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;
import havis.llrpservice.xml.properties.DefaultsGroup;
import havis.llrpservice.xml.properties.LLRPServerInstancePropertiesType;
import havis.llrpservice.xml.properties.LLRPServerPropertiesType;
import havis.util.platform.Platform;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.jibx.runtime.JiBXException;
import org.xml.sax.SAXException;

import com.rits.cloning.Cloner;

/**
 * LLRPServiceManager is the main class of the LLRP server. It reads the
 * properties and configuration files and starts the management interfaces and
 * service instances.
 * <p>
 * The properties and configuration files for the server and its instances must
 * be placed in the classpath:
 * <ul>
 * <li><code>havis-llrpservice/LLRPServerProperties.xml</code>
 * <li><code>havis-llrpservice/LLRPServerConfiguration.xml</code>
 * <li>
 * <code>havis-llrpservice/instances/&lt;instanceId&gt;/LLRPServerInstanceProperties.xml</code>
 * <li>
 * <code>havis-llrpservice/instances/&lt;instanceId&gt;/LLRPServerInstanceConfiguration.xml</code>
 * </ul>
 * </p>
 * The default base path <code>havis-llrpservice</code> can be changed: see
 * {@link #LLRPServiceManager(String, ServiceFactory, ServiceFactory, ServiceFactory)}
 */
public class LLRPServiceManager implements Runnable {

	private final static Logger log = Logger.getLogger(LLRPServiceManager.class.getName());

	private final static String BASE_PATH = "havis-llrpservice";
	private final static String SERVER_PROPERTIES_NAME = "LLRPServerProperties.xml";
	private final static String INSTANCE_PROPERTIES_NAME = "LLRPServerInstanceProperties.xml";
	private final static String SERVER_CONFIG_NAME = "LLRPServerConfiguration.xml";
	private final static String INSTANCE_CONFIG_NAME = "LLRPServerInstanceConfiguration.xml";

	private final static String MGMT_PATH_BASE = "/havis.llrpservice/";
	private final static String MGMT_PATH_SERVER = MGMT_PATH_BASE + "Server";
	private final static String MGMT_PATH_INSTANCE = MGMT_PATH_BASE + "Instances";

	// The base path to properties and configuration files. An relative path
	// starts at the classpath. If it is not set in the constructor then the
	// default is used: BASE_PATH.
	private Path propsConfigBasePath;
	private LLRPServerPropertiesType serverProperties;
	private LLRPServerConfigurationType serverConfig;
	private ServerConfiguration serverConfigManager;
	// instanceId -> instance handle
	private Map<String, InstanceHandle> serviceInstances = new HashMap<>();
	private ExecutorService threadPool;
	private TCPServerMultiplexed tcpServerLLRP;
	private final ServiceFactory<Platform> platformServiceFactory;
	private final ServiceFactory<RFDevice> rfcServiceFactory;
	private final ServiceFactory<IODevice> gpioServiceFactory;
	private final ReentrantLock lock = new ReentrantLock();
	private final Condition serverLatch = lock.newCondition();
	private EventPipe<ServerEvent> serverEvents = new EventPipe<>(lock);
	private boolean isStopped = false;

	private class InstanceListener implements LLRPServiceInstanceListener {

		private final String instanceId;
		private boolean isRestarting;

		public InstanceListener(String instanceId) {
			this.instanceId = instanceId;
		}

		@Override
		public void opened(int llrpPort) {
			log.log(Level.INFO, instanceId + " instance " + (isRestarting ? "Restarted" : "Started")
					+ " on port " + llrpPort);
		}

		@Override
		public void closed(Throwable t, boolean isRestarting) {
			this.isRestarting = isRestarting;
			if (t != null) {
				LogRecord record = new LogRecord(Level.SEVERE, "Stopped instance " + instanceId
						+ (isRestarting ? " for restart" : "") + " with exception");
				record.setThrown(t);
				log.log(record);
			} else if (log.isLoggable(Level.INFO)) {
				log.log(Level.INFO,
						"Stopped instance " + instanceId + (isRestarting ? " for restart" : ""));
			}
		}
	}

	private class InstanceHandle {
		Future<?> future;
		LLRPServiceInstance instance;
		ServiceInstance mbean;
		XMLFile<LLRPServerInstancePropertiesType> propertiesFile;
		XMLFile<LLRPServerInstanceConfigurationType> configFile;
	}

	private static class ServerEvent {
		enum Type {
			SERVER_DOWN, SERVER_UP
		}

		private Exception exception;
		private Type type;

		private ServerEvent(Type type) {
			this.type = type;
		}

		private ServerEvent(Type type, Exception e) {
			this.type = type;
			this.exception = e;
		}
	}

	public static void main(String[] args)
			throws LLRPServiceManagerException, PropertiesException, ConfigurationException {
		// create a LLRP service manager
		final LLRPServiceManager manager = new LLRPServiceManager(null /* propsConfigBasePath */,
				null /* scServiceFactory */, null /* rfcServiceFactory */,
				null /* gpioServiceFactory */);
		// register a shutdown hook for hangup signal/ctrl-c
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				try {
					if (log.isLoggable(Level.INFO)) {
						log.log(Level.INFO, "Hang up signal received");
					}
					manager.stop();
				} catch (Throwable t) {
					log.log(Level.SEVERE, "Stopping of server failed", t);
				}
			}
		});
		// run LLRP service
		manager.run();
	}

	/**
	 * @param propsConfigBaseDir
	 *            The base path to the properties and configuration files. A
	 *            relative path starts at the classpath (<code>null</code> is
	 *            handled in the same way as the default
	 *            <code>havis-llrpservice</code> ).
	 * @param platformServiceFactory
	 *            The service factory for the platform. If it is
	 *            <code>null</code> then the controller is created via the Java
	 *            Reflection API (see configuration for properties).
	 * @param rfcServiceFactory
	 *            The service factory for the RF controller. If it is
	 *            <code>null</code> then the controller is created via the Java
	 *            Reflection API (see configuration for properties).
	 * @throws LLRPServiceManagerException
	 *             the loading of configurations failed
	 * @throws PropertiesException
	 *             the loading of the properties failed (missing files or
	 *             invalid properties)
	 * @throws ConfigurationException
	 */
	public LLRPServiceManager(String propsConfigBaseDir,
			ServiceFactory<Platform> platformServiceFactory,
			ServiceFactory<RFDevice> rfcServiceFactory, ServiceFactory<IODevice> gpioServiceFactory)
			throws LLRPServiceManagerException, PropertiesException, ConfigurationException {
		propsConfigBaseDir = propsConfigBaseDir == null || propsConfigBaseDir.trim().length() == 0
				? BASE_PATH : propsConfigBaseDir;
		propsConfigBasePath = new PathHandler().toAbsolutePath(propsConfigBaseDir);
		if (propsConfigBasePath == null) {
			throw new ConfigurationException(
					"Missing path to configuration files: " + propsConfigBaseDir);
		}

		this.platformServiceFactory = platformServiceFactory;
		this.rfcServiceFactory = rfcServiceFactory;
		this.gpioServiceFactory = gpioServiceFactory;

		try {
			try {
				// load properties and configurations
				loadPropsAndConfigs();
			} catch (LLRPServiceManagerException | PropertiesException | ConfigurationException e) {
				throw e;
			} catch (Exception e) {
				throw new LLRPServiceManagerException(
						"Loading of properties or configuration failed", e);
			}
		} catch (Exception e) {
			log.log(Level.SEVERE, "Starting of server failed", e);
			throw e;
		}
	}

	@Override
	public void run() {
		lock.lock();
		try {
			while (!isStopped) {
				Management management = null;
				Future<?> tcpServerLLRPFuture = null;
				try {
					boolean registerForManagement = Boolean.getBoolean("havis.llrpservice.server.registerForManagement");
					// if the server is restarted
					if (serverProperties == null) {
						// load properties and configurations
						loadPropsAndConfigs();
					}
					if (registerForManagement) {
						// create management with a copy of the server properties
						management = new ManagementFactory(new Cloner().deepClone(serverProperties)).createManagement();
						management.open();
						// register the server for management
						management.register(MGMT_PATH_SERVER, new Server(this));
					}
					// open the server configuration manager
					serverConfigManager.open();
					// start the LLRP server
					threadPool = Executors.newFixedThreadPool(1 + serviceInstances.size());
					tcpServerLLRP = new TCPServerMultiplexed();
					tcpServerLLRP.setReadBufferSize(serverConfig.getDefaults().getInterfaces()
							.getLLRP().getTCPReadBufferSize());
					tcpServerLLRPFuture = threadPool.submit(tcpServerLLRP);
					// for each instance
					for (String instanceId : serviceInstances.keySet()) {
						// register the instance for management
						InstanceHandle ih = getInstanceHandle(instanceId);
						ih.mbean = new ServiceInstance(this, instanceId);
						if (management != null) {
							management.register(MGMT_PATH_INSTANCE + "/" + instanceId, ih.mbean);
						}
						// start the instance
						startServiceInstance(instanceId);
					}
					serverEvents.fire(new ServerEvent(ServerEvent.Type.SERVER_UP));

					// wait for shutdown signal
					try {
						serverLatch.await();
					} catch (InterruptedException e) {
						log.log(Level.SEVERE,
								"Waiting for shutdown of server has been interrupted. The server is restarted.",
								e);
					}
				} catch (Exception e) {
					log.log(Level.SEVERE, "Starting of server failed", e);
					serverEvents.fire(new ServerEvent(ServerEvent.Type.SERVER_UP, e));
					isStopped = true;
				}
				try {
					// stop instances
					for (String instanceId : serviceInstances.keySet()) {
						stopServiceInstance(instanceId);
					}
					// close the TCP server
					if (tcpServerLLRP != null) {
						tcpServerLLRP.requestClosing();
						tcpServerLLRPFuture.get(serverProperties.getUnexpectedTimeout(),
								TimeUnit.SECONDS);
					}
					// close the thread pool
					if (threadPool != null) {
						threadPool.shutdown();
					}
					// close the server configuration manager
					if (serverConfigManager != null) {
						serverConfigManager.close();
					}
					// close the management
					if (management != null) {
						management.close();
					}
				} catch (Exception e) {
					log.log(Level.SEVERE, "Stopping of server failed", e);
					serverEvents.fire(new ServerEvent(ServerEvent.Type.SERVER_DOWN, e));
					isStopped = true;
				} finally {
					// remove loaded configuration and properties
					serviceInstances.clear();

					serverConfig = null;
					serverProperties = null;
					if (log.isLoggable(Level.INFO)) {
						log.log(Level.INFO, "Stopped server");
					}
					serverEvents.fire(new ServerEvent(ServerEvent.Type.SERVER_DOWN));
				}
			}
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Restarts the LLRP service. If the server is down then an exception is
	 * thrown.
	 * 
	 * @throws LLRPServiceManagerException
	 */
	public void restart() throws LLRPServiceManagerException {
		lock.lock();
		try {
			if (serviceInstances.size() == 0) {
				throw new LLRPServiceManagerException(
						"The server cannot be restarted because it is down");
			}
			serverLatch.signal();
			try {
				int countDown = 2;
				while (countDown > 0) {
					for (ServerEvent event : serverEvents
							.await(serverProperties.getUnexpectedTimeout() * 1000)) {
						if (event.exception != null) {
							throw event.exception;
						}
						switch (countDown) {
						case 2:
							if (event.type == ServerEvent.Type.SERVER_DOWN) {
								countDown--;
							}
							break;
						case 1:
							if (event.type == ServerEvent.Type.SERVER_UP) {
								countDown--;
							}
							break;
						}
					}
				}
			} catch (Exception e) {
				throw new LLRPServiceManagerException("The server could not be restarted", e);
			}
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Stops the LLRP service.
	 * 
	 * @throws LLRPServiceManagerException
	 */
	public void stop() throws LLRPServiceManagerException {
		lock.lock();
		try {
			// if the server is already down
			if (serviceInstances.size() == 0) {
				return;
			}
			int unexpectedTimeout = serverProperties.getUnexpectedTimeout() * 1000;
			isStopped = true;
			serverLatch.signal();
			try {
				boolean isDown = false;
				while (!isDown) {
					for (ServerEvent event : serverEvents.await(unexpectedTimeout)) {
						if (event.exception != null) {
							throw event.exception;
						}
						isDown = event.type == ServerEvent.Type.SERVER_DOWN;
					}
				}
			} catch (Exception e) {
				throw new LLRPServiceManagerException("The server could not be stopped", e);
			}
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Starts a LLRP service instance. The instance must be configured. If the
	 * server is down then an exception is thrown.
	 * 
	 * @param instanceId
	 * @throws LLRPServiceManagerException
	 */
	public void startServiceInstance(String instanceId) throws LLRPServiceManagerException {
		lock.lock();
		try {
			if (serviceInstances.size() == 0) {
				throw new LLRPServiceManagerException("The instance '" + instanceId
						+ "' cannot be started because the server is down");
			}
			try {
				InstanceHandle ih = getInstanceHandle(instanceId);
				// if the instance is running
				if (ih.future != null) {
					return;
				}
				// get LLRP capabilities for the instance
				PropertiesAnalyser propertiesAnalyser = new PropertiesAnalyser(serverProperties);
				if (ih.propertiesFile != null) {
					propertiesAnalyser.setServerInstanceProperties(ih.propertiesFile.getContent());
				}
				DefaultsGroup instanceProperties = propertiesAnalyser.getInstancesProperties();
				if (log.isLoggable(Level.INFO)) {
					log.log(Level.INFO, "Starting instance " + instanceId);
				}
				// create and start the instance
				ih.instance = new LLRPServiceInstance(serverConfigManager, ih.configFile,
						instanceProperties, serverProperties.getUnexpectedTimeout(), tcpServerLLRP,
						platformServiceFactory, rfcServiceFactory, gpioServiceFactory);
				ih.instance.addListener(new InstanceListener(instanceId));
				ih.future = threadPool.submit(ih.instance);
				ih.mbean.setIsActive(true);
			} catch (Exception e) {
				throw new LLRPServiceManagerException(
						"The instance '" + instanceId + "' could not be started", e);
			}
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Stops a LLRP service instance.
	 * 
	 * @param instanceId
	 * @throws LLRPServiceManagerException
	 */
	public void stopServiceInstance(String instanceId) throws LLRPServiceManagerException {
		lock.lock();
		try {
			// if the server is down
			if (serviceInstances.size() == 0) {
				return;
			}
			InstanceHandle ih = getInstanceHandle(instanceId);
			// if the instance is already down
			if (ih.future == null) {
				return;
			}
			if (log.isLoggable(Level.INFO)) {
				log.log(Level.INFO, "Stopping instance " + instanceId);
			}
			// cancel the execution and wait for the end of the thread
			ih.instance.cancelExecution();
			ih.future.get(serverProperties.getUnexpectedTimeout(), TimeUnit.SECONDS);

			ih.instance = null;
			ih.future = null;
			ih.mbean.setIsActive(false);
		} catch (Exception e) {
			throw new LLRPServiceManagerException(
					"The instance '" + instanceId + "' could not be stopped", e);
		} finally {
			lock.unlock();
		}
	}

	private InstanceHandle getInstanceHandle(String instanceId) {
		InstanceHandle ih = serviceInstances.get(instanceId);
		if (ih == null) {
			ih = new InstanceHandle();
			serviceInstances.put(instanceId, ih);
		}
		return ih;
	}

	/**
	 * Loads properties and configurations.
	 * 
	 * @throws JiBXException
	 * @throws IOException
	 * @throws SAXException
	 * @throws LLRPServiceManagerException
	 * @throws PropertiesException
	 *             the loading of properties failed (missing files or invalid
	 *             properties)
	 * @throws ConfigurationException
	 */
	private void loadPropsAndConfigs() throws JiBXException, IOException, SAXException,
			LLRPServiceManagerException, PropertiesException, ConfigurationException {
		// load properties
		Path latestConfigBaseDir = loadServerProperties();
		// load configurations
		List<Path> instanceDirs = loadServerConfig(latestConfigBaseDir);
		loadInstanceProperties(instanceDirs);
		// "<anyPath>/latest/instances"
		loadInstanceConfigs(instanceDirs, latestConfigBaseDir);
		// remove unconfigured instances
		removeUnconfiguredInstanceHandles();
	}

	/**
	 * Loads the server and instance properties.
	 * 
	 * @return the path to the base directory with the latest configuration
	 *         files
	 * 
	 * @throws JiBXException
	 * @throws IOException
	 * @throws SAXException
	 * @throws PropertiesException
	 *             the loading of properties failed (missing files or invalid
	 *             properties)
	 */
	private Path loadServerProperties()
			throws JiBXException, IOException, SAXException, PropertiesException {
		// get server properties
		// "<anyServerPath>/LLRPServerProperties.xml"
		Path serverFilePath = propsConfigBasePath.resolve(SERVER_PROPERTIES_NAME);
		// if file does not exist
		if (new PathHandler().toAbsolutePath(serverFilePath) == null) {
			throw new PropertiesException(
					"Missing server properties file '" + SERVER_PROPERTIES_NAME + "'");
		}
		XMLFile<LLRPServerPropertiesType> serverPropertiesFile = new XMLFile<LLRPServerPropertiesType>(
				LLRPServerPropertiesType.class, serverFilePath, null /* latestPath */);
		serverProperties = serverPropertiesFile.getContent();

		if (log.isLoggable(Level.INFO)) {
			log.log(Level.INFO, "Loaded global properties from " + serverFilePath);
		}

		// get the directory path to the latest stored files. A relative path
		// starts at the directory with the server properties file.
		// "<anyPath>/latest"
		Path latestConfigBaseDir = Paths
				.get(serverProperties.getOutput().getLatestConfigurationBaseDir().trim());
		if (!latestConfigBaseDir.isAbsolute()) {
			latestConfigBaseDir = propsConfigBasePath.resolve(latestConfigBaseDir);
		}
		return latestConfigBaseDir;
	}

	private void loadInstanceProperties(List<Path> instanceDirs)
			throws JiBXException, IOException, SAXException, PropertiesException {
		PropertiesValidator propsValidator = new PropertiesValidator();
		// get instance properties
		for (Path instanceDir : instanceDirs) {
			// "<anyInstancePath>/LLRPServerInstanceProperties.xml"
			Path instanceFilePath = instanceDir.resolve(INSTANCE_PROPERTIES_NAME);
			// if file exists
			if (new PathHandler().toAbsolutePath(instanceFilePath) != null) {
				// put file content to list
				XMLFile<LLRPServerInstancePropertiesType> instanceFile = new XMLFile<LLRPServerInstancePropertiesType>(
						LLRPServerInstancePropertiesType.class, instanceFilePath,
						null /* latestPath */);
				LLRPServerInstancePropertiesType instanceContent = instanceFile.getContent();
				propsValidator.validate(instanceContent, instanceFile.getPath().toString());
				getInstanceHandle(instanceContent.getInstanceId()).propertiesFile = instanceFile;
				if (log.isLoggable(Level.INFO)) {
					log.log(Level.INFO, "Loaded instance properties "
							+ instanceContent.getInstanceId() + " from " + instanceFilePath);
				}
			}
		}
	}

	/**
	 * Loads the server and instance configurations.
	 * 
	 * @param latestConfigBaseDir
	 *            the path to the base directory with the latest configuration
	 *            files
	 * 
	 * @return the directory paths to the instance configuration files
	 * 
	 * @throws JiBXException
	 * @throws IOException
	 * @throws SAXException
	 * @throws ConfigurationException
	 */
	private List<Path> loadServerConfig(Path latestConfigBaseDir)
			throws JiBXException, IOException, SAXException, ConfigurationException {
		// get server configuration
		// "<anyServerPath>/LLRPServerConfiguration.xml"
		Path serverFilePath = propsConfigBasePath.resolve(SERVER_CONFIG_NAME);
		// if file does not exist
		if (new PathHandler().toAbsolutePath(serverFilePath) == null) {
			throw new ConfigurationException(
					"Missing server configuration file '" + SERVER_CONFIG_NAME + "'");
		}
		// "<anyLatestPath>/LLRPServerConfiguration.xml"
		Path serverLatestConfigFilePath = latestConfigBaseDir.resolve(SERVER_CONFIG_NAME);
		XMLFile<LLRPServerConfigurationType> serverConfigFile = new XMLFile<LLRPServerConfigurationType>(
				LLRPServerConfigurationType.class, serverFilePath, serverLatestConfigFilePath);
		LLRPServerConfigurationType serverConfigContent = serverConfigFile.getContent();
		ConfigurationValidator confValidator = new ConfigurationValidator();
		confValidator.validate(serverConfigContent, serverConfigFile.getPath().toString());
		serverConfigManager = new ServerConfiguration(serverConfigFile);
		serverConfig = serverConfigContent;
		if (log.isLoggable(Level.INFO)) {
			log.log(Level.INFO, "Loaded global configuration from " + serverFilePath);
		}

		// Get the directory paths to the instance configuration files. A
		// relative path starts at the directory with the server configuration
		// file.
		List<Path> instanceDirs = new ArrayList<>();
		for (String instanceBaseDir : serverConfig.getInstanceConfigurations()
				.getInstanceConfigurationBaseDirList()) {
			Path instanceBaseDirPath = Paths.get(instanceBaseDir.trim());
			if (!instanceBaseDirPath.isAbsolute()) {
				instanceBaseDirPath = propsConfigBasePath.resolve(instanceBaseDirPath);
			}
			instanceDirs.add(instanceBaseDirPath);
		}
		return instanceDirs;
	}

	private void loadInstanceConfigs(List<Path> instanceDirs, Path latestConfigBaseDir)
			throws JiBXException, IOException, SAXException, ConfigurationException {
		ConfigurationValidator confValidator = new ConfigurationValidator();
		for (Path instanceDir : instanceDirs) {
			// "<anyInstancePath>/LLRPServerInstanceConfiguration.xml"
			Path instanceFilePath = instanceDir.resolve(INSTANCE_CONFIG_NAME);
			// if file exists
			if (new PathHandler().toAbsolutePath(instanceFilePath) != null) {
				XMLFile<LLRPServerInstanceConfigurationType> instanceFile = new XMLFile<LLRPServerInstanceConfigurationType>(
						LLRPServerInstanceConfigurationType.class, instanceFilePath,
						null /* latestPath */);
				LLRPServerInstanceConfigurationType instanceContent = instanceFile.getContent();
				confValidator.validate(instanceContent, instanceFile.getPath().toString());
				String instanceId = instanceContent.getInstanceId();
				// "<anyLatestPath>/<instanceId>/LLRPServerInstanceConfiguration.xml"
				instanceFile.setLatestPath(
						latestConfigBaseDir.resolve(instanceId).resolve(INSTANCE_CONFIG_NAME));
				getInstanceHandle(instanceId).configFile = instanceFile;
				if (log.isLoggable(Level.INFO)) {
					log.log(Level.INFO, "Loaded instance configuration " + instanceId + " from "
							+ instanceFilePath);
				}
			}
		}
	}

	private void removeUnconfiguredInstanceHandles() throws LLRPServiceManagerException {
		List<String> remove = new ArrayList<>();
		// for each instance
		for (Entry<String, InstanceHandle> entry : serviceInstances.entrySet()) {
			String instanceId = entry.getKey();
			InstanceHandle instanceHandle = entry.getValue();
			// if the config or the properties file does not exist
			if (instanceHandle.configFile == null || instanceHandle.propertiesFile == null) {
				remove.add(instanceId);
			}
		}
		for (String instanceId : remove) {
			InstanceHandle instanceHandle = serviceInstances.remove(instanceId);
			if (log.isLoggable(Level.WARNING)) {
				XMLFile<?> existingFile = instanceHandle.configFile != null
						? instanceHandle.configFile : instanceHandle.propertiesFile;
				log.log(Level.WARNING,
						"Ignoring instance " + instanceId
								+ ". A configuration file and a properties file is required in "
								+ existingFile.getInitialPath().getParent().toString());
			}
		}
		if (serviceInstances.size() == 0) {
			throw new LLRPServiceManagerException("Cannot start server without an instance");
		}
	}
}
