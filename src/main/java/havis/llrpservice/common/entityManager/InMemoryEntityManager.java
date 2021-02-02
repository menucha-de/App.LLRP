package havis.llrpservice.common.entityManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.rits.cloning.Cloner;

/**
 * This class is a implementation of EntityManager, which provides in memory
 * entity management. It only provides internal lock and unlock mechanisms.
 * Persistence functionality is not provided.
 * 
 * @param <T>
 *            Class type handled by EntityManager
 */

public class InMemoryEntityManager<T> implements EntityManager<T> {

	private static final Logger log = Logger.getLogger(InMemoryEntityManager.class.getName());

	private class InternalEntity {
		// Entity Object
		private T obj;

		public InternalEntity(T obj) {
			this.obj = obj;
		}
	}

	private Map<String, InternalEntity> entities = new HashMap<>();
	private Cloner cloner = new Cloner();

	public InMemoryEntityManager() {
	}

	public InMemoryEntityManager(Map<String, T> initialEntities) {
		this();
		if (initialEntities.size() > 0) {
			for (Entry<String, T> entity : initialEntities.entrySet()) {
				entities.put(entity.getKey(),
						new InternalEntity(entity.getValue()));
			}
			log.log(Level.FINE, "Initial entities added: {0}", initialEntities.keySet());
		}
	}

	@Override
	public synchronized void open() throws EntityManagerException {
	}

	@Override
	public void close() throws EntityManagerException {
	}

	@Override
	public synchronized List<String> add(List<T> entities) {
		List<String> entityIds = new ArrayList<String>();
		for (T entity : entities) {
			// Calculate entity ID
			String entityId = UUID.randomUUID().toString().replace("-", "");
			entityIds.add(entityId);
			// Store entity in entity map
			this.entities.put(entityId, new InternalEntity(entity));
		}
		if (entities.size() > 0) {
			log.log(Level.FINE, "Entities added: {0}", entityIds);
		}
		return entityIds;
	}

	@Override
	public synchronized List<T> remove(List<String> entityIds)
			throws UnknownEntityException {
		List<T> result = new ArrayList<T>();
		// check if entities are managed
		entitiesManaged(entityIds);
		for (String entityId : entityIds) {
			// Store the object temporally
			InternalEntity entity = entities.remove(entityId);
			result.add(entity.obj);
		}
		log.log(Level.FINE, "Entities removed: {0}", entityIds);
		return result;
	}

	@Override
	public synchronized void flush(String groupId, List<String> entityIds) {
	}

	@Override
	public synchronized List<String> refresh(String groupId) {
		return new ArrayList<String>();
	}

	@Override
	public synchronized List<EntityGroup> getGroups() {
		return new ArrayList<EntityGroup>();
	}

	@Override
	public synchronized void delete(String groupId) {
	}

	@Override
	public synchronized List<Entity<T>> acquire(List<String> entityIds)
			throws UnknownEntityException {
		List<Entity<T>> result = new ArrayList<>();
		// check if entities are managed
		entitiesManaged(entityIds);
		for (String entityId : entityIds) {
			InternalEntity currentEntity = entities.get(entityId);
			// create a clone of the entity object
			T clone = cloner.deepClone(currentEntity.obj);
			// add an entity with a reference to the original object and a clone
			// to the result list
			Entity<T> entity = new Entity<>(entityId, currentEntity.obj, clone);
			result.add(entity);
		}
		log.log(Level.INFO, "Entities acquired: {0}", entityIds);
		return result;
	}

	@Override
	public synchronized void release(List<Entity<T>> entities, boolean write)
			throws UnknownEntityException, StaleEntityStateException {
		// check if entities are managed
		List<String> entityIds = new ArrayList<>();
		for (Entity<T> entity : entities) {
			entityIds.add(entity.getEntityId());
		}
		entitiesManaged(entityIds);
		// if entities shall be replaced
		if (write) {
			Map<InternalEntity, T> objClones = new HashMap<>();
			// for each entity
			for (Entity<T> entity : entities) {
				// get current entity
				InternalEntity currentEntity = this.entities.get(entity
						.getEntityId());
				// if the entity has not been changed since the entity has been
				// acquired
				if (entity.getSourceObject() == currentEntity.obj) {
					// save the clone of the current entity object
					objClones.put(currentEntity, entity.getObject());
				} else {
					throw new StaleEntityStateException(
							"Entity "
									+ entity.getEntityId()
									+ " cannot be replaced because the entity was already changed otherwise");
				}
			}
			for (Entry<InternalEntity, T> entry : objClones.entrySet()) {
				InternalEntity currentEntity = entry.getKey();
				T objClone = entry.getValue();
				// replace entity object with clone
				currentEntity.obj = objClone;
			}
		}
		log.log(Level.INFO, "Entities released: {0}", entityIds);		
	}

	/**
	 * Checks if entities are managed. If an entity is not managed an exception
	 * is thrown.
	 * 
	 * @param entityIds
	 * @throws UnknownEntityException
	 */
	private void entitiesManaged(List<String> entityIds)
			throws UnknownEntityException {
		for (String entityId : entityIds) {
			// If not entity exists
			if (!entities.containsKey(entityId)) {
				throw new UnknownEntityException("Entity unknown " + entityId);
			}
		}
	}
}
