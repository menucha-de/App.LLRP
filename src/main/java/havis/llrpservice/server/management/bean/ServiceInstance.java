package havis.llrpservice.server.management.bean;

import havis.llrpservice.server.service.LLRPServiceManager;

public class ServiceInstance implements ServiceInstanceMBean {
	private final LLRPServiceManager llrpServiceManager;
	private final String serviceInstanceId;
	private boolean isActive;

	public ServiceInstance(LLRPServiceManager llrpServiceManager,
			String serviceInstanceId) {
		this.llrpServiceManager = llrpServiceManager;
		this.serviceInstanceId = serviceInstanceId;
	}

	@Override
	public synchronized boolean getIsActive() {
		return isActive;
	}

	public synchronized void setIsActive(boolean isActive) {
		this.isActive = isActive;
	}

	@Override
	public String getServiceInstanceId() {
		return serviceInstanceId;
	};

	@Override
	public void start() throws Exception {
		llrpServiceManager.startServiceInstance(serviceInstanceId);
	}

	@Override
	public void stop() throws Exception {
		llrpServiceManager.stopServiceInstance(serviceInstanceId);
	}
}
