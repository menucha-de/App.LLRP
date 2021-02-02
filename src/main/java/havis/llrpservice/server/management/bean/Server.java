package havis.llrpservice.server.management.bean;

import havis.llrpservice.server.service.LLRPServiceManager;

public class Server implements ServerMBean {

	private final LLRPServiceManager llrpServiceManager;

	public Server(LLRPServiceManager llrpServiceManager) {
		this.llrpServiceManager = llrpServiceManager;
	}

	@Override
	public void restart() throws Exception {
		llrpServiceManager.restart();
	}

	@Override
	public void stop() throws Exception {
		llrpServiceManager.stop();
	}
}
