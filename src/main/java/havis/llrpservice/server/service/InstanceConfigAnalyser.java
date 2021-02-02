package havis.llrpservice.server.service;

import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;

/**
 * Analyzes a server configuration and an server instance configuration for
 * instance properties. Properties from the server instance configuration have
 * higher priority then from the server configuration.
 */
class InstanceConfigAnalyser {

	private LLRPServerConfigurationType server;
	private LLRPServerInstanceConfigurationType instance;

	public InstanceConfigAnalyser(LLRPServerConfigurationType serverConfig,
			LLRPServerInstanceConfigurationType serverInstanceConfig) {
		this.server = serverConfig;
		this.instance = serverInstanceConfig;
	}

	public String getInstanceId() {
		return instance.getInstanceId();
	}

	public boolean hasGPIO() {
		return server.getDefaults().getInterfaces().getGPIO() != null
				|| instance.getInterfaces() != null
				&& instance.getInterfaces().getGPIO() != null;
	}
}
