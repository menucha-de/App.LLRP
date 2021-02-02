package havis.llrpservice.common.fsm;

import java.util.ArrayList;
import java.util.List;

public class Transition<TEvent> {
	private final String name;
	private final List<Guard<TEvent>> guards = new ArrayList<>();
	private final List<Action<TEvent>> actions = new ArrayList<>();

	public Transition(String name) {
		this.name = name;
	}

	public Transition(String name, Guard<TEvent> guard) {
		this(name);
		addGuard(guard);
	}

	public Transition(String name, Action<TEvent> action) {
		this(name);
		addAction(action);
	}

	public Transition(String name, Guard<TEvent> guard, Action<TEvent> action) {
		this(name, guard);
		addAction(action);
	}

	public String getName() {
		return name;
	}

	public Transition<TEvent> addGuard(Guard<TEvent> guard) {
		guards.add(guard);
		return this;
	}

	public List<Guard<TEvent>> getGuards() {
		return guards;
	}

	public Transition<TEvent> addAction(Action<TEvent> action) {
		actions.add(action);
		return this;
	}

	public List<Action<TEvent>> getActions() {
		return actions;
	}
}
