package havis.llrpservice.common.fsm;

import java.util.ArrayList;
import java.util.List;

public class State<TEvent> {

	private final String name;
	private final List<StateConnection<TEvent>> connections = new ArrayList<>();
	private final List<Action<TEvent>> entryActions = new ArrayList<>();
	private final List<Action<TEvent>> exitActions = new ArrayList<>();
	private State<TEvent> parent;

	public State(String name) {
		this.name = name;
	}

	public State(String name, TEvent event, Transition<TEvent> transition,
			State<TEvent> destState) {
		this(name);
		addConnection(event, transition, destState);
	}

	public State(String name, State<TEvent> parent) {
		this(name);
		this.parent = parent;
	}

	public State(String name, State<TEvent> parent, TEvent event,
			Transition<TEvent> transition, State<TEvent> destState) {
		this(name, parent);
		addConnection(event, transition, destState);
	}

	public State<TEvent> getParent() {
		return parent;
	}

	public String getName() {
		return name;
	}

	public State<TEvent> addConnection(TEvent event,
			Transition<TEvent> transition, State<TEvent> destState) {
		connections.add(new StateConnection<>(this, event, transition,
				destState));
		return this;
	}

	public List<StateConnection<TEvent>> getConnections() {
		return connections;
	}

	public State<TEvent> addEntryAction(Action<TEvent> action) {
		entryActions.add(action);
		return this;
	}

	public List<Action<TEvent>> getEntryActions() {
		return entryActions;
	}

	public State<TEvent> addExitAction(Action<TEvent> action) {
		exitActions.add(action);
		return this;
	}

	public List<Action<TEvent>> getExitActions() {
		return exitActions;
	}
}