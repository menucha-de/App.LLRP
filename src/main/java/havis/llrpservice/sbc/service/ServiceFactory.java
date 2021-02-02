package havis.llrpservice.sbc.service;

public interface ServiceFactory<T> {
	/**
	 * Gets a service for a host and port. It may be cached internally and
	 * returned multiple times. The service must be released with
	 * {@link #release(Object)} for each call of this method.
	 * 
	 * @param host
	 * @param port
	 * @param timeout
	 *            time out in milliseconds
	 * @return The service
	 * @throws ServiceFactoryException
	 */
	T getService(String host, int port, long timeout)
			throws ServiceFactoryException;

	/**
	 * Releases the service which has been returned by
	 * {@link #getService(String, int, long)}.
	 * 
	 * @param service
	 * @throws ServiceFactoryException
	 */
	void release(T service) throws ServiceFactoryException;
}
