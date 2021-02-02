package havis.llrpservice.server.service.fsm;

import java.util.ArrayList;
import java.util.List;

import havis.device.io.Type;
import havis.device.rf.capabilities.CapabilityType;
import havis.llrpservice.common.fsm.Action;
import havis.llrpservice.common.fsm.FSM;
import havis.llrpservice.common.fsm.FSMActionException;
import havis.llrpservice.common.fsm.FSMGuardException;
import havis.llrpservice.common.fsm.Guard;
import havis.llrpservice.common.fsm.State;
import havis.llrpservice.common.fsm.Transition;
import havis.llrpservice.data.message.GetReaderCapabilities;
import havis.llrpservice.data.message.Message;
import havis.llrpservice.data.message.parameter.LLRPStatus;
import havis.llrpservice.data.message.parameter.LLRPStatusCode;
import havis.llrpservice.sbc.rfc.message.GetCapabilitiesResponse;
import havis.llrpservice.server.service.fsm.gpio.FSMGPIOGetConfigurationResponseEvent;
import havis.llrpservice.server.service.fsm.gpio.GPIORuntimeData;
import havis.llrpservice.server.service.fsm.lllrp.FSMLLRPGetReaderCapabilitiesEvent;
import havis.llrpservice.server.service.fsm.lllrp.LLRPRuntimeData;
import havis.llrpservice.server.service.fsm.rfc.FSMRFCGetCapabilitiesResponseEvent;
import havis.llrpservice.server.service.fsm.rfc.RFCRuntimeData;
import havis.llrpservice.server.service.messageHandling.LLRPMessageCreator;
import havis.util.platform.Platform;

public class FSMCapabilitiesCreator {

	private final FSMCommonCreator commonCreator;

	FSMCapabilitiesCreator(FSMCommonCreator commonCreator) {
		this.commonCreator = commonCreator;
	}

	void createLLRPGetReaderCapabilities(final FSM<FSMEvent> fsm, final FSMEvents fsmEvents,
			State<FSMEvent> llrpMessageReceivedState, State<FSMEvent> waitForMessageState)
			throws FSMActionException {
		State<FSMEvent> waitForCapabilitiesResponseState = new State<>(
				"1.1 waitForCapabilitiesResponse");

		llrpMessageReceivedState.addConnection(fsmEvents.LLRP_GET_READER_CAPABILITIES_RECEIVED,
				new Transition<FSMEvent>("2-1.1", new Guard<FSMEvent>() {
					@Override
					public boolean evaluate(State<FSMEvent> srcState, FSMEvent event,
							State<FSMEvent> destState) {
						FSMLLRPGetReaderCapabilitiesEvent fsmEvent = (FSMLLRPGetReaderCapabilitiesEvent) event;
						LLRPRuntimeData runtimeData = fsmEvent.getRuntimeData();
						GetReaderCapabilities currentMessage = (GetReaderCapabilities) runtimeData
								.getCurrentMessage().getMessage();
						LLRPStatus status = runtimeData.getCurrentMessage().getStatus();
						// check custom extension
						if (status.getStatusCode() == LLRPStatusCode.M_SUCCESS) {
							status = runtimeData.getMessageValidator().validateCustomExtension(
									currentMessage.getCustomExtensionPoint());
							runtimeData.getCurrentMessage().setStatus(status);
						}
						if (status.getStatusCode() != LLRPStatusCode.M_SUCCESS) {
							return false;
						}
						// create RFC request and set it to the event
						List<CapabilityType> rfcRequest = fsmEvent.getRFCRuntimeData()
								.getMessageCreator().createRequest(currentMessage);
						fsmEvent.setRFCRequest(rfcRequest);
						// create GPIO request and set it to the event if GPIO
						// is enabled
						GPIORuntimeData gpioRuntimeData = fsmEvent.getGPIORuntimeData();
						List<Type> gpioRequest = gpioRuntimeData != null
								? fsmEvent.getGPIORuntimeData().getMessageCreator()
										.createRequest(currentMessage)
								: new ArrayList<Type>();
						fsmEvent.setGPIORequest(gpioRequest);
						// if any back end request has been created
						return rfcRequest.size() > 0 || gpioRequest.size() > 0;
					}
				}, new Action<FSMEvent>() {
					@Override
					public void perform(State<FSMEvent> srcState, FSMEvent event,
							State<FSMEvent> destState) throws FSMActionException {
						FSMLLRPGetReaderCapabilitiesEvent fsmEvent = (FSMLLRPGetReaderCapabilitiesEvent) event;
						LLRPRuntimeData runtimeData = fsmEvent.getRuntimeData();
						// Request the capabilities from the RF controller and
						// GPIO controller.
						// The GET_READER_CAPABILITIES_RESPONSE is sent when the
						// RFC/GPIO responses are received.
						try {
							if (fsmEvent.getRFCRequest().size() > 0) {
								RFCRuntimeData rfcRuntimeData = fsmEvent.getRFCRuntimeData();
								rfcRuntimeData.setMessageExpected(true);
								rfcRuntimeData.getMessageHandler()
										.requestCapabilities(fsmEvent.getRFCRequest());
							}

							if (fsmEvent.getGPIORequest().size() > 0) {
								GPIORuntimeData gpioRuntimeData = fsmEvent.getGPIORuntimeData();
								gpioRuntimeData.setMessageExpected(true);
								gpioRuntimeData.getMessageHandler().requestConfiguration(
										fsmEvent.getGPIORequest(), (short) 0 /* gpiPortNum */,
										(short) 0 /* gpoPortNum */);
							}
						} catch (Exception e) {
							// set error status to LLRP runtime data
							LLRPStatus status = runtimeData.getMessageCreator()
									.createStatus(LLRPStatusCode.R_DEVICE_ERROR, e.getMessage());
							runtimeData.getCurrentMessage().setStatus(status);
							// enqueue events to change to state "1 wait for
							// message" without receiving any RFC/GPIO messages
							try {
								fsm.fire(fsmEvents.RFC_MESSAGE_RECEIVED);
								fsm.fire(fsmEvents.RFC_GET_CAPABILITIES_RESPONSE_RECEIVED);
							} catch (FSMGuardException e1) {
								throw new FSMActionException(
										"Cannot fire events RFC_MESSAGE_RECEIVED, RFC_GET_CAPABILITIES_RESPONSE_RECEIVED",
										e1);
							}
						}
					}
				}), waitForCapabilitiesResponseState);

		llrpMessageReceivedState.addConnection(fsmEvents.LLRP_GET_READER_CAPABILITIES_RECEIVED,
				new Transition<FSMEvent>("2-1", new Action<FSMEvent>() {
					@Override
					public void perform(State<FSMEvent> srcState, FSMEvent event,
							State<FSMEvent> destState) throws FSMActionException {
						FSMLLRPGetReaderCapabilitiesEvent fsmEvent = (FSMLLRPGetReaderCapabilitiesEvent) event;
						LLRPRuntimeData runtimeData = fsmEvent.getRuntimeData();
						// send GET_READER_CAPABILITIES_RESPONSE
						sendLLRPGetReaderCapabilitiesResponse(
								fsmEvent.getLLRPServiceInstanceRuntimeData().getPlatform(),
								runtimeData, fsmEvent.getRFCRuntimeData(),
								null /* currentRFCMessage */, fsmEvent.getGPIORuntimeData(),
								null /* currentGPIOMessage */);
					}
				}), waitForMessageState);

		State<FSMEvent> rfcMessageReceived31State = commonCreator
				.createRFCMessageReceivedState("3.1 rfcMessageReceived");

		State<FSMEvent> gpioMessageReceived41State = commonCreator
				.createGPIOMessageReceivedState("4.1 gpioMessageReceived");

		waitForCapabilitiesResponseState.addConnection(fsmEvents.RFC_MESSAGE_RECEIVED,
				new Transition<FSMEvent>("1.1-3.1"), rfcMessageReceived31State);
		waitForCapabilitiesResponseState.addConnection(fsmEvents.GPIO_MESSAGE_RECEIVED,
				new Transition<FSMEvent>("1.1-4.1"), gpioMessageReceived41State);

		rfcMessageReceived31State.addConnection(fsmEvents.RFC_GET_CAPABILITIES_RESPONSE_RECEIVED,
				new Transition<FSMEvent>("3.1-1", new Guard<FSMEvent>() {
					@Override
					public boolean evaluate(State<FSMEvent> srcState, FSMEvent event,
							State<FSMEvent> destState) {
						FSMRFCGetCapabilitiesResponseEvent fsmEvent = (FSMRFCGetCapabilitiesResponseEvent) event;
						LLRPRuntimeData llrpRuntimeData = fsmEvent.getLLRPRuntimeData();
						GPIORuntimeData gpioRuntimeData = fsmEvent.getGPIORuntimeData();
						LLRPStatus status = llrpRuntimeData.getCurrentMessage().getStatus();
						// if the LLRP request has been processed with an error
						// or a GPIO response is NOT expected or a GPIO message
						// is available
						return LLRPStatusCode.M_SUCCESS != status.getStatusCode()
								|| gpioRuntimeData == null || !gpioRuntimeData.isMessageExpected()
								|| gpioRuntimeData.getCurrentMessage() != null;
					}
				}, new Action<FSMEvent>() {
					@Override
					public void perform(State<FSMEvent> srcState, FSMEvent event,
							State<FSMEvent> destState) throws FSMActionException {
						FSMRFCGetCapabilitiesResponseEvent fsmEvent = (FSMRFCGetCapabilitiesResponseEvent) event;
						RFCRuntimeData runtimeData = fsmEvent.getRuntimeData();
						GetCapabilitiesResponse currentMessage = (GetCapabilitiesResponse) runtimeData
								.getCurrentMessage();
						GPIORuntimeData gpioRuntimeData = fsmEvent.getGPIORuntimeData();
						havis.llrpservice.sbc.gpio.message.GetConfigurationResponse currentGPIOMessage = null;
						if (gpioRuntimeData != null) {
							currentGPIOMessage = (havis.llrpservice.sbc.gpio.message.GetConfigurationResponse) gpioRuntimeData
									.getCurrentMessage();
						}
						// send LLRP response
						sendLLRPGetReaderCapabilitiesResponse(
								fsmEvent.getLLRPServiceInstanceRuntimeData().getPlatform(),
								fsmEvent.getLLRPRuntimeData(), runtimeData, currentMessage,
								gpioRuntimeData, currentGPIOMessage);
					}
				}), waitForMessageState);

		rfcMessageReceived31State.addConnection(fsmEvents.RFC_GET_CAPABILITIES_RESPONSE_RECEIVED,
				new Transition<FSMEvent>("3.1-1.1"), waitForCapabilitiesResponseState);

		gpioMessageReceived41State.addConnection(fsmEvents.GPIO_GET_CONFIGURATION_RESPONSE_RECEIVED,
				new Transition<FSMEvent>("4.1-1", new Guard<FSMEvent>() {
					@Override
					public boolean evaluate(State<FSMEvent> srcState, FSMEvent event,
							State<FSMEvent> destState) {
						FSMGPIOGetConfigurationResponseEvent fsmEvent = (FSMGPIOGetConfigurationResponseEvent) event;
						LLRPRuntimeData llrpRuntimeData = fsmEvent.getLLRPRuntimeData();
						RFCRuntimeData rfcRuntimeData = fsmEvent.getRFCRuntimeData();
						LLRPStatus status = llrpRuntimeData.getCurrentMessage().getStatus();
						// if the LLRP request has been processed with an error
						// or a RFC response is NOT expected or a RFC message is
						// available
						return LLRPStatusCode.M_SUCCESS != status.getStatusCode()
								|| !rfcRuntimeData.isMessageExpected()
								|| rfcRuntimeData.getCurrentMessage() != null;
					}
				}, new Action<FSMEvent>() {
					@Override
					public void perform(State<FSMEvent> srcState, FSMEvent event,
							State<FSMEvent> destState) throws FSMActionException {
						FSMGPIOGetConfigurationResponseEvent fsmEvent = (FSMGPIOGetConfigurationResponseEvent) event;
						GPIORuntimeData runtimeData = fsmEvent.getRuntimeData();
						havis.llrpservice.sbc.gpio.message.GetConfigurationResponse currentMessage = (havis.llrpservice.sbc.gpio.message.GetConfigurationResponse) runtimeData
								.getCurrentMessage();
						RFCRuntimeData rfcRuntimeData = fsmEvent.getRFCRuntimeData();
						GetCapabilitiesResponse currentRFCMessage = (GetCapabilitiesResponse) rfcRuntimeData
								.getCurrentMessage();
						Platform platform = fsmEvent.getLLRPServiceInstanceRuntimeData()
								.getPlatform();
						// send LLRP response
						sendLLRPGetReaderCapabilitiesResponse(platform,
								fsmEvent.getLLRPRuntimeData(), rfcRuntimeData, currentRFCMessage,
								runtimeData, currentMessage);
					}
				}), waitForMessageState);

		gpioMessageReceived41State.addConnection(fsmEvents.GPIO_GET_CONFIGURATION_RESPONSE_RECEIVED,
				new Transition<FSMEvent>("4.1-1.1"), waitForCapabilitiesResponseState);

		commonCreator.createRFCExecuteResponse(fsmEvents, rfcMessageReceived31State,
				waitForCapabilitiesResponseState, "3.1-1.1");

		commonCreator.createGPIOStateChanged(fsmEvents, gpioMessageReceived41State,
				waitForCapabilitiesResponseState, "4.1-1.1");
	}

	private void sendLLRPGetReaderCapabilitiesResponse(Platform platform,
			LLRPRuntimeData llrpRuntimeData, RFCRuntimeData rfcRuntimeData,
			GetCapabilitiesResponse currentRFCMessage, GPIORuntimeData gpioRuntimeData,
			havis.llrpservice.sbc.gpio.message.GetConfigurationResponse currentGPIOMessage)
			throws FSMActionException {
		GetReaderCapabilities currentLLRPMessage = (GetReaderCapabilities) llrpRuntimeData
				.getCurrentMessage().getMessage();
		LLRPStatus llrpStatus = llrpRuntimeData.getCurrentMessage().getStatus();
		LLRPMessageCreator llrpMessageCreator = llrpRuntimeData.getMessageCreator();
		// check for exceptions
		if (llrpStatus.getStatusCode() == LLRPStatusCode.M_SUCCESS) {
			if (currentRFCMessage != null && currentRFCMessage.getException() != null) {
				llrpStatus = llrpMessageCreator.createStatus(LLRPStatusCode.R_DEVICE_ERROR,
						currentRFCMessage.getException().getMessage());
			} else if (currentGPIOMessage != null && currentGPIOMessage.getException() != null) {
				llrpStatus = llrpMessageCreator.createStatus(LLRPStatusCode.R_DEVICE_ERROR,
						currentGPIOMessage.getException().getMessage());
			}
		}
		// create LLRP GET_READER_CAPABILITIES_RESPONSE
		Message llrpResponse = llrpMessageCreator.createResponse(currentLLRPMessage,
				llrpRuntimeData.getProtocolVersion(), llrpRuntimeData.getLLRPCapabilities(),
				currentRFCMessage, currentGPIOMessage, platform, llrpStatus);
		// remove processed LLRP + RFC + GPIO message
		llrpRuntimeData.removeCurrentMessage(currentLLRPMessage.getMessageHeader().getId());
		if (currentRFCMessage != null) {
			rfcRuntimeData.getCurrentMessages().remove(currentRFCMessage);
			rfcRuntimeData.setMessageExpected(false);
		}
		if (gpioRuntimeData != null && currentGPIOMessage != null) {
			gpioRuntimeData.getCurrentMessages().remove(currentGPIOMessage);
			gpioRuntimeData.setMessageExpected(false);
		}
		// send LLRP response
		try {
			llrpRuntimeData.getMessageHandler().requestSendingData(llrpResponse);
		} catch (Exception e) {
			throw new FSMActionException("Cannot send LLRP message " + llrpResponse, e);
		}
	}
}
