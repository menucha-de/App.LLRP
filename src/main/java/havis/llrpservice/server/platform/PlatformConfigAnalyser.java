package havis.llrpservice.server.platform;

import havis.llrpservice.xml.configuration.AddressGroup;
import havis.llrpservice.xml.configuration.InstanceSystemControllerType;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;
import havis.llrpservice.xml.configuration.SystemControllerPortProperties;
import havis.llrpservice.xml.configuration.SystemControllerType;

/**
 * Analyzes a server configuration and an server instance configuration for
 * platform (system controller) properties. Properties from the server instance
 * configuration have higher priority then from the server configuration.
 */
public class PlatformConfigAnalyser {

	private SystemControllerType server;
	private InstanceSystemControllerType instance;

	public PlatformConfigAnalyser(LLRPServerConfigurationType serverConfig) {
		this.server = serverConfig.getDefaults().getInterfaces().getSystemController();
	}

	public void setServerInstanceConfig(LLRPServerInstanceConfigurationType serverInstanceConfig) {
		this.instance = serverInstanceConfig != null && serverInstanceConfig.getInterfaces() != null
				&& serverInstanceConfig.getInterfaces().getSystemController() != null
						? serverInstanceConfig.getInterfaces().getSystemController() : null;
	}

	public AddressGroup getAddress() {
		return instance == null || instance.getAddressGroup() == null ? server.getAddressGroup()
				: instance.getAddressGroup();
	}

	public SystemControllerPortProperties getSystemControllerPortProperties() {
		return instance == null || instance.getSystemControllerPortProperties() == null
				? server.getSystemControllerPortProperties()
				: instance.getSystemControllerPortProperties();
	}
}
