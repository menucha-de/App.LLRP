package havis.llrpservice.server.persistence;

import havis.llrpservice.common.entityManager.Entity;
import havis.llrpservice.common.entityManager.EntityGroup;
import havis.llrpservice.common.entityManager.EntityManager;
import havis.llrpservice.common.entityManager.EntityManagerException;
import havis.llrpservice.common.entityManager.FileEntityManager.FileProperty;
import havis.llrpservice.common.entityManager.InMemoryEntityManager;
import havis.llrpservice.common.entityManager.JSONFileEntityManager;
import havis.llrpservice.common.entityManager.JSONFileEntityManager.JsonProperty;
import havis.llrpservice.common.entityManager.JavaBinaryFileEntityManager;
import havis.llrpservice.common.entityManager.MissingPropertyException;
import havis.llrpservice.common.entityManager.StaleEntityStateException;
import havis.llrpservice.common.entityManager.UnknownEntityException;
import havis.llrpservice.common.entityManager.XMLFileEntityManager;
import havis.llrpservice.common.entityManager.XMLFileEntityManager.XmlProperty;
import havis.llrpservice.common.serializer.JsonSerializer;
import havis.llrpservice.server.configuration.ConfigurationException;
import havis.llrpservice.xml.configuration.CleanUpType;
import havis.llrpservice.xml.configuration.FileProperties;
import havis.llrpservice.xml.configuration.GroupIdType;
import havis.llrpservice.xml.configuration.JSONType;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;
import havis.llrpservice.xml.configuration.OutputType;
import havis.llrpservice.xml.configuration.PersistTimesType;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jibx.runtime.JiBXException;

/**
 * This class provides the handling of entity managers ({@link EntityManager}).
 * <p>
 * A configuration can be set which defines the handling behavior. Without a
 * configuration the added entities are not persisted (
 * {@link InMemoryEntityManager}).
 * </p>
 * <p>
 * To add an entity manager, meta information for the handled class type must be
 * registered with {@link #addClass(Class, String)}.
 * </p>
 * <p>
 * This class is thread safe.
 * </p>
 */
public class Persistence {

	private static final Logger log = Logger.getLogger(Persistence.class.getName());

	// Synchronization object to provide thread safety
	private final Object sync = new Object();

	/**
	 * Wraps a list of entities with state properties.
	 * 
	 * @param <T>
	 */
	private class EntitiesWrapper<T> {
		/**
		 * Manager with entity list.
		 */
		EntityManager<T> manager;
		Class<T> clazz = null;
		/**
		 * List of entityIds created by the {@link #manager} for added entities.
		 */
		List<String> entityIds = new ArrayList<>();
	}

	// Entities held by the persistence class. Maps a class name to the
	// associated entities.
	private Map<String, EntitiesWrapper<?>> entitiesWrappers = new HashMap<>();
	// Versions of class types held by the persistence class
	private Map<Class<?>, String> versions = new HashMap<>();
	// Analyses the configuration
	private final PersistenceConfigAnalyser config = new PersistenceConfigAnalyser();
	private boolean isOpened = false;

	/**
	 * Opens the persistence.
	 * 
	 * @throws EntityManagerException
	 */
	public synchronized void open() throws EntityManagerException {
		// open all existing entities managers
		for (EntitiesWrapper<?> currentWrapper : entitiesWrappers.values()) {
			currentWrapper.manager.open();
		}
		isOpened = true;
	}

	/**
	 * Closes the persistence.
	 * <p>
	 * The managed entities remain unchanged. Only connections to external
	 * resources are closed.
	 * </p>
	 * 
	 * @throws EntityManagerException
	 */
	public synchronized void close() throws EntityManagerException {
		// close all existing entities managers
		for (EntitiesWrapper<?> currentWrapper : entitiesWrappers.values()) {
			currentWrapper.manager.close();
		}
		isOpened = false;
	}

	/**
	 * Like
	 * {@link #setServerConfiguration(LLRPServerConfigurationType, LLRPServerInstanceConfigurationType, String, String)}
	 * but only the server configuration is set.
	 * 
	 * @param serverConfig
	 * @param serverConfigBaseDir
	 *            the absolute path to the directory with the server
	 *            configuration files
	 * @throws UnknownClassException
	 * @throws EntityManagerException
	 * @throws PersistenceException
	 * @throws ConfigurationException
	 */
	public synchronized void setServerConfiguration(LLRPServerConfigurationType serverConfig,
			String serverConfigBaseDir) throws UnknownClassException, EntityManagerException,
			PersistenceException, ConfigurationException {
		// Update ConfigAnalyser with new configuration
		setServerConfiguration(serverConfig, /* serverInstanceConfig */null, serverConfigBaseDir,
				/* instanceConfigBaseDir */null);
		reloadConfiguration();
	}

	/**
	 * Sets the configuration. All stored entities of each manager will be
	 * re-created with behavior defined in the configuration. Each recreated
	 * manager will store their entities under the same identifier.
	 * 
	 * @param serverConfig
	 * @param serverInstanceConfig
	 * @param serverConfigBaseDir
	 *            the absolute path to the directory with the server
	 *            configuration files
	 * @param instanceConfigBaseDir
	 *            the absolute path to the directory with the instance
	 *            configuration files
	 * @throws UnknownClassException
	 * @throws EntityManagerException
	 * @throws PersistenceException
	 * @throws ConfigurationException
	 */
	public synchronized void setServerConfiguration(LLRPServerConfigurationType serverConfig,
			LLRPServerInstanceConfigurationType serverInstanceConfig, String serverConfigBaseDir,
			String instanceConfigBaseDir) throws UnknownClassException, EntityManagerException,
			PersistenceException, ConfigurationException {
		// Update ConfigAnalyser with new configuration
		this.config.setServerConfig(serverConfig, serverConfigBaseDir);
		this.config.setServerInstanceConfig(serverInstanceConfig, instanceConfigBaseDir);
		reloadConfiguration();
	}

	/**
	 * Reloads the configuration.
	 * <p>
	 * The existing entity managers are closed. Their entities are moved to
	 * newly created entity managers. The new entity managers are opened if the
	 * persistence is opened.
	 * </p>
	 * 
	 * @throws UnknownClassException
	 * @throws MissingPropertyException
	 * @throws EntityManagerException
	 * @throws PersistenceException
	 * @throws ConfigurationException
	 */
	@SuppressWarnings("unchecked")
	private void reloadConfiguration() throws UnknownClassException, MissingPropertyException,
			EntityManagerException, PersistenceException, ConfigurationException {
		// Temporary buffer for initial entities
		// (class name => entityId => entity object)
		Map<Class<Object>, Map<String, Object>> allInitialEntities = new HashMap<>();

		// for all entities wrappers
		for (EntitiesWrapper<?> currentWrapper : entitiesWrappers.values()) {
			// Remove entities from manager
			List<?> entities = currentWrapper.manager.remove(currentWrapper.entityIds);
			if (isOpened) {
				// close the manager
				currentWrapper.manager.close();
			}
			// Map entityIds to their objects
			Map<String, Object> initialEntities = new HashMap<>();
			int i = 0;
			for (Object entity : entities) {
				initialEntities.put(currentWrapper.entityIds.get(i), entity);
				i++;
			}
			// Map class name to entities
			allInitialEntities.put((Class<Object>) currentWrapper.clazz, initialEntities);
		}

		// For all stored entities in local list
		for (Entry<Class<Object>, Map<String, Object>> entry : allInitialEntities.entrySet()) {
			// String className = entity.getKey();
			Class<Object> clazz = entry.getKey();
			Map<String, Object> initialEntities = entry.getValue();
			// create new wrapper with configured behavior
			EntitiesWrapper<Object> currentWrapper = createEntitiesWrapper(clazz, initialEntities);
			if (isOpened) {
				// open the manager
				currentWrapper.manager.open();
			}
			// replace old wrapper with new one
			entitiesWrappers.put(clazz.getName(), currentWrapper);
			if (log.isLoggable(Level.FINE)) {
				log.log(Level.FINE,
						"New configuration for manager " + clazz.getName() + " applied.");
			}
		}
	}

	/**
	 * Gets an entities wrapper. The class for the wrapper must have been
	 * registered with {@link #addClass(Class, String)} before.
	 * 
	 * @param clazz
	 * @return The wrapped entities
	 * @throws UnknownClassException
	 */
	@SuppressWarnings("unchecked")
	private <T> EntitiesWrapper<T> getEntitiesWrapper(Class<T> clazz) throws UnknownClassException {
		// get entities wrapper
		String className = clazz.getName();
		EntitiesWrapper<T> entitiesWrapper = (EntitiesWrapper<T>) entitiesWrappers.get(className);
		// entities wrappers are creates while adding a class
		if (entitiesWrapper == null) {
			throw new UnknownClassException("Unknown class " + className);
		}
		return entitiesWrapper;
	}

	/**
	 * Creates an entities wrapper. The wrapper is <em>not</em> added to the
	 * local list.
	 * 
	 * @param clazz
	 * @param initialEntities
	 * @return The wrapped entities
	 * @throws UnknownClassException
	 * @throws MissingPropertyException
	 * @throws EntityManagerException
	 * @throws PersistenceException
	 * @throws ConfigurationException
	 */
	@SuppressWarnings("unchecked")
	private <T> EntitiesWrapper<T> createEntitiesWrapper(Class<T> clazz,
			Map<String, T> initialEntities) throws UnknownClassException, MissingPropertyException,
			EntityManagerException, PersistenceException, ConfigurationException {
		// Create new wrapper
		EntitiesWrapper<T> currentWrapper = new EntitiesWrapper<T>();
		// Set entities wrapper class (type)
		currentWrapper.clazz = clazz;
		// Add initial entities
		if (initialEntities != null) {
			currentWrapper.entityIds.addAll(initialEntities.keySet());
		}

		// Get output out of configuration
		String className = clazz.getName();
		OutputType output = config.getOutput(className);
		// Get persistence time out of configuration
		PersistTimesType times = config.getTimes(className);
		// if entities shall not be persisted
		if (output == null || times == null || (!times.isAfterChanges() && !times.isManual())) {
			if (initialEntities == null) {
				// Create empty manager
				currentWrapper.manager = new InMemoryEntityManager<T>();
			} else {
				// Create manager with initialEntities
				currentWrapper.manager = new InMemoryEntityManager<T>(initialEntities);
			}
		} else {
			String version = versions.get(clazz);
			FileProperties outProperties = output.getFile().getFileProperties();

			// Create property map for FileEntityManager
			Map<FileProperty, Object> fileProperties = new HashMap<>();
			fileProperties.put(FileProperty.BASEDIR, outProperties.getBaseDir());
			// XML file type
			if (outProperties.getType().ifXML()) {
				// Create property map for XML-Type
				Map<XmlProperty, Object> xmlProperties = new HashMap<>();
				// Set encoding
				switch (outProperties.getType().getXML().getEncoding()) {
				case UT_F8:
					xmlProperties.put(XmlProperty.ENCODING, StandardCharsets.UTF_8);
					break;
				}
				try {
					// Create manager
					if (initialEntities == null) {
						currentWrapper.manager = new XMLFileEntityManager<T>(clazz, version,
								fileProperties, xmlProperties);
					} else {
						currentWrapper.manager = new XMLFileEntityManager<T>(clazz, version,
								fileProperties, xmlProperties, initialEntities);
					}
				} catch (JiBXException e) {
					throw new EntityManagerException(e);
				}
			} // JSON File type
			else if (outProperties.getType().ifJSON()) {
				// Create property map for JSON-Type
				Map<JsonProperty, Object> jsonProperties = new HashMap<>();
				JSONType type = outProperties.getType().getJSON();
				// Set encoding
				switch (type.getEncoding()) {
				case UT_F8:
					jsonProperties.put(JsonProperty.ENCODING, StandardCharsets.UTF_8);
					break;
				}

				if (type.getMixInClassName() != null) {
					Map<Class<?>, Class<?>> mixIns;
					// Reflection
					try {
						Class<?> cls = Class.forName(type.getMixInClassName().trim());
						mixIns = (Map<Class<?>, Class<?>>) cls.newInstance();
					} catch (Exception e) {
						throw new ConfigurationException(e);
					}
					// Create serializer with mixins
					JsonSerializer serializer = new JsonSerializer(clazz);
					serializer.addDeserializerMixIns(mixIns);
					serializer.addSerializerMixIns(mixIns);
					// Set serializer as property
					jsonProperties.put(JsonProperty.SERIALIZER, serializer);
				}

				// Create Manager
				if (initialEntities == null) {
					currentWrapper.manager = new JSONFileEntityManager<T>(clazz, version,
							fileProperties, jsonProperties);
				} else {
					currentWrapper.manager = new JSONFileEntityManager<T>(clazz, version,
							fileProperties, jsonProperties, initialEntities);
				}
			} // other types
			else {
				// Other types result in JavaBinaryManager
				if (initialEntities == null) {
					currentWrapper.manager = new JavaBinaryFileEntityManager<T>(clazz, version,
							fileProperties);
				} else {
					currentWrapper.manager = new JavaBinaryFileEntityManager<T>(clazz, version,
							fileProperties, initialEntities);
				}
			}
		}

		return currentWrapper;
	}

	/**
	 * Gets the entities wrappers for a list of entityIds.
	 * 
	 * @param entityIds
	 * @return map of entities wrappers to entityIds
	 * @throws UnknownEntityException
	 */
	private Map<EntitiesWrapper<?>, List<String>> getEntityWrappers(Collection<String> entityIds)
			throws UnknownEntityException {
		int remainingEntityIdsCount = entityIds.size();
		Map<EntitiesWrapper<?>, List<String>> result = new HashMap<>();
		// Walk through local entities
		for (EntitiesWrapper<?> currentWrapper : entitiesWrappers.values()) {
			// Entities existing in wrapper
			List<String> accessedEntities = new ArrayList<>();
			for (String entityId : entityIds) {
				if (currentWrapper.entityIds.contains(entityId)) {
					accessedEntities.add(entityId);
				}
			}
			if (accessedEntities.size() > 0) {
				result.put(currentWrapper, accessedEntities);
				remainingEntityIdsCount -= accessedEntities.size();
			}
		}
		if (remainingEntityIdsCount > 0) {
			throw new UnknownEntityException(
					"Found " + remainingEntityIdsCount + " unknown entities");
		}
		return result;
	}

	/**
	 * Adds entities and returns generated entity identifiers.
	 * 
	 * @param clazz
	 * @param entities
	 * @return The entities
	 * @throws UnknownClassException
	 * @throws EntityManagerException
	 */
	public synchronized <T> List<String> add(Class<T> clazz, List<T> entities)
			throws UnknownClassException, EntityManagerException {
		// get existing wrapper or create an empty one
		EntitiesWrapper<T> currentWrapper = getEntitiesWrapper(clazz);
		// Add entities to manager
		List<String> entityIds = currentWrapper.manager.add(entities);
		// put generated entityIds to wrapper
		currentWrapper.entityIds.addAll(entityIds);
		if (log.isLoggable(Level.FINE)) {
			log.log(Level.FINE, "Entities added to manager " + clazz.getName());
		}
		// if flushing after changes is activated
		if (canBeFlushed(currentWrapper, /* externalCall */false)) {
			flush(currentWrapper, /* externalCall */false);
		}
		return entityIds;
	}

	/**
	 * Removes entities and returns them.
	 * 
	 * @param entityIds
	 * @return The entities
	 * @throws EntityManagerException
	 */
	public synchronized List<Object> remove(List<String> entityIds) throws EntityManagerException {
		List<Object> entities = new ArrayList<>();
		// group the entityIds by the relating entities wrappers
		Map<EntitiesWrapper<?>, List<String>> entries = getEntityWrappers(entityIds);
		// for each entities wrapper
		for (Entry<EntitiesWrapper<?>, List<String>> entry : entries.entrySet()) {
			EntitiesWrapper<?> currentWrapper = entry.getKey();
			List<String> currentEntityIds = entry.getValue();
			// remove entities
			List<?> currentEntities = currentWrapper.manager.remove(currentEntityIds);
			entities.addAll(currentEntities);
			currentWrapper.entityIds.removeAll(currentEntityIds);
			// if flushing after changes is activated
			if (canBeFlushed(currentWrapper, /* externalCall */false)) {
				flush(currentWrapper, /* externalCall */false);
			}
			if (log.isLoggable(Level.FINE)) {
				log.log(Level.FINE,
						"Entities removed from manager " + currentWrapper.clazz.getName());
			}
		}
		return entities;
	}

	/**
	 * Determines whether an entities wrapper can be flushed.
	 * 
	 * @param currentWrapper
	 * @param externalCall
	 * @return True if can be flushed, false otherwise
	 */
	private boolean canBeFlushed(EntitiesWrapper<?> currentWrapper, boolean externalCall) {
		PersistTimesType times = config.getTimes(currentWrapper.clazz.getName());
		return times != null // a persistence config exists
				// for external calls the "manual" flag is set
				&& (externalCall && times.isManual()
						// for internal calls the "afterChanges" flag is set
						|| !externalCall && times.isAfterChanges());
	}

	/**
	 * Performs a manually flush of entities to the storage.
	 * <p>
	 * If the manually flushing is disabled in the configuration then nothing is
	 * done.
	 * </p>
	 * 
	 * @param clazz
	 * @return <code>null</code> if manual flushing is disabled
	 * @throws UnknownClassException
	 * @throws EntityManagerException
	 */
	public synchronized String flush(Class<?> clazz)
			throws UnknownClassException, EntityManagerException {
		EntitiesWrapper<?> currentWrapper = getEntitiesWrapper(clazz);
		// if manual flushing is activated
		if (canBeFlushed(currentWrapper, /* externalCall */true)) {
			return flush(currentWrapper, /* externalCall */true);
		}
		return null;
	}

	/**
	 * Writes loaded entities as a group to the storage. If the group does not
	 * exists it is created else the group will be overwritten. The group
	 * identifier of the flushed entities is returned.
	 * 
	 * @param currentWrapper
	 * @param externalCall
	 * @return <code>null</code> if flushing is disabled
	 * @throws EntityManagerException
	 */
	private String flush(EntitiesWrapper<?> currentWrapper, boolean externalCall)
			throws EntityManagerException {
		String className = currentWrapper.clazz.getName();
		String result = null;
		// Get groupId definition out of configuration
		GroupIdType groupId = config.getGroupId(className);
		if (groupId != null) {
			// Create a date out of configuration given format (in UTC)
			SimpleDateFormat formatter = new SimpleDateFormat(groupId.getBaseFormat());
			formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
			result = formatter.format(new Date());
			if (groupId.getSuffix() != null) {
				if (groupId.getSuffix().isSerialNumber()) {
					result += UUID.randomUUID().toString().replace("-", "");
				}
			}
			// Flush entities with groupId
			currentWrapper.manager.flush(result, currentWrapper.entityIds);

			// If clean up after flush is activated
			if (canBeCleanedUp(currentWrapper, /* externalCall */false)) {
				cleanUp(currentWrapper, /* externalCall */false);
			}

			if (log.isLoggable(Level.INFO)) {
				log.log(Level.INFO, "Manager " + className + " flushed ("
						+ (externalCall ? "manually" : "automatically") + ")");
			}
		}
		return result;
	}

	/**
	 * Loads all entities of a group from the storage and replaces the currently
	 * loaded entities. Entities, which are not part of the group, are retained
	 * unchanged. The entity identifiers of the loaded group are returned.
	 * 
	 * @param clazz
	 * @param groupId
	 * @return The entities
	 * @throws UnknownClassException
	 * @throws EntityManagerException
	 */
	public synchronized <T> List<String> refresh(Class<T> clazz, String groupId)
			throws UnknownClassException, EntityManagerException {
		// Get entities wrapper or create one, if not exist
		EntitiesWrapper<?> currentWrapper = getEntitiesWrapper(clazz);
		// Add refreshed entityIds to result
		List<String> entityIds = currentWrapper.manager.refresh(groupId);
		// Refresh local list
		currentWrapper.entityIds.removeAll(entityIds);
		currentWrapper.entityIds.addAll(entityIds);
		if (log.isLoggable(Level.INFO)) {
			log.log(Level.INFO, "Entities of manager " + clazz.getName()
					+ " refreshed with data of group " + groupId);
		}
		return entityIds;
	}

	/**
	 * Gets informations of existing groups of a class in the storage. If no
	 * group exists, an empty array is returned.
	 * 
	 * @param clazz
	 * @return The entity groups
	 * @throws UnknownClassException
	 * @throws EntityManagerException
	 */
	public synchronized <T> List<EntityGroup> getGroups(Class<T> clazz)
			throws UnknownClassException, EntityManagerException {
		// Get entities wrapper or create one, if not exist
		EntitiesWrapper<?> currentWrapper = getEntitiesWrapper(clazz);
		return currentWrapper.manager.getGroups();
	}

	/**
	 * Deletes a group for a class from the storage. Loaded entities are
	 * retained unchanged.
	 * 
	 * @param clazz
	 * @param groupId
	 * @throws UnknownClassException
	 * @throws EntityManagerException
	 */
	public synchronized <T> void delete(Class<T> clazz, String groupId)
			throws UnknownClassException, EntityManagerException {
		// Get entities wrapper or create one, if not exist
		EntitiesWrapper<?> currentWrapper = getEntitiesWrapper(clazz);
		currentWrapper.manager.delete(groupId);
		if (log.isLoggable(Level.INFO)) {
			log.log(Level.INFO, "Group " + groupId + " of manager " + clazz.getName() + " deleted");
		}
	}

	/**
	 * Determines whether an entities wrapper can be cleaned up.
	 * 
	 * @param currentWrapper
	 * @param externalCall
	 * @return True if can be cleaned up, false otherwise
	 */
	private boolean canBeCleanedUp(EntitiesWrapper<?> currentWrapper, boolean externalCall) {
		CleanUpType cleanUp = config.getCleanUp(currentWrapper.clazz.getName());
		return cleanUp != null // config exists for clean up
				// for external calls the "manual" flag is set
				&& (externalCall && cleanUp.getTimes().isManual()
						// for internal calls the "afterFlush" flag is set
						|| !externalCall && cleanUp.getTimes().isAfterFlush());
	}

	/**
	 * Performs a manually clean up.
	 * <p>
	 * If the manually clean up is disabled in the configuration then nothing is
	 * done.
	 * </p>
	 * 
	 * @param clazz
	 * @throws UnknownClassException
	 * @throws EntityManagerException
	 */
	public synchronized void cleanUp(Class<?> clazz)
			throws UnknownClassException, EntityManagerException {
		EntitiesWrapper<?> currentWrapper = getEntitiesWrapper(clazz);
		// If manual clean up is activated
		if (canBeCleanedUp(currentWrapper, /* externalCall */true)) {
			cleanUp(currentWrapper, /* externalCall */true);
		}
	}

	/**
	 * Performs a clean up for an entities wrapper.
	 * 
	 * @param clazz
	 * @param externalCall
	 * @throws EntityManagerException
	 */
	private void cleanUp(EntitiesWrapper<?> currentWrapper, boolean externalCall)
			throws EntityManagerException {
		String className = currentWrapper.clazz.getName();
		// Get clean up parameters out of configuration
		CleanUpType cleanUp = config.getCleanUp(className);
		if (cleanUp != null) {
			Integer cleanUpInterval = cleanUp.getMaxCreationDateInterval();

			// Current date
			long now = System.currentTimeMillis();

			// Delete all groups, which fit the clean up parameters
			for (EntityGroup group : currentWrapper.manager.getGroups()) {
				if (cleanUpInterval == null || cleanUpInterval == 0
						|| ((now - group.getCreationDate().getTime()) / 1000.0) > cleanUpInterval) {
					currentWrapper.manager.delete(group.getGroupId());
				}
			}
			if (log.isLoggable(Level.INFO)) {
				log.log(Level.INFO, "Clean up for manager " + className + " performed ("
						+ (externalCall ? "manually" : "automatically") + ")");
			}
		}
	}

	/**
	 * Acquires loaded entities.
	 * <p>
	 * Modifications of the entities can be applied with
	 * {@link #release(List, boolean)}.
	 * </p>
	 * 
	 * @param entityIds
	 * @return The entities
	 * @throws UnknownEntityException
	 * @throws EntityManagerException
	 */
	@SuppressWarnings("unchecked")
	public synchronized List<Entity<Object>> acquire(List<String> entityIds)
			throws UnknownEntityException, EntityManagerException {
		List<Entity<Object>> localEntities = new ArrayList<>();
		// group the entityIds by the relating entities wrappers
		Map<EntitiesWrapper<?>, List<String>> entries = getEntityWrappers(entityIds);
		// for each entities wrapper
		for (Entry<EntitiesWrapper<?>, List<String>> entry : entries.entrySet()) {
			EntitiesWrapper<?> currentWrapper = entry.getKey();
			List<String> currentEntityIds = entry.getValue();
			// acquire the entities
			List<?> entities = currentWrapper.manager.acquire(currentEntityIds);
			localEntities.addAll((List<Entity<Object>>) entities);
			if (log.isLoggable(Level.FINE)) {
				log.log(Level.FINE, "Entities acquired for manager [0}",
						currentWrapper.clazz.getName());
			}
		}
		// reorder the entities and return them
		List<Entity<Object>> result = new ArrayList<>();
		for (String entityId : entityIds) {
			for (Entity<Object> entity : localEntities) {
				if (entity.getEntityId().equals(entityId)) {
					result.add(entity);
					break;
				}
			}
		}
		return result;
	}

	/**
	 * Releases entities.
	 * <p>
	 * If modifications shall be written and the loaded entity has not been
	 * changed since the entity was acquired then the entity is replaced with
	 * the given entity clone else the writing fails with
	 * {@link StaleEntityStateException} (optimistic locking).
	 * </p>
	 * 
	 * @param entities
	 * @throws UnknownEntityException
	 * @throws EntityManagerException
	 */
	@SuppressWarnings("unchecked")
	public synchronized <T> void release(List<Entity<Object>> entities, boolean write)
			throws UnknownEntityException, EntityManagerException {
		// map entityId to entity
		Map<String, Entity<Object>> entityMap = new HashMap<>();
		for (Entity<Object> entity : entities) {
			entityMap.put(entity.getEntityId(), entity);
		}
		// group the entityIds by the relating entities wrappers
		Map<EntitiesWrapper<?>, List<String>> entries = getEntityWrappers(entityMap.keySet());
		// for each entities wrapper
		for (Entry<EntitiesWrapper<?>, List<String>> entry : entries.entrySet()) {
			EntitiesWrapper<?> currentWrapper = entry.getKey();
			List<String> currentEntityIds = entry.getValue();
			// get entities for entityIds
			List<Entity<T>> currentEntities = new ArrayList<>();
			for (String currentEntityId : currentEntityIds) {
				// unchecked cast: all entities of current manager have the same
				// type as the manager itself
				currentEntities.add((Entity<T>) entityMap.get(currentEntityId));
			}
			// release the entities
			EntityManager<T> manager = (EntityManager<T>) currentWrapper.manager;
			manager.release(currentEntities, write);
			// if entities have been changed then flush the entity manager
			if (write && canBeFlushed(currentWrapper, /* externalCall */false)) {
				flush(currentWrapper, /* externalCall */false);
			}
			if (log.isLoggable(Level.FINE)) {
				log.log(Level.FINE,
						"Entities released for manager " + currentWrapper.clazz.getName());
			}
		}
	}

	/**
	 * Adds a class with meta informations.
	 * 
	 * @param clazz
	 * @param version
	 * @throws UnknownClassException
	 * @throws MissingPropertyException
	 * @throws EntityManagerException
	 * @throws PersistenceException
	 * @throws ConfigurationException
	 */
	public <T> void addClass(Class<T> clazz, String version)
			throws UnknownClassException, MissingPropertyException, EntityManagerException,
			PersistenceException, ConfigurationException {
		synchronized (sync) {
			// add meta infos
			versions.put(clazz, version);
			// create an entities wrapper
			EntitiesWrapper<T> currentWrapper = createEntitiesWrapper(clazz, /* initialEntities */
					null);
			if (isOpened) {
				// open the manager
				currentWrapper.manager.open();
			}
			// add the wrapper to local list
			entitiesWrappers.put(clazz.getName(), currentWrapper);
		}
	}

	/**
	 * Removes a class.
	 * 
	 * @param clazz
	 * @throws EntityManagerException
	 * @throws UnknownClassException
	 */
	public <T> void removeClass(Class<T> clazz)
			throws EntityManagerException, UnknownClassException {
		synchronized (sync) {
			// remove the wrapper from local list
			EntitiesWrapper<?> entitiesWrapper = entitiesWrappers.remove(clazz.getName());
			if (entitiesWrapper == null) {
				throw new UnknownClassException("Unknown class " + clazz.getName());
			}
			if (isOpened) {
				// close the manager
				entitiesWrapper.manager.close();
			}
			// remove meta infos
			versions.remove(clazz);
		}
	}
}
