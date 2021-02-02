package havis.llrpservice.server.service.fsm;

import havis.llrpservice.common.fsm.Action;
import havis.llrpservice.common.fsm.FSMActionException;
import havis.llrpservice.common.fsm.State;
import havis.llrpservice.common.fsm.Transition;
import havis.llrpservice.data.message.CloseConnection;
import havis.llrpservice.data.message.CloseConnectionResponse;
import havis.llrpservice.data.message.parameter.LLRPStatus;
import havis.llrpservice.data.message.parameter.LLRPStatusCode;
import havis.llrpservice.server.service.fsm.lllrp.FSMLLRPCloseConnectionEvent;
import havis.llrpservice.server.service.fsm.lllrp.LLRPRuntimeData;

public class FSMConnectionCreator {

	void createLLRPCloseConnection(FSMEvents fsmEvents, State<FSMEvent> llrpMessageReceivedState,
			State<FSMEvent> waitForMessageState) throws FSMActionException {
		llrpMessageReceivedState.addConnection(fsmEvents.LLRP_CLOSE_CONNECTION_RECEIVED,
				new Transition<FSMEvent>("2-1", new Action<FSMEvent>() {
					@Override
					public void perform(State<FSMEvent> srcState, FSMEvent event,
							State<FSMEvent> destState) throws FSMActionException {
						FSMLLRPCloseConnectionEvent fsmEvent = (FSMLLRPCloseConnectionEvent) event;
						LLRPRuntimeData runtimeData = fsmEvent.getRuntimeData();
						CloseConnection currentMessage = (CloseConnection) runtimeData
								.getCurrentMessage().getMessage();
						LLRPStatus status = runtimeData.getCurrentMessage().getStatus();
						// if valid message
						if (status.getStatusCode() == LLRPStatusCode.M_SUCCESS) {
							// trigger restarting of LLRP server
							runtimeData.setRestartServer(true);
						}
						// create response
						CloseConnectionResponse response = runtimeData.getMessageCreator()
								.createResponse(currentMessage, runtimeData.getProtocolVersion(),
										status);
						// remove processed message
						runtimeData.removeCurrentMessage(currentMessage.getMessageHeader().getId());
						// send response
						try {
							runtimeData.getMessageHandler().requestSendingData(response);
						} catch (Exception e) {
							throw new FSMActionException("Cannot send LLRP message " + response, e);
						}
					}
				}), waitForMessageState);
	}
}
