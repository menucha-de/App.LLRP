package havis.llrpservice.server.service.messageHandling;

import havis.device.rf.capabilities.CapabilityType;
import havis.device.rf.configuration.Configuration;
import havis.device.rf.configuration.ConfigurationType;
import havis.llrpservice.data.message.GetReaderCapabilities;
import havis.llrpservice.data.message.GetReaderConfig;
import havis.llrpservice.data.message.SetReaderConfig;
import havis.llrpservice.data.message.parameter.AntennaConfiguration;
import havis.llrpservice.data.message.parameter.AntennaProperties;

import java.util.ArrayList;
import java.util.List;

public class RFCMessageCreator {

	private RFCMessageConverter converter = new RFCMessageConverter();

	public class RFCGetReaderConfigRequest {
		private List<ConfigurationType> types;
		private short antennaId;

		private RFCGetReaderConfigRequest() {
		}

		public List<ConfigurationType> getTypes() {
			return types;
		}

		public short getAntennaId() {
			return antennaId;
		}
	}

	public class RFCSetReaderConfigRequest {
		private List<Configuration> configurations;
		private boolean isReset;

		private RFCSetReaderConfigRequest() {
		}

		public List<Configuration> getConfigurations() {
			return configurations;
		}

		public boolean isReset() {
			return isReset;
		}
	}

	/**
	 * Creates a RFC request from LLRP {@link GetReaderCapabilities} request.
	 * 
	 * @param llrpMessage
	 * @return The request
	 */
	public List<CapabilityType> createRequest(GetReaderCapabilities llrpMessage) {
		List<CapabilityType> ret = new ArrayList<>();
		switch (llrpMessage.getRequestedData()) {
		case GENERAL_DEVICE_CAPABILITIES:
			ret.add(CapabilityType.DEVICE_CAPABILITIES);
			break;
		case REGULATORY_CAPABILITIES:
			ret.add(CapabilityType.REGULATORY_CAPABILITIES);
			break;
		case ALL:
			ret.add(CapabilityType.DEVICE_CAPABILITIES);
			ret.add(CapabilityType.REGULATORY_CAPABILITIES);
			break;
		default:
			break;
		}
		return ret;
	}

	/**
	 * Creates a RFC request from LLRP {@link GetReaderConfig} request.
	 * 
	 * @param llrpMessage
	 * @return The request
	 */
	public RFCGetReaderConfigRequest createRequest(GetReaderConfig llrpMessage) {
		RFCGetReaderConfigRequest request = new RFCGetReaderConfigRequest();
		request.types = new ArrayList<>();
		request.antennaId = (short) llrpMessage.getAntennaID();
		switch (llrpMessage.getRequestedData()) {
		case ALL:
			request.types.add(ConfigurationType.ANTENNA_CONFIGURATION);
			request.types.add(ConfigurationType.ANTENNA_PROPERTIES);
			break;
		case ANTENNA_PROPERTIES:
			request.types.add(ConfigurationType.ANTENNA_PROPERTIES);
			break;
		case ANTENNA_CONFIGURATION:
			request.types.add(ConfigurationType.ANTENNA_CONFIGURATION);
			break;
		default:
			break;
		}
		return request;
	}

	/**
	 * Creates a RFC request from {@link SetReaderConfig} request.
	 * 
	 * @param llrpMessage
	 * @return The request
	 */
	public RFCSetReaderConfigRequest createRequest(SetReaderConfig llrpMessage) {
		RFCSetReaderConfigRequest request = new RFCSetReaderConfigRequest();
		request.configurations = new ArrayList<>();
		request.isReset = llrpMessage.isResetToFactoryDefaults();
		// antenna properties
		if (llrpMessage.getAntennaPropertiesList() != null) {
			for (AntennaProperties antennaProperty : llrpMessage.getAntennaPropertiesList()) {
				request.configurations.add(converter.convert(antennaProperty));
			}
		}
		// antenna configuration
		if (llrpMessage.getAntennaConfigurationList() != null) {
			for (AntennaConfiguration antennaConfiguration : llrpMessage
					.getAntennaConfigurationList()) {
				// if antenna configuration contains data
				if (antennaConfiguration.getRfReceiver() != null
						|| antennaConfiguration.getRfTransmitter() != null) {
					request.configurations.add(converter.convert(antennaConfiguration));
				}
			}
		}
		return request;
	}

}
