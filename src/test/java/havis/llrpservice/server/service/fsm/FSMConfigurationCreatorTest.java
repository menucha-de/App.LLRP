package havis.llrpservice.server.service.fsm;

import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

import havis.device.io.State;
import havis.device.io.StateEvent;
import havis.device.rf.configuration.Configuration;
import havis.llrpservice.common.fsm.FSM;
import havis.llrpservice.common.fsm.FSMActionException;
import havis.llrpservice.data.message.GetReaderConfig;
import havis.llrpservice.data.message.GetReaderConfigRequestedData;
import havis.llrpservice.data.message.GetReaderConfigResponse;
import havis.llrpservice.data.message.MessageHeader;
import havis.llrpservice.data.message.ProtocolVersion;
import havis.llrpservice.data.message.ReaderEventNotification;
import havis.llrpservice.data.message.SetReaderConfig;
import havis.llrpservice.data.message.SetReaderConfigResponse;
import havis.llrpservice.data.message.parameter.AntennaConfiguration;
import havis.llrpservice.data.message.parameter.Custom;
import havis.llrpservice.data.message.parameter.EventNotificationState;
import havis.llrpservice.data.message.parameter.EventNotificationStateEventType;
import havis.llrpservice.data.message.parameter.GPIEvent;
import havis.llrpservice.data.message.parameter.GPIPortCurrentState;
import havis.llrpservice.data.message.parameter.GPIPortCurrentStateGPIState;
import havis.llrpservice.data.message.parameter.Identification;
import havis.llrpservice.data.message.parameter.IdentificationIDType;
import havis.llrpservice.data.message.parameter.KeepaliveSpec;
import havis.llrpservice.data.message.parameter.KeepaliveSpecTriggerType;
import havis.llrpservice.data.message.parameter.LLRPStatusCode;
import havis.llrpservice.data.message.parameter.RFReceiver;
import havis.llrpservice.data.message.parameter.ROReportSpec;
import havis.llrpservice.data.message.parameter.ReaderEventNotificationSpec;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.sbc.gpio.message.StateChanged;
import havis.llrpservice.sbc.rfc.message.GetConfigurationResponse;
import havis.llrpservice.sbc.rfc.message.ResetConfigurationResponse;
import havis.llrpservice.sbc.rfc.message.SetConfigurationResponse;
import havis.llrpservice.server.gpio.GPIOMessageHandler;
import havis.llrpservice.server.llrp.LLRPMessageHandler;
import havis.llrpservice.server.rfc.RFCMessageHandler;
import havis.llrpservice.server.service.InvalidIdentifierException;
import havis.llrpservice.server.service.ROAccessReportDepot;
import havis.llrpservice.server.service.ROSpecsManager;
import havis.llrpservice.server.service.fsm.gpio.GPIORuntimeData;
import havis.llrpservice.server.service.fsm.lllrp.LLRPRuntimeData;
import havis.llrpservice.server.service.fsm.rfc.RFCRuntimeData;
import havis.llrpservice.xml.properties.IdentificationSourceType;
import havis.llrpservice.xml.properties.IdentificationTypeEnumeration;
import havis.llrpservice.xml.properties.LLRPCapabilitiesType;
import havis.util.platform.Platform;
import mockit.Delegate;
import mockit.Expectations;
import mockit.Mocked;
import mockit.Verifications;

public class FSMConfigurationCreatorTest {

	@Test
	public void createLLRPGetReaderConfigNoRfcNoGpio(@Mocked final Platform platfom,
			@Mocked final LLRPMessageHandler llrpMessageHandler,
			@Mocked final RFCMessageHandler rfcMessageHandler) throws Exception {
		// create FSM
		FSMEvents fsmEvents = createFSMEvents(platfom, null /* roSpecsManager */,
				llrpMessageHandler, rfcMessageHandler, null /* gpioMessageHandler */,
				null /* reportDepot */);
		FSM<FSMEvent> fsm = new FSMCreator().create(fsmEvents);

		System.setProperty("mica.device.serial_no", "258"); // 0x0102
		// send LLRP request
		fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData()
				.setNegotiatedProtocolVersion(ProtocolVersion.LLRP_V1_1);
		final short antennaId = 1;
		fsmEvents.LLRP_MESSAGE_RECEIVED.setMessage(new GetReaderConfig(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_1, 1 /* id */),
				(short) antennaId, GetReaderConfigRequestedData.IDENTIFICATION, 0 /* gpiPortNum */,
				0 /* gpoPortNum */));
		fsm.fire(fsmEvents.LLRP_MESSAGE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("2 "));
		fsm.fire(fsmEvents.LLRP_GET_READER_CONFIG_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));
		new Verifications() {
			{
				// the LLRP response has been sent
				GetReaderConfigResponse response;
				llrpMessageHandler.requestSendingData(response = withCapture());
				times = 1;

				Assert.assertEquals(response.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);
				Identification identification = response.getIdentification();
				Assert.assertEquals(identification.getIdType(), IdentificationIDType.EPC);
				Assert.assertEquals(identification.getReaderID(),
						new byte[] { 0, 0, 0, 0, 0, 0, 1, 2 });
			}
		};

		// all processed messages must have been removed from runtime data
		Assert.assertNull(fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData().getCurrentMessage());

		// remove system property
		System.setProperty("mica.device.serial_no", "");
	}

	@Test
	@SuppressWarnings("unchecked")
	public void createLLRPGetReaderConfigOnlyRfc(@Mocked final Platform platfom,
			@Mocked final LLRPMessageHandler llrpMessageHandler,
			@Mocked final RFCMessageHandler rfcMessageHandler) throws Exception {
		// create FSM
		FSMEvents fsmEvents = createFSMEvents(platfom, null /* roSpecsManager */,
				llrpMessageHandler, rfcMessageHandler, null /* gpioMessageHandler */,
				null /* reportDepot */);
		FSM<FSMEvent> fsm = new FSMCreator().create(fsmEvents);

		// send LLRP request
		fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData()
				.setNegotiatedProtocolVersion(ProtocolVersion.LLRP_V1_1);
		final short antennaId = 1;
		fsmEvents.LLRP_MESSAGE_RECEIVED.setMessage(new GetReaderConfig(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_1, 1 /* id */), antennaId,
				GetReaderConfigRequestedData.ANTENNA_CONFIGURATION, 0 /* gpiPortNum */,
				0 /* gpoPortNum */));
		fsm.fire(fsmEvents.LLRP_MESSAGE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("2 "));
		fsm.fire(fsmEvents.LLRP_GET_READER_CONFIG_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));
		new Verifications() {
			{
				// a RFC request has been sent
				rfcMessageHandler.requestConfiguration(withInstanceOf(List.class) /* confTypes */,
						antennaId);
				times = 1;
			}
		};

		// send RFC response
		fsmEvents.RFC_MESSAGE_RECEIVED.setMessage(new GetConfigurationResponse(
				new havis.llrpservice.sbc.rfc.message.MessageHeader(2 /* id */),
				new ArrayList<Configuration>()));
		fsm.fire(fsmEvents.RFC_MESSAGE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("3"));
		fsm.fire(fsmEvents.RFC_GET_CONFIGURATION_RESPONSE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));

		new Verifications() {
			{
				// the LLRP response has been sent
				GetReaderConfigResponse response;
				llrpMessageHandler.requestSendingData(response = withCapture());
				times = 1;

				Assert.assertEquals(response.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);
			}
		};

		// all processed messages must have been removed from runtime data
		Assert.assertNull(fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData().getCurrentMessage());
		Assert.assertNull(fsmEvents.RFC_GET_CONFIGURATION_RESPONSE_RECEIVED.getRuntimeData()
				.getCurrentMessage());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void createLLRPGetReaderConfigRfcAndGpio(@Mocked final Platform platfom,
			@Mocked final ROSpecsManager roSpecsManager,
			@Mocked final LLRPMessageHandler llrpMessageHandler,
			@Mocked final RFCMessageHandler rfcMessageHandler,
			@Mocked final GPIOMessageHandler gpioMessageHandler) throws Exception {
		// create FSM
		FSMEvents fsmEvents = createFSMEvents(platfom, roSpecsManager, llrpMessageHandler,
				rfcMessageHandler, gpioMessageHandler, null /* reportDepot */);
		FSM<FSMEvent> fsm = new FSMCreator().create(fsmEvents);

		// send LLRP request
		fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData()
				.setNegotiatedProtocolVersion(ProtocolVersion.LLRP_V1_1);
		final short antennaId = 1;
		final short gpiPortNum = 2;
		final short gpoPortNum = 3;
		fsmEvents.LLRP_MESSAGE_RECEIVED.setMessage(new GetReaderConfig(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_1, 1 /* id */), antennaId,
				GetReaderConfigRequestedData.ALL, gpiPortNum, gpoPortNum));
		fsm.fire(fsmEvents.LLRP_MESSAGE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("2 "));
		fsm.fire(fsmEvents.LLRP_GET_READER_CONFIG_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));
		new Verifications() {
			{
				// a RFC request has been sent
				rfcMessageHandler.requestConfiguration(withInstanceOf(List.class) /* confTypes */,
						antennaId);
				times = 1;
				// a GPIO request has been sent
				gpioMessageHandler.requestConfiguration(withInstanceOf(List.class), gpiPortNum,
						gpoPortNum);
				times = 1;
			}
		};

		// first send RFC response
		fsmEvents.RFC_MESSAGE_RECEIVED.setMessage(new GetConfigurationResponse(
				new havis.llrpservice.sbc.rfc.message.MessageHeader(2 /* id */),
				new ArrayList<Configuration>()));
		fsm.fire(fsmEvents.RFC_MESSAGE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("3 "));
		fsm.fire(fsmEvents.RFC_GET_CONFIGURATION_RESPONSE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));

		// send GPIO response
		fsmEvents.GPIO_MESSAGE_RECEIVED
				.setMessage(new havis.llrpservice.sbc.gpio.message.GetConfigurationResponse(
						new havis.llrpservice.sbc.gpio.message.MessageHeader(3 /* id */),
						new ArrayList<havis.device.io.Configuration>()));
		fsm.fire(fsmEvents.GPIO_MESSAGE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("4 "));
		fsm.fire(fsmEvents.GPIO_GET_CONFIGURATION_RESPONSE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));
		new Verifications() {
			{
				// the LLRP response has been sent
				GetReaderConfigResponse response;
				llrpMessageHandler.requestSendingData(response = withCapture());
				times = 1;

				Assert.assertEquals(response.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);
			}
		};

		// all processed messages must have been removed from runtime data
		Assert.assertNull(fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData().getCurrentMessage());
		Assert.assertNull(fsmEvents.RFC_GET_CONFIGURATION_RESPONSE_RECEIVED.getRuntimeData()
				.getCurrentMessage());
		Assert.assertNull(fsmEvents.GPIO_GET_CONFIGURATION_RESPONSE_RECEIVED.getRuntimeData()
				.getCurrentMessage());
	}

	// @Mocked
	// Platform platfom;
	// @Mocked
	// ROSpecsManager roSpecsManager;
	// @Mocked
	// LLRPMessageHandler llrpMessageHandler;
	// @Mocked
	// RFCMessageHandler rfcMessageHandler;
	// @Mocked
	// GPIOMessageHandler gpioMessageHandler;
	// @Mocked
	// ReaderEventNotificationSpec rens;

	@Test
	public void createLLRPGetReaderConfigRfcAndGpioAndGPIEvents(//
			@Mocked final Platform platfom, @Mocked final ROSpecsManager roSpecsManager,
			@Mocked final LLRPMessageHandler llrpMessageHandler,
			@Mocked final RFCMessageHandler rfcMessageHandler,
			@Mocked final GPIOMessageHandler gpioMessageHandler,
			@Mocked final ReaderEventNotificationSpec rens//
	) throws Exception {
		class Data {
			List<EventNotificationState> states = new ArrayList<>();
		}
		final Data data = new Data();
		new Expectations() {
			{
				rens.getEventNotificationStateList();
				result = new Delegate<ReaderEventNotificationSpec>() {
					@SuppressWarnings("unused")
					public List<EventNotificationState> getEventNotificationStateList() {
						return data.states;
					}
				};
			}
		};
		// create FSM
		FSMEvents fsmEvents = createFSMEvents(platfom, roSpecsManager, llrpMessageHandler,
				rfcMessageHandler, gpioMessageHandler, null /* reportDepot */);
		FSM<FSMEvent> fsm = new FSMCreator().create(fsmEvents);

		// send LLRP request
		fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData()
				.setNegotiatedProtocolVersion(ProtocolVersion.LLRP_V1_1);
		fsmEvents.LLRP_MESSAGE_RECEIVED.setMessage(new GetReaderConfig(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_1, 1 /* id */),
				(short) 1 /* antennaId */, GetReaderConfigRequestedData.ALL, 2 /* gpiPortNum */,
				(short) 3 /* gpoPortNum */));
		fsm.fire(fsmEvents.LLRP_MESSAGE_RECEIVED);
		fsm.fire(fsmEvents.LLRP_GET_READER_CONFIG_RECEIVED);

		// first send GPIO response
		fsmEvents.GPIO_MESSAGE_RECEIVED
				.setMessage(new havis.llrpservice.sbc.gpio.message.GetConfigurationResponse(
						new havis.llrpservice.sbc.gpio.message.MessageHeader(41 /* id */),
						new ArrayList<havis.device.io.Configuration>()));
		fsm.fire(fsmEvents.GPIO_MESSAGE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("4 "));
		fsm.fire(fsmEvents.GPIO_GET_CONFIGURATION_RESPONSE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));

		// send a GPI event as GPIO response (sending of LLRP GPI events is
		// disabled)
		fsmEvents.GPIO_MESSAGE_RECEIVED.setMessage(
				new StateChanged(new havis.llrpservice.sbc.gpio.message.MessageHeader(3 /* id */),
						new StateEvent((short) 2 /* id */, State.HIGH)));
		fsm.fire(fsmEvents.GPIO_MESSAGE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("4 "));
		fsm.fire(fsmEvents.GPIO_STATE_CHANGED_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));

		new Verifications() {
			{
				// the ROSpecsManager is informed
				roSpecsManager.gpiEventReceived(withInstanceOf(GPIEvent.class));
				times = 1;
				// no LLRP event has been sent
				llrpMessageHandler
						.requestSendingData(withInstanceOf(ReaderEventNotification.class));
				times = 0;
			}
		};

		// send a GPI event as GPIO response (sending of LLRP GPI events is
		// enabled)
		data.states.add(new EventNotificationState(new TLVParameterHeader((byte) 0),
				EventNotificationStateEventType.GPI_EVENT, true /* state */));
		fsm.fire(fsmEvents.GPIO_MESSAGE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("4 "));
		fsm.fire(fsmEvents.GPIO_STATE_CHANGED_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));

		new Verifications() {
			{
				// the ROSpecsManager is informed
				roSpecsManager.gpiEventReceived(withInstanceOf(GPIEvent.class));
				times = 2;
				// the LLRP event has been sent
				ReaderEventNotification response;
				llrpMessageHandler.requestSendingData(response = withCapture());
				times = 1;
				Assert.assertTrue(
						response.getReaderEventNotificationData().getGpiEvent().isState());
			}
		};

		// send RFC response
		fsmEvents.RFC_MESSAGE_RECEIVED.setMessage(new GetConfigurationResponse(
				new havis.llrpservice.sbc.rfc.message.MessageHeader(31 /* id */),
				new ArrayList<Configuration>()));
		fsm.fire(fsmEvents.RFC_MESSAGE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("3 "));
		fsm.fire(fsmEvents.RFC_GET_CONFIGURATION_RESPONSE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));

		new Verifications() {
			{
				// the LLRP response has been sent
				GetReaderConfigResponse response;
				llrpMessageHandler.requestSendingData(response = withCapture());
				times = 1;

				Assert.assertEquals(response.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);
			}
		};

		// all processed messages must have been removed from runtime data
		Assert.assertNull(fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData().getCurrentMessage());
		Assert.assertNull(fsmEvents.RFC_GET_CONFIGURATION_RESPONSE_RECEIVED.getRuntimeData()
				.getCurrentMessage());
		Assert.assertNull(fsmEvents.GPIO_GET_CONFIGURATION_RESPONSE_RECEIVED.getRuntimeData()
				.getCurrentMessage());
	}

	@Test
	public void createLLRPGetReaderConfigErrorCustomExtension(@Mocked final Platform platfom,
			@Mocked final LLRPMessageHandler llrpMessageHandler,
			@Mocked final RFCMessageHandler rfcMessageHandler,
			@Mocked final GPIOMessageHandler gpioMessageHandler) throws Exception {
		// create FSM
		FSMEvents fsmEvents = createFSMEvents(platfom, null /* roSpecsManager */,
				llrpMessageHandler, rfcMessageHandler, gpioMessageHandler, null /* reportDepot */);
		FSM<FSMEvent> fsm = new FSMCreator().create(fsmEvents);

		// send LLRP request
		fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData()
				.setNegotiatedProtocolVersion(ProtocolVersion.LLRP_V1_1);
		GetReaderConfig request = new GetReaderConfig(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_1, 1 /* id */),
				(short) 1 /* antennaId */, GetReaderConfigRequestedData.ALL,
				(short) 2 /* gpiPortNum */, (short) 3 /* gpoPortNum */);
		List<Custom> customExtensionPoint = new ArrayList<>();
		customExtensionPoint.add(new Custom(new TLVParameterHeader((byte) 0), 1L /* vendorID */,
				2L /* subType */, new byte[0] /* data */));
		request.setCustomList(customExtensionPoint);
		fsmEvents.LLRP_MESSAGE_RECEIVED.setMessage(request);
		fsm.fire(fsmEvents.LLRP_MESSAGE_RECEIVED);
		fsm.fire(fsmEvents.LLRP_GET_READER_CONFIG_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));
		new Verifications() {
			{
				// the LLRP response has been sent with an error
				GetReaderConfigResponse response;
				llrpMessageHandler.requestSendingData(response = withCapture());
				times = 1;

				Assert.assertEquals(response.getStatus().getStatusCode(),
						LLRPStatusCode.P_UNEXPECTED_PARAMETER);
			}
		};

		// all processed messages must have been removed from runtime data
		Assert.assertNull(fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData().getCurrentMessage());
	}

	@Test
	public void createLLRPGetReaderConfigErrorRFCResponse(@Mocked final Platform platfom,
			@Mocked final LLRPMessageHandler llrpMessageHandler,
			@Mocked final RFCMessageHandler rfcMessageHandler,
			@Mocked final GPIOMessageHandler gpioMessageHandler) throws Exception {
		// create FSM
		FSMEvents fsmEvents = createFSMEvents(platfom, null /* roSpecsManager */,
				llrpMessageHandler, rfcMessageHandler, gpioMessageHandler, null /* reportDepot */);
		FSM<FSMEvent> fsm = new FSMCreator().create(fsmEvents);

		// send LLRP request
		fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData()
				.setNegotiatedProtocolVersion(ProtocolVersion.LLRP_V1_1);
		GetReaderConfig request = new GetReaderConfig(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_1, 1 /* id */),
				(short) 1 /* antennaId */, GetReaderConfigRequestedData.ANTENNA_CONFIGURATION,
				2 /* gpiPortNum */, (short) 3 /* gpoPortNum */);
		fsmEvents.LLRP_MESSAGE_RECEIVED.setMessage(request);
		fsm.fire(fsmEvents.LLRP_MESSAGE_RECEIVED);
		fsm.fire(fsmEvents.LLRP_GET_READER_CONFIG_RECEIVED);

		// send RFC response with error
		GetConfigurationResponse rfcResponse = new GetConfigurationResponse(
				new havis.llrpservice.sbc.rfc.message.MessageHeader(31 /* id */),
				new ArrayList<Configuration>());
		rfcResponse.setException(new FSMActionException("huhu"));
		fsmEvents.RFC_MESSAGE_RECEIVED.setMessage(rfcResponse);
		fsm.fire(fsmEvents.RFC_MESSAGE_RECEIVED);
		fsm.fire(fsmEvents.RFC_GET_CONFIGURATION_RESPONSE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));

		new Verifications() {
			{
				// the LLRP response has been sent with an error
				GetReaderConfigResponse response;
				llrpMessageHandler.requestSendingData(response = withCapture());
				times = 1;

				Assert.assertTrue(response.getStatus().getErrorDescription().contains("huhu"));
				Assert.assertEquals(response.getStatus().getStatusCode(),
						LLRPStatusCode.R_DEVICE_ERROR);
			}
		};

		// all processed messages must have been removed from runtime data
		Assert.assertNull(fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData().getCurrentMessage());
		Assert.assertNull(fsmEvents.RFC_GET_CONFIGURATION_RESPONSE_RECEIVED.getRuntimeData()
				.getCurrentMessage());
	}

	@Test
	public void createLLRPGetReaderConfigErrorGPIOResponse(@Mocked final Platform platfom,
			@Mocked final LLRPMessageHandler llrpMessageHandler,
			@Mocked final RFCMessageHandler rfcMessageHandler,
			@Mocked final GPIOMessageHandler gpioMessageHandler) throws Exception {
		// create FSM
		FSMEvents fsmEvents = createFSMEvents(platfom, null /* roSpecsManager */,
				llrpMessageHandler, rfcMessageHandler, gpioMessageHandler, null /* reportDepot */);
		FSM<FSMEvent> fsm = new FSMCreator().create(fsmEvents);

		// send LLRP request
		fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData()
				.setNegotiatedProtocolVersion(ProtocolVersion.LLRP_V1_1);
		GetReaderConfig request = new GetReaderConfig(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_1, 1 /* id */),
				(short) 1 /* antennaId */, GetReaderConfigRequestedData.GPI_CURRENT_STATE,
				2 /* gpiPortNum */, (short) 3 /* gpoPortNum */);
		fsmEvents.LLRP_MESSAGE_RECEIVED.setMessage(request);
		fsm.fire(fsmEvents.LLRP_MESSAGE_RECEIVED);
		fsm.fire(fsmEvents.LLRP_GET_READER_CONFIG_RECEIVED);

		// send GPIO response with error
		havis.llrpservice.sbc.gpio.message.GetConfigurationResponse gpioResponse = new havis.llrpservice.sbc.gpio.message.GetConfigurationResponse(
				new havis.llrpservice.sbc.gpio.message.MessageHeader(41 /* id */),
				new ArrayList<havis.device.io.Configuration>());
		gpioResponse.setException(new FSMActionException("huhu"));
		fsmEvents.GPIO_MESSAGE_RECEIVED.setMessage(gpioResponse);
		fsm.fire(fsmEvents.GPIO_MESSAGE_RECEIVED);
		fsm.fire(fsmEvents.GPIO_GET_CONFIGURATION_RESPONSE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));

		new Verifications() {
			{
				// the LLRP response has been sent with an error
				GetReaderConfigResponse response;
				llrpMessageHandler.requestSendingData(response = withCapture());
				times = 1;

				Assert.assertTrue(response.getStatus().getErrorDescription().contains("huhu"));
				Assert.assertEquals(response.getStatus().getStatusCode(),
						LLRPStatusCode.R_DEVICE_ERROR);
			}
		};

		// all processed messages must have been removed from runtime data
		Assert.assertNull(fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData().getCurrentMessage());
		Assert.assertNull(fsmEvents.GPIO_GET_CONFIGURATION_RESPONSE_RECEIVED.getRuntimeData()
				.getCurrentMessage());
	}

	// @Mocked
	// Platform platform;
	// @Mocked
	// ROSpecsManager roSpecsManager;
	// @Mocked
	// LLRPMessageHandler llrpMessageHandler;
	// @Mocked
	// RFCMessageHandler rfcMessageHandler;
	// @Mocked
	// GPIOMessageHandler gpioMessageHandler;
	// @Mocked
	// ReaderEventNotificationSpec rens;

	@Test
	public void createLLRPGetReaderConfigErrorGPIEvent(//
			@Mocked final Platform platform, @Mocked final ROSpecsManager roSpecsManager,
			@Mocked final LLRPMessageHandler llrpMessageHandler,
			@Mocked final RFCMessageHandler rfcMessageHandler,
			@Mocked final GPIOMessageHandler gpioMessageHandler,
			@Mocked final ReaderEventNotificationSpec rens//
	) throws Exception {
		class Data {
			List<EventNotificationState> states = new ArrayList<>();
		}
		final Data data = new Data();
		new Expectations() {
			{
				roSpecsManager.gpiEventReceived(withInstanceOf(GPIEvent.class));
				result = new InvalidIdentifierException("huhu");

				rens.getEventNotificationStateList();
				result = new Delegate<ReaderEventNotificationSpec>() {
					@SuppressWarnings("unused")
					public List<EventNotificationState> getEventNotificationStateList() {
						return data.states;
					}
				};
			}
		};
		// create FSM
		FSMEvents fsmEvents = createFSMEvents(platform, roSpecsManager, llrpMessageHandler,
				rfcMessageHandler, gpioMessageHandler, null /* reportDepot */);
		FSM<FSMEvent> fsm = new FSMCreator().create(fsmEvents);

		// send LLRP GetReaderConfig request
		fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData()
				.setNegotiatedProtocolVersion(ProtocolVersion.LLRP_V1_1);
		fsmEvents.LLRP_MESSAGE_RECEIVED.setMessage(new GetReaderConfig(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_1, 1 /* id */),
				(short) 1 /* antennaId */, GetReaderConfigRequestedData.ALL, 2 /* gpiPortNum */,
				(short) 3 /* gpoPortNum */));
		fsm.fire(fsmEvents.LLRP_MESSAGE_RECEIVED);
		fsm.fire(fsmEvents.LLRP_GET_READER_CONFIG_RECEIVED);

		// send a GPI event (sending of LLRP exception events is disabled)
		fsmEvents.GPIO_MESSAGE_RECEIVED.setMessage(
				new StateChanged(new havis.llrpservice.sbc.gpio.message.MessageHeader(3 /* id */),
						new StateEvent((short) 2 /* id */, State.HIGH)));
		fsm.fire(fsmEvents.GPIO_MESSAGE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("4 "));
		fsm.fire(fsmEvents.GPIO_STATE_CHANGED_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));

		new Verifications() {
			{
				// no LLRP event has been sent
				llrpMessageHandler
						.requestSendingData(withInstanceOf(ReaderEventNotification.class));
				times = 0;
			}
		};

		// send a GPI event (sending of LLRP exception events is enabled)
		data.states.add(new EventNotificationState(new TLVParameterHeader((byte) 0),
				EventNotificationStateEventType.READER_EXCEPTION_EVENT, true /* state */));
		fsm.fire(fsmEvents.GPIO_MESSAGE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("4 "));
		fsm.fire(fsmEvents.GPIO_STATE_CHANGED_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));

		new Verifications() {
			{
				// the LLRP exception event has been sent
				ReaderEventNotification response;
				llrpMessageHandler.requestSendingData(response = withCapture());
				times = 1;
				assertTrue(response.getReaderEventNotificationData().getReaderExceptionEvent()
						.getStringMessage().contains("Cannot process GPI event"));
			}
		};
	}

	// @Mocked
	// Platform platfom;
	// @Mocked
	// LLRPMessageHandler llrpMessageHandler;
	// @Mocked
	// ROSpecsManager roSpecsManager;
	// @Mocked
	// RFCMessageHandler rfcMessageHandler;

	@Test
	public void createLLRPSetReaderConfigEmptyAndKeepAlive(//
			@Mocked final Platform platfom, @Mocked final LLRPMessageHandler llrpMessageHandler,
			@Mocked final ROSpecsManager roSpecsManager,
			@Mocked final RFCMessageHandler rfcMessageHandler//
	) throws Exception {
		// create FSM
		FSMEvents fsmEvents = createFSMEvents(platfom, roSpecsManager, llrpMessageHandler,
				rfcMessageHandler, null /* gpioMessageHandler */, null /* reportDepot */);
		FSM<FSMEvent> fsm = new FSMCreator().create(fsmEvents);

		// send LLRP request without keepAliveSpec
		fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData()
				.setNegotiatedProtocolVersion(ProtocolVersion.LLRP_V1_1);
		SetReaderConfig request = new SetReaderConfig(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_1, 1 /* id */),
				false /* resetToFactoryDefaults */);
		fsmEvents.LLRP_MESSAGE_RECEIVED.setMessage(request);
		fsm.fire(fsmEvents.LLRP_MESSAGE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("2 "));
		fsm.fire(fsmEvents.LLRP_SET_READER_CONFIG_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));

		new Verifications() {
			{
				// the keep alive thread has been initialized
				llrpMessageHandler.setKeepaliveInterval(0 /* keepAliveTimeInterval */,
						anyLong /* stopTimeout */);
				times = 1;

				// the LLRP response has been sent
				SetReaderConfigResponse response;
				llrpMessageHandler.requestSendingData(response = withCapture());
				times = 1;
				Assert.assertEquals(response.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);
			}
		};

		// send LLRP request with keepAliveSpec starting keep alive
		KeepaliveSpec keepAliveSpec = new KeepaliveSpec(new TLVParameterHeader((byte) 0),
				KeepaliveSpecTriggerType.PERIODIC);
		final long keepAliveTimeInterval = 3;
		keepAliveSpec.setTimeInterval(keepAliveTimeInterval);
		request.setKeepaliveSpec(keepAliveSpec);
		fsmEvents.LLRP_MESSAGE_RECEIVED.setMessage(request);
		fsm.fire(fsmEvents.LLRP_MESSAGE_RECEIVED);
		fsm.fire(fsmEvents.LLRP_SET_READER_CONFIG_RECEIVED);

		// the config has been saved
		Assert.assertEquals(fsmEvents.LLRP_SET_READER_CONFIG_RECEIVED.getRuntimeData()
				.getReaderConfig().getKeepaliveSpec(), keepAliveSpec);

		new Verifications() {
			{
				// the keepAlive thread has been updated
				llrpMessageHandler.setKeepaliveInterval(keepAliveTimeInterval,
						anyLong /* stopTimeout */);
				times = 1;

				// the LLRP response has been sent
				SetReaderConfigResponse response;
				llrpMessageHandler.requestSendingData(response = withCapture());
				times = 2;

				Assert.assertEquals(response.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);
			}
		};

		// send LLRP request with keepAliveSpec stopping keep alive
		keepAliveSpec.setTriggerType(KeepaliveSpecTriggerType.NULL);
		fsmEvents.LLRP_MESSAGE_RECEIVED.setMessage(request);
		fsm.fire(fsmEvents.LLRP_MESSAGE_RECEIVED);
		fsm.fire(fsmEvents.LLRP_SET_READER_CONFIG_RECEIVED);
		new Verifications() {
			{
				// the keepAlive thread has been updated
				llrpMessageHandler.setKeepaliveInterval(0 /* keepAliveTimeInterval */,
						anyLong /* stopTimeout */);
				times = 2;

				// the LLRP response has been sent
				SetReaderConfigResponse response;
				llrpMessageHandler.requestSendingData(response = withCapture());
				times = 3;

				Assert.assertEquals(response.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);
			}
		};

		// all processed messages must have been removed from runtime data
		Assert.assertNull(fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData().getCurrentMessage());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void createLLRPSetReaderConfigRfc(@Mocked final Platform platfom,
			@Mocked final LLRPMessageHandler llrpMessageHandler,
			@Mocked final ROSpecsManager roSpecsManager,
			@Mocked final RFCMessageHandler rfcMessageHandler) throws Exception {
		// create FSM
		FSMEvents fsmEvents = createFSMEvents(platfom, roSpecsManager, llrpMessageHandler,
				rfcMessageHandler, null /* gpioMessageHandler */, null /* reportDepot */);
		FSM<FSMEvent> fsm = new FSMCreator().create(fsmEvents);

		// send LLRP request with activated reset to factory defaults
		fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData()
				.setNegotiatedProtocolVersion(ProtocolVersion.LLRP_V1_1);
		SetReaderConfig request = new SetReaderConfig(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_1, 1 /* id */),
				false /* resetToFactoryDefaults */);
		List<AntennaConfiguration> antennaConfigurationList = new ArrayList<>();
		AntennaConfiguration antennaConf = new AntennaConfiguration(
				new TLVParameterHeader((byte) 0), 1 /* antennaId */);
		antennaConf.setRfReceiver(
				new RFReceiver(new TLVParameterHeader((byte) 0), 11 /* receiverSensitivity */));
		antennaConfigurationList.add(antennaConf);
		request.setAntennaConfigurationList(antennaConfigurationList);
		fsmEvents.LLRP_MESSAGE_RECEIVED.setMessage(request);
		fsm.fire(fsmEvents.LLRP_MESSAGE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("2 "));
		fsm.fire(fsmEvents.LLRP_SET_READER_CONFIG_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));
		new Verifications() {
			{
				// a RFC request has been sent
				rfcMessageHandler.requestExecution(false /* reset */,
						withInstanceOf(List.class) /* configuration */);
				times = 1;
			}
		};

		// send RFC set response
		fsmEvents.RFC_MESSAGE_RECEIVED.setMessage(new SetConfigurationResponse(
				new havis.llrpservice.sbc.rfc.message.MessageHeader(2 /* id */)));
		fsm.fire(fsmEvents.RFC_MESSAGE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("3 "));
		fsm.fire(fsmEvents.RFC_SET_CONFIGURATION_RESPONSE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));

		new Verifications() {
			{
				// the LLRP response has been sent
				SetReaderConfigResponse response;
				llrpMessageHandler.requestSendingData(response = withCapture());
				times = 1;

				Assert.assertEquals(response.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);
			}
		};

		// all processed messages must have been removed from runtime data
		Assert.assertNull(fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData().getCurrentMessage());
		Assert.assertNull(fsmEvents.RFC_GET_CONFIGURATION_RESPONSE_RECEIVED.getRuntimeData()
				.getCurrentMessage());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void createLLRPSetReaderConfigRfcAndGpio(@Mocked final Platform platfom,
			@Mocked final LLRPMessageHandler llrpMessageHandler,
			@Mocked final ROSpecsManager roSpecsManager,
			@Mocked final RFCMessageHandler rfcMessageHandler,
			@Mocked final GPIOMessageHandler gpioMessageHandler) throws Exception {
		// create FSM
		FSMEvents fsmEvents = createFSMEvents(platfom, roSpecsManager, llrpMessageHandler,
				rfcMessageHandler, gpioMessageHandler, null /* reportDepot */);
		FSM<FSMEvent> fsm = new FSMCreator().create(fsmEvents);

		// send LLRP request for antenna configuration and GPI configuration
		fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData()
				.setNegotiatedProtocolVersion(ProtocolVersion.LLRP_V1_1);
		SetReaderConfig request = new SetReaderConfig(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_1, 1 /* id */),
				false /* resetToFactoryDefaults */);

		List<AntennaConfiguration> antennaConfigurationList = new ArrayList<>();
		AntennaConfiguration antennaConf = new AntennaConfiguration(
				new TLVParameterHeader((byte) 0), 1 /* antennaId */);
		antennaConf.setRfReceiver(
				new RFReceiver(new TLVParameterHeader((byte) 0), 11 /* receiverSensitivity */));
		antennaConfigurationList.add(antennaConf);
		request.setAntennaConfigurationList(antennaConfigurationList);

		List<GPIPortCurrentState> gpiPortCurrentStateList = new ArrayList<>();
		gpiPortCurrentStateList.add(new GPIPortCurrentState(new TLVParameterHeader((byte) 0),
				2 /* gpiPortNum */, true /* gpiConfig */, GPIPortCurrentStateGPIState.HIGH));
		request.setGpiPortCurrentStateList(gpiPortCurrentStateList);

		fsmEvents.LLRP_MESSAGE_RECEIVED.setMessage(request);
		fsm.fire(fsmEvents.LLRP_MESSAGE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("2 "));
		fsm.fire(fsmEvents.LLRP_SET_READER_CONFIG_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));
		new Verifications() {
			{
				// a RFC request has been sent
				rfcMessageHandler.requestExecution(false /* reset */,
						withInstanceOf(List.class) /* configurations */);
				times = 1;
				// a GPIO request has been sent
				gpioMessageHandler.requestExecution(false /* reset */,
						withInstanceOf(List.class) /* configurations */);
				times = 1;
			}
		};

		// first send RFC response
		fsmEvents.RFC_MESSAGE_RECEIVED.setMessage(new SetConfigurationResponse(
				new havis.llrpservice.sbc.rfc.message.MessageHeader(3 /* id */)));
		fsm.fire(fsmEvents.RFC_MESSAGE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("3 "));
		fsm.fire(fsmEvents.RFC_SET_CONFIGURATION_RESPONSE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));

		// send GPIO response
		fsmEvents.GPIO_MESSAGE_RECEIVED
				.setMessage(new havis.llrpservice.sbc.gpio.message.SetConfigurationResponse(
						new havis.llrpservice.sbc.gpio.message.MessageHeader(4 /* id */)));
		fsm.fire(fsmEvents.GPIO_MESSAGE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("4 "));
		fsm.fire(fsmEvents.GPIO_SET_CONFIGURATION_RESPONSE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));
		new Verifications() {
			{
				// the LLRP response has been sent
				SetReaderConfigResponse response;
				llrpMessageHandler.requestSendingData(response = withCapture());
				times = 1;

				Assert.assertEquals(response.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);
			}
		};

		// all processed messages must have been removed from runtime data
		Assert.assertNull(fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData().getCurrentMessage());
		Assert.assertNull(fsmEvents.RFC_GET_CONFIGURATION_RESPONSE_RECEIVED.getRuntimeData()
				.getCurrentMessage());
		Assert.assertNull(fsmEvents.GPIO_GET_CONFIGURATION_RESPONSE_RECEIVED.getRuntimeData()
				.getCurrentMessage());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void createLLRPSetReaderConfigResetROReportSpecRfcGpio(@Mocked final Platform platfom,
			@Mocked final LLRPMessageHandler llrpMessageHandler,
			@Mocked final ROSpecsManager roSpecsManager, @Mocked final ROReportSpec roReportSpec,
			@Mocked final RFCMessageHandler rfcMessageHandler,
			@Mocked final GPIOMessageHandler gpioMessageHandler) throws Exception {
		// create FSM
		FSMEvents fsmEvents = createFSMEvents(platfom, roSpecsManager, llrpMessageHandler,
				rfcMessageHandler, gpioMessageHandler, null /* reportDepot */);
		FSM<FSMEvent> fsm = new FSMCreator().create(fsmEvents);

		// send LLRP request for antenna configuration and GPI configuration
		fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData()
				.setNegotiatedProtocolVersion(ProtocolVersion.LLRP_V1_1);
		final SetReaderConfig request = new SetReaderConfig(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_1, 1 /* id */),
				true /* resetToFactoryDefaults */);
		request.setRoReportSpec(roReportSpec);
		List<AntennaConfiguration> antennaConfigurationList = new ArrayList<>();
		AntennaConfiguration antennaConf = new AntennaConfiguration(
				new TLVParameterHeader((byte) 0), 1 /* antennaId */);
		antennaConf.setRfReceiver(
				new RFReceiver(new TLVParameterHeader((byte) 0), 11 /* receiverSensitivity */));
		antennaConfigurationList.add(antennaConf);
		request.setAntennaConfigurationList(antennaConfigurationList);

		List<GPIPortCurrentState> gpiPortCurrentStateList = new ArrayList<>();
		gpiPortCurrentStateList.add(new GPIPortCurrentState(new TLVParameterHeader((byte) 0),
				2 /* gpiPortNum */, true /* gpiConfig */, GPIPortCurrentStateGPIState.HIGH));
		request.setGpiPortCurrentStateList(gpiPortCurrentStateList);

		fsmEvents.LLRP_MESSAGE_RECEIVED.setMessage(request);
		fsm.fire(fsmEvents.LLRP_MESSAGE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("2 "));
		fsm.fire(fsmEvents.LLRP_SET_READER_CONFIG_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));
		new Verifications() {
			{
				// the default ROReportSpec is set to ROReportSpecsManager
				roSpecsManager.getROReportSpecsManager()
						.setDefaultROReportSpec(request.getRoReportSpec());
				times = 1;
				// a RFC request has been sent
				rfcMessageHandler.requestExecution(true /* reset */,
						withInstanceOf(List.class) /* configurations */);
				times = 1;
				// a GPIO request has been sent
				gpioMessageHandler.requestExecution(true /* reset */,
						withInstanceOf(List.class) /* configurations */);
				times = 1;
			}
		};

		// first send GPIO responses (reset + set)
		fsmEvents.GPIO_MESSAGE_RECEIVED
				.setMessage(new havis.llrpservice.sbc.gpio.message.ResetConfigurationResponse(
						new havis.llrpservice.sbc.gpio.message.MessageHeader(41 /* id */)));
		fsm.fire(fsmEvents.GPIO_MESSAGE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("4 "));
		fsm.fire(fsmEvents.GPIO_RESET_CONFIGURATION_RESPONSE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));

		fsmEvents.GPIO_MESSAGE_RECEIVED
				.setMessage(new havis.llrpservice.sbc.gpio.message.SetConfigurationResponse(
						new havis.llrpservice.sbc.gpio.message.MessageHeader(42 /* id */)));
		fsm.fire(fsmEvents.GPIO_MESSAGE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("4 "));
		fsm.fire(fsmEvents.GPIO_SET_CONFIGURATION_RESPONSE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));

		// send RFC responses (reset + set)
		fsmEvents.RFC_MESSAGE_RECEIVED.setMessage(new ResetConfigurationResponse(
				new havis.llrpservice.sbc.rfc.message.MessageHeader(31 /* id */)));
		fsm.fire(fsmEvents.RFC_MESSAGE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("3 "));
		fsm.fire(fsmEvents.RFC_RESET_CONFIGURATION_RESPONSE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));

		fsmEvents.RFC_MESSAGE_RECEIVED.setMessage(new SetConfigurationResponse(
				new havis.llrpservice.sbc.rfc.message.MessageHeader(32 /* id */)));
		fsm.fire(fsmEvents.RFC_MESSAGE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("3 "));
		fsm.fire(fsmEvents.RFC_SET_CONFIGURATION_RESPONSE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));

		new Verifications() {
			{
				// the LLRP response has been sent
				SetReaderConfigResponse response;
				llrpMessageHandler.requestSendingData(response = withCapture());
				times = 1;

				Assert.assertEquals(response.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);
			}
		};

		// all processed messages must have been removed from runtime data
		Assert.assertNull(fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData().getCurrentMessage());
		Assert.assertNull(fsmEvents.RFC_GET_CONFIGURATION_RESPONSE_RECEIVED.getRuntimeData()
				.getCurrentMessage());
		Assert.assertNull(fsmEvents.GPIO_GET_CONFIGURATION_RESPONSE_RECEIVED.getRuntimeData()
				.getCurrentMessage());
	}

	@Test
	public void createLLRPSetReaderConfigErrorCustomExtension(@Mocked final Platform platfom,
			@Mocked final LLRPMessageHandler llrpMessageHandler,
			@Mocked final RFCMessageHandler rfcMessageHandler,
			@Mocked final GPIOMessageHandler gpioMessageHandler) throws Exception {
		// create FSM
		FSMEvents fsmEvents = createFSMEvents(platfom, null /* roSpecsManager */,
				llrpMessageHandler, rfcMessageHandler, gpioMessageHandler, null /* reportDepot */);
		FSM<FSMEvent> fsm = new FSMCreator().create(fsmEvents);

		// send LLRP request
		fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData()
				.setNegotiatedProtocolVersion(ProtocolVersion.LLRP_V1_1);
		SetReaderConfig request = new SetReaderConfig(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_1, 1 /* id */),
				false /* resetToFactoryDefaults */);
		List<Custom> customExtensionPoint = new ArrayList<>();
		customExtensionPoint.add(new Custom(new TLVParameterHeader((byte) 0), 1L /* vendorID */,
				2L /* subType */, new byte[0] /* data */));
		request.setCustomList(customExtensionPoint);
		fsmEvents.LLRP_MESSAGE_RECEIVED.setMessage(request);
		fsm.fire(fsmEvents.LLRP_MESSAGE_RECEIVED);
		fsm.fire(fsmEvents.LLRP_SET_READER_CONFIG_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));
		new Verifications() {
			{
				// the LLRP response has been sent with an error
				SetReaderConfigResponse response;
				llrpMessageHandler.requestSendingData(response = withCapture());
				times = 1;

				Assert.assertEquals(response.getStatus().getStatusCode(),
						LLRPStatusCode.P_UNEXPECTED_PARAMETER);
			}
		};

		// all processed messages must have been removed from runtime data
		Assert.assertNull(fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData().getCurrentMessage());
	}

	@Test
	public void createLLRPSetReaderCapabilitiesErrorRFCResponse(@Mocked final Platform platfom,
			@Mocked final LLRPMessageHandler llrpMessageHandler,
			@Mocked final ROSpecsManager roSpecsManager,
			@Mocked final RFCMessageHandler rfcMessageHandler,
			@Mocked final GPIOMessageHandler gpioMessageHandler) throws Exception {
		// create FSM
		FSMEvents fsmEvents = createFSMEvents(platfom, roSpecsManager, llrpMessageHandler,
				rfcMessageHandler, gpioMessageHandler, null /* reportDepot */);
		FSM<FSMEvent> fsm = new FSMCreator().create(fsmEvents);

		// send LLRP request
		fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData()
				.setNegotiatedProtocolVersion(ProtocolVersion.LLRP_V1_1);
		SetReaderConfig request = new SetReaderConfig(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_1, 1 /* id */),
				false /* resetToFactoryDefaults */);

		List<AntennaConfiguration> antennaConfigurationList = new ArrayList<>();
		AntennaConfiguration antennaConf = new AntennaConfiguration(
				new TLVParameterHeader((byte) 0), 1 /* antennaId */);
		antennaConf.setRfReceiver(
				new RFReceiver(new TLVParameterHeader((byte) 0), 11 /* receiverSensitivity */));
		antennaConfigurationList.add(antennaConf);
		request.setAntennaConfigurationList(antennaConfigurationList);

		fsmEvents.LLRP_MESSAGE_RECEIVED.setMessage(request);
		fsm.fire(fsmEvents.LLRP_MESSAGE_RECEIVED);
		fsm.fire(fsmEvents.LLRP_SET_READER_CONFIG_RECEIVED);

		// send RFC response with error
		SetConfigurationResponse rfcResponse = new SetConfigurationResponse(
				new havis.llrpservice.sbc.rfc.message.MessageHeader(31 /* id */));
		rfcResponse.setException(new FSMActionException("huhu"));
		fsmEvents.RFC_MESSAGE_RECEIVED.setMessage(rfcResponse);
		fsm.fire(fsmEvents.RFC_MESSAGE_RECEIVED);
		fsm.fire(fsmEvents.RFC_SET_CONFIGURATION_RESPONSE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));

		new Verifications() {
			{
				// the LLRP response has been sent with an error
				SetReaderConfigResponse response;
				llrpMessageHandler.requestSendingData(response = withCapture());
				times = 1;

				Assert.assertTrue(response.getStatus().getErrorDescription().contains("huhu"));
				Assert.assertEquals(response.getStatus().getStatusCode(),
						LLRPStatusCode.R_DEVICE_ERROR);
			}
		};

		// all processed messages must have been removed from runtime data
		Assert.assertNull(fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData().getCurrentMessage());
		Assert.assertNull(fsmEvents.RFC_GET_CONFIGURATION_RESPONSE_RECEIVED.getRuntimeData()
				.getCurrentMessage());
	}

	@Test
	public void createLLRPSetReaderCapabilitiesErrorGPIOResponse(@Mocked final Platform platfom,
			@Mocked final LLRPMessageHandler llrpMessageHandler,
			@Mocked final ROSpecsManager roSpecsManager,
			@Mocked final RFCMessageHandler rfcMessageHandler,
			@Mocked final GPIOMessageHandler gpioMessageHandler) throws Exception {
		// create FSM
		FSMEvents fsmEvents = createFSMEvents(platfom, roSpecsManager, llrpMessageHandler,
				rfcMessageHandler, gpioMessageHandler, null /* reportDepot */);
		FSM<FSMEvent> fsm = new FSMCreator().create(fsmEvents);

		// send LLRP request
		fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData()
				.setNegotiatedProtocolVersion(ProtocolVersion.LLRP_V1_1);
		SetReaderConfig request = new SetReaderConfig(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_1, 1 /* id */),
				false /* resetToFactoryDefaults */);

		List<GPIPortCurrentState> gpiPortCurrentStateList = new ArrayList<>();
		gpiPortCurrentStateList.add(new GPIPortCurrentState(new TLVParameterHeader((byte) 0),
				2 /* gpiPortNum */, true /* gpiConfig */, GPIPortCurrentStateGPIState.HIGH));
		request.setGpiPortCurrentStateList(gpiPortCurrentStateList);

		fsmEvents.LLRP_MESSAGE_RECEIVED.setMessage(request);
		fsm.fire(fsmEvents.LLRP_MESSAGE_RECEIVED);
		fsm.fire(fsmEvents.LLRP_SET_READER_CONFIG_RECEIVED);

		// send GPIO response with error
		havis.llrpservice.sbc.gpio.message.SetConfigurationResponse gpioResponse = new havis.llrpservice.sbc.gpio.message.SetConfigurationResponse(
				new havis.llrpservice.sbc.gpio.message.MessageHeader(41 /* id */));
		gpioResponse.setException(new FSMActionException("huhu"));
		fsmEvents.GPIO_MESSAGE_RECEIVED.setMessage(gpioResponse);
		fsm.fire(fsmEvents.GPIO_MESSAGE_RECEIVED);
		fsm.fire(fsmEvents.GPIO_SET_CONFIGURATION_RESPONSE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));

		new Verifications() {
			{
				// the LLRP response has been sent with an error
				SetReaderConfigResponse response;
				llrpMessageHandler.requestSendingData(response = withCapture());
				times = 1;

				Assert.assertTrue(response.getStatus().getErrorDescription().contains("huhu"));
				Assert.assertEquals(response.getStatus().getStatusCode(),
						LLRPStatusCode.R_DEVICE_ERROR);
			}
		};

		// all processed messages must have been removed from runtime data
		Assert.assertNull(fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData().getCurrentMessage());
		Assert.assertNull(fsmEvents.GPIO_GET_CONFIGURATION_RESPONSE_RECEIVED.getRuntimeData()
				.getCurrentMessage());
	}

	private FSMEvents createFSMEvents(Platform platform, ROSpecsManager roSpecsManager,
			LLRPMessageHandler llrpMessageHandler, RFCMessageHandler rfcMessageHandler,
			GPIOMessageHandler gpioMessageHandler, ROAccessReportDepot reportDepot)
			throws FSMActionException {
		IdentificationSourceType identificationSource = new IdentificationSourceType();
		identificationSource.setType(IdentificationTypeEnumeration.EPC);
		identificationSource.setLength(8);
		identificationSource.setPropertyName("mica.device.serial_no");
		LLRPCapabilitiesType llrpCapabilities = new LLRPCapabilitiesType();
		llrpCapabilities.setCanDoRFSurvey(false);
		llrpCapabilities.setCanReportBufferFillWarning(false);
		llrpCapabilities.setSupportsClientRequestOpSpec(false);
		llrpCapabilities.setCanDoTagInventoryStateAwareSingulation(false);
		llrpCapabilities.setSupportsEventAndReportHolding(true);
		llrpCapabilities.setMaxPriorityLevelSupported((byte) 6);
		llrpCapabilities.setClientRequestOpSpecTimeout(7000);
		llrpCapabilities.setMaxNumROSpecs(1000);
		llrpCapabilities.setMaxNumSpecsPerROSpec(1000);
		llrpCapabilities.setMaxNumInventoryParameterSpecsPerAISpec(1000);
		llrpCapabilities.setMaxNumAccessSpecs(1000);
		llrpCapabilities.setMaxNumOpSpecsPerAccessSpec(1000);
		LLRPServiceInstanceRuntimeData serviceInstanceRuntimeData = new LLRPServiceInstanceRuntimeData(
				platform, 5 /* unexpectedTimeout */);
		LLRPRuntimeData llrpRuntimeData = new LLRPRuntimeData(identificationSource,
				llrpCapabilities, llrpMessageHandler, roSpecsManager, reportDepot);
		RFCRuntimeData rfcRuntimeData = new RFCRuntimeData(rfcMessageHandler);
		GPIORuntimeData gpioRuntimeData = null;
		if (gpioMessageHandler != null) {
			gpioRuntimeData = new GPIORuntimeData(gpioMessageHandler);
		}
		return new FSMEvents(serviceInstanceRuntimeData, llrpRuntimeData, rfcRuntimeData,
				gpioRuntimeData);
	}
}
