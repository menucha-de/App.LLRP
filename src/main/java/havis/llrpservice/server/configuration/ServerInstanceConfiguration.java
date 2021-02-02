package havis.llrpservice.server.configuration;

import havis.llrpservice.common.entityManager.Entity;
import havis.llrpservice.common.entityManager.EntityManagerException;
import havis.llrpservice.common.entityManager.WrongMetaDataException;
import havis.llrpservice.common.io.XMLFile;
import havis.llrpservice.server.persistence.ClassVersions;
import havis.llrpservice.server.persistence.ObservablePersistence;
import havis.llrpservice.server.persistence.PersistenceException;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ServerConfiguration class simplifies the usage of
 * LLRPServerInstanceConfigurationType object added to corresponding
 * persistence.
 */
public class ServerInstanceConfiguration {

	private static final Logger log = Logger.getLogger(ServerInstanceConfiguration.class.getName());

	/**
	 * Implementation of {@link ServerInstanceConfigurationListener}
	 * 
	 */
	private class ServerInstanceServerConfListener implements
			ServerConfigurationListener {
		// Gain access to members of parent
		private ServerInstanceConfiguration parent;

		/**
		 * Set the parent.
		 * 
		 * @param parent
		 */
		public ServerInstanceServerConfListener(
				ServerInstanceConfiguration parent) {
			this.parent = parent;
		}

		/**
		 * See {@link ServerConfigurationListener#updated(ServerConfiguration)}
		 * <p>
		 * Sets the new configuration in the persistence. Informs all listener,
		 * that the instance configuration has been changed.
		 * </p>
		 */
		@Override
		public void updated(ServerConfiguration config) {
			Throwable exception = null;
			synchronized (lockListeners) {
				try {
					// Get copy of server configuration object
					Entity<LLRPServerConfigurationType> serverEntity = config
							.acquire();
					config.release(serverEntity,/* write */false);
					// Get copy of server instance configuration object
					Entity<LLRPServerInstanceConfigurationType> instanceEntity = acquire();
					// Set new configuration
					persistence.setServerConfiguration(
							serverEntity.getObject(),
							instanceEntity.getObject(), serverConf.getPath()
									.getParent().toString(), file
									.getInitialPath().getParent().toString());
					release(instanceEntity, /* write */false);
					// Sets current configuration to updated one
					currentServerConfig = serverEntity.getObject();
				} catch (Throwable t) {
					log.log(Level.SEVERE, "Update of server configuration failed", t);
					exception = t;
				}
			}
			// Inform listeners
			for (ServerInstanceConfigurationListener entry : listeners) {
				entry.updated(parent, config, exception);
			}
		}
	}

	// List of listeners listen for configuration changes
	private List<ServerInstanceConfigurationListener> listeners;
	private final Object lockListeners = new Object();
	private ObservablePersistence persistence;
	private ServerConfigurationListener serverConfListener;
	// XMLConfigurationFile from type LLRPServerInstanceConfigurationType
	private XMLFile<LLRPServerInstanceConfigurationType> file;
	// Current server configuration
	private ServerConfiguration serverConf;
	// Current entity as list (List contains only one value)
	private List<String> entityIdList;
	// Current server configuration object
	private LLRPServerConfigurationType currentServerConfig;

	/**
	 * Assigns file object and server configuration. Create instances.
	 * 
	 * @param serverConf
	 * @param file
	 */
	public ServerInstanceConfiguration(ServerConfiguration serverConf,
			XMLFile<LLRPServerInstanceConfigurationType> file) {
		this.file = file;
		this.serverConf = serverConf;
		listeners = new CopyOnWriteArrayList<ServerInstanceConfigurationListener>();
		persistence = new ObservablePersistence();
	}

	/**
	 * Reads the content from file and sets this content to persistence as
	 * entity. Afterwards, the content will be set, together with the server
	 * configuration, as configuration of the persistence. Opens the persistence
	 * and add a listener.
	 * 
	 * @throws PersistenceException
	 * 
	 * @throws EntityManagerException
	 * @throws ConfigurationException
	 * @throws PersistenceException
	 */
	public void open() throws EntityManagerException, ConfigurationException,
			PersistenceException {
		List<LLRPServerInstanceConfigurationType> configList = new ArrayList<LLRPServerInstanceConfigurationType>();
		synchronized (lockListeners) {
			LLRPServerInstanceConfigurationType config;
			try {
				// Get content from file
				config = file.getContent();
			} catch (Exception e) {
				throw new ConfigurationException(e);
			}
			configList.add(config);
			persistence.open();
			// If there is no entity in persistence (protection for multiple
			// open call)
			if (entityIdList == null) {
				// set class version
				persistence
						.addClass(
								LLRPServerInstanceConfigurationType.class,
								ClassVersions
										.get(LLRPServerInstanceConfigurationType.class));
				// add entity (InMemoryManager)
				entityIdList = persistence.add(
						LLRPServerInstanceConfigurationType.class, configList);
				// get copy of server config and set the whole configuration
				// (entity will be switched to entity type configured)
				Entity<LLRPServerConfigurationType> entity = serverConf
						.acquire();
				currentServerConfig = entity.getObject();
				try {
					persistence.setServerConfiguration(currentServerConfig,
							config,
							serverConf.getPath().getParent().toString(), file
									.getInitialPath().getParent().toString());
				} catch (FileNotFoundException e) {
					throw new ConfigurationException(e);
				}
				serverConf.release(entity, /* write */false);
				// register as listener at server config
				serverConfListener = new ServerInstanceServerConfListener(this);
				serverConf.addListener(serverConfListener);
			}
		}
		if (log.isLoggable(Level.FINE)) {
			log.log(Level.FINE, "ServerInstanceConfiguration opened. EntityId of LLRPServerInstanceConfigurationType is {0}.", entityIdList);
		}
	}

	/**
	 * Removes all listener and close the persistence.
	 * 
	 * @throws EntityManagerException
	 */
	public void close() throws EntityManagerException {
		synchronized (lockListeners) {
			serverConf.removeListener(serverConfListener);
		}
		persistence.close();
	}

	/**
	 * Acquires the configuration object from persistence and returns it as
	 * entity.
	 * 
	 * @return The entity
	 * @throws EntityManagerException
	 */
	public Entity<LLRPServerInstanceConfigurationType> acquire()
			throws EntityManagerException {
		synchronized (lockListeners) {
			List<Entity<Object>> entities = persistence.acquire(entityIdList);
			return castObjectToType(entities.get(0));
		}
	}

	/**
	 * Generic casting.
	 * 
	 * @param entity
	 * @return The entity
	 */
	@SuppressWarnings("unchecked")
	private <T> Entity<LLRPServerInstanceConfigurationType> castObjectToType(
			Entity<T> entity) {
		return (Entity<LLRPServerInstanceConfigurationType>) entity;
	}

	/**
	 * Generic casting.
	 * 
	 * @param entity
	 * @return The entity
	 */
	@SuppressWarnings("unchecked")
	private <T> Entity<Object> castTypeToObject(Entity<T> entity) {
		return (Entity<Object>) entity;
	}

	/**
	 * If write is true, the server configuration will be set in persistence and
	 * will be stored. All listeners will be informed, that an update takes
	 * place in this case. The given entity will be released in persistence. The
	 * entity released at first will be noted. If release is called with other
	 * acquired entities an Exception will be thrown.
	 * 
	 * @param instanceEntity
	 *            Entity to be released
	 * @param write
	 *            Indicator, if entity has been changed or not
	 * @throws ConfigurationException
	 * @throws PersistenceException
	 * @throws WrongMetaDataException
	 */
	public void release(
			Entity<LLRPServerInstanceConfigurationType> instanceEntity,
			boolean write) throws EntityManagerException,
			ConfigurationException, PersistenceException {
		synchronized (lockListeners) {
			if (write) {
				if (log.isLoggable(Level.FINE)) {
					log.log(Level.FINE, "Release instance configuration and try to set set new server instance configuration.");
				}
				LLRPServerInstanceConfigurationType serverInstanceConfig = instanceEntity
						.getObject();
				try {
					persistence.setServerConfiguration(currentServerConfig,
							serverInstanceConfig, serverConf.getPath()
									.toString(), file.getInitialPath()
									.getParent().toString());
					file.save(serverInstanceConfig);
				} catch (Exception e) {
					throw new ConfigurationException(e);
				}
			}
		}
		// release entity in persistence
		Entity<Object> e = castTypeToObject(instanceEntity);
		List<Entity<Object>> entities = new ArrayList<>();
		entities.add(e);
		persistence.release(entities, write);

		if (write) {
			if (log.isLoggable(Level.FINE)) {
				log.log(Level.FINE, "Inform listeners about changed instance configuration.");
			}
			// inform all listener
			for (ServerInstanceConfigurationListener entry : listeners) {
				entry.updated(this, serverConf, /* exception */null);
			}
		}
	}

	/**
	 * Adds a listener. If the configuration is changed (write access),
	 * listeners will be informed.
	 * 
	 * @param listener
	 */
	public void addListener(ServerInstanceConfigurationListener listener) {
		listeners.add(listener);
	}

	/**
	 * Removes a listener.
	 * 
	 * @param listener
	 */
	public void removeListener(ServerInstanceConfigurationListener listener) {
		List<ServerInstanceConfigurationListener> removed = new ArrayList<ServerInstanceConfigurationListener>();
		for (ServerInstanceConfigurationListener entry : listeners) {
			if (listener == entry) {
				removed.add(entry);
			}
		}
		listeners.removeAll(removed);
	}

	/**
	 * Gets the persistence object from ServerInstanceConfiguration.
	 * <p>
	 * <strong>Attention!</strong>
	 * </p>
	 * <p>
	 * Use returned persistence only to distribute. Do not use any method of the
	 * persistence. Use the method provide by this class instead.
	 * </p>
	 * 
	 * @return The persistence object
	 */
	public ObservablePersistence getPersistence() {
		return persistence;
	}

}
