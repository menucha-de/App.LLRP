package havis.llrpservice.server.persistence;

import havis.llrpservice.common.entityManager.Entity;
import havis.llrpservice.common.entityManager.EntityManagerException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Implements an observable {@link Persistence}.
 * <p>
 * Events are fired for all listener implementations which have been registered
 * with {@link #addListener(PersistenceListener, Class)}.
 * </p>
 * <p>
 * This class is thread safe.
 * </p>
 */
public class ObservablePersistence extends Persistence {
	private Map<Class<?>, List<PersistenceListener>> listeners;

	public ObservablePersistence() {
		listeners = new HashMap<Class<?>, List<PersistenceListener>>();
	}

	/**
	 * Adds a listener.
	 * 
	 * @param listener
	 * @param clazz
	 */
	public synchronized <T> void addListener(PersistenceListener listener, Class<T> clazz) {
		List<PersistenceListener> list = listeners.get(clazz);
		if (list == null) {
			list = new CopyOnWriteArrayList<PersistenceListener>();
			listeners.put(clazz, list);
		}
		list.add(listener);
	}

	/**
	 * Removes a listener.
	 * 
	 * @param listener
	 * @param clazz
	 */
	public synchronized <T> void removeListener(PersistenceListener listener, Class<T> clazz) {
		List<PersistenceListener> list = getListeners(clazz);
		List<PersistenceListener> removed = new ArrayList<PersistenceListener>();
		if (list != null) {
			for (PersistenceListener entry : list) {
				if (listener == entry) {
					removed.add(entry);
				}
			}
			list.removeAll(removed);
		}
	}

	/**
	 * See {@link Persistence#add(Class, List)}.
	 * <p>
	 * An event {@link PersistenceListener#added(ObservablePersistence, List)}
	 * is sent to all listeners.
	 * </p>
	 */
	@Override
	public <T> List<String> add(Class<T> clazz, List<T> entities)
			throws UnknownClassException, EntityManagerException {
		List<String> result = super.add(clazz, entities);
		if (result.size() > 0) {
			List<PersistenceListener> list = getListeners(clazz);
			if (list != null) {
				for (PersistenceListener listener : list) {
					listener.added(this, result);
				}
			}
		}
		return result;
	}

	/**
	 * See {@link Persistence#remove(List)}.
	 * <p>
	 * An event {@link PersistenceListener#removed(ObservablePersistence, List)}
	 * is sent to all listeners.
	 * </p>
	 */
	@Override
	public List<Object> remove(List<String> entityIds) throws EntityManagerException {
		List<Object> result = super.remove(entityIds);
		Map<Class<?>, List<String>> idsMap = new HashMap<Class<?>, List<String>>();

		int entryIndex = 0;
		for (Object entry : result) {
			Class<?> clazz = entry.getClass();
			String id = entityIds.get(entryIndex);
			List<String> ids = idsMap.get(clazz);
			if (ids == null) {
				ids = new ArrayList<String>();
				idsMap.put(clazz, ids);
			}
			ids.add(id);

			entryIndex++;
		}

		for (Entry<Class<?>, List<String>> entry : idsMap.entrySet()) {
			List<String> currentEntityIds = entry.getValue();
			Class<?> clazz = entry.getKey();
			if (currentEntityIds.size() > 0) {
				List<PersistenceListener> list = getListeners(clazz);
				if (list != null) {
					for (PersistenceListener listener : list) {
						listener.removed(this, currentEntityIds);
					}
				}
			}
		}
		return result;
	}

	/**
	 * See {@link Persistence#refresh(Class, String)}.
	 * <p>
	 * An event {@link PersistenceListener#updated(ObservablePersistence, List)}
	 * is sent to all listeners.
	 * </p>
	 */
	@Override
	public <T> List<String> refresh(Class<T> clazz, String groupId)
			throws UnknownClassException, EntityManagerException {
		List<String> entityIds = super.refresh(clazz, groupId);
		if (entityIds.size() > 0) {
			List<PersistenceListener> list = getListeners(clazz);
			if (list != null) {
				for (PersistenceListener listener : list) {
					listener.updated(this, entityIds);
				}
			}
		}
		return entityIds;
	}

	/**
	 * See {@link Persistence#release(List, boolean)}.
	 * <p>
	 * If modifications are written then an event
	 * {@link PersistenceListener#updated(ObservablePersistence, List)} is sent
	 * to all listeners.
	 * </p>
	 */
	@Override
	public <T> void release(List<Entity<Object>> entities, boolean write)
			throws EntityManagerException {
		super.release(entities, write);
		if (write) {
			Map<Class<?>, List<String>> idsMap = new HashMap<Class<?>, List<String>>();
			for (Entity<Object> entity : entities) {
				Class<?> clazz = entity.getObject().getClass();
				List<String> idsList = idsMap.get(clazz);
				if (idsList == null) {
					idsList = new ArrayList<String>();
					idsMap.put(clazz, idsList);
				}
				idsList.add(entity.getEntityId());
			}

			for (Entry<Class<?>, List<String>> entry : idsMap.entrySet()) {
				List<String> ids = entry.getValue();
				if (ids.size() > 0) {
					List<PersistenceListener> listeners = getListeners(entry.getKey());
					if (listeners != null) {
						for (PersistenceListener listener : listeners) {
							listener.updated(this, ids);
						}
					}
				}
			}
		}
	}

	private synchronized List<PersistenceListener> getListeners(Class<?> clazz) {
		return listeners.get(clazz);
	}
}
