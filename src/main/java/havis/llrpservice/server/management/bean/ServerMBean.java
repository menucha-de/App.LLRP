package havis.llrpservice.server.management.bean;

import havis.llrpservice.server.service.LLRPServiceManager;

public interface ServerMBean {
	/**
	 * Restarts the LLRP server. The JVM is <em>not</em> restarted.
	 * 
	 * @throws Exception
	 */
	void restart() throws Exception;

	/**
	 * Stops the whole LLRP server. If {@link LLRPServiceManager} is the main
	 * class of the application then the JVM is stopped.
	 * 
	 * @throws Exception
	 */
	void stop() throws Exception;
}