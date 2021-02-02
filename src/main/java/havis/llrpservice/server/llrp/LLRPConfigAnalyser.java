package havis.llrpservice.server.llrp;

import havis.llrpservice.xml.configuration.AddressGroup;
import havis.llrpservice.xml.configuration.InstanceLLRPType;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;
import havis.llrpservice.xml.configuration.LLRPType;

/**
 * Analyzes a server configuration and an server instance configuration for LLRP
 * properties. Properties from the server instance configuration have higher
 * priority then from the server configuration.
 */
public class LLRPConfigAnalyser {

	private InstanceLLRPType instance;
	private LLRPType server;

	public LLRPConfigAnalyser(LLRPServerConfigurationType serverConfig) {
		this.server = serverConfig.getDefaults().getInterfaces().getLLRP();
	}

	public void setServerInstanceConfig(
			LLRPServerInstanceConfigurationType serverInstanceConfig) {
		this.instance = serverInstanceConfig != null
				&& serverInstanceConfig.getInterfaces() != null
				&& serverInstanceConfig.getInterfaces().getLLRP() != null ? serverInstanceConfig
				.getInterfaces().getLLRP() : null;
	}

	public AddressGroup getAddress() {
		return instance == null || instance.getAddressGroup() == null ? server
				.getAddressGroup() : instance.getAddressGroup();
	}

	public int getOpenCloseTimeout() {
		return instance == null || instance.getLlrpPortProperties() == null ? server
				.getOpenCloseTimeout() : instance.getLlrpPortProperties()
				.getOpenCloseTimeout();
	}
}
