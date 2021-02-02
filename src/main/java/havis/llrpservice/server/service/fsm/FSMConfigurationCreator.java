package havis.llrpservice.server.service.fsm;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import havis.llrpservice.common.fsm.Action;
import havis.llrpservice.common.fsm.FSMActionException;
import havis.llrpservice.common.fsm.State;
import havis.llrpservice.common.fsm.Transition;
import havis.llrpservice.data.message.GetReaderConfig;
import havis.llrpservice.data.message.Message;
import havis.llrpservice.data.message.SetReaderConfig;
import havis.llrpservice.data.message.parameter.Identification;
import havis.llrpservice.data.message.parameter.IdentificationIDType;
import havis.llrpservice.data.message.parameter.KeepaliveSpec;
import havis.llrpservice.data.message.parameter.KeepaliveSpecTriggerType;
import havis.llrpservice.data.message.parameter.LLRPStatus;
import havis.llrpservice.data.message.parameter.LLRPStatusCode;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.sbc.gpio.GPIOException;
import havis.llrpservice.sbc.rfc.RFCException;
import havis.llrpservice.sbc.rfc.message.GetConfigurationResponse;
import havis.llrpservice.sbc.rfc.message.SetConfigurationResponse;
import havis.llrpservice.server.llrp.LLRPMessageHandler;
import havis.llrpservice.server.rfc.UnsupportedAccessOperationException;
import havis.llrpservice.server.rfc.UnsupportedAirProtocolException;
import havis.llrpservice.server.rfc.UnsupportedSpecTypeException;
import havis.llrpservice.server.service.data.LLRPReaderConfig;
import havis.llrpservice.server.service.fsm.gpio.FSMGPIOGetConfigurationResponseEvent;
import havis.llrpservice.server.service.fsm.gpio.FSMGPIOResetConfigurationResponseEvent;
import havis.llrpservice.server.service.fsm.gpio.FSMGPIOSetConfigurationResponseEvent;
import havis.llrpservice.server.service.fsm.gpio.GPIORuntimeData;
import havis.llrpservice.server.service.fsm.lllrp.FSMLLRPGetReaderConfigEvent;
import havis.llrpservice.server.service.fsm.lllrp.FSMLLRPSetReaderConfigEvent;
import havis.llrpservice.server.service.fsm.lllrp.LLRPRuntimeData;
import havis.llrpservice.server.service.fsm.rfc.FSMRFCGetConfigurationResponseEvent;
import havis.llrpservice.server.service.fsm.rfc.FSMRFCResetConfigurationResponseEvent;
import havis.llrpservice.server.service.fsm.rfc.FSMRFCSetConfigurationResponseEvent;
import havis.llrpservice.server.service.fsm.rfc.RFCRuntimeData;
import havis.llrpservice.server.service.messageHandling.GPIOMessageCreator.GPIOGetReaderConfigRequest;
import havis.llrpservice.server.service.messageHandling.GPIOMessageCreator.GPIOSetReaderConfigRequest;
import havis.llrpservice.server.service.messageHandling.LLRPMessageCreator;
import havis.llrpservice.server.service.messageHandling.RFCMessageCreator.RFCGetReaderConfigRequest;
import havis.llrpservice.server.service.messageHandling.RFCMessageCreator.RFCSetReaderConfigRequest;
import havis.llrpservice.xml.properties.IdentificationSourceType;

public class FSMConfigurationCreator {

	private final FSMCommonCreator commonCreator;

	FSMConfigurationCreator(FSMCommonCreator commonCreator) {
		this.commonCreator = commonCreator;
	}

	void createLLRPGetReaderConfig(FSMEvents fsmEvents, State<FSMEvent> llrpMessageReceivedState,
			State<FSMEvent> rfcMessageReceivedState, State<FSMEvent> gpioMessageReceivedState,
			State<FSMEvent> waitForMessageState) throws FSMActionException {
		llrpMessageReceivedState.addConnection(fsmEvents.LLRP_GET_READER_CONFIG_RECEIVED,
				new Transition<FSMEvent>("2-1", new Action<FSMEvent>() {
					@Override
					public void perform(State<FSMEvent> srcState, FSMEvent event,
							State<FSMEvent> destState) throws FSMActionException {
						FSMLLRPGetReaderConfigEvent fsmEvent = (FSMLLRPGetReaderConfigEvent) event;
						LLRPRuntimeData runtimeData = fsmEvent.getRuntimeData();
						GetReaderConfig currentMessage = (GetReaderConfig) runtimeData
								.getCurrentMessage().getMessage();
						LLRPStatus status = runtimeData.getCurrentMessage().getStatus();
						// check custom extension
						if (LLRPStatusCode.M_SUCCESS == status.getStatusCode()) {
							status = runtimeData.getMessageValidator()
									.validateCustomExtension(currentMessage.getCustomList());
						}
						boolean isMessageSent = false;
						if (LLRPStatusCode.M_SUCCESS == status.getStatusCode()) {
							try {
								switch (currentMessage.getRequestedData()) {
								case ALL:
								case IDENTIFICATION:
									setIdentification(runtimeData);
									break;
								default:
									break;
								}
								// request the configurations from the
								// RF controller and the GPIO controller
								isMessageSent = sendRFCGPIOMessages(fsmEvent.getRFCRuntimeData(),
										fsmEvent.getGPIORuntimeData(), currentMessage);
							} catch (Exception e) {
								status = runtimeData.getMessageCreator().createStatus(
										LLRPStatusCode.R_DEVICE_ERROR, e.getMessage());
							}
						}
						// if error or NO message has been sent to any back end
						if (LLRPStatusCode.M_SUCCESS != status.getStatusCode() || !isMessageSent) {
							// set error status to LLRP runtime data for the
							// creation of an LLRP error response and to avoid
							// the processing of RFC/GPIO responses
							runtimeData.getCurrentMessage().setStatus(status);
							// send GET_READER_CONFIG_RESPONSE
							sendLLRPGetReaderConfigResponse(runtimeData,
									fsmEvent.getRFCRuntimeData(), null /* currentRFCMessage */,
									fsmEvent.getGPIORuntimeData(), null /* currentGPIOMessage */);
						}
						// else the GET_READER_CONFIG_RESPONSE is sent when the
						// RFC/GPIO responses are received
					}

					private void setIdentification(LLRPRuntimeData runtimeData)
							throws FSMActionException {
						IdentificationSourceType identificationSrc = runtimeData
								.getIdentificationSource();
						switch (identificationSrc.getType()) {
						case MAC_ADDRESS:
							String localHostAddr = runtimeData.getMessageHandler()
									.getConnectionLocalHostAddress();
							try {
								Device device = new Device();
								byte[] macAddr = device.getLocalMacAddress(localHostAddr);
								runtimeData.setIdentification(new Identification(
										new TLVParameterHeader(), IdentificationIDType.MAC_ADDRESS,
										macAddr == null ? new byte[0]
												: device.getLocalEUI64Address(macAddr)));
							} catch (Exception e) {
								throw new FSMActionException(
										"Cannot get MAC address for " + localHostAddr, e);
							}
						case EPC:
							try {
								byte[] epc = new byte[identificationSrc.getLength()];
								new Device().getIntIdentification(
										identificationSrc.getPropertyName(), epc);
								runtimeData.setIdentification(new Identification(
										new TLVParameterHeader(), IdentificationIDType.EPC, epc));
							} catch (Exception e) {
								throw new FSMActionException("Cannot get EPC of device", e);
							}
						default:
							break;
						}
					}

					private boolean sendRFCGPIOMessages(RFCRuntimeData rfcRuntimeData,
							GPIORuntimeData gpioRuntimeData, GetReaderConfig currentMessage)
							throws RFCException, UnsupportedSpecTypeException,
							UnsupportedAccessOperationException, UnsupportedAirProtocolException,
							GPIOException {
						boolean isMessageSent = false;
						// send RFC configuration request
						RFCGetReaderConfigRequest rfcRequest = rfcRuntimeData.getMessageCreator()
								.createRequest(currentMessage);
						if (rfcRequest.getTypes().size() > 0) {
							rfcRuntimeData.setMessageExpected(true);
							rfcRuntimeData.getMessageHandler().requestConfiguration(
									rfcRequest.getTypes(), rfcRequest.getAntennaId());
							isMessageSent = true;
						}

						// send GPIO configuration request if GPIO is
						// enabled
						if (gpioRuntimeData != null) {
							GPIOGetReaderConfigRequest gpioRequest = gpioRuntimeData
									.getMessageCreator().createRequest(currentMessage);
							if (gpioRequest.getTypes().size() > 0) {
								gpioRuntimeData.setMessageExpected(true);
								gpioRuntimeData.getMessageHandler().requestConfiguration(
										gpioRequest.getTypes(), gpioRequest.getGpiPortNum(),
										gpioRequest.getGpoPortNum());
								isMessageSent = true;
							}
						}
						return isMessageSent;
					}
				}), waitForMessageState);

		llrpMessageReceivedState.addConnection(fsmEvents.LLRP_SET_READER_CONFIG_RECEIVED,
				new Transition<FSMEvent>("2-1", new Action<FSMEvent>() {
					@Override
					public void perform(State<FSMEvent> srcState, FSMEvent event,
							State<FSMEvent> destState) throws FSMActionException {
						FSMLLRPSetReaderConfigEvent fsmEvent = (FSMLLRPSetReaderConfigEvent) event;
						LLRPRuntimeData runtimeData = fsmEvent.getRuntimeData();
						SetReaderConfig currentMessage = (SetReaderConfig) runtimeData
								.getCurrentMessage().getMessage();
						LLRPStatus status = runtimeData.getCurrentMessage().getStatus();
						// check custom extension
						if (LLRPStatusCode.M_SUCCESS == status.getStatusCode()) {
							status = runtimeData.getMessageValidator()
									.validateCustomExtension(currentMessage.getCustomList());
						}
						boolean isMessageSent = false;
						if (LLRPStatusCode.M_SUCCESS == status.getStatusCode()) {
							try {
								LLRPReaderConfig readerConfig = runtimeData.getReaderConfig();
								// if reader config shall be reset first
								if (currentMessage.isResetToFactoryDefaults()) {
									readerConfig.reset();
								}
								// if holding of events and reports is NOT
								// supported
								if (!runtimeData.getLLRPCapabilities()
										.isSupportsEventAndReportHolding()) {
									// remove relating settings from received
									// reader config
									currentMessage.setEventAndReports(null);
								}
								// update reader config
								readerConfig.set(currentMessage);
								// set default ROReportSpec to
								// ROReportSpecsManager
								runtimeData.getRoSpecsManager().getROReportSpecsManager()
										.setDefaultROReportSpec(readerConfig.getRoReportSpec());
								// update keep alive handling
								updateKeepAlive(readerConfig.getKeepaliveSpec(),
										fsmEvent.getLLRPServiceInstanceRuntimeData()
												.getUnexpectedTimeout() /* stopTimeout */,
										runtimeData.getMessageHandler());
								// request updating of configs via RF + GPIO
								// controllers
								isMessageSent = sendRFCGPIOMessages(fsmEvent.getRFCRuntimeData(),
										fsmEvent.getGPIORuntimeData(), currentMessage);
							} catch (Exception e) {
								status = runtimeData.getMessageCreator().createStatus(
										LLRPStatusCode.R_DEVICE_ERROR, e.getMessage());
							}
						}
						// if error or NO message has been sent to any back
						// end
						if (LLRPStatusCode.M_SUCCESS != status.getStatusCode() || !isMessageSent) {
							// set error status to LLRP runtime data for the
							// creation of an LLRP error response and to avoid
							// the processing of RFC/GPIO responses
							runtimeData.getCurrentMessage().setStatus(status);
							// send SET_READER_CONFIG_RESPONSE
							sendLLRPSetReaderConfigResponse(runtimeData,
									fsmEvent.getRFCRuntimeData(), null /* currentRFCMessage */,
									fsmEvent.getGPIORuntimeData(), null /* currentGPIOMessage */);
						}
						// else the SET_READER_CONFIG_RESPONSE
						// is sent when the RFC/GPIO responses
						// are received
					}

					private boolean sendRFCGPIOMessages(RFCRuntimeData rfcRuntimeData,
							GPIORuntimeData gpioRuntimeData, SetReaderConfig currentMessage)
							throws RFCException, UnsupportedSpecTypeException,
							UnsupportedAccessOperationException, UnsupportedAirProtocolException,
							GPIOException {
						boolean isMessageSent = false;
						// send RFC configuration request
						RFCSetReaderConfigRequest rfcRequest = rfcRuntimeData.getMessageCreator()
								.createRequest(currentMessage);
						if (rfcRequest.isReset() || rfcRequest.getConfigurations().size() > 0) {
							rfcRuntimeData.setMessageExpected(true);
							rfcRuntimeData.getMessageHandler().requestExecution(
									rfcRequest.isReset(), rfcRequest.getConfigurations());
							isMessageSent = true;
						}

						// send GPIO configuration request if GPIO is
						// enabled
						if (gpioRuntimeData != null) {
							GPIOSetReaderConfigRequest gpioRequest = gpioRuntimeData
									.getMessageCreator().createRequest(currentMessage);
							if (gpioRequest.isReset()
									|| gpioRequest.getConfigurations().size() > 0) {
								gpioRuntimeData.setMessageExpected(true);
								gpioRuntimeData.getMessageHandler().requestExecution(
										gpioRequest.isReset(), gpioRequest.getConfigurations());
								isMessageSent = true;
							}
						}
						return isMessageSent;
					}

					/**
					 * @param keepaliveSpec
					 * @param stopTimeout
					 *            stop time out in ms
					 * @param messageHandler
					 * @throws InterruptedException
					 * @throws ExecutionException
					 * @throws TimeoutException
					 */
					private void updateKeepAlive(KeepaliveSpec keepaliveSpec, long stopTimeout,
							LLRPMessageHandler messageHandler)
							throws InterruptedException, ExecutionException, TimeoutException {
						if (keepaliveSpec.getTriggerType() == KeepaliveSpecTriggerType.NULL) {
							// disable the keep alive
							messageHandler.setKeepaliveInterval(0 /* interval */, stopTimeout);
						} else {
							// enable/update the keep alive
							messageHandler.setKeepaliveInterval(keepaliveSpec.getTimeInterval(),
									stopTimeout);
						}
					}
				}), waitForMessageState);

		rfcMessageReceivedState.addConnection(fsmEvents.RFC_GET_CONFIGURATION_RESPONSE_RECEIVED,
				new Transition<FSMEvent>("3-1", new Action<FSMEvent>() {
					@Override
					public void perform(State<FSMEvent> srcState, FSMEvent event,
							State<FSMEvent> destState) throws FSMActionException {
						FSMRFCGetConfigurationResponseEvent fsmEvent = (FSMRFCGetConfigurationResponseEvent) event;
						RFCRuntimeData runtimeData = fsmEvent.getRuntimeData();
						GetConfigurationResponse currentMessage = (GetConfigurationResponse) runtimeData
								.getCurrentMessage();
						GPIORuntimeData gpioRuntimeData = fsmEvent.getGPIORuntimeData();
						LLRPRuntimeData llrpRuntimeData = fsmEvent.getLLRPRuntimeData();
						// if an error occurred
						if (LLRPStatusCode.M_SUCCESS != llrpRuntimeData.getCurrentMessage()
								.getStatus().getStatusCode()) {
							return;
						}
						havis.llrpservice.sbc.gpio.message.GetConfigurationResponse currentGPIOMessage = null;
						// if GPIO is enabled and a response is expected
						if (gpioRuntimeData != null && gpioRuntimeData.isMessageExpected()) {
							currentGPIOMessage = (havis.llrpservice.sbc.gpio.message.GetConfigurationResponse) gpioRuntimeData
									.getCurrentMessage();
							// if GPIO message is not available yet
							if (currentGPIOMessage == null) {
								// create LLRP message after the GPIO message
								// has been received
								return;
							}
						}
						// send LLRP response
						sendLLRPGetReaderConfigResponse(fsmEvent.getLLRPRuntimeData(), runtimeData,
								currentMessage, gpioRuntimeData, currentGPIOMessage);
					}
				}), waitForMessageState);

		rfcMessageReceivedState.addConnection(fsmEvents.RFC_SET_CONFIGURATION_RESPONSE_RECEIVED,
				new Transition<FSMEvent>("3-1", new Action<FSMEvent>() {
					@Override
					public void perform(State<FSMEvent> srcState, FSMEvent event,
							State<FSMEvent> destState) throws FSMActionException {
						FSMRFCSetConfigurationResponseEvent fsmEvent = (FSMRFCSetConfigurationResponseEvent) event;
						RFCRuntimeData runtimeData = fsmEvent.getRuntimeData();
						SetConfigurationResponse currentMessage = (SetConfigurationResponse) runtimeData
								.getCurrentMessage();
						GPIORuntimeData gpioRuntimeData = fsmEvent.getGPIORuntimeData();
						LLRPRuntimeData llrpRuntimeData = fsmEvent.getLLRPRuntimeData();
						// if an error occurred
						if (LLRPStatusCode.M_SUCCESS != llrpRuntimeData.getCurrentMessage()
								.getStatus().getStatusCode()) {
							return;
						}
						havis.llrpservice.sbc.gpio.message.SetConfigurationResponse currentGPIOMessage = null;
						// if GPIO is enabled and a response is expected
						if (gpioRuntimeData != null && gpioRuntimeData.isMessageExpected()) {
							currentGPIOMessage = (havis.llrpservice.sbc.gpio.message.SetConfigurationResponse) gpioRuntimeData
									.getCurrentMessage();
							// if GPIO message is not available yet
							if (currentGPIOMessage == null) {
								// create LLRP message after the GPIO message
								// has been received
								return;
							}
						}
						// send LLRP response
						sendLLRPSetReaderConfigResponse(fsmEvent.getLLRPRuntimeData(), runtimeData,
								currentMessage, gpioRuntimeData, currentGPIOMessage);
					}
				}), waitForMessageState);

		rfcMessageReceivedState.addConnection(fsmEvents.RFC_RESET_CONFIGURATION_RESPONSE_RECEIVED,
				new Transition<FSMEvent>("3-1", new Action<FSMEvent>() {
					@Override
					public void perform(State<FSMEvent> srcState, FSMEvent event,
							State<FSMEvent> destState) {
						FSMRFCResetConfigurationResponseEvent fsmEvent = (FSMRFCResetConfigurationResponseEvent) event;
						RFCRuntimeData runtimeData = fsmEvent.getRuntimeData();
						LLRPRuntimeData llrpRuntimeData = fsmEvent.getLLRPRuntimeData();
						// if an error occurred
						if (LLRPStatusCode.M_SUCCESS != llrpRuntimeData.getCurrentMessage()
								.getStatus().getStatusCode()) {
							return;
						}
						// remove processed message
						runtimeData.getCurrentMessages().remove(runtimeData.getCurrentMessage());
					}
				}), waitForMessageState);

		gpioMessageReceivedState.addConnection(fsmEvents.GPIO_GET_CONFIGURATION_RESPONSE_RECEIVED,
				new Transition<FSMEvent>("4-1", new Action<FSMEvent>() {
					@Override
					public void perform(State<FSMEvent> srcState, FSMEvent event,
							State<FSMEvent> destState) throws FSMActionException {
						FSMGPIOGetConfigurationResponseEvent fsmEvent = (FSMGPIOGetConfigurationResponseEvent) event;
						GPIORuntimeData runtimeData = fsmEvent.getRuntimeData();
						havis.llrpservice.sbc.gpio.message.GetConfigurationResponse currentMessage = (havis.llrpservice.sbc.gpio.message.GetConfigurationResponse) runtimeData
								.getCurrentMessage();
						RFCRuntimeData rfcRuntimeData = fsmEvent.getRFCRuntimeData();
						LLRPRuntimeData llrpRuntimeData = fsmEvent.getLLRPRuntimeData();
						// if an error occurred
						if (LLRPStatusCode.M_SUCCESS != llrpRuntimeData.getCurrentMessage()
								.getStatus().getStatusCode()) {
							return;
						}
						GetConfigurationResponse currentRFCMessage = null;
						// if a RFC response is expected
						if (rfcRuntimeData.isMessageExpected()) {
							currentRFCMessage = (GetConfigurationResponse) rfcRuntimeData
									.getCurrentMessage();
							// if RFC message is not available yet
							if (currentRFCMessage == null) {
								// send LLRP response after the RFC message has
								// been received
								return;
							}
						}
						// send LLRP response
						sendLLRPGetReaderConfigResponse(fsmEvent.getLLRPRuntimeData(),
								rfcRuntimeData, currentRFCMessage, runtimeData, currentMessage);
					}
				}), waitForMessageState);

		gpioMessageReceivedState.addConnection(fsmEvents.GPIO_SET_CONFIGURATION_RESPONSE_RECEIVED,
				new Transition<FSMEvent>("4-1", new Action<FSMEvent>() {
					@Override
					public void perform(State<FSMEvent> srcState, FSMEvent event,
							State<FSMEvent> destState) throws FSMActionException {
						FSMGPIOSetConfigurationResponseEvent fsmEvent = (FSMGPIOSetConfigurationResponseEvent) event;
						GPIORuntimeData runtimeData = fsmEvent.getRuntimeData();
						havis.llrpservice.sbc.gpio.message.SetConfigurationResponse currentMessage = (havis.llrpservice.sbc.gpio.message.SetConfigurationResponse) runtimeData
								.getCurrentMessage();
						RFCRuntimeData rfcRuntimeData = fsmEvent.getRFCRuntimeData();
						LLRPRuntimeData llrpRuntimeData = fsmEvent.getLLRPRuntimeData();
						// if an error occurred
						if (LLRPStatusCode.M_SUCCESS != llrpRuntimeData.getCurrentMessage()
								.getStatus().getStatusCode()) {
							return;
						}
						SetConfigurationResponse currentRFCMessage = null;
						// if a RFC response is expected
						if (rfcRuntimeData.isMessageExpected()) {
							currentRFCMessage = (SetConfigurationResponse) rfcRuntimeData
									.getCurrentMessage();
							// if RFC message is not available yet
							if (currentRFCMessage == null) {
								// send LLRP response after the RFC message has
								// been received
								return;
							}
						}
						// send LLRP response
						sendLLRPSetReaderConfigResponse(fsmEvent.getLLRPRuntimeData(),
								rfcRuntimeData, currentRFCMessage, runtimeData, currentMessage);
					}
				}), waitForMessageState);

		gpioMessageReceivedState.addConnection(fsmEvents.GPIO_RESET_CONFIGURATION_RESPONSE_RECEIVED,
				new Transition<FSMEvent>("4-1", new Action<FSMEvent>() {
					@Override
					public void perform(State<FSMEvent> srcState, FSMEvent event,
							State<FSMEvent> destState) {
						FSMGPIOResetConfigurationResponseEvent fsmEvent = (FSMGPIOResetConfigurationResponseEvent) event;
						GPIORuntimeData runtimeData = fsmEvent.getRuntimeData();
						LLRPRuntimeData llrpRuntimeData = fsmEvent.getLLRPRuntimeData();
						// if an error occurred
						if (LLRPStatusCode.M_SUCCESS != llrpRuntimeData.getCurrentMessage()
								.getStatus().getStatusCode()) {
							return;
						}
						// remove processed message
						runtimeData.getCurrentMessages().remove(runtimeData.getCurrentMessage());
					}
				}), waitForMessageState);

		commonCreator.createRFCExecuteResponse(fsmEvents, rfcMessageReceivedState,
				waitForMessageState, "3.1-1.1");

		commonCreator.createGPIOStateChanged(fsmEvents, gpioMessageReceivedState,
				waitForMessageState, "4-1");
	}

	private void sendLLRPGetReaderConfigResponse(LLRPRuntimeData llrpRuntimeData,
			RFCRuntimeData rfcRuntimeData, GetConfigurationResponse currentRFCMessage,
			GPIORuntimeData gpioRuntimeData,
			havis.llrpservice.sbc.gpio.message.GetConfigurationResponse currentGPIOMessage)
			throws FSMActionException {
		GetReaderConfig currentLLRPMessage = (GetReaderConfig) llrpRuntimeData.getCurrentMessage()
				.getMessage();
		LLRPStatus llrpStatus = llrpRuntimeData.getCurrentMessage().getStatus();
		LLRPMessageCreator llrpMessageCreator = llrpRuntimeData.getMessageCreator();
		// check for exceptions
		if (LLRPStatusCode.M_SUCCESS == llrpStatus.getStatusCode()) {
			if (currentRFCMessage != null && currentRFCMessage.getException() != null) {
				llrpStatus = llrpMessageCreator.createStatus(LLRPStatusCode.R_DEVICE_ERROR,
						currentRFCMessage.getException().getMessage());
			} else if (currentGPIOMessage != null && currentGPIOMessage.getException() != null) {
				llrpStatus = llrpMessageCreator.createStatus(LLRPStatusCode.R_DEVICE_ERROR,
						currentGPIOMessage.getException().getMessage());
			}
		}
		// create LLRP GET_READER_CONFIG_RESPONSE incl.
		// RF + GPIO configuration
		Message llrpResponse = llrpMessageCreator.createResponse(currentLLRPMessage,
				llrpRuntimeData, currentRFCMessage, currentGPIOMessage, llrpStatus);
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

	private void sendLLRPSetReaderConfigResponse(LLRPRuntimeData llrpRuntimeData,
			RFCRuntimeData rfcRuntimeData, SetConfigurationResponse currentRFCMessage,
			GPIORuntimeData gpioRuntimeData,
			havis.llrpservice.sbc.gpio.message.SetConfigurationResponse currentGPIOMessage)
			throws FSMActionException {
		SetReaderConfig currentLLRPMessage = (SetReaderConfig) llrpRuntimeData.getCurrentMessage()
				.getMessage();
		LLRPStatus llrpStatus = llrpRuntimeData.getCurrentMessage().getStatus();
		LLRPMessageCreator llrpMessageCreator = llrpRuntimeData.getMessageCreator();
		// check for exceptions
		if (LLRPStatusCode.M_SUCCESS == llrpStatus.getStatusCode()) {
			if (currentRFCMessage != null && currentRFCMessage.getException() != null) {
				llrpStatus = llrpMessageCreator.createStatus(LLRPStatusCode.R_DEVICE_ERROR,
						currentRFCMessage.getException().getMessage());
			} else if (currentGPIOMessage != null && currentGPIOMessage.getException() != null) {
				llrpStatus = llrpMessageCreator.createStatus(LLRPStatusCode.R_DEVICE_ERROR,
						currentGPIOMessage.getException().getMessage());
			}
		}
		// create LLRP response
		Message llrpResponse = llrpMessageCreator.createResponse(currentLLRPMessage,
				llrpRuntimeData.getProtocolVersion(), llrpStatus);
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
