package havis.llrpservice.server.management;


public interface Management {

	/**
	 * Opens the management service.
	 * 
	 * @throws ManagementException
	 */
	void open() throws ManagementException;

	/**
	 * Closes the management service.
	 * 
	 * @throws ManagementException
	 */
	void close() throws ManagementException;

	/**
	 * Registers a management bean.
	 * 
	 * @param path
	 *            a path separated with <code>/</code>
	 * @param mbean
	 * @throws ManagementException
	 */
	void register(String path, Object mbean) throws ManagementException;

	/**
	 * Unregisters a management bean.
	 * 
	 * @param path
	 *            a path separated with <code>/</code>
	 * @throws ManagementException
	 */
	void unregister(String path) throws ManagementException;
}
