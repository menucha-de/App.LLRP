package havis.llrpservice.server.rfc;

import havis.llrpservice.xml.configuration.AddressGroup;
import havis.llrpservice.xml.configuration.InstanceRFCType;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;
import havis.llrpservice.xml.configuration.RFCType;
import havis.llrpservice.xml.configuration.RfcPortProperties;

/**
 * Analyzes a server configuration and an server instance configuration for RFC
 * properties. Properties from the server instance configuration have higher
 * priority then from the server configuration.
 */
class RFCConfigAnalyser {

	private RFCType server;
	private InstanceRFCType instance;

	public RFCConfigAnalyser(LLRPServerConfigurationType serverConfig) {
		this.server = serverConfig.getDefaults().getInterfaces().getRFC();
	}

	public void setServerInstanceConfig(
			LLRPServerInstanceConfigurationType serverInstanceConfig) {
		this.instance = serverInstanceConfig != null
				&& serverInstanceConfig.getInterfaces() != null
				&& serverInstanceConfig.getInterfaces().getRFC() != null ? serverInstanceConfig
				.getInterfaces().getRFC() : null;
	}

	public AddressGroup getAddress() {
		return instance == null || instance.getAddressGroup() == null ? server
				.getAddressGroup() : instance.getAddressGroup();
	}

	public RfcPortProperties getRFCPortProperties() {
		return instance == null || instance.getRfcPortProperties() == null ? server
				.getRfcPortProperties() : instance.getRfcPortProperties();
	}
}
