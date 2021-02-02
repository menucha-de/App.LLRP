package havis.llrpservice.server.service.messageHandling;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import havis.device.io.Direction;
import havis.device.io.IOConfiguration;
import havis.device.io.State;
import havis.llrpservice.common.serializer.JsonSerializer;
import havis.llrpservice.csc.llrp.json.LLRPJacksonMixIns;
import havis.llrpservice.data.message.AddAccessSpec;
import havis.llrpservice.data.message.AddROSpec;
import havis.llrpservice.data.message.CloseConnection;
import havis.llrpservice.data.message.CustomMessage;
import havis.llrpservice.data.message.DeleteAccessSpec;
import havis.llrpservice.data.message.DeleteROSpec;
import havis.llrpservice.data.message.DisableAccessSpec;
import havis.llrpservice.data.message.DisableROSpec;
import havis.llrpservice.data.message.EnableAccessSpec;
import havis.llrpservice.data.message.EnableROSpec;
import havis.llrpservice.data.message.GetAccessSpecs;
import havis.llrpservice.data.message.GetROSpecs;
import havis.llrpservice.data.message.GetReaderCapabilities;
import havis.llrpservice.data.message.GetReaderCapabilitiesRequestedData;
import havis.llrpservice.data.message.GetReaderCapabilitiesResponse;
import havis.llrpservice.data.message.GetReaderConfig;
import havis.llrpservice.data.message.GetReaderConfigRequestedData;
import havis.llrpservice.data.message.GetReaderConfigResponse;
import havis.llrpservice.data.message.GetSupportedVersion;
import havis.llrpservice.data.message.GetSupportedVersionResponse;
import havis.llrpservice.data.message.Message;
import havis.llrpservice.data.message.ProtocolVersion;
import havis.llrpservice.data.message.ReaderEventNotification;
import havis.llrpservice.data.message.SetProtocolVersion;
import havis.llrpservice.data.message.SetProtocolVersionResponse;
import havis.llrpservice.data.message.SetReaderConfig;
import havis.llrpservice.data.message.StartROSpec;
import havis.llrpservice.data.message.StopROSpec;
import havis.llrpservice.data.message.parameter.AccessSpec;
import havis.llrpservice.data.message.parameter.GPIEvent;
import havis.llrpservice.data.message.parameter.LLRPStatus;
import havis.llrpservice.data.message.parameter.LLRPStatusCode;
import havis.llrpservice.data.message.parameter.Parameter;
import havis.llrpservice.data.message.parameter.ROSpec;
import havis.llrpservice.data.message.parameter.ROSpecEvent;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.sbc.gpio.message.MessageHeader;
import havis.llrpservice.sbc.rfc.message.GetCapabilitiesResponse;
import havis.llrpservice.sbc.rfc.message.GetConfigurationResponse;
import havis.llrpservice.server.service.fsm.lllrp.LLRPRuntimeData;
import havis.llrpservice.xml.properties.LLRPCapabilitiesType;
import havis.util.platform.Platform;
import mockit.Mocked;
import mockit.NonStrictExpectations;

@Test
public class LLRPMessageCreatorTest {

	private String testResourcePath = "havis/llrpservice/server/service/messageHandling/messages";
	private String requestSupportedVersion = "requestSupportedVersion.json";
	private String responseSupportedVersion = "responseSupportedVersion.json";
	private String requestSetVersion = "requestSetVersion.json";
	private String responseSetVersion = "responseSetVersion.json";
	private String requestCapabilities = "requestCapabilities.json";
	private List<String> responseCapabilities = Arrays
			.asList(new String[] { "responseCapabilities1.json", "responseCapabilities2.json",
					"responseCapabilities3.json", "responseCapabilities4.json",
					"responseCapabilities5.json", "responseCapabilities6.json", });
	private String requestConfiguration = "requestConfiguration.json";
	private List<String> responseConfiguration = Arrays
			.asList(new String[] { "responseConfiguration01.json", "responseConfiguration02.json",
					"responseConfiguration03.json", "responseConfiguration04.json",
					"responseConfiguration05.json", "responseConfiguration06.json",
					"responseConfiguration07.json", "responseConfiguration08.json",
					"responseConfiguration09.json", "responseConfiguration10.json",
					"responseConfiguration11.json", "responseConfiguration12.json",
					"responseConfiguration13.json" });
	private String requestSetReaderConfig = "requestSetReaderConfig.json";
	private String responseSetReaderConfig = "responseSetReaderConfig.json";
	private String requestCloseConnection = "requestCloseConnection.json";
	private String responseCloseConnection = "responseCloseConnection.json";
	private String requestAddROSpec = "requestAddROSpec.json";
	private String responseAddROSpec = "responseAddROSpec.json";
	private String responseDeleteROSpec = "responseDeleteROSpec.json";
	private String requestDeleteROSpec = "requestDeleteROSpec.json";
	private String requestEnableROSpec = "requestEnableROSpec.json";
	private String responseEnableROSpec = "responseEnableROSpec.json";
	private String responseDisableROSpec = "responseDisableROSpec.json";
	private String requestDisableROSpec = "requestDisableROSpec.json";
	private String requestGetROSpecs = "requestGetROSpecs.json";
	private String responseGetROSpecs = "responseGetROSpecs.json";
	private String requestStartROSpec = "requestStartROSpec.json";
	private String responseStartROSpec = "responseStartROSpec.json";
	private String responseStopROSpec = "responseStopROSpec.json";
	private String requestStopROSpec = "requestStopROSpec.json";
	private String requestAddAccessSpec = "requestAddAccessSpec.json";
	private String responseAddAccessSpec = "responseAddAccessSpec.json";
	private String requestDeleteAccessSpec = "requestDeleteAccessSpec.json";
	private String responseDeleteAccessSpec = "responseDeleteAccessSpec.json";
	private String requestEnableAccessSpec = "requestEnableAccessSpec.json";
	private String responseEnableAccessSpec = "responseEnableAccessSpec.json";
	private String requestDisableAccessSpec = "requestDisableAccessSpec.json";
	private String responseDisableAccessSpec = "responseDisableAccessSpec.json";
	private String requestGetAccessSpecs = "requestGetAccessSpecs.json";
	private String responseGetAccessSpecs = "responseGetAccessSpecs.json";
	private String requestCustomMessage = "requestCustomMessage.json";
	private String responseCustomMessage = "responseCustomMessage.json";
	private String llrpROSpecEvent = "../parameter/llrpROSpecEvent.json";

	private String rfcCapsResponseFile = "rfcCapsResponse.json";
	private String rfcConfigurationResponse = "rfcConfigResponse.json";

	private LLRPJacksonMixIns mixIns = new LLRPJacksonMixIns();
	private JsonSerializer llrpSerializer = new JsonSerializer(_LLRPWrapperTest.class);
	private JsonSerializer rfcSerializer = new JsonSerializer(_RFCWrapperTest.class);

	@BeforeClass
	public void init() throws Exception {
		ClassLoader loader = getClass().getClassLoader();
		URL resource = loader.getResource(testResourcePath);
		testResourcePath = Paths.get(new URI(resource.toString())).toString() + "/";

		llrpSerializer.setPrettyPrint(true);
		llrpSerializer.addSerializerMixIns(mixIns);
		llrpSerializer.addDeserializerMixIns(mixIns);

		rfcSerializer.setPrettyPrint(true);
	}

	@Test
	public void createGetSupportedVersionResponse() throws Exception {
		LLRPMessageCreator creator = new LLRPMessageCreator();
		LLRPRuntimeData llrpRuntimeData = new LLRPRuntimeData(null /* identificationSource */,
				null /* llrpCapabilities */, null /* llrpMessageHandler */,
				null /* roSpecsManager */, null /* reportDepot */);
		GetSupportedVersion request = (GetSupportedVersion) readLLRPMessage(
				requestSupportedVersion);
		GetSupportedVersionResponse compare = creator.createResponse(request,
				ProtocolVersion.LLRP_V1_1, llrpRuntimeData.getProtocolVersion(),
				llrpRuntimeData.SUPPORTED_PROTOCOL_VERSION, getStatus());
		// writeFile("a.json", compare, null /* parameter */);
		Assert.assertEquals(compare.toString(),
				readLLRPMessage(responseSupportedVersion).toString());
	}

	@Test
	public void createSetProtocolVersionResponse() throws Exception {
		LLRPMessageCreator creator = new LLRPMessageCreator();
		SetProtocolVersion request = (SetProtocolVersion) readLLRPMessage(requestSetVersion);
		SetProtocolVersionResponse compare = creator.createResponse(request, getStatus());
		Assert.assertEquals(compare.toString(), readLLRPMessage(responseSetVersion).toString());
	}

	@Mocked
	Platform platform;

	public void createGetReaderCapabilitiesResponse(//
	// @Mocked Platform platform//
	) throws Exception {
		LLRPMessageCreator creator = new LLRPMessageCreator();
		LLRPRuntimeData llrpRuntimeData = new LLRPRuntimeData(null /* identificationSource */,
				null /* llrpCapabilities */, null /* llrpMessageHandler */,
				null /* roSpecsManager */, null /* reportDepot */);
		LLRPCapabilitiesType llrpCapabilitiesType = new LLRPCapabilitiesType();

		// get GetReaderCapabilities request
		GetReaderCapabilities request = (GetReaderCapabilities) readLLRPMessage(
				requestCapabilities);

		// get rfcConfigResponse
		_RFCWrapperTest rfcWrapper = rfcSerializer
				.deserialize(_FileHelperTest.readFile(testResourcePath + rfcCapsResponseFile));
		GetCapabilitiesResponse rfcConfigResponse = (GetCapabilitiesResponse) rfcWrapper
				.getRfcMessage();

		// create gpioConfigResponse
		List<havis.device.io.Configuration> gpioConfiguration = new ArrayList<>();
		gpioConfiguration.add(new IOConfiguration((short) 1 /* id */, Direction.INPUT, State.HIGH,
				true /* gpiEventsEnabled */));
		gpioConfiguration.add(new IOConfiguration((short) 1 /* id */, Direction.OUTPUT, State.LOW,
				false /* gpiEventsEnabled */));
		gpioConfiguration.add(new IOConfiguration((short) 1 /* id */, Direction.OUTPUT, State.HIGH,
				false/* gpiEventsEnabled */));
		havis.llrpservice.sbc.gpio.message.GetConfigurationResponse gpioConfigResponse = new havis.llrpservice.sbc.gpio.message.GetConfigurationResponse(
				new havis.llrpservice.sbc.gpio.message.MessageHeader(1 /* id */),
				gpioConfiguration);

		List<GetReaderCapabilitiesRequestedData> requestedData = new ArrayList<>();
		requestedData.add(GetReaderCapabilitiesRequestedData.ALL);
		requestedData.add(GetReaderCapabilitiesRequestedData.C1G2_LLRP_CAPABILITIES);
		requestedData.add(GetReaderCapabilitiesRequestedData.GENERAL_DEVICE_CAPABILITIES);
		requestedData.add(GetReaderCapabilitiesRequestedData.LLRP_CAPABILITIES);
		requestedData.add(GetReaderCapabilitiesRequestedData.REGULATORY_CAPABILITIES);

		GetReaderCapabilitiesResponse compare;
		for (int i = 0; i < requestedData.size(); i++) {
			request.setRequestedData(requestedData.get(i));
			compare = creator.createResponse(request, llrpRuntimeData.getProtocolVersion(),
					llrpCapabilitiesType, rfcConfigResponse, gpioConfigResponse, platform,
					getStatus());

			Assert.assertEquals(compare.toString(),
					readLLRPMessage(responseCapabilities.get(i)).toString());
		}

		// create error response
		compare = creator.createResponse(request, llrpRuntimeData.getProtocolVersion(),
				null /* llrpCapabilitiesType */, null /* rfcConfigResponse */,
				null /* gpioConfigResponse */, platform,
				new LLRPStatus(new TLVParameterHeader((byte) 0), LLRPStatusCode.A_INVALID, "huhu"));
		Assert.assertEquals(compare.toString(),
				readLLRPMessage(responseCapabilities.get(5)).toString());
	}

	public void createGetReaderConfigResponse() throws Exception {
		LLRPMessageCreator creator = new LLRPMessageCreator();
		LLRPRuntimeData llrpRuntimeData = new LLRPRuntimeData(null /* identificationSource */,
				null /* llrpCapabilities */, null /* llrpMessageHandler */,
				null /* roSpecsManager */, null /* reportDepot */);

		// get GetReaderConfig request
		GetReaderConfig request = (GetReaderConfig) readLLRPMessage(requestConfiguration);

		// get rfcConfigResponse
		_RFCWrapperTest rfcWrapper = rfcSerializer
				.deserialize(_FileHelperTest.readFile(testResourcePath + rfcConfigurationResponse));
		GetConfigurationResponse rfcConfigResponse = (GetConfigurationResponse) rfcWrapper
				.getRfcMessage();

		// create gpioConfigResponse
		List<havis.device.io.Configuration> gpioConfiguration = new ArrayList<>();
		gpioConfiguration.add(new IOConfiguration((short) 1 /* id */, Direction.INPUT, State.HIGH,
				true /* gpiEventsEnabled */));
		gpioConfiguration.add(new IOConfiguration((short) 2 /* id */, Direction.OUTPUT, State.LOW,
				false /* gpiEventsEnabled */));
		gpioConfiguration.add(new IOConfiguration((short) 3 /* id */, Direction.OUTPUT, State.HIGH,
				false/* gpiEventsEnabled */));
		havis.llrpservice.sbc.gpio.message.GetConfigurationResponse gpioConfigResponse = new havis.llrpservice.sbc.gpio.message.GetConfigurationResponse(
				new MessageHeader(1 /* id */), gpioConfiguration);

		List<GetReaderConfigRequestedData> requestedData = new ArrayList<>();
		requestedData.add(GetReaderConfigRequestedData.ALL);
		requestedData.add(GetReaderConfigRequestedData.ACCESS_REPORT_SPEC);
		requestedData.add(GetReaderConfigRequestedData.ANTENNA_CONFIGURATION);
		requestedData.add(GetReaderConfigRequestedData.ANTENNA_PROPERTIES);
		requestedData.add(GetReaderConfigRequestedData.EVENTS_AND_REPORTS);
		requestedData.add(GetReaderConfigRequestedData.GPI_CURRENT_STATE); // responseConfiguration06.json
		requestedData.add(GetReaderConfigRequestedData.GPO_WRITE_DATA); // responseConfiguration07.json
		requestedData.add(GetReaderConfigRequestedData.IDENTIFICATION);
		requestedData.add(GetReaderConfigRequestedData.KEEPALIVE_SPEC);
		requestedData.add(GetReaderConfigRequestedData.LLRP_CONFIGURATION_STATE_VALUE);
		requestedData.add(GetReaderConfigRequestedData.READER_EVENT_NOTIFICATION);
		requestedData.add(GetReaderConfigRequestedData.RO_REPORT_SPEC);

		for (int i = 0; i < requestedData.size(); i++) {
			request.setRequestedData(requestedData.get(i));
			GetReaderConfigResponse compare = creator.createResponse(request, llrpRuntimeData,
					rfcConfigResponse, gpioConfigResponse, getStatus());
			// writeFile("a.json", compare, null /* parameter */);
			Assert.assertEquals(compare.toString(),
					readLLRPMessage(responseConfiguration.get(i)).toString());
		}

		// create error response
		GetReaderConfigResponse compare = creator.createResponse(request, llrpRuntimeData,
				null /* rfcConfigResponse */, null /* gpioConfigResponse */,
				new LLRPStatus(new TLVParameterHeader((byte) 0), LLRPStatusCode.A_INVALID, "huhu"));
		Assert.assertEquals(compare.toString(),
				readLLRPMessage(responseConfiguration.get(12)).toString());
	}

	@Test
	public void remainingCreations() throws Exception {
		LLRPMessageCreator creator = new LLRPMessageCreator();
		LLRPRuntimeData llrpRuntimeData = new LLRPRuntimeData(null /* identificationSource */,
				null /* llrpCapabilities */, null /* llrpMessageHandler */,
				null /* roSpecsManager */, null /* reportDepot */);

		SetReaderConfig setReaderConfig = (SetReaderConfig) readLLRPMessage(requestSetReaderConfig);
		Message compare = creator.createResponse(setReaderConfig,
				llrpRuntimeData.getProtocolVersion(), getStatus());
		Assert.assertEquals(compare.toString(),
				readLLRPMessage(responseSetReaderConfig).toString());

		CloseConnection closeConnection = (CloseConnection) readLLRPMessage(requestCloseConnection);
		compare = creator.createResponse(closeConnection, llrpRuntimeData.getProtocolVersion(),
				getStatus());
		Assert.assertEquals(compare.toString(),
				readLLRPMessage(responseCloseConnection).toString());

		AddROSpec addROSpec = (AddROSpec) readLLRPMessage(requestAddROSpec);
		compare = creator.createResponse(addROSpec, llrpRuntimeData.getProtocolVersion(),
				getStatus());
		Assert.assertEquals(compare.toString(), readLLRPMessage(responseAddROSpec).toString());

		DeleteROSpec deleteROSpec = (DeleteROSpec) readLLRPMessage(requestDeleteROSpec);
		compare = creator.createResponse(deleteROSpec, llrpRuntimeData.getProtocolVersion(),
				getStatus());
		Assert.assertEquals(compare.toString(), readLLRPMessage(responseDeleteROSpec).toString());

		EnableROSpec enableROSpec = (EnableROSpec) readLLRPMessage(requestEnableROSpec);
		compare = creator.createResponse(enableROSpec, llrpRuntimeData.getProtocolVersion(),
				getStatus());
		Assert.assertEquals(compare.toString(), readLLRPMessage(responseEnableROSpec).toString());

		DisableROSpec disableROSpec = (DisableROSpec) readLLRPMessage(requestDisableROSpec);
		compare = creator.createResponse(disableROSpec, llrpRuntimeData.getProtocolVersion(),
				getStatus());
		Assert.assertEquals(compare.toString(), readLLRPMessage(responseDisableROSpec).toString());

		GetROSpecs getROSpecs = (GetROSpecs) readLLRPMessage(requestGetROSpecs);
		List<ROSpec> roSpecList = new ArrayList<>();
		compare = creator.createResponse(getROSpecs, llrpRuntimeData.getProtocolVersion(),
				roSpecList, getStatus());
		Assert.assertEquals(compare.toString(), readLLRPMessage(responseGetROSpecs).toString());

		StartROSpec startROSpec = (StartROSpec) readLLRPMessage(requestStartROSpec);
		compare = creator.createResponse(startROSpec, llrpRuntimeData.getProtocolVersion(),
				getStatus());
		Assert.assertEquals(compare.toString(), readLLRPMessage(responseStartROSpec).toString());

		StopROSpec stopROSpec = (StopROSpec) readLLRPMessage(requestStopROSpec);
		compare = creator.createResponse(stopROSpec, llrpRuntimeData.getProtocolVersion(),
				getStatus());
		Assert.assertEquals(compare.toString(), readLLRPMessage(responseStopROSpec).toString());

		AddAccessSpec addAccessSpec = (AddAccessSpec) readLLRPMessage(requestAddAccessSpec);
		compare = creator.createResponse(addAccessSpec, llrpRuntimeData.getProtocolVersion(),
				getStatus());
		Assert.assertEquals(compare.toString(), readLLRPMessage(responseAddAccessSpec).toString());

		DeleteAccessSpec deleteAccessSpec = (DeleteAccessSpec) readLLRPMessage(
				requestDeleteAccessSpec);
		compare = creator.createResponse(deleteAccessSpec, llrpRuntimeData.getProtocolVersion(),
				getStatus());
		Assert.assertEquals(compare.toString(),
				readLLRPMessage(responseDeleteAccessSpec).toString());

		EnableAccessSpec enableAccessSpec = (EnableAccessSpec) readLLRPMessage(
				requestEnableAccessSpec);
		compare = creator.createResponse(enableAccessSpec, llrpRuntimeData.getProtocolVersion(),
				getStatus());
		Assert.assertEquals(compare.toString(),
				readLLRPMessage(responseEnableAccessSpec).toString());

		DisableAccessSpec disableAccessSpec = (DisableAccessSpec) readLLRPMessage(
				requestDisableAccessSpec);
		compare = creator.createResponse(disableAccessSpec, llrpRuntimeData.getProtocolVersion(),
				getStatus());
		Assert.assertEquals(compare.toString(),
				readLLRPMessage(responseDisableAccessSpec).toString());

		GetAccessSpecs getAccessSpecs = (GetAccessSpecs) readLLRPMessage(requestGetAccessSpecs);
		List<AccessSpec> accessSpecList = new ArrayList<>();
		compare = creator.createResponse(getAccessSpecs, llrpRuntimeData.getProtocolVersion(),
				accessSpecList, getStatus());
		Assert.assertEquals(compare.toString(), readLLRPMessage(responseGetAccessSpecs).toString());

		CustomMessage custom = (CustomMessage) readLLRPMessage(requestCustomMessage);
		compare = creator.createResponse(custom, llrpRuntimeData.getProtocolVersion());
		Assert.assertEquals(compare.toString(), readLLRPMessage(responseCustomMessage).toString());

	}

	@Test
	public void createReaderEventNotification(@Mocked final Platform systemController)
			throws Exception {
		new NonStrictExpectations() {
			{
				systemController.hasUTCClock();
				result = true;

				systemController.getUptime();
				result = 3;
			}
		};
		LLRPRuntimeData llrpRuntimeData = new LLRPRuntimeData(null /* identificationSource */,
				null /* llrpCapabilities */, null /* llrpMessageHandler */,
				null /* roSpecsManager */, null /* reportDepot */);
		llrpRuntimeData.setNegotiatedProtocolVersion(ProtocolVersion.LLRP_V1_1);

		ROSpecEvent roSpecEvent = (ROSpecEvent) readLLRPParameter(llrpROSpecEvent);
		ReaderEventNotification msg = new LLRPMessageCreator().createNotification(roSpecEvent,
				llrpRuntimeData.getProtocolVersion(), systemController);
		Assert.assertEquals(ProtocolVersion.LLRP_V1_1, msg.getMessageHeader().getVersion());
		Assert.assertNotNull(msg.getReaderEventNotificationData().getUtcTimestamp());
		Assert.assertNull(msg.getReaderEventNotificationData().getUptime());
		Assert.assertEquals(roSpecEvent, msg.getReaderEventNotificationData().getRoSpecEvent());

		GPIEvent gpiEvent = new GPIEvent(new TLVParameterHeader((byte) 0), 1 /* gpiPortNum */,
				true /* state */);
		msg = new LLRPMessageCreator().createNotification(gpiEvent,
				llrpRuntimeData.getProtocolVersion(), systemController);
		Assert.assertEquals(ProtocolVersion.LLRP_V1_1, msg.getMessageHeader().getVersion());
		Assert.assertNotNull(msg.getReaderEventNotificationData().getUtcTimestamp());
		Assert.assertNull(msg.getReaderEventNotificationData().getUptime());
		Assert.assertEquals(gpiEvent, msg.getReaderEventNotificationData().getGpiEvent());
	}

	private TLVParameterHeader getTLVHeader() {
		byte reserved = 0x00;
		return new TLVParameterHeader(reserved);
	}

	private LLRPStatus getStatus() {
		return new LLRPStatus(getTLVHeader(), LLRPStatusCode.M_SUCCESS, "");
	}

	/**
	 * Helper method to write JSON files with a LLRP message / parameter.
	 * 
	 * @param fileName
	 * @param llrpMessage
	 * @param llrpParameter
	 * @throws IOException
	 */
	@SuppressWarnings("unused")
	private void writeFile(String fileName, Message llrpMessage, Parameter llrpParameter)
			throws IOException {
		_LLRPWrapperTest w = new _LLRPWrapperTest();
		w.setMessage(llrpMessage);
		w.setParameter(llrpParameter);
		_FileHelperTest.writeFile(testResourcePath + fileName, llrpSerializer.serialize(w));
	}

	private Message readLLRPMessage(String fileName) throws Exception {
		_LLRPWrapperTest wrapper = llrpSerializer
				.deserialize(_FileHelperTest.readFile(testResourcePath + fileName));
		return wrapper.getMessage();
	}

	private Parameter readLLRPParameter(String fileName) throws Exception {
		_LLRPWrapperTest wrapper = llrpSerializer
				.deserialize(_FileHelperTest.readFile(testResourcePath + fileName));
		return wrapper.getParameter();
	}

}
