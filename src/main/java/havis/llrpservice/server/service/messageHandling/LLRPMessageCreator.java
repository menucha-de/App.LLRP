package havis.llrpservice.server.service.messageHandling;

import java.util.ArrayList;
import java.util.List;

import havis.device.io.IOConfiguration;
import havis.device.rf.capabilities.Capabilities;
import havis.device.rf.capabilities.DeviceCapabilities;
import havis.device.rf.configuration.Configuration;
import havis.llrpservice.common.ids.IdGenerator;
import havis.llrpservice.data.message.AddAccessSpec;
import havis.llrpservice.data.message.AddAccessSpecResponse;
import havis.llrpservice.data.message.AddROSpec;
import havis.llrpservice.data.message.AddROSpecResponse;
import havis.llrpservice.data.message.CloseConnection;
import havis.llrpservice.data.message.CloseConnectionResponse;
import havis.llrpservice.data.message.CustomMessage;
import havis.llrpservice.data.message.DeleteAccessSpec;
import havis.llrpservice.data.message.DeleteAccessSpecResponse;
import havis.llrpservice.data.message.DeleteROSpec;
import havis.llrpservice.data.message.DeleteROSpecResponse;
import havis.llrpservice.data.message.DisableAccessSpec;
import havis.llrpservice.data.message.DisableAccessSpecResponse;
import havis.llrpservice.data.message.DisableROSpec;
import havis.llrpservice.data.message.DisableROSpecResponse;
import havis.llrpservice.data.message.EnableAccessSpec;
import havis.llrpservice.data.message.EnableAccessSpecResponse;
import havis.llrpservice.data.message.EnableROSpec;
import havis.llrpservice.data.message.EnableROSpecResponse;
import havis.llrpservice.data.message.ErrorMessage;
import havis.llrpservice.data.message.GetAccessSpecs;
import havis.llrpservice.data.message.GetAccessSpecsResponse;
import havis.llrpservice.data.message.GetROSpecs;
import havis.llrpservice.data.message.GetROSpecsResponse;
import havis.llrpservice.data.message.GetReaderCapabilities;
import havis.llrpservice.data.message.GetReaderCapabilitiesResponse;
import havis.llrpservice.data.message.GetReaderConfig;
import havis.llrpservice.data.message.GetReaderConfigResponse;
import havis.llrpservice.data.message.GetSupportedVersion;
import havis.llrpservice.data.message.GetSupportedVersionResponse;
import havis.llrpservice.data.message.Message;
import havis.llrpservice.data.message.MessageHeader;
import havis.llrpservice.data.message.ProtocolVersion;
import havis.llrpservice.data.message.ReaderEventNotification;
import havis.llrpservice.data.message.SetProtocolVersion;
import havis.llrpservice.data.message.SetProtocolVersionResponse;
import havis.llrpservice.data.message.SetReaderConfig;
import havis.llrpservice.data.message.SetReaderConfigResponse;
import havis.llrpservice.data.message.StartROSpec;
import havis.llrpservice.data.message.StartROSpecResponse;
import havis.llrpservice.data.message.StopROSpec;
import havis.llrpservice.data.message.StopROSpecResponse;
import havis.llrpservice.data.message.parameter.AccessReportSpec;
import havis.llrpservice.data.message.parameter.AccessSpec;
import havis.llrpservice.data.message.parameter.AntennaConfiguration;
import havis.llrpservice.data.message.parameter.AntennaProperties;
import havis.llrpservice.data.message.parameter.C1G2LLRPCapabilities;
import havis.llrpservice.data.message.parameter.EventsAndReports;
import havis.llrpservice.data.message.parameter.GPIEvent;
import havis.llrpservice.data.message.parameter.GPIPortCurrentState;
import havis.llrpservice.data.message.parameter.GPOWriteData;
import havis.llrpservice.data.message.parameter.GeneralDeviceCapabilities;
import havis.llrpservice.data.message.parameter.Identification;
import havis.llrpservice.data.message.parameter.KeepaliveSpec;
import havis.llrpservice.data.message.parameter.LLRPCapabilities;
import havis.llrpservice.data.message.parameter.LLRPConfigurationStateValue;
import havis.llrpservice.data.message.parameter.LLRPStatus;
import havis.llrpservice.data.message.parameter.LLRPStatusCode;
import havis.llrpservice.data.message.parameter.Parameter;
import havis.llrpservice.data.message.parameter.ParameterTypes.ParameterType;
import havis.llrpservice.data.message.parameter.ProtocolId;
import havis.llrpservice.data.message.parameter.ROReportSpec;
import havis.llrpservice.data.message.parameter.ROSpec;
import havis.llrpservice.data.message.parameter.ROSpecEvent;
import havis.llrpservice.data.message.parameter.ReaderEventNotificationData;
import havis.llrpservice.data.message.parameter.ReaderEventNotificationSpec;
import havis.llrpservice.data.message.parameter.ReaderExceptionEvent;
import havis.llrpservice.data.message.parameter.RegulatoryCapabilities;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.sbc.rfc.message.GetCapabilitiesResponse;
import havis.llrpservice.sbc.rfc.message.GetConfigurationResponse;
import havis.llrpservice.server.service.data.LLRPReaderCapabilities;
import havis.llrpservice.server.service.data.LLRPReaderConfig;
import havis.llrpservice.server.service.fsm.lllrp.LLRPRuntimeData;
import havis.llrpservice.xml.properties.LLRPCapabilitiesType;
import havis.util.platform.Platform;
import havis.util.platform.PlatformException;

public class LLRPMessageCreator extends havis.llrpservice.server.llrp.LLRPMessageCreator {
	private final LLRPMessageConverter converter = new LLRPMessageConverter();
	private final LLRPReaderCapabilities readerCapabilities = new LLRPReaderCapabilities();

	public GetSupportedVersionResponse createResponse(GetSupportedVersion request,
			ProtocolVersion protocolVersion, ProtocolVersion currentProtocolVersion,
			ProtocolVersion supportedProtocolVersion, LLRPStatus llrpStatus) {
		MessageHeader header = new MessageHeader((byte) 0, protocolVersion,
				request.getMessageHeader().getId());
		return new GetSupportedVersionResponse(header, currentProtocolVersion,
				supportedProtocolVersion, llrpStatus);
	}

	public SetProtocolVersionResponse createResponse(SetProtocolVersion request,
			LLRPStatus llrpStatus) {
		// see LLRP spec 9.1.4
		MessageHeader header = new MessageHeader((byte) 0x00, ProtocolVersion.LLRP_V1_1,
				request.getMessageHeader().getId());
		return new SetProtocolVersionResponse(header, llrpStatus);
	}

	public GetReaderCapabilitiesResponse createResponse(GetReaderCapabilities request,
			ProtocolVersion protocolVersion, LLRPCapabilitiesType llrpCaps,
			GetCapabilitiesResponse rfcCapsResponse,
			havis.llrpservice.sbc.gpio.message.GetConfigurationResponse gpioConfigResponse,
			Platform platform, LLRPStatus llrpStatus) {
		MessageHeader header = new MessageHeader((byte) 0, protocolVersion,
				request.getMessageHeader().getId());
		if (LLRPStatusCode.M_SUCCESS != llrpStatus.getStatusCode()) {
			return new GetReaderCapabilitiesResponse(header, llrpStatus);
		}
		DeviceCapabilities deviceCaps = null;
		havis.device.rf.capabilities.RegulatoryCapabilities regulatoryCaps = null;
		if (rfcCapsResponse != null) {
			for (Capabilities capability : rfcCapsResponse.getCapabilities()) {
				if (capability instanceof DeviceCapabilities) {
					deviceCaps = (DeviceCapabilities) capability;
				} else if (capability instanceof havis.device.rf.capabilities.RegulatoryCapabilities) {
					regulatoryCaps = (havis.device.rf.capabilities.RegulatoryCapabilities) capability;
				}
			}
		}
		int gpiPortCount = 0;
		int gpoPortCount = 0;
		// if GPIO is enabled
		if (gpioConfigResponse != null) {
			for (havis.device.io.Configuration config : gpioConfigResponse.getConfiguration()) {
				if (config instanceof IOConfiguration) {
					IOConfiguration pinConfig = (IOConfiguration) config;
					switch (pinConfig.getDirection()) {
					case INPUT:
						gpiPortCount++;
						break;
					case OUTPUT:
						gpoPortCount++;
						break;
					}
				}
			}
		}

		// Mapping
		GeneralDeviceCapabilities generalDeviceCapabilities = null;
		try {
			generalDeviceCapabilities = converter.convert(deviceCaps,
					readerCapabilities.isCanSetAntennaProperties(), ProtocolId.EPC_GLOBAL_C1G2,
					gpiPortCount, gpoPortCount, platform.hasUTCClock());
		} catch (Exception e) {
			return new GetReaderCapabilitiesResponse(header,
					createStatus(LLRPStatusCode.R_DEVICE_ERROR, e.getMessage()));
		}
		RegulatoryCapabilities regulatoryCapabilities = converter.convert(regulatoryCaps,
				readerCapabilities);
		C1G2LLRPCapabilities c1g2llrpCapabilities = readerCapabilities.getC1G2LLRPCapabilities();
		LLRPCapabilities llrpCapabilities = converter.convert(llrpCaps);

		// Result
		GetReaderCapabilitiesResponse capsResponse = null;
		switch (request.getRequestedData()) {
		case GENERAL_DEVICE_CAPABILITIES:
			capsResponse = new GetReaderCapabilitiesResponse(header, llrpStatus,
					generalDeviceCapabilities);
			break;
		case REGULATORY_CAPABILITIES:
			capsResponse = new GetReaderCapabilitiesResponse(header, llrpStatus,
					regulatoryCapabilities);
			break;
		case C1G2_LLRP_CAPABILITIES:
			capsResponse = new GetReaderCapabilitiesResponse(header, llrpStatus,
					c1g2llrpCapabilities);
			break;
		case LLRP_CAPABILITIES:
			capsResponse = new GetReaderCapabilitiesResponse(header, llrpStatus, llrpCapabilities);
			break;
		case ALL:
		default:
			capsResponse = new GetReaderCapabilitiesResponse(header, llrpStatus,
					generalDeviceCapabilities);
			capsResponse.setRegulatoryCap(regulatoryCapabilities);
			capsResponse.setC1g2llrpCap(c1g2llrpCapabilities);
			capsResponse.setLlrpCap(llrpCapabilities);
		}
		return capsResponse;
	}

	public GetReaderConfigResponse createResponse(GetReaderConfig llrpRequest,
			LLRPRuntimeData llrpRuntimeData, GetConfigurationResponse rfcConfigResponse,
			havis.llrpservice.sbc.gpio.message.GetConfigurationResponse gpioConfigResponse,
			LLRPStatus llrpResponseStatus) {
		MessageHeader messageHeader = new MessageHeader((byte) 0,
				llrpRuntimeData.getProtocolVersion(), llrpRequest.getMessageHeader().getId());
		GetReaderConfigResponse configResponse = new GetReaderConfigResponse(messageHeader,
				llrpResponseStatus);
		if (LLRPStatusCode.M_SUCCESS != llrpResponseStatus.getStatusCode()) {
			return configResponse;
		}
		// get properties from RFC configuration
		List<AntennaConfiguration> antennaConfigurationList = null;
		List<AntennaProperties> antennaPropertiesList = null;
		if (rfcConfigResponse != null) {
			antennaConfigurationList = new ArrayList<>();
			antennaPropertiesList = new ArrayList<>();
			if (rfcConfigResponse.getConfiguration() != null) {
				for (Configuration configuration : rfcConfigResponse.getConfiguration()) {
					if (configuration instanceof havis.device.rf.configuration.AntennaConfiguration) {
						antennaConfigurationList.add(converter.convert(
								(havis.device.rf.configuration.AntennaConfiguration) configuration));
					} else if (configuration instanceof havis.device.rf.configuration.AntennaProperties) {
						antennaPropertiesList.add(converter.convert(
								(havis.device.rf.configuration.AntennaProperties) configuration));
					}
				}
			}
		}
		// get properties from GPIO configuration if GPIO is enabled
		List<GPIPortCurrentState> gpiPortCurrentStateList = null;
		List<GPOWriteData> gpoWriteDataList = null;
		if (gpioConfigResponse != null) {
			gpiPortCurrentStateList = new ArrayList<>();
			gpoWriteDataList = new ArrayList<>();
			if (gpioConfigResponse.getConfiguration() != null) {
				for (havis.device.io.Configuration configuration : gpioConfigResponse
						.getConfiguration()) {
					if (configuration instanceof IOConfiguration) {
						IOConfiguration ioConfiguration = (IOConfiguration) configuration;
						switch (ioConfiguration.getDirection()) {
						case INPUT:
							gpiPortCurrentStateList
									.add(converter.convertGPIConfig(ioConfiguration));
							break;
						case OUTPUT:
							gpoWriteDataList.add(converter.convertGPOConfig(ioConfiguration));
						}
					}
				}
			}
		}
		// get data that can NOT be modified by SET_READER_CONFIG
		Identification identification = llrpRuntimeData.getIdentification();
		LLRPConfigurationStateValue llrpConfigurationStateValue = llrpRuntimeData
				.getLLRPConfigStateValue();
		// get data that can be modified by SET_READER_CONFIG
		LLRPReaderConfig readerConfig = llrpRuntimeData.getReaderConfig();
		AccessReportSpec accessReportSpec = readerConfig.getAccessReportSpec();
		EventsAndReports eventAndReports = readerConfig.getEventAndReports();
		KeepaliveSpec keepaliveSpec = readerConfig.getKeepaliveSpec();
		ReaderEventNotificationSpec readerEventNotificationSpec = readerConfig
				.getReaderEventNotificationSpec();
		ROReportSpec roReportSpec = readerConfig.getRoReportSpec();

		switch (llrpRequest.getRequestedData()) {
		case ANTENNA_CONFIGURATION:
			configResponse.setAntennaConfigurationList(antennaConfigurationList);
			break;
		case ANTENNA_PROPERTIES:
			configResponse.setAntennaPropertiesList(antennaPropertiesList);
			break;
		case GPI_CURRENT_STATE:
			configResponse.setGpiPortCurrentStateList(gpiPortCurrentStateList);
			break;
		case GPO_WRITE_DATA:
			configResponse.setGpoWriteDataList(gpoWriteDataList);
			break;
		case IDENTIFICATION:
			configResponse.setIdentification(identification);
			break;
		case LLRP_CONFIGURATION_STATE_VALUE:
			configResponse.setLlrpConfigurationStateValue(llrpConfigurationStateValue);
			break;
		case ACCESS_REPORT_SPEC:
			configResponse.setAccessReportSpec(accessReportSpec);
			break;
		case EVENTS_AND_REPORTS:
			configResponse.setEventAndReports(eventAndReports);
			break;
		case KEEPALIVE_SPEC:
			configResponse.setKeepaliveSpec(keepaliveSpec);
			break;
		case READER_EVENT_NOTIFICATION:
			configResponse.setReaderEventNotificationSpec(readerEventNotificationSpec);
			break;
		case RO_REPORT_SPEC:
			configResponse.setRoReportSpec(roReportSpec);
			break;
		case ALL:
		default:
			configResponse.setAntennaConfigurationList(antennaConfigurationList);
			configResponse.setAntennaPropertiesList(antennaPropertiesList);
			configResponse.setGpiPortCurrentStateList(gpiPortCurrentStateList);
			configResponse.setGpoWriteDataList(gpoWriteDataList);
			configResponse.setIdentification(identification);
			configResponse.setLlrpConfigurationStateValue(llrpConfigurationStateValue);
			configResponse.setAccessReportSpec(accessReportSpec);
			configResponse.setEventAndReports(eventAndReports);
			configResponse.setKeepaliveSpec(keepaliveSpec);
			configResponse.setReaderEventNotificationSpec(readerEventNotificationSpec);
			configResponse.setRoReportSpec(roReportSpec);
		}
		return configResponse;
	}

	/**
	 * Creates a {@link ReaderEventNotification} message from an LLRP event.
	 * 
	 * @param llrpEvent
	 *            supported LLRP events: {@link ParameterType#RO_SPEC_EVENT},
	 *            {@link ParameterType#GPI_EVENT},
	 *            {@link ParameterType#READER_EXCEPTION_EVENT}
	 * @param protocolVersion
	 * @param platform
	 * @return The LLRP event
	 * @throws PlatformException
	 */
	public ReaderEventNotification createNotification(Parameter llrpEvent,
			ProtocolVersion protocolVersion, Platform platform) throws PlatformException {
		MessageHeader header = new MessageHeader((byte) 0x00, protocolVersion,
				IdGenerator.getNextLongId());

		ReaderEventNotificationData rend = createReaderEventNotificationData(platform);
		switch (llrpEvent.getParameterHeader().getParameterType()) {
		case RO_SPEC_EVENT:
			rend.setRoSpecEvent((ROSpecEvent) llrpEvent);
			break;
		case GPI_EVENT:
			rend.setGpiEvent((GPIEvent) llrpEvent);
			break;
		case READER_EXCEPTION_EVENT:
			rend.setReaderExceptionEvent((ReaderExceptionEvent) llrpEvent);
			break;
		default:
		}
		return new ReaderEventNotification(header, rend);
	}

	public SetReaderConfigResponse createResponse(SetReaderConfig request,
			ProtocolVersion protocolVersion, LLRPStatus llrpStatus) {
		MessageHeader header = new MessageHeader((byte) 0x00, protocolVersion,
				request.getMessageHeader().getId());
		return new SetReaderConfigResponse(header, llrpStatus);
	}

	public CloseConnectionResponse createResponse(CloseConnection request,
			ProtocolVersion protocolVersion, LLRPStatus status) {
		MessageHeader header = new MessageHeader((byte) 0x00, protocolVersion,
				request.getMessageHeader().getId());
		return new CloseConnectionResponse(header, status);
	}

	public AddROSpecResponse createResponse(AddROSpec request, ProtocolVersion protocolVersion,
			LLRPStatus status) {
		MessageHeader header = new MessageHeader((byte) 0x00, protocolVersion,
				request.getMessageHeader().getId());
		return new AddROSpecResponse(header, status);
	}

	public DeleteROSpecResponse createResponse(DeleteROSpec request,
			ProtocolVersion protocolVersion, LLRPStatus status) {
		MessageHeader header = new MessageHeader((byte) 0x00, protocolVersion,
				request.getMessageHeader().getId());
		return new DeleteROSpecResponse(header, status);
	}

	public EnableROSpecResponse createResponse(EnableROSpec request,
			ProtocolVersion protocolVersion, LLRPStatus status) {
		MessageHeader header = new MessageHeader((byte) 0x00, protocolVersion,
				request.getMessageHeader().getId());
		return new EnableROSpecResponse(header, status);
	}

	public DisableROSpecResponse createResponse(DisableROSpec request,
			ProtocolVersion protocolVersion, LLRPStatus status) {
		MessageHeader header = new MessageHeader((byte) 0x00, protocolVersion,
				request.getMessageHeader().getId());
		return new DisableROSpecResponse(header, status);
	}

	public GetROSpecsResponse createResponse(GetROSpecs request, ProtocolVersion protocolVersion,
			List<ROSpec> roSpecList, LLRPStatus status) {
		MessageHeader header = new MessageHeader((byte) 0x00, protocolVersion,
				request.getMessageHeader().getId());
		GetROSpecsResponse getRoSpecResponse = new GetROSpecsResponse(header, status);
		getRoSpecResponse.setRoSpecList(roSpecList);
		return getRoSpecResponse;
	}

	public StartROSpecResponse createResponse(StartROSpec request, ProtocolVersion protocolVersion,
			LLRPStatus status) {
		MessageHeader header = new MessageHeader((byte) 0x00, protocolVersion,
				request.getMessageHeader().getId());
		return new StartROSpecResponse(header, status);
	}

	public StopROSpecResponse createResponse(StopROSpec request, ProtocolVersion protocolVersion,
			LLRPStatus status) {
		MessageHeader header = new MessageHeader((byte) 0x00, protocolVersion,
				request.getMessageHeader().getId());
		return new StopROSpecResponse(header, status);
	}

	public AddAccessSpecResponse createResponse(AddAccessSpec request,
			ProtocolVersion protocolVersion, LLRPStatus status) {
		MessageHeader header = new MessageHeader((byte) 0x00, protocolVersion,
				request.getMessageHeader().getId());
		return new AddAccessSpecResponse(header, status);
	}

	public DeleteAccessSpecResponse createResponse(DeleteAccessSpec request,
			ProtocolVersion protocolVersion, LLRPStatus status) {
		MessageHeader header = new MessageHeader((byte) 0x00, protocolVersion,
				request.getMessageHeader().getId());
		return new DeleteAccessSpecResponse(header, status);
	}

	public EnableAccessSpecResponse createResponse(EnableAccessSpec request,
			ProtocolVersion protocolVersion, LLRPStatus status) {
		MessageHeader header = new MessageHeader((byte) 0x00, protocolVersion,
				request.getMessageHeader().getId());
		return new EnableAccessSpecResponse(header, status);
	}

	public DisableAccessSpecResponse createResponse(DisableAccessSpec request,
			ProtocolVersion protocolVersion, LLRPStatus status) {
		MessageHeader header = new MessageHeader((byte) 0x00, protocolVersion,
				request.getMessageHeader().getId());
		return new DisableAccessSpecResponse(header, status);
	}

	public GetAccessSpecsResponse createResponse(GetAccessSpecs request,
			ProtocolVersion protocolVersion, List<AccessSpec> accessSpecList, LLRPStatus status) {
		MessageHeader header = new MessageHeader((byte) 0x00, protocolVersion,
				request.getMessageHeader().getId());
		GetAccessSpecsResponse getAccessSpecsResponse = new GetAccessSpecsResponse(header, status);
		getAccessSpecsResponse.setAccessSpecList(accessSpecList);
		return getAccessSpecsResponse;
	}

	public Message createResponse(CustomMessage request, ProtocolVersion protocolVersion) {
		MessageHeader header = new MessageHeader((byte) 0x00, protocolVersion,
				request.getMessageHeader().getId());
		return new ErrorMessage(header, createStatus(LLRPStatusCode.M_UNSUPPORTED_MESSAGE,
				"Custom messages are not supported"));
	}

	public LLRPStatus createStatus(LLRPStatusCode statusCode, String errorDescription) {
		return new LLRPStatus(new TLVParameterHeader((byte) 0x00), statusCode, errorDescription);
	}
}
