package havis.llrpservice.server.gpio;

import havis.llrpservice.xml.configuration.AddressGroup;
import havis.llrpservice.xml.configuration.GPIOType;
import havis.llrpservice.xml.configuration.GpioPortProperties;
import havis.llrpservice.xml.configuration.InstanceGPIOType;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;

/**
 * Analyzes a server configuration and an server instance configuration for GPIO
 * properties. Properties from the server instance configuration have higher
 * priority then from the server configuration.
 */
class GPIOConfigAnalyser {

	private GPIOType server;
	private InstanceGPIOType instance;

	public GPIOConfigAnalyser(LLRPServerConfigurationType serverConfig) {
		this.server = serverConfig.getDefaults().getInterfaces().getGPIO();
	}

	public void setServerInstanceConfig(
			LLRPServerInstanceConfigurationType serverInstanceConfig) {
		this.instance = serverInstanceConfig != null
				&& serverInstanceConfig.getInterfaces() != null
				&& serverInstanceConfig.getInterfaces().getGPIO() != null ? serverInstanceConfig
				.getInterfaces().getGPIO() : null;
	}

	public AddressGroup getAddress() {
		if (instance != null && instance.getAddressGroup() != null) {
			return instance.getAddressGroup();
		}
		return server != null ? server.getAddressGroup() : null;
	}

	public GpioPortProperties getGPIOPortProperties() {
		if (instance != null && instance.getGpioPortProperties() != null) {
			return instance.getGpioPortProperties();
		}
		return server != null ? server.getGpioPortProperties() : null;
	}
}
