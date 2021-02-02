package havis.llrpservice.common.fsm;

public interface Guard<TEvent> {
	boolean evaluate(State<TEvent> srcState, TEvent event, State<TEvent> destState)
			throws FSMGuardException;
}
