package havis.llrpservice.server.service;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.testng.annotations.Test;

import havis.llrpservice.common.entityManager.EntityManagerException;
import havis.llrpservice.common.io.XMLFile;
import havis.llrpservice.common.tcp.TCPServerMultiplexed;
import havis.llrpservice.common.tcp.TCPUnknownChannelException;
import havis.llrpservice.server.configuration.ConfigurationException;
import havis.llrpservice.server.configuration.ServerConfiguration;
import havis.llrpservice.server.management.Management;
import havis.llrpservice.server.management.ManagementException;
import havis.llrpservice.server.management.bean.Server;
import havis.llrpservice.server.management.bean.ServiceInstance;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;
import havis.llrpservice.xml.properties.DefaultsGroup;
import mockit.Capturing;
import mockit.Deencapsulation;
import mockit.Delegate;
import mockit.Invocation;
import mockit.Mocked;
import mockit.NonStrictExpectations;
import mockit.Verifications;

public class LLRPServiceManagerTest {

	private static final String BASE_PATH = Paths
			.get(LLRPServiceManagerTest.class.getPackage().getName().replace('.', '/'))
			.resolve("LLRPServiceManager").toString();

	@Test
	public void constructorError() throws Exception {
		try {
			new LLRPServiceManager(BASE_PATH + "x", null /* scServiceFactory */,
					null /* rfcServiceFactory */, null /* gpioServiceFactory */);
		} catch (ConfigurationException e) {
			assertTrue(e.getMessage().contains("Missing path to configuration files"));
		}

		try {
			new LLRPServiceManager(BASE_PATH + "Error1", null /* scServiceFactory */,
					null /* rfcServiceFactory */, null /* gpioServiceFactory */);
		} catch (ConfigurationException e) {
			assertTrue(e.getMessage().contains("Missing server configuration file"));
		}

		try {
			new LLRPServiceManager(BASE_PATH + "Error2", null /* scServiceFactory */,
					null /* rfcServiceFactory */, null /* gpioServiceFactory */);
		} catch (PropertiesException e) {
			assertTrue(e.getMessage().contains("Missing server properties file"));
		}

		try {
			new LLRPServiceManager(BASE_PATH + "Error3", null /* scServiceFactory */,
					null /* rfcServiceFactory */, null /* gpioServiceFactory */);
		} catch (LLRPServiceManagerException e) {
			assertTrue(e.getMessage().contains("Cannot start server without an instance"));
		}

		// invalid server properties file
		try {
			new LLRPServiceManager(BASE_PATH + "Error4", null /* scServiceFactory */,
					null /* rfcServiceFactory */, null /* gpioServiceFactory */);
		} catch (LLRPServiceManagerException e) {
			assertTrue(e.getMessage().contains("Loading of properties")
					&& e.getCause().getMessage().contains("No unmarshaller"));
		}
	}

	@Test
	public void run1(final @Mocked ServerConfiguration serverConf, final @Capturing Management mgmt,
			final @Mocked TCPServerMultiplexed tcpServer,
			final @Mocked LLRPServiceInstance instance) throws Exception {
		// create LLRP service manager
		LLRPServiceManager server = new LLRPServiceManager(BASE_PATH + "1",
				null /* scServiceFactory */, null /* rfcServiceFactory */,
				null /* gpioServiceFactory */);
		new Verifications() {
			{
				// the configuration manager has been created with the path to
				// the latest configurations files from the server properties
				// => the server properties and the server configuration has
				// been read successfully
				XMLFile<LLRPServerConfigurationType> xmlFile;
				new ServerConfiguration(xmlFile = withCapture());
				times = 1;
				assertTrue(xmlFile.getLatestPath()
						.endsWith("latestConfiguration/LLRPServerConfiguration.xml"));
			}
		};
		// start the server
		ExecutorService threadPool = Executors.newFixedThreadPool(1);
		Future<?> future = threadPool.submit(server);
		Thread.sleep(1000);
		new Verifications() {
			{
				// the management has been opened
				mgmt.open();
				times = 1;
				// the server and instance mbean has been registered
				mgmt.register("/havis.llrpservice/Server", withInstanceOf(Server.class));
				times = 1;
				mgmt.register("/havis.llrpservice/Instances/instanceId",
						withInstanceOf(ServiceInstance.class));
				times = 1;

				// the server configuration manager has been opened
				serverConf.open();
				times = 1;

				// the TCP server has been created
				new TCPServerMultiplexed();
				times = 1;

				// the configured service instance has been started
				XMLFile<LLRPServerInstanceConfigurationType> xmlFile;
				new LLRPServiceInstance(withInstanceOf(ServerConfiguration.class),
						xmlFile = withCapture(), withInstanceOf(DefaultsGroup.class),
						60 /* unexpectedTimeout */, withInstanceOf(TCPServerMultiplexed.class),
						null /* scServiceFactory */, null /* rfcServiceFactory */,
						null /* gpioServiceFactory */);
				times = 1;
				// the server instance configuration has been read successfully
				assertEquals(xmlFile.getContent().getInstanceId(), "instanceId");
			}
		};
		// stop the server
		server.stop();
		Thread.sleep(1000);
		new Verifications() {
			{
				// the service instance has been stopped
				instance.cancelExecution();
				times = 1;

				// the TCP server has been closed
				tcpServer.requestClosing();

				// the server configuration manager has been closed
				serverConf.open();
				times = 1;

				// the management has been closed
				mgmt.close();
			}
		};
		future.get(3000, TimeUnit.MILLISECONDS);
		threadPool.shutdown();
	}

	@SuppressWarnings("unchecked")
	@Test
	public void run2(final @Mocked LLRPServiceInstance instance, final @Mocked Logger log)
			throws Exception {
		new NonStrictExpectations() {
			{
				log.isLoggable(Level.INFO);
				result = true;
				log.isLoggable(Level.WARNING);
				result = true;
			}
		};
		Logger origLog = Deencapsulation.getField(LLRPServiceManager.class, "log");
		Deencapsulation.setField(LLRPServiceManager.class, "log", log);
		// create and start LLRP service manager
		LLRPServiceManager server = new LLRPServiceManager(BASE_PATH + "2",
				null /* scServiceFactory */, null /* rfcServiceFactory */,
				null /* gpioServiceFactory */);
		ExecutorService threadPool = Executors.newFixedThreadPool(1);
		Future<?> future = threadPool.submit(server);
		Thread.sleep(1000);
		new Verifications() {
			{
				// only the service instance with the configuration file has
				// been started
				log.log(Level.WARNING, withMatch("^Ignoring instance test2.*"));
				times = 1;

				XMLFile<LLRPServerInstanceConfigurationType> xmlFile;
				DefaultsGroup instanceProps;
				new LLRPServiceInstance(withInstanceOf(ServerConfiguration.class),
						xmlFile = withCapture(), instanceProps = withCapture(),
						60 /* unexpectedTimeout */, withInstanceOf(TCPServerMultiplexed.class),
						null /* scServiceFactory */, null /* rfcServiceFactory */,
						null /* gpioServiceFactory */);
				times = 1;
				assertEquals(xmlFile.getContent().getInstanceId(), "test1");

				// the LLRP properties of the started instance has been used
				assertEquals(instanceProps.getLLRPCapabilities().getMaxNumAccessSpecs(), 1);
			}
		};

		// try to start the running instance again
		server.startServiceInstance("test1");
		new Verifications() {
			{
				// the instance is not started again
				new LLRPServiceInstance(withInstanceOf(ServerConfiguration.class),
						withInstanceOf(XMLFile.class), withInstanceOf(DefaultsGroup.class),
						anyInt/* unexpectedTimeout */, withInstanceOf(TCPServerMultiplexed.class),
						null /* scServiceFactory */, null /* rfcServiceFactory */,
						null /* gpioServiceFactory */);
				times = 1;
			}
		};

		// restart the server
		server.restart();
		Thread.sleep(1000);
		new Verifications() {
			{
				// the instance has been started again
				new LLRPServiceInstance(withInstanceOf(ServerConfiguration.class),
						withInstanceOf(XMLFile.class), withInstanceOf(DefaultsGroup.class),
						60/* unexpectedTimeout */, withInstanceOf(TCPServerMultiplexed.class),
						null /* scServiceFactory */, null /* rfcServiceFactory */,
						null /* gpioServiceFactory */);
				times = 2;
			}
		};

		// stop the server twice
		// no error occurs
		server.stop();
		server.stop();

		// try to restart the server
		try {
			server.restart();
		} catch (LLRPServiceManagerException e) {
			// a stopped server cannot be restarted
			assertTrue(e.getMessage().contains("cannot be restarted"));
		}

		// try to start the instance
		try {
			server.startServiceInstance("test1");
			fail();
		} catch (LLRPServiceManagerException e) {
			// if the server does not run then an instance cannot be started
			assertTrue(e.getMessage().contains("cannot be started"));
		}

		// stop the inactive instance
		// no exception occurs
		server.stopServiceInstance("test1");

		future.get(3000, TimeUnit.MILLISECONDS);
		threadPool.shutdown();

		Deencapsulation.setField(LLRPServiceManager.class, "log", origLog);
	}

	@Test
	public void runError(final @Mocked ServerConfiguration serverConf,
			final @Capturing Management mgmt, final @Mocked Logger log) throws Exception {
		new NonStrictExpectations() {
			{
				log.isLoggable(Level.WARNING);
				result = true;
			}
		};
		Logger origLog = Deencapsulation.getField(LLRPServiceManager.class, "log");
		Deencapsulation.setField(LLRPServiceManager.class, "log", log);
		final Exception startException = new Exception("huhu");
		final Exception stopException = new Exception("oh");
		new NonStrictExpectations() {
			{
				// opening of server config manager fails
				serverConf.open();
				result = startException;

				// closing of management fails
				mgmt.close();
				result = stopException;
			}
		};
		// create and start LLRP service manager
		LLRPServiceManager server = new LLRPServiceManager(BASE_PATH + "2",
				null /* scServiceFactory */, null /* rfcServiceFactory */,
				null /* gpioServiceFactory */);
		ExecutorService threadPool = Executors.newFixedThreadPool(1);
		Future<?> future = threadPool.submit(server);
		Thread.sleep(1000);
		new Verifications() {
			{
				log.log(Level.SEVERE, withMatch("^Starting of server.*"), startException);
				times = 1;

				log.log(Level.SEVERE, withMatch("^Stopping of server.*"), stopException);
				times = 1;
			}
		};
		future.get(3000, TimeUnit.MILLISECONDS);
		threadPool.shutdown();

		Deencapsulation.setField(LLRPServiceManager.class, "log", origLog);
	}

	@Test
	public void restartError1(final @Capturing Management mgmt) throws Exception {
		class Data {
			boolean throwStartException = false;
		}
		final Data data = new Data();
		final ManagementException startException = new ManagementException("huhu");
		new NonStrictExpectations() {
			{
				mgmt.open();
				result = new Delegate<ServerConfiguration>() {
					@SuppressWarnings("unused")
					void open() throws ManagementException {
						if (data.throwStartException) {
							throw startException;
						}
					}
				};
			}
		};
		// create and start LLRP service manager
		LLRPServiceManager server = new LLRPServiceManager(BASE_PATH + "2",
				null /* scServiceFactory */, null /* rfcServiceFactory */,
				null /* gpioServiceFactory */);
		ExecutorService threadPool = Executors.newFixedThreadPool(1);
		Future<?> future = threadPool.submit(server);
		Thread.sleep(1000);

		// restart the server with an exception while starting the server
		data.throwStartException = true;
		try {
			server.restart();
			fail();
		} catch (LLRPServiceManagerException e) {
			assertTrue(e.getMessage().contains("The server could not be restarted"));
		}

		// stop the server
		server.stop();
		future.get(3000, TimeUnit.MILLISECONDS);
		threadPool.shutdown();
	}

	@Test
	public void restartError2(final @Capturing Management mgmt) throws Exception {
		new NonStrictExpectations() {
			{
				mgmt.close();
				result = new EntityManagerException("huhu");
			}
		};
		// create and start LLRP service manager
		final LLRPServiceManager server = new LLRPServiceManager(BASE_PATH + "2",
				null /* scServiceFactory */, null /* rfcServiceFactory */,
				null /* gpioServiceFactory */);
		ExecutorService threadPool = Executors.newFixedThreadPool(1);
		Future<?> future = threadPool.submit(server);
		Thread.sleep(1000);

		// restart the server with an exception while stopping the server
		try {
			server.restart();
			fail();
		} catch (LLRPServiceManagerException e) {
			assertTrue(e.getMessage().contains("The server could not be restarted"));
		}

		// stop the server
		server.stop();
		future.get(3000, TimeUnit.MILLISECONDS);
		threadPool.shutdown();
	}

	@Test
	public void stopError(final @Capturing Management mgmt) throws Exception {
		new NonStrictExpectations() {
			{
				mgmt.close();
				result = new EntityManagerException("oh");
			}
		};
		// create and start LLRP service manager
		LLRPServiceManager server = new LLRPServiceManager(BASE_PATH + "2",
				null /* scServiceFactory */, null /* rfcServiceFactory */,
				null /* gpioServiceFactory */);
		ExecutorService threadPool = Executors.newFixedThreadPool(1);
		Future<?> future = threadPool.submit(server);
		Thread.sleep(1000);

		// stop the server with an exception
		try {
			server.stop();
			fail();
		} catch (LLRPServiceManagerException e) {
			assertTrue(e.getMessage().contains("The server could not be stopped"));
		}

		future.get(3000, TimeUnit.MILLISECONDS);
		threadPool.shutdown();
	}

	@Test
	public void startServiceInstanceError(@Mocked final Logger log) throws Exception {
		final PropertiesAnalyser propertiesAnalyser = new PropertiesAnalyser(null);

		new NonStrictExpectations() {
			{
				log.isLoggable(Level.FINER);
				result = false;
			}
		};

		class Data {
			boolean throwException = false;
		}
		final Data data = new Data();
		new NonStrictExpectations(PropertiesAnalyser.class) {
			{
				propertiesAnalyser.getInstancesProperties();
				result = new Delegate<PropertiesAnalyser>() {
					@SuppressWarnings("unused")
					DefaultsGroup getInstancesProperties(Invocation inv) throws IOException {
						if (data.throwException) {
							throw new IOException("oh");
						}
						return inv.proceed();
					}
				};
			}
		};
		// create and start LLRP service manager
		LLRPServiceManager server = new LLRPServiceManager(BASE_PATH + "2",
				null /* scServiceFactory */, null /* rfcServiceFactory */,
				null /* gpioServiceFactory */);
		ExecutorService threadPool = Executors.newFixedThreadPool(1);
		Future<?> future = threadPool.submit(server);
		Thread.sleep(1000);
		// stop the running service instance
		server.stopServiceInstance("test1");

		// start the instance with an exception
		data.throwException = true;
		try {
			server.startServiceInstance("test1");
			fail();
		} catch (LLRPServiceManagerException e) {
			assertTrue(e.getMessage().contains("could not be started"));
		}

		// stop the server
		server.stop();
		future.get(3000, TimeUnit.MILLISECONDS);
		threadPool.shutdown();
	}

	@Test
	public void stopServiceInstanceError(final @Mocked LLRPServiceInstance serviceInstance,
			@Mocked final Logger log) throws Exception {

		new NonStrictExpectations() {
			{
				log.isLoggable(Level.FINER);
				result = false;
			}
		};

		class Data {
			boolean throwException = false;
		}
		final Data data = new Data();
		new NonStrictExpectations() {
			{
				serviceInstance.cancelExecution();
				result = new Delegate<LLRPServiceInstance>() {
					@SuppressWarnings("unused")
					public void cancelExecution() throws TCPUnknownChannelException {
						if (data.throwException) {
							throw new TCPUnknownChannelException("oh");
						}
					}
				};
			}
		};
		// create and start LLRP service manager
		LLRPServiceManager server = new LLRPServiceManager(BASE_PATH + "2",
				null /* scServiceFactory */, null /* rfcServiceFactory */,
				null /* gpioServiceFactory */);
		ExecutorService threadPool = Executors.newFixedThreadPool(1);
		Future<?> future = threadPool.submit(server);
		Thread.sleep(1000);

		// stop the instance with an exception
		data.throwException = true;
		try {
			server.stopServiceInstance("test1");
			fail();
		} catch (LLRPServiceManagerException e) {
			assertTrue(e.getMessage().contains("could not be stopped"));
		}
		data.throwException = false;

		// stop the server
		server.stop();
		future.get(3000, TimeUnit.MILLISECONDS);
		threadPool.shutdown();
	}
}
