package havis.llrpservice.server.configuration;

import havis.llrpservice.common.entityManager.Entity;
import havis.llrpservice.common.entityManager.EntityManagerException;
import havis.llrpservice.common.entityManager.UnknownEntityException;
import havis.llrpservice.common.entityManager.WrongMetaDataException;
import havis.llrpservice.common.io.XMLFile;
import havis.llrpservice.server.persistence.ClassVersions;
import havis.llrpservice.server.persistence.ObservablePersistence;
import havis.llrpservice.server.persistence.PersistenceException;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;

import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ServerConfiguration class simplifies the usage of LLRPServerConfigurationType
 * object added to corresponding persistence.
 */
public class ServerConfiguration {

	private static final Logger log = Logger.getLogger(ServerConfiguration.class.getName());

	public static final String CLASS_VERSION = "1.0";

	// List of listeners listen for configuration changes
	private List<ServerConfigurationListener> listeners;
	private final Object lockListeners = new Object();
	private ObservablePersistence persistence;
	// XMLFile from type LLRPServerConfigurationType
	private XMLFile<LLRPServerConfigurationType> file;
	// Current entity as list (List contains only one value)
	private List<String> entityIdList;

	/**
	 * Assigns file Object. Create instances.
	 * 
	 * @param file
	 *            XMLFile to handle the content.
	 */
	public ServerConfiguration(XMLFile<LLRPServerConfigurationType> file) {
		this.file = file;
		listeners = new CopyOnWriteArrayList<ServerConfigurationListener>();
		persistence = new ObservablePersistence();
	}

	/**
	 * Reads the content from file and sets this content to persistence as
	 * entity. Afterwards, the content will be set as configuration to
	 * persistence. Opens the persistence.
	 * 
	 * @throws ConfigurationException
	 * @throws EntityManagerException
	 * @throws PersistenceException
	 * @throws WrongMetaDataException
	 */
	public void open() throws ConfigurationException, EntityManagerException, PersistenceException {
		List<LLRPServerConfigurationType> configList = new ArrayList<LLRPServerConfigurationType>();
		synchronized (lockListeners) {
			LLRPServerConfigurationType config = null;
			try {
				// Get content from file
				config = file.getContent();
			} catch (Throwable t) {
				throw new ConfigurationException(t);
			}
			configList.add(config);
			persistence.open();
			// If there is no entity in persistence (protection for multiple
			// open call)
			if (entityIdList == null) {
				// Set class version
				persistence.addClass(LLRPServerConfigurationType.class,
						ClassVersions.get(LLRPServerConfigurationType.class));
				// Add entity (InMemoryManager)
				entityIdList = persistence.add(LLRPServerConfigurationType.class, configList);
				// Set configuration (entity will be switched to entity type
				// configured)

				persistence.setServerConfiguration(config,
						file.getInitialPath().getParent().toString());

			}
		}
		if (log.isLoggable(Level.FINE)) {
			log.log(Level.FINE,
					"ServerConfiguration opened. EntityId of LLRPServerConfigurationType is "
							+ entityIdList);
		}
	}

	/**
	 * Closes the persistence.
	 * 
	 * @throws EntityManagerException
	 */
	public void close() throws EntityManagerException {
		persistence.close();
	}

	/**
	 * Acquires the configuration object from persistence and returns it as
	 * entity.
	 * 
	 * @return The entity
	 * @throws EntityManagerException
	 * @throws UnknownEntityException
	 */
	public Entity<LLRPServerConfigurationType> acquire() throws EntityManagerException {
		synchronized (lockListeners) {
			List<Entity<Object>> entities = null;

			entities = persistence.acquire(entityIdList);

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
	private <T> Entity<LLRPServerConfigurationType> castObjectToType(Entity<T> entity) {
		return (Entity<LLRPServerConfigurationType>) entity;
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
	 * @param entity
	 *            Entity to be released
	 * @param write
	 *            Indicator, if entity has been changed or not
	 * @throws EntityManagerException
	 * @throws ConfigurationException
	 * @throws PersistenceException
	 * @throws WrongMetaDataException
	 */
	public void release(Entity<LLRPServerConfigurationType> entity, boolean write)
			throws EntityManagerException, ConfigurationException, PersistenceException {
		synchronized (lockListeners) {
			if (write) {
				if (log.isLoggable(Level.FINE)) {
					log.log(Level.FINE,
							"Release configuration and try to set set new server configuration.");
				}
				// If configuration changed, set configuration to persistence
				LLRPServerConfigurationType serverConf = entity.getObject();
				try {
					persistence.setServerConfiguration(serverConf,
							file.getInitialPath().getParent().toString());
					// Save file to latest path
					file.save(serverConf);
				} catch (Exception e) {
					throw new ConfigurationException(e);
				}
			}
		}
		// release entity in persistence
		Entity<Object> e = castTypeToObject(entity);
		List<Entity<Object>> entities = new ArrayList<>();
		entities.add(e);
		persistence.release(entities, write);

		if (write) {
			if (log.isLoggable(Level.FINE)) {
				log.log(Level.FINE, "Inform listeners about changed configuration.");
			}
			// inform all listener
			for (ServerConfigurationListener entry : listeners) {
				entry.updated(this);
			}
		}
	}

	/**
	 * Adds a listener to ServerConfiguration. If configuration is changed
	 * (write access), listeners will be informed.
	 * 
	 * @param listener
	 */
	public void addListener(ServerConfigurationListener listener) {
		listeners.add(listener);
	}

	/**
	 * Removes listener from ServerConfiguration
	 * 
	 * @param listener
	 */
	public void removeListener(ServerConfigurationListener listener) {
		List<ServerConfigurationListener> removed = new ArrayList<ServerConfigurationListener>();
		for (ServerConfigurationListener entry : listeners) {
			if (listener == entry) {
				removed.add(entry);
			}
		}
		listeners.removeAll(removed);
	}

	/**
	 * Gets the persistence object from ServerConfiguration.
	 * <p>
	 * <strong>Attention!
	 * </p>
	 * </strong>
	 * <p>
	 * Use returned persistence only to distribute. Do not use any method of the
	 * persistence. Use the method provide by this class instead.
	 * 
	 * </p>
	 * 
	 * @return The persistence object
	 */
	public ObservablePersistence getPersistence() {
		return persistence;
	}

	public Path getPath() throws FileNotFoundException {
		synchronized (lockListeners) {
			return file.getInitialPath();
		}
	}

}
