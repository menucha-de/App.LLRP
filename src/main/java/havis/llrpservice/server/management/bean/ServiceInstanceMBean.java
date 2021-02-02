package havis.llrpservice.server.management.bean;

public interface ServiceInstanceMBean {
	/**
	 * Gets the identifier of the service instance.
	 * 
	 * @return The service instance identifier
	 */
	String getServiceInstanceId();

	/**
	 * Gets the current state of the service instance
	 * 
	 * @return <code>True</code> if the instance is active
	 */
	boolean getIsActive();

	/**
	 * Starts the service instance.
	 * 
	 * @throws Exception
	 */
	void start() throws Exception;

	/**
	 * Stops the service instance.
	 * 
	 * @throws Exception
	 */
	void stop() throws Exception;
}
