package havis.llrpservice.server.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import havis.llrpservice.common.entityManager.Entity;
import havis.llrpservice.common.entityManager.EntityGroup;
import havis.llrpservice.common.entityManager.EntityManagerException;
import havis.llrpservice.common.entityManager.UnknownEntityException;
import havis.llrpservice.server.configuration.ConfigurationException;
import havis.llrpservice.server.persistence.ClassVersions;
import havis.llrpservice.server.persistence.ObservablePersistence;
import havis.llrpservice.server.persistence.PersistenceException;
import havis.llrpservice.server.persistence.PersistenceListener;
import havis.llrpservice.server.persistence.UnknownClassException;
import havis.llrpservice.server.service.data.ROAccessReportEntity;

/**
 * This class manages the ROAccessReport handling.
 * 
 */
public class ROAccessReportDepot {
	private static final Logger log = Logger.getLogger(ROAccessReportDepot.class.getName());

	public interface ROAccessReportDepotListener {
		public void added(ROAccessReportDepot src, List<String> entityIds);

		public void removed(ROAccessReportDepot src, List<String> entityIds);

		public void updated(ROAccessReportDepot src, List<String> entityIds);
	}

	/**
	 * Listener to persistence changes
	 * 
	 */
	private class ROAccessPersistenceListener implements PersistenceListener {
		ROAccessReportDepot parent;

		public ROAccessPersistenceListener(ROAccessReportDepot parent) {
			this.parent = parent;
		}

		@Override
		public void added(ObservablePersistence src, List<String> entityIds) {
			for (ROAccessReportDepotListener listener : listeners) {
				listener.added(parent, entityIds);
			}
		}

		@Override
		public void removed(ObservablePersistence src, List<String> entityIds) {
			for (ROAccessReportDepotListener listener : listeners) {
				listener.removed(parent, entityIds);
			}
		}

		@Override
		public void updated(ObservablePersistence src, List<String> entityIds) {
			for (ROAccessReportDepotListener listener : listeners) {
				listener.updated(parent, entityIds);
			}
		}
	}

	private ROAccessPersistenceListener listener;
	private ObservablePersistence persistence;
	private List<ROAccessReportDepotListener> listeners = new CopyOnWriteArrayList<ROAccessReportDepotListener>();
	private List<String> entityIds;
	private final Object lock = new Object();

	/**
	 * Refresh ROAccessReports from storage. Gets the last repository for
	 * reports (sorted by creation date) and loads the entities. Add listener to
	 * the persistence.
	 * <p>
	 * <strong>Attention!</strong>
	 * </p>
	 * <p>
	 * Use passed persistence parameter only to distribute. Do not use any
	 * method of the persistence. Use the method provide by this class instead.
	 * </p>
	 * 
	 * @param persistence
	 * @throws EntityManagerException
	 * @throws PersistenceException
	 * @throws ConfigurationException
	 */
	public void open(ObservablePersistence persistence)
			throws EntityManagerException, PersistenceException, ConfigurationException {
		listener = new ROAccessPersistenceListener(this);
		this.persistence = persistence;
		persistence.addClass(ROAccessReportEntity.class,
				ClassVersions.get(ROAccessReportEntity.class));
		List<EntityGroup> groups = persistence.getGroups(ROAccessReportEntity.class);
		Collections.sort(groups, new Comparator<EntityGroup>() {
			@Override
			public int compare(EntityGroup o1, EntityGroup o2) {
				return o1.getCreationDate().compareTo(o2.getCreationDate());
			}
		});
		synchronized (lock) {
			if (groups.size() > 0) {
				entityIds = persistence.refresh(ROAccessReportEntity.class,
						groups.get(groups.size() - 1).getGroupId());
			} else {
				entityIds = new ArrayList<String>();
			}
		}
		persistence.addListener(listener, ROAccessReportEntity.class);
		if (log.isLoggable(Level.INFO)) {
			log.log(Level.INFO, "ROAccessReportDepot has been opened.");
		}
	}

	/**
	 * Removes listener from the persistence
	 */
	public void close() {
		persistence.removeListener(listener, ROAccessReportEntity.class);
	}

	/**
	 * Add ROAccessReports to the depot.
	 * 
	 * @param entities
	 * @return The entities
	 * @throws UnknownClassException
	 * @throws EntityManagerException
	 */
	public List<String> add(List<ROAccessReportEntity> entities)
			throws UnknownClassException, EntityManagerException {
		List<String> entityIds = persistence.add(ROAccessReportEntity.class, entities);
		synchronized (lock) {
			this.entityIds.addAll(entityIds);
			return entityIds;
		}
	}

	/**
	 * Removes ROAccessReports from the depot.
	 * 
	 * @param entityIds
	 * @return The removed entities
	 * @throws EntityManagerException
	 */
	public List<ROAccessReportEntity> remove(List<String> entityIds) throws EntityManagerException {
		List<ROAccessReportEntity> reports = new ArrayList<>();
		for (Object report : persistence.remove(entityIds)) {
			reports.add((ROAccessReportEntity) report);
		}
		synchronized (lock) {
			this.entityIds.removeAll(entityIds);
		}
		return reports;
	}

	/**
	 * Acquires ROAccessReports as {@link Entity} objects.
	 * 
	 * @param entityIds
	 * @return The acquired entities
	 * @throws UnknownEntityException
	 * @throws EntityManagerException
	 */
	public List<Entity<Object>> acquire(List<String> entityIds)
			throws UnknownEntityException, EntityManagerException {
		return persistence.acquire(entityIds);
	}

	/**
	 * Releases acquired entities.
	 * 
	 * @param entities
	 * @param write
	 *            - Defines if changes has taken place.
	 * @throws EntityManagerException
	 */
	public void release(List<Entity<Object>> entities, boolean write)
			throws EntityManagerException {
		persistence.release(entities, write);
	}

	/**
	 * Add a listener to the depot.
	 * 
	 * @param listener
	 */
	public void addListener(ROAccessReportDepotListener listener) {
		listeners.add(listener);
	}

	/**
	 * Removes a listener from the depot.
	 * 
	 * @param listener
	 */
	public void removeListener(ROAccessReportDepotListener listener) {
		List<ROAccessReportDepotListener> removed = new ArrayList<ROAccessReportDepotListener>();
		for (ROAccessReportDepotListener entry : listeners) {
			if (listener == entry) {
				removed.add(entry);
			}
		}
		listeners.removeAll(removed);
	}

	/**
	 * Get entity ids of all entities held by the depot.
	 * 
	 * @return The entity ids
	 */
	public List<String> getEntityIds() {
		synchronized (lock) {
			return new ArrayList<String>(entityIds);
		}
	}

	/**
	 * Manually flush all ROAccessReports in the depot.
	 * 
	 * @throws UnknownClassException
	 * @throws EntityManagerException
	 */
	public void flush() throws UnknownClassException, EntityManagerException {
		persistence.flush(ROAccessReportEntity.class);
	}

}
