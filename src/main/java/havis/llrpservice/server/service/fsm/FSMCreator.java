package havis.llrpservice.server.service.fsm;

import havis.llrpservice.common.fsm.FSM;
import havis.llrpservice.common.fsm.FSMActionException;
import havis.llrpservice.common.fsm.State;
import havis.llrpservice.common.fsm.Transition;

public class FSMCreator {
	final FSMCommonCreator commonCreator = new FSMCommonCreator();
	final FSMCapabilitiesCreator capabilitiesCreator = new FSMCapabilitiesCreator(commonCreator);
	final FSMConfigurationCreator configurationCreator = new FSMConfigurationCreator(commonCreator);
	final FSMConnectionCreator connectionCreator = new FSMConnectionCreator();

	public FSM<FSMEvent> create(FSMEvents fsmEvents) throws FSMActionException {
		State<FSMEvent> waitForMessageState = new State<>("1 waitForMessage");
		FSM<FSMEvent> fsm = new FSM<>("FSM", waitForMessageState, 10 /* maxHistorySize */);

		State<FSMEvent> llrpMessageReceivedState = commonCreator
				.createLLRPMessageReceivedState("2 llrpMessageReceived");

		State<FSMEvent> rfcMessageReceivedState = commonCreator
				.createRFCMessageReceivedState("3 rfcMessageReceived");

		State<FSMEvent> gpioMessageReceivedState = commonCreator
				.createGPIOMessageReceivedState("4 gpioMessageReceived");

		waitForMessageState.addConnection(fsmEvents.LLRP_MESSAGE_RECEIVED,
				new Transition<FSMEvent>("1-2"), llrpMessageReceivedState);
		waitForMessageState.addConnection(fsmEvents.RFC_MESSAGE_RECEIVED,
				new Transition<FSMEvent>("1-3"), rfcMessageReceivedState);
		waitForMessageState.addConnection(fsmEvents.GPIO_MESSAGE_RECEIVED,
				new Transition<FSMEvent>("1-4"), gpioMessageReceivedState);

		capabilitiesCreator.createLLRPGetReaderCapabilities(fsm, fsmEvents,
				llrpMessageReceivedState, waitForMessageState);
		configurationCreator.createLLRPGetReaderConfig(fsmEvents, llrpMessageReceivedState,
				rfcMessageReceivedState, gpioMessageReceivedState, waitForMessageState);
		connectionCreator.createLLRPCloseConnection(fsmEvents, llrpMessageReceivedState,
				waitForMessageState);

		return fsm;
	}
}
