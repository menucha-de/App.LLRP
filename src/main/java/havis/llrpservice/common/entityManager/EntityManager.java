package havis.llrpservice.common.entityManager;

import java.util.List;

public interface EntityManager<T> {

	/**
	 * Opens the entity manager.
	 * <p>
	 * Eg. connections to external resources are opened.
	 * <p>
	 * 
	 * @throws WrongMetaDataException
	 * 
	 * @throws Exception
	 */
	public void open() throws EntityManagerException, WrongMetaDataException;

	/**
	 * Closes the entity manager.
	 * <p>
	 * The managed entities remain unchanged. Only eg. connections to external
	 * resources are closed.
	 * </p>
	 */
	public void close() throws EntityManagerException;

	/**
	 * Adds entities and returns generated entityIds.
	 * 
	 * @param entities
	 * @return The entities
	 */
	public List<String> add(List<T> entities) throws EntityManagerException;

	/**
	 * Removes loaded entities and returns them.
	 * <p>
	 * Existing groups in the storage containing any of these entities are
	 * retained unchanged.
	 * </p>
	 * 
	 * @param entityIds
	 * @return The entities
	 */
	public List<T> remove(List<String> entityIds) throws EntityManagerException;

	/**
	 * Acquires loaded entities.
	 * <p>
	 * Modifications of the entities can be applied with
	 * {@link #release(List, boolean)}.
	 * </p>
	 * 
	 * @param entityIds
	 * @return The entities
	 */
	public List<Entity<T>> acquire(List<String> entityIds)
			throws EntityManagerException;

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
	 * @param write
	 */
	public void release(List<Entity<T>> entities, boolean write)
			throws EntityManagerException;

	/**
	 * Writes loaded entities as a group to the storage. If the group does not
	 * exists it is created else the group will be overwritten.
	 * 
	 * @param groupId
	 * @param entityIds
	 */
	public void flush(String groupId, List<String> entityIds)
			throws EntityManagerException;

	/**
	 * Loads all entities of a group from the storage and replaces the currently
	 * loaded entities. Entities, which are not part of the group, are retained
	 * unchanged. The entity identifiers of the loaded group are returned.
	 * 
	 * @param groupId
	 * @return The entities
	 */
	public List<String> refresh(String groupId) throws EntityManagerException;

	/**
	 * Gets informations of all existing groups in the storage. If no group
	 * exists, an empty array is returned.
	 * 
	 * @return The entity groups
	 */
	public List<EntityGroup> getGroups() throws EntityManagerException;

	/**
	 * Deletes a group from the storage. Loaded entities are retained unchanged.
	 * 
	 * @param groupId
	 */
	public void delete(String groupId) throws EntityManagerException;

}
