package havis.llrpservice.server.service.fsm;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import havis.llrpservice.common.fsm.Action;
import havis.llrpservice.common.fsm.FSMActionException;
import havis.llrpservice.common.fsm.State;
import havis.llrpservice.common.fsm.Transition;
import havis.llrpservice.data.message.Message;
import havis.llrpservice.data.message.ROAccessReport;
import havis.llrpservice.data.message.parameter.EventNotificationState;
import havis.llrpservice.data.message.parameter.EventNotificationStateEventType;
import havis.llrpservice.data.message.parameter.GPIEvent;
import havis.llrpservice.data.message.parameter.LLRPStatus;
import havis.llrpservice.data.message.parameter.Parameter;
import havis.llrpservice.data.message.parameter.ROReportSpec;
import havis.llrpservice.data.message.parameter.ReaderExceptionEvent;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.sbc.gpio.message.StateChanged;
import havis.llrpservice.sbc.rfc.message.ExecuteResponse;
import havis.llrpservice.server.rfc.messageData.ExecuteResponseData;
import havis.llrpservice.server.service.data.ROAccessReportEntity;
import havis.llrpservice.server.service.fsm.gpio.FSMGPIOMessageEvent;
import havis.llrpservice.server.service.fsm.gpio.FSMGPIOStateChangedEvent;
import havis.llrpservice.server.service.fsm.gpio.GPIORuntimeData;
import havis.llrpservice.server.service.fsm.lllrp.FSMLLRPMessageEvent;
import havis.llrpservice.server.service.fsm.lllrp.LLRPRuntimeData;
import havis.llrpservice.server.service.fsm.rfc.FSMRFCExecuteResponseEvent;
import havis.llrpservice.server.service.fsm.rfc.FSMRFCMessageEvent;
import havis.llrpservice.server.service.fsm.rfc.RFCRuntimeData;
import havis.llrpservice.server.service.messageHandling.LLRPMessageConverter;

public class FSMCommonCreator {
	private static final Logger log = Logger.getLogger(FSMCommonCreator.class.getName());

	State<FSMEvent> createLLRPMessageReceivedState(String stateName) {
		State<FSMEvent> llrpMessageReceivedState = new State<>(stateName);
		llrpMessageReceivedState.addEntryAction(new Action<FSMEvent>() {
			@Override
			public void perform(State<FSMEvent> srcState, FSMEvent event,
					State<FSMEvent> destState) {
				FSMLLRPMessageEvent fsmEvent = (FSMLLRPMessageEvent) event;
				Message currentMessage = fsmEvent.getMessage();
				LLRPRuntimeData runtimeData = fsmEvent.getRuntimeData();
				// check for valid protocol version
				LLRPStatus status = runtimeData.getMessageValidator()
						.validateProtocolVersion(currentMessage, runtimeData.getProtocolVersion());
				// save current LLRP message
				runtimeData.addCurrentMessage(currentMessage, status);
			}
		});
		return llrpMessageReceivedState;
	}

	State<FSMEvent> createRFCMessageReceivedState(String stateName) {
		State<FSMEvent> rfcMessageReceivedState = new State<>(stateName);
		rfcMessageReceivedState.addEntryAction(new Action<FSMEvent>() {
			@Override
			public void perform(State<FSMEvent> srcState, FSMEvent event,
					State<FSMEvent> destState) {
				FSMRFCMessageEvent fsmEvent = (FSMRFCMessageEvent) event;
				RFCRuntimeData runtimeData = fsmEvent.getRuntimeData();
				if (fsmEvent.getMessage() != null) {
					// save current RFC message
					runtimeData.getCurrentMessages().add(fsmEvent.getMessage());
					runtimeData.getMessageData().put(
							fsmEvent.getMessage().getMessageHeader().getId(),
							fsmEvent.getMessageData());
				}
			}
		});
		return rfcMessageReceivedState;
	}

	State<FSMEvent> createGPIOMessageReceivedState(String stateName) {
		State<FSMEvent> gpioMessageReceivedState = new State<>(stateName);
		gpioMessageReceivedState.addEntryAction(new Action<FSMEvent>() {
			@Override
			public void perform(State<FSMEvent> srcState, FSMEvent event,
					State<FSMEvent> destState) {
				FSMGPIOMessageEvent fsmEvent = (FSMGPIOMessageEvent) event;
				GPIORuntimeData runtimeData = fsmEvent.getRuntimeData();
				// save current GPIO message
				runtimeData.getCurrentMessages().add(fsmEvent.getMessage());
			}
		});
		return gpioMessageReceivedState;
	}

	void createRFCExecuteResponse(FSMEvents fsmEvents, State<FSMEvent> srcState,
			State<FSMEvent> destState, String transitionName) throws FSMActionException {
		srcState.addConnection(fsmEvents.RFC_EXECUTE_RESPONSE_RECEIVED,
				new Transition<FSMEvent>(transitionName, new Action<FSMEvent>() {
					@Override
					public void perform(State<FSMEvent> srcState, FSMEvent event,
							State<FSMEvent> destState) throws FSMActionException {
						FSMRFCExecuteResponseEvent fsmEvent = (FSMRFCExecuteResponseEvent) event;
						RFCRuntimeData runtimeData = fsmEvent.getRuntimeData();
						LLRPRuntimeData llrpRuntimeData = fsmEvent.getLLRPRuntimeData();
						LLRPServiceInstanceRuntimeData llrpServiceInstanceRuntimeData = fsmEvent
								.getLLRPServiceInstanceRuntimeData();
						ExecuteResponse currentMessage = (ExecuteResponse) runtimeData
								.getCurrentMessage();
						ExecuteResponseData currentMessageData = (ExecuteResponseData) runtimeData
								.getMessageData().get(currentMessage.getMessageHeader().getId());

						// remove processed message
						runtimeData.getCurrentMessages().remove(currentMessage);
						runtimeData.getMessageData().remove(currentMessage.getMessageHeader().getId());

						try {
							// get ROReportSpec for ROSpec
							ROReportSpec roReportSpec = llrpRuntimeData.getRoSpecsManager()
									.getROReportSpecsManager()
									.getROReportSpec(currentMessageData.getRoSpecId());
							// create a report
							ROAccessReport report = llrpRuntimeData.getROAccessReportCreator()
									.create(llrpRuntimeData.getProtocolVersion(), currentMessage,
											currentMessageData,
											roReportSpec.getTagReportContentSelector());
							// if report contains tag data
							if (report.getTagReportDataList() != null
									&& !report.getTagReportDataList().isEmpty()) {
								ROAccessReportEntity reportEntity = new ROAccessReportEntity();
								reportEntity.setRoSpecId(currentMessageData.getRoSpecId());
								reportEntity.setReport(report);
								// add report to depot
								llrpRuntimeData.getROAccessReportDepot()
										.add(Arrays.asList(reportEntity));
							}
							// inform ROSpecsManager + ROReportSpecsManager
							// (ROSpecs may be
							// started or stopped, ROAccessReports may be send)
							llrpRuntimeData.getRoSpecsManager().executionResponseReceived(
									currentMessageData.getRoSpecId(), currentMessage.getTagData(),
									currentMessageData.isLastResponse());
						} catch (Exception e) {
							String errorMsg = "Cannot process RF execution response";
							log.log(Level.SEVERE, errorMsg, e);
							boolean hold = llrpRuntimeData.getReaderConfig().getEventAndReports()
									.getHold();
							if (!hold) {
								try {
									for (EventNotificationState state : llrpRuntimeData
											.getReaderConfig().getReaderEventNotificationSpec()
											.getEventNotificationStateList()) {
										if (state.isNotificationState()
												&& EventNotificationStateEventType.READER_EXCEPTION_EVENT == state
														.getEventType()) {
											ReaderExceptionEvent exceptionEvent = new ReaderExceptionEvent(
													new TLVParameterHeader((byte) 0), errorMsg);
											// create notification message
											Message msg = llrpRuntimeData.getMessageCreator()
													.createNotification(exceptionEvent,
															llrpRuntimeData.getProtocolVersion(),
															llrpServiceInstanceRuntimeData
																	.getPlatform());
											// send message
											llrpRuntimeData.getMessageHandler()
													.requestSendingData(msg);
											break;
										}
									}
								} catch (Exception e1) {
									throw new FSMActionException(
											"Cannot send reader exception notification", e1);
								}
							}
						}
					}
				}), destState);
	}

	void createGPIOStateChanged(FSMEvents fsmEvents, State<FSMEvent> srcState,
			State<FSMEvent> destState, String transitionName) throws FSMActionException {
		srcState.addConnection(fsmEvents.GPIO_STATE_CHANGED_RECEIVED,
				new Transition<FSMEvent>(transitionName, new Action<FSMEvent>() {
					@Override
					public void perform(State<FSMEvent> srcState, FSMEvent event,
							State<FSMEvent> destState) throws FSMActionException {
						FSMGPIOStateChangedEvent fsmEvent = (FSMGPIOStateChangedEvent) event;
						GPIORuntimeData runtimeData = fsmEvent.getRuntimeData();
						LLRPRuntimeData llrpRuntimeData = fsmEvent.getLLRPRuntimeData();
						LLRPServiceInstanceRuntimeData llrpServiceInstanceRuntimeData = fsmEvent
								.getLLRPServiceInstanceRuntimeData();
						StateChanged currentMessage = (StateChanged) runtimeData
								.getCurrentMessage();

						// remove processed message
						runtimeData.getCurrentMessages().remove(currentMessage);

						// create LLRP event
						Parameter llrpEvent = new LLRPMessageConverter().convert(currentMessage);
						EventNotificationStateEventType type = EventNotificationStateEventType.GPI_EVENT;
						try {
							// inform the ROSpecsManager + RFCMessageHandler
							// (ROSpecs may be started or stopped, AISpecs may
							// be stopped)
							llrpRuntimeData.getRoSpecsManager()
									.gpiEventReceived((GPIEvent) llrpEvent);
						} catch (Exception e) {
							String errorMsg = "Cannot process GPI event: " + currentMessage;
							log.log(Level.SEVERE, errorMsg);
							llrpEvent = new ReaderExceptionEvent(new TLVParameterHeader((byte) 0),
									errorMsg);
							type = EventNotificationStateEventType.READER_EXCEPTION_EVENT;
						}
						// send LLRP event
						boolean hold = llrpRuntimeData.getReaderConfig().getEventAndReports()
								.getHold();
						if (!hold) {
							try {
								for (EventNotificationState state : llrpRuntimeData
										.getReaderConfig().getReaderEventNotificationSpec()
										.getEventNotificationStateList()) {
									if (state.isNotificationState()
											&& type == state.getEventType()) {
										// create notification message
										Message msg = llrpRuntimeData.getMessageCreator()
												.createNotification(llrpEvent,
														llrpRuntimeData.getProtocolVersion(),
														llrpServiceInstanceRuntimeData
																.getPlatform());
										// send message
										llrpRuntimeData.getMessageHandler().requestSendingData(msg);
										break;
									}
								}
							} catch (Exception e) {
								throw new FSMActionException(
										"Cannot send reader exception notification", e);
							}
						}
					}
				}), destState);
	}
}
