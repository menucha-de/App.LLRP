package havis.llrpservice.common.fsm;

public interface Action<TEvent> {
	void perform(State<TEvent> srcState, TEvent event, State<TEvent> destState)
			throws FSMActionException;
}
