package havis.llrpservice.server.service.messageHandling;

import havis.device.io.Configuration;
import havis.device.io.Type;
import havis.llrpservice.data.message.GetReaderCapabilities;
import havis.llrpservice.data.message.GetReaderConfig;
import havis.llrpservice.data.message.SetReaderConfig;
import havis.llrpservice.data.message.parameter.GPIPortCurrentState;
import havis.llrpservice.data.message.parameter.GPOWriteData;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates GPIO requests from LLRP/RFC messages.
 */
public class GPIOMessageCreator {
	private GPIOMessageConverter converter = new GPIOMessageConverter();

	public class GPIOGetReaderConfigRequest {
		private List<Type> types;
		private short gpiPortNum;
		private short gpoPortNum;

		private GPIOGetReaderConfigRequest() {
		}

		public List<Type> getTypes() {
			return types;
		}

		public short getGpiPortNum() {
			return gpiPortNum;
		}

		public short getGpoPortNum() {
			return gpoPortNum;
		}
	}

	public class GPIOSetReaderConfigRequest {
		private List<Configuration> configurations;
		private boolean isReset;

		private GPIOSetReaderConfigRequest() {
		}

		public List<Configuration> getConfigurations() {
			return configurations;
		}

		public boolean isReset() {
			return isReset;
		}
	}

	/**
	 * Creates a GPIO request from LLRP {@link GetReaderCapabilities} request.
	 * 
	 * @param llrpMessage
	 * @return The request
	 */
	public List<Type> createRequest(GetReaderCapabilities llrpMessage) {
		List<Type> ret = new ArrayList<>();
		switch (llrpMessage.getRequestedData()) {
		case ALL:
		case GENERAL_DEVICE_CAPABILITIES:
			ret.add(Type.IO);
			break;
		case C1G2_LLRP_CAPABILITIES:
		case LLRP_CAPABILITIES:
		case REGULATORY_CAPABILITIES:
		}
		return ret;
	}

	/**
	 * Creates a GPIO request from LLRP {@link GetReaderConfig} request.
	 * 
	 * @param llrpMessage
	 * @return The request
	 */
	public GPIOGetReaderConfigRequest createRequest(GetReaderConfig llrpMessage) {
		GPIOGetReaderConfigRequest request = new GPIOGetReaderConfigRequest();
		request.types = new ArrayList<>();
		request.gpiPortNum = (short) llrpMessage.getGpiPortNum();
		request.gpoPortNum = (short) llrpMessage.getGpoPortNum();
		switch (llrpMessage.getRequestedData()) {
		case ALL:
		case GPI_CURRENT_STATE:
		case GPO_WRITE_DATA:
			request.types.add(Type.IO);
			break;
		case ACCESS_REPORT_SPEC:
		case ANTENNA_CONFIGURATION:
		case ANTENNA_PROPERTIES:
		case EVENTS_AND_REPORTS:
		case IDENTIFICATION:
		case KEEPALIVE_SPEC:
		case LLRP_CONFIGURATION_STATE_VALUE:
		case READER_EVENT_NOTIFICATION:
		case RO_REPORT_SPEC:
		}
		return request;
	}

	/**
	 * Creates a GPIO request from {@link SetReaderConfig} request.
	 * 
	 * @param llrpMessage
	 * @return The request
	 */
	public GPIOSetReaderConfigRequest createRequest(SetReaderConfig llrpMessage) {
		GPIOSetReaderConfigRequest request = new GPIOSetReaderConfigRequest();
		request.configurations = new ArrayList<>();
		request.isReset = llrpMessage.isResetToFactoryDefaults();
		if (llrpMessage.getGpiPortCurrentStateList() != null) {
			for (GPIPortCurrentState gpiState : llrpMessage
					.getGpiPortCurrentStateList()) {
				request.configurations.add(converter.convert(gpiState));
			}
		}
		if (llrpMessage.getGpoWriteDataList() != null) {
			for (GPOWriteData gpoState : llrpMessage.getGpoWriteDataList()) {
				request.configurations.add(converter.convert(gpoState));
			}
		}
		return request;
	}
}
