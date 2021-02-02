package havis.llrpservice.common.fsm;

public class StateConnection<TEvent> {

	private final State<TEvent> srcState;
	private final TEvent event;
	private final Transition<TEvent> transition;
	private final State<TEvent> destState;

	public StateConnection(State<TEvent> srcState, TEvent event,
			Transition<TEvent> transition, State<TEvent> destState) {
		this.srcState = srcState;
		this.event = event;
		this.transition = transition;
		this.destState = destState;
	}

	public State<TEvent> getSrcState() {
		return srcState;
	}

	public TEvent getEvent() {
		return event;
	}

	public Transition<TEvent> getTransition() {
		return transition;
	}

	public State<TEvent> getDestState() {
		return destState;
	}
}
