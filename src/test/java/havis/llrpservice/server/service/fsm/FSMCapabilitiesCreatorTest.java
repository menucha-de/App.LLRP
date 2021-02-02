package havis.llrpservice.server.service.fsm;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.Test;

import havis.device.io.Configuration;
import havis.device.io.State;
import havis.device.io.StateEvent;
import havis.device.rf.capabilities.Capabilities;
import havis.llrpservice.common.fsm.FSM;
import havis.llrpservice.common.fsm.FSMActionException;
import havis.llrpservice.data.message.GetReaderCapabilities;
import havis.llrpservice.data.message.GetReaderCapabilitiesRequestedData;
import havis.llrpservice.data.message.GetReaderCapabilitiesResponse;
import havis.llrpservice.data.message.MessageHeader;
import havis.llrpservice.data.message.ProtocolVersion;
import havis.llrpservice.data.message.ReaderEventNotification;
import havis.llrpservice.data.message.parameter.Custom;
import havis.llrpservice.data.message.parameter.EventNotificationState;
import havis.llrpservice.data.message.parameter.EventNotificationStateEventType;
import havis.llrpservice.data.message.parameter.GPIEvent;
import havis.llrpservice.data.message.parameter.LLRPStatusCode;
import havis.llrpservice.data.message.parameter.ReaderEventNotificationSpec;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.sbc.gpio.message.GetConfigurationResponse;
import havis.llrpservice.sbc.gpio.message.StateChanged;
import havis.llrpservice.sbc.rfc.RFCException;
import havis.llrpservice.sbc.rfc.message.GetCapabilitiesResponse;
import havis.llrpservice.server.gpio.GPIOMessageHandler;
import havis.llrpservice.server.llrp.LLRPMessageHandler;
import havis.llrpservice.server.rfc.RFCMessageHandler;
import havis.llrpservice.server.service.InvalidIdentifierException;
import havis.llrpservice.server.service.ROAccessReportDepot;
import havis.llrpservice.server.service.ROSpecsManager;
import havis.llrpservice.server.service.fsm.gpio.GPIORuntimeData;
import havis.llrpservice.server.service.fsm.lllrp.LLRPRuntimeData;
import havis.llrpservice.server.service.fsm.rfc.RFCRuntimeData;
import havis.llrpservice.xml.properties.LLRPCapabilitiesType;
import havis.util.platform.Platform;
import mockit.Delegate;
import mockit.Expectations;
import mockit.Mocked;
import mockit.Verifications;

public class FSMCapabilitiesCreatorTest {

	// @Mocked
	// Platform platform;
	// @Mocked
	// LLRPMessageHandler llrpMessageHandler;
	// @Mocked
	// RFCMessageHandler rfcMessageHandler;

	@Test
	public void createLLRPGetReaderCapabilitiesNoRfcNoGpio(//
			@Mocked final Platform platform, @Mocked final LLRPMessageHandler llrpMessageHandler,
			@Mocked final RFCMessageHandler rfcMessageHandler//
	) throws Exception {
		// create FSM
		FSMEvents fsmEvents = createFSMEvents(platform, null /* roSpecsManager */,
				llrpMessageHandler, rfcMessageHandler, null /* gpioMessageHandler */,
				null /* reportDepot */);
		FSM<FSMEvent> fsm = new FSMCreator().create(fsmEvents);

		// send LLRP request
		fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData()
				.setNegotiatedProtocolVersion(ProtocolVersion.LLRP_V1_1);
		fsmEvents.LLRP_MESSAGE_RECEIVED.setMessage(new GetReaderCapabilities(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_1, 1 /* id */),
				GetReaderCapabilitiesRequestedData.LLRP_CAPABILITIES));
		fsm.fire(fsmEvents.LLRP_MESSAGE_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("2 "));
		fsm.fire(fsmEvents.LLRP_GET_READER_CAPABILITIES_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("1 "));
		new Verifications() {
			{
				// the LLRP response has been sent
				GetReaderCapabilitiesResponse response;
				llrpMessageHandler.requestSendingData(response = withCapture());
				times = 1;

				assertEquals(response.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);
			}
		};

		// all processed messages must have been removed from runtime data
		assertNull(fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData().getCurrentMessage());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void createLLRPGetReaderCapabilitiesOnlyRfc(@Mocked final Platform platform,
			@Mocked final LLRPMessageHandler llrpMessageHandler,
			@Mocked final RFCMessageHandler rfcMessageHandler) throws Exception {
		// create FSM
		FSMEvents fsmEvents = createFSMEvents(platform, null /* roSpecsManager */,
				llrpMessageHandler, rfcMessageHandler, null /* gpioMessageHandler */,
				null /* reportDepot */);
		FSM<FSMEvent> fsm = new FSMCreator().create(fsmEvents);

		// send LLRP request
		fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData()
				.setNegotiatedProtocolVersion(ProtocolVersion.LLRP_V1_1);
		fsmEvents.LLRP_MESSAGE_RECEIVED.setMessage(new GetReaderCapabilities(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_1, 1 /* id */),
				GetReaderCapabilitiesRequestedData.GENERAL_DEVICE_CAPABILITIES));
		fsm.fire(fsmEvents.LLRP_MESSAGE_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("2 "));
		fsm.fire(fsmEvents.LLRP_GET_READER_CAPABILITIES_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("1.1"));
		new Verifications() {
			{
				// a RFC request has been sent
				rfcMessageHandler.requestCapabilities(withInstanceOf(List.class));
				times = 1;
			}
		};

		// send RFC response
		fsmEvents.RFC_MESSAGE_RECEIVED.setMessage(new GetCapabilitiesResponse(
				new havis.llrpservice.sbc.rfc.message.MessageHeader(2 /* id */),
				new ArrayList<Capabilities>()));
		fsm.fire(fsmEvents.RFC_MESSAGE_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("3.1"));
		fsm.fire(fsmEvents.RFC_GET_CAPABILITIES_RESPONSE_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("1 "));

		new Verifications() {
			{
				// the LLRP response has been sent
				GetReaderCapabilitiesResponse response;
				llrpMessageHandler.requestSendingData(response = withCapture());
				times = 1;

				assertEquals(response.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);
			}
		};

		// all processed messages must have been removed from runtime data
		assertNull(fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData().getCurrentMessage());
		assertNull(fsmEvents.RFC_GET_CAPABILITIES_RESPONSE_RECEIVED.getRuntimeData()
				.getCurrentMessage());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void createLLRPGetReaderCapabilitiesRfcAndGpio(@Mocked final Platform platform,
			@Mocked final LLRPMessageHandler llrpMessageHandler,
			@Mocked final RFCMessageHandler rfcMessageHandler,
			@Mocked final GPIOMessageHandler gpioMessageHandler) throws Exception {
		// create FSM
		FSMEvents fsmEvents = createFSMEvents(platform, null /* roSpecsManager */,
				llrpMessageHandler, rfcMessageHandler, gpioMessageHandler, null /* reportDepot */);
		FSM<FSMEvent> fsm = new FSMCreator().create(fsmEvents);

		// send LLRP request
		fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData()
				.setNegotiatedProtocolVersion(ProtocolVersion.LLRP_V1_1);
		fsmEvents.LLRP_MESSAGE_RECEIVED.setMessage(new GetReaderCapabilities(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_1, 1 /* id */),
				GetReaderCapabilitiesRequestedData.GENERAL_DEVICE_CAPABILITIES));
		fsm.fire(fsmEvents.LLRP_MESSAGE_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("2 "));
		fsm.fire(fsmEvents.LLRP_GET_READER_CAPABILITIES_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("1.1"));
		new Verifications() {
			{
				// a RFC request has been sent
				rfcMessageHandler.requestCapabilities(withInstanceOf(List.class));
				times = 1;
				// a GPIO request has been sent
				gpioMessageHandler.requestConfiguration(withInstanceOf(List.class),
						(short) 0 /* gpiPortNum */, (short) 0 /* gpoPortNum */);
				times = 1;
			}
		};

		// first send RFC response
		fsmEvents.RFC_MESSAGE_RECEIVED.setMessage(new GetCapabilitiesResponse(
				new havis.llrpservice.sbc.rfc.message.MessageHeader(2 /* id */),
				new ArrayList<Capabilities>()));
		fsm.fire(fsmEvents.RFC_MESSAGE_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("3.1"));
		fsm.fire(fsmEvents.RFC_GET_CAPABILITIES_RESPONSE_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("1.1"));

		// send GPIO response
		fsmEvents.GPIO_MESSAGE_RECEIVED.setMessage(new GetConfigurationResponse(
				new havis.llrpservice.sbc.gpio.message.MessageHeader(3 /* id */),
				new ArrayList<Configuration>()));
		fsm.fire(fsmEvents.GPIO_MESSAGE_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("4.1"));
		fsm.fire(fsmEvents.GPIO_GET_CONFIGURATION_RESPONSE_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("1 "));
		new Verifications() {
			{
				// the LLRP response has been sent
				GetReaderCapabilitiesResponse response;
				llrpMessageHandler.requestSendingData(response = withCapture());
				times = 1;

				assertEquals(response.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);
			}
		};

		// all processed messages must have been removed from runtime data
		assertNull(fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData().getCurrentMessage());
		assertNull(fsmEvents.RFC_GET_CAPABILITIES_RESPONSE_RECEIVED.getRuntimeData()
				.getCurrentMessage());
		assertNull(fsmEvents.GPIO_GET_CONFIGURATION_RESPONSE_RECEIVED.getRuntimeData()
				.getCurrentMessage());
	}

	@Mocked
	Platform platform;
	@Mocked
	LLRPMessageHandler llrpMessageHandler;
	@Mocked
	RFCMessageHandler rfcMessageHandler;
	@Mocked
	GPIOMessageHandler gpioMessageHandler;

	@Test
	@SuppressWarnings("unchecked")
	public void createLLRPGetReaderCapabilitiesError(//
	// @Mocked final Platform platform, @Mocked final LLRPMessageHandler
	// llrpMessageHandler,
	// @Mocked final RFCMessageHandler rfcMessageHandler,
	// @Mocked final GPIOMessageHandler gpioMessageHandler//
	) throws Exception {
		new Expectations() {
			{
				rfcMessageHandler.requestCapabilities(withInstanceOf(List.class));
				result = new RFCException("huhu");
			}
		};
		// create FSM
		FSMEvents fsmEvents = createFSMEvents(platform, null /* roSpecsManager */,
				llrpMessageHandler, rfcMessageHandler, gpioMessageHandler, null /* reportDepot */);
		FSM<FSMEvent> fsm = new FSMCreator().create(fsmEvents);

		// send LLRP request
		fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData()
				.setNegotiatedProtocolVersion(ProtocolVersion.LLRP_V1_1);
		fsmEvents.LLRP_MESSAGE_RECEIVED.setMessage(new GetReaderCapabilities(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_1, 1 /* id */),
				GetReaderCapabilitiesRequestedData.GENERAL_DEVICE_CAPABILITIES));
		fsm.fire(fsmEvents.LLRP_MESSAGE_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("2 "));
		fsm.fire(fsmEvents.LLRP_GET_READER_CAPABILITIES_RECEIVED);
		// the waiting for RFC/GPIO messages is skipped due to the RFC exception
		assertTrue(fsm.getCurrentState().getName().startsWith("1 "));
		new Verifications() {
			{
				// sending of RFC request failed
				rfcMessageHandler.requestCapabilities(withInstanceOf(List.class));
				times = 1;
				// a GPIO request has NOT been sent
				gpioMessageHandler.requestConfiguration(withInstanceOf(List.class),
						(short) 0 /* gpiPortNum */, (short) 0 /* gpoPortNum */);
				times = 0;

				// an LLRP error response has been sent
				GetReaderCapabilitiesResponse response;
				llrpMessageHandler.requestSendingData(response = withCapture());
				times = 1;

				assertEquals(response.getStatus().getStatusCode(), LLRPStatusCode.R_DEVICE_ERROR);
			}
		};

		// all processed messages must have been removed from runtime data
		assertNull(fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData().getCurrentMessage());
		assertNull(fsmEvents.RFC_GET_CAPABILITIES_RESPONSE_RECEIVED.getRuntimeData()
				.getCurrentMessage());
		assertNull(fsmEvents.GPIO_GET_CONFIGURATION_RESPONSE_RECEIVED.getRuntimeData()
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
	public void createLLRPGetReaderCapabilitiesGpioAndRfcAndGPIEvents(//
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

		// send LLRP request
		fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData()
				.setNegotiatedProtocolVersion(ProtocolVersion.LLRP_V1_1);
		fsmEvents.LLRP_MESSAGE_RECEIVED.setMessage(new GetReaderCapabilities(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_1, 1 /* id */),
				GetReaderCapabilitiesRequestedData.GENERAL_DEVICE_CAPABILITIES));
		fsm.fire(fsmEvents.LLRP_MESSAGE_RECEIVED);
		fsm.fire(fsmEvents.LLRP_GET_READER_CAPABILITIES_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("1.1 "));

		// first send GPIO response
		fsmEvents.GPIO_MESSAGE_RECEIVED.setMessage(new GetConfigurationResponse(
				new havis.llrpservice.sbc.gpio.message.MessageHeader(3 /* id */),
				new ArrayList<Configuration>()));
		fsm.fire(fsmEvents.GPIO_MESSAGE_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("4.1"));
		fsm.fire(fsmEvents.GPIO_GET_CONFIGURATION_RESPONSE_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("1.1"));

		// send a GPI event as GPIO response (sending of LLRP GPI events is
		// disabled)
		fsmEvents.GPIO_MESSAGE_RECEIVED.setMessage(
				new StateChanged(new havis.llrpservice.sbc.gpio.message.MessageHeader(3 /* id */),
						new StateEvent((short) 2 /* id */, State.HIGH)));
		fsm.fire(fsmEvents.GPIO_MESSAGE_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("4.1 "));
		fsm.fire(fsmEvents.GPIO_STATE_CHANGED_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("1.1 "));

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
		assertTrue(fsm.getCurrentState().getName().startsWith("4.1 "));
		fsm.fire(fsmEvents.GPIO_STATE_CHANGED_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("1.1 "));

		new Verifications() {
			{
				// the ROSpecsManager is informed
				roSpecsManager.gpiEventReceived(withInstanceOf(GPIEvent.class));
				times = 2;
				// the LLRP event has been sent
				ReaderEventNotification response;
				llrpMessageHandler.requestSendingData(response = withCapture());
				times = 1;
				assertTrue(response.getReaderEventNotificationData().getGpiEvent().isState());
			}
		};

		// send RFC response
		fsmEvents.RFC_MESSAGE_RECEIVED.setMessage(new GetCapabilitiesResponse(
				new havis.llrpservice.sbc.rfc.message.MessageHeader(2 /* id */),
				new ArrayList<Capabilities>()));
		fsm.fire(fsmEvents.RFC_MESSAGE_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("3.1"));
		fsm.fire(fsmEvents.RFC_GET_CAPABILITIES_RESPONSE_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("1 "));

		new Verifications() {
			{
				// the LLRP response has been sent
				GetReaderCapabilitiesResponse response;
				llrpMessageHandler.requestSendingData(response = withCapture());
				times = 1;

				assertEquals(response.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);
			}
		};

		// all processed messages must have been removed from runtime data
		assertNull(fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData().getCurrentMessage());
		assertNull(fsmEvents.RFC_GET_CAPABILITIES_RESPONSE_RECEIVED.getRuntimeData()
				.getCurrentMessage());
		assertNull(fsmEvents.GPIO_GET_CONFIGURATION_RESPONSE_RECEIVED.getRuntimeData()
				.getCurrentMessage());
	}

	@Test
	public void createLLRPGetReaderCapabilitiesErrorCustomExtension(@Mocked final Platform platform,
			@Mocked final LLRPMessageHandler llrpMessageHandler,
			@Mocked final RFCMessageHandler rfcMessageHandler,
			@Mocked final GPIOMessageHandler gpioMessageHandler) throws Exception {
		// create FSM
		FSMEvents fsmEvents = createFSMEvents(platform, null /* roSpecsManager */,
				llrpMessageHandler, rfcMessageHandler, gpioMessageHandler, null /* reportDepot */);
		FSM<FSMEvent> fsm = new FSMCreator().create(fsmEvents);

		GetReaderCapabilities request = new GetReaderCapabilities(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_0_1, 1 /* id */),
				GetReaderCapabilitiesRequestedData.GENERAL_DEVICE_CAPABILITIES);
		List<Custom> customExtensionPoint = new ArrayList<>();
		customExtensionPoint.add(new Custom(new TLVParameterHeader((byte) 0), 1L /* vendorID */,
				2L /* subType */, new byte[0] /* data */));
		request.setCustomExtensionPoint(customExtensionPoint);

		// send LLRP request
		fsmEvents.LLRP_MESSAGE_RECEIVED.setMessage(request);
		fsm.fire(fsmEvents.LLRP_MESSAGE_RECEIVED);
		fsm.fire(fsmEvents.LLRP_GET_READER_CAPABILITIES_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("1 "));
		new Verifications() {
			{
				// the LLRP response has been sent with an error
				GetReaderCapabilitiesResponse response;
				llrpMessageHandler.requestSendingData(response = withCapture());
				times = 1;

				assertEquals(response.getStatus().getStatusCode(),
						LLRPStatusCode.P_UNEXPECTED_PARAMETER);
			}
		};

		// all processed messages must have been removed from runtime data
		assertNull(fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData().getCurrentMessage());
	}

	@Test
	public void createLLRPGetReaderCapabilitiesErrorRFCResponse(@Mocked final Platform platform,
			@Mocked final LLRPMessageHandler llrpMessageHandler,
			@Mocked final RFCMessageHandler rfcMessageHandler) throws Exception {
		// create FSM
		FSMEvents fsmEvents = createFSMEvents(platform, null /* roSpecsManager */,
				llrpMessageHandler, rfcMessageHandler, null /* gpioMessageHandler */,
				null /* reportDepot */);
		FSM<FSMEvent> fsm = new FSMCreator().create(fsmEvents);

		// send LLRP request
		fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData()
				.setNegotiatedProtocolVersion(ProtocolVersion.LLRP_V1_1);
		fsmEvents.LLRP_MESSAGE_RECEIVED.setMessage(new GetReaderCapabilities(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_1, 1 /* id */),
				GetReaderCapabilitiesRequestedData.GENERAL_DEVICE_CAPABILITIES));
		fsm.fire(fsmEvents.LLRP_MESSAGE_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("2 "));
		fsm.fire(fsmEvents.LLRP_GET_READER_CAPABILITIES_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("1.1"));

		// send RFC response with error
		GetCapabilitiesResponse rfcResponse = new GetCapabilitiesResponse(
				new havis.llrpservice.sbc.rfc.message.MessageHeader(31 /* id */),
				new ArrayList<Capabilities>());
		rfcResponse.setException(new FSMActionException("huhu"));
		fsmEvents.RFC_MESSAGE_RECEIVED.setMessage(rfcResponse);
		fsm.fire(fsmEvents.RFC_MESSAGE_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("3.1 "));
		fsm.fire(fsmEvents.RFC_GET_CAPABILITIES_RESPONSE_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("1 "));

		new Verifications() {
			{
				// the LLRP response has been sent with an error
				GetReaderCapabilitiesResponse response;
				llrpMessageHandler.requestSendingData(response = withCapture());
				times = 1;

				assertTrue(response.getStatus().getErrorDescription().contains("huhu"));
				assertEquals(response.getStatus().getStatusCode(), LLRPStatusCode.R_DEVICE_ERROR);
			}
		};

		// all processed messages must have been removed from runtime data
		assertNull(fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData().getCurrentMessage());
		assertNull(fsmEvents.RFC_GET_CAPABILITIES_RESPONSE_RECEIVED.getRuntimeData()
				.getCurrentMessage());
	}

	@Test
	public void createLLRPGetReaderConfigErrorGPIOResponse(@Mocked final Platform platform,
			@Mocked final LLRPMessageHandler llrpMessageHandler,
			@Mocked final RFCMessageHandler rfcMessageHandler,
			@Mocked final GPIOMessageHandler gpioMessageHandler) throws Exception {
		// create FSM
		FSMEvents fsmEvents = createFSMEvents(platform, null /* roSpecsManager */,
				llrpMessageHandler, rfcMessageHandler, gpioMessageHandler, null /* reportDepot */);
		FSM<FSMEvent> fsm = new FSMCreator().create(fsmEvents);

		// send LLRP request
		fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData()
				.setNegotiatedProtocolVersion(ProtocolVersion.LLRP_V1_1);
		GetReaderCapabilities request = new GetReaderCapabilities(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_1, 1 /* id */),
				GetReaderCapabilitiesRequestedData.GENERAL_DEVICE_CAPABILITIES);
		fsmEvents.LLRP_MESSAGE_RECEIVED.setMessage(request);
		fsm.fire(fsmEvents.LLRP_MESSAGE_RECEIVED);
		fsm.fire(fsmEvents.LLRP_GET_READER_CAPABILITIES_RECEIVED);

		// send GPIO response with error
		havis.llrpservice.sbc.gpio.message.GetConfigurationResponse gpioResponse = new GetConfigurationResponse(
				new havis.llrpservice.sbc.gpio.message.MessageHeader(41 /* id */),
				new ArrayList<Configuration>());
		gpioResponse.setException(new FSMActionException("huhu"));
		fsmEvents.GPIO_MESSAGE_RECEIVED.setMessage(gpioResponse);
		fsm.fire(fsmEvents.GPIO_MESSAGE_RECEIVED);
		fsm.fire(fsmEvents.GPIO_GET_CONFIGURATION_RESPONSE_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("1.1 "));

		// send RFC response
		fsmEvents.RFC_MESSAGE_RECEIVED.setMessage(new GetCapabilitiesResponse(
				new havis.llrpservice.sbc.rfc.message.MessageHeader(31 /* id */),
				new ArrayList<Capabilities>()));
		fsm.fire(fsmEvents.RFC_MESSAGE_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("3.1"));
		fsm.fire(fsmEvents.RFC_GET_CAPABILITIES_RESPONSE_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("1 "));

		new Verifications() {
			{
				// the LLRP response has been sent with an error
				GetReaderCapabilitiesResponse response;
				llrpMessageHandler.requestSendingData(response = withCapture());
				times = 1;

				assertTrue(response.getStatus().getErrorDescription().contains("huhu"));
				assertEquals(response.getStatus().getStatusCode(), LLRPStatusCode.R_DEVICE_ERROR);
			}
		};

		// all processed messages must have been removed from runtime data
		assertNull(fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData().getCurrentMessage());
		assertNull(fsmEvents.GPIO_GET_CONFIGURATION_RESPONSE_RECEIVED.getRuntimeData()
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

		// send LLRP GetReaderCapabilities request
		fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData()
				.setNegotiatedProtocolVersion(ProtocolVersion.LLRP_V1_1);
		fsmEvents.LLRP_MESSAGE_RECEIVED.setMessage(new GetReaderCapabilities(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_1, 1 /* id */),
				GetReaderCapabilitiesRequestedData.GENERAL_DEVICE_CAPABILITIES));
		fsm.fire(fsmEvents.LLRP_MESSAGE_RECEIVED);
		fsm.fire(fsmEvents.LLRP_GET_READER_CAPABILITIES_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("1.1 "));

		// send a GPI event (sending of LLRP exception events is disabled)
		fsmEvents.GPIO_MESSAGE_RECEIVED.setMessage(
				new StateChanged(new havis.llrpservice.sbc.gpio.message.MessageHeader(3 /* id */),
						new StateEvent((short) 2 /* id */, State.HIGH)));
		fsm.fire(fsmEvents.GPIO_MESSAGE_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("4.1 "));
		fsm.fire(fsmEvents.GPIO_STATE_CHANGED_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("1.1 "));

		new Verifications() {
			{
				// no LLRP event has been sent due to exception
				llrpMessageHandler
						.requestSendingData(withInstanceOf(ReaderEventNotification.class));
				times = 0;
			}
		};

		// send a GPI event (sending of LLRP exception events is enabled)
		data.states.add(new EventNotificationState(new TLVParameterHeader((byte) 0),
				EventNotificationStateEventType.READER_EXCEPTION_EVENT, true /* state */));
		fsm.fire(fsmEvents.GPIO_MESSAGE_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("4.1 "));
		fsm.fire(fsmEvents.GPIO_STATE_CHANGED_RECEIVED);
		assertTrue(fsm.getCurrentState().getName().startsWith("1.1 "));

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

	private FSMEvents createFSMEvents(Platform platform, ROSpecsManager roSpecsManager,
			LLRPMessageHandler llrpMessageHandler, RFCMessageHandler rfcMessageHandler,
			GPIOMessageHandler gpioMessageHandler, ROAccessReportDepot reportDepot)
			throws FSMActionException {
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
		LLRPRuntimeData llrpRuntimeData = new LLRPRuntimeData(null /* identificationSource */,
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
