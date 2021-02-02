package havis.llrpservice.common.entityManager;

public class Entity<T> {
	private T srcObj;
	private String entityId;
	private T obj;

	Entity(String entityId, T srcObj, T obj) {
		this.entityId = entityId;
		this.srcObj = srcObj;
		this.obj = obj;
	}

	/**
	 * @return the source object
	 */
	T getSourceObject() {
		return srcObj;
	}

	/**
	 * @return the entityId
	 */
	public String getEntityId() {
		return entityId;
	}

	/**
	 * @return the object
	 */
	public T getObject() {
		return obj;
	}

	/**
	 * @param obj
	 *            the object to set
	 */
	public void setObject(T obj) {
		this.obj = obj;
	}
}
