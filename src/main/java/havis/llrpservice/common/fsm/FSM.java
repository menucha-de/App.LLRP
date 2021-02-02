package havis.llrpservice.common.fsm;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple implementation of a Finite State Machine.
 * 
 * @param <TEvent>
 */
public class FSM<TEvent> {

	private Logger log = Logger.getLogger(FSM.class.getName());

	private final List<TEvent> events = new ArrayList<>();
	private boolean isFiring = false;
	private final String name;
	private final State<TEvent> initialState;
	private State<TEvent> currentState;
	private int maxHistorySize;
	private List<StateConnection<TEvent>> history;

	/**
	 * Creates a FSM.
	 * 
	 * @param name
	 * @param initialState
	 * @param maxHistorySize
	 *            the max. size of the history
	 * @throws FSMActionException
	 */
	public FSM(String name, State<TEvent> initialState, int maxHistorySize)
			throws FSMActionException {
		this.name = name;
		this.initialState = initialState;
		this.currentState = initialState;
		history = new ArrayList<>();
		this.maxHistorySize = maxHistorySize;

		if (log.isLoggable(Level.FINE)) {
			log.log(Level.FINE, "FSM " + name + ": InitialState "
					+ getStatePath(initialState).toString() + ", maxHistorySize " + maxHistorySize);
		}

		// perform enter actions
		performEnterActions(null /* srcState */, null /* event */, initialState);
	}

	/**
	 * Creates a FSM with an existing history.
	 * 
	 * @param name
	 * @param history
	 *            An existing history with at least one entry. The destination
	 *            state of the last history entry is used as initial state.
	 * @param maxHistorySize
	 *            The max. size of the history. If the given history is larger
	 *            than this max. size then entries from the beginning of the
	 *            list are removed.
	 * @throws FSMException
	 */
	public FSM(String name, List<StateConnection<TEvent>> history, int maxHistorySize)
			throws FSMException {
		if (history == null || history.size() == 0) {
			throw new FSMException("The history must not be empty");
		}
		this.name = name;
		// the last destination state of the history is the initial state
		this.initialState = history.get(history.size() - 1).getDestState();
		this.currentState = initialState;
		this.history = history;
		setMaxHistorySize(maxHistorySize);

		if (log.isLoggable(Level.FINE)) {
			log.log(Level.FINE, "FSM " + name + ": InitialState "
					+ getStatePath(initialState).toString() + ", maxHistorySize " + maxHistorySize);
		}

		// perform enter actions
		performEnterActions(null /* srcState */, null /* event */, initialState);
	}

	public String getName() {
		return name;
	}

	public State<TEvent> getInitialState() {
		return initialState;
	}

	public State<TEvent> getCurrentState() {
		return currentState;
	}

	/**
	 * Fires an event. If other events are currently fired then the new event is
	 * enqueued.
	 * <p>
	 * If a history is configured and the history is full then the first entry
	 * of the list is removed before a new entry is appended.
	 * </p>
	 * 
	 * @param ev
	 * @return This instance
	 * @throws FSMGuardException
	 * @throws FSMActionException
	 */
	public FSM<TEvent> fire(TEvent ev) throws FSMGuardException, FSMActionException {
		events.add(ev);
		if (isFiring) {
			return this;
		}
		isFiring = true;
		try {
			while (events.size() > 0) {
				TEvent event = events.remove(0);
				// for each state connection of current state
				for (StateConnection<TEvent> connection : currentState.getConnections()) {
					// if the event matches
					if (connection.getEvent().equals(event)) {
						Transition<TEvent> transition = connection.getTransition();
						// all guards must accept the event
						boolean performActions = true;
						for (Guard<TEvent> guard : transition.getGuards()) {
							if (!guard.evaluate(connection.getSrcState(), event,
									connection.getDestState())) {
								performActions = false;
								break;
							}
						}
						// if actions can be performed
						if (performActions) {
							// perform exit actions
							for (Action<TEvent> action : currentState.getExitActions()) {
								action.perform(currentState, event, connection.getDestState());
							}
							// perform actions of transition
							for (Action<TEvent> action : transition.getActions()) {
								action.perform(currentState, event, connection.getDestState());
							}
							// perform enter actions
							performEnterActions(currentState, event, connection.getDestState());
							// save destination state as current state
							currentState = connection.getDestState();
							// if a history shall be created
							if (maxHistorySize > 0) {
								// if the history is full
								if (history.size() == maxHistorySize) {
									// remove the oldest entry
									history.remove(0);
									if (log.isLoggable(Level.FINE)) {
										log.log(Level.FINE, "Removed 1 history entry");
									}
								}
								// add connection to history
								history.add(connection);
							}
							if (log.isLoggable(Level.FINE)) {
								log.log(Level.FINE,
										"FSM " + name + ": Event " + event.toString() + ", state "
												+ getStatePath(connection.getSrcState()).toString()
												+ " -> "
												+ getStatePath(connection.getDestState()).toString()
												+ ", transition " + transition.getName());
							}
							break;
						}
					}
				}
			}
		} finally {
			isFiring = false;
		}
		return this;
	}

	/**
	 * Sets the max. history size.
	 * <p>
	 * If an existing history is larger than this max. size then entries from
	 * the beginning of the list are removed.
	 * </p>
	 * 
	 * @param maxSize
	 * @return This instance
	 */
	public FSM<TEvent> setMaxHistorySize(int maxSize) {
		maxHistorySize = maxSize;
		// if new history size is smaller than the current history size
		// then remove the oldest history entries
		int sizeDiff = history.size() - maxHistorySize;
		for (int i = 0; i < sizeDiff; i++) {
			history.remove(0);
		}
		if (log.isLoggable(Level.FINE)) {
			log.log(Level.FINE,
					"Removed " + sizeDiff + " history entr" + (sizeDiff == 1 ? "y" : "ies"));
		}
		return this;
	}

	public int getMaxHistorySize() {
		return maxHistorySize;
	}

	public FSM<TEvent> clearHistory() {
		int historySize = history.size();
		history.clear();
		if (log.isLoggable(Level.FINE)) {
			log.log(Level.FINE, "Cleared history with " + historySize + " entr"
					+ (historySize == 1 ? "y" : "ies"));
		}
		return this;
	}

	public List<StateConnection<TEvent>> getHistory() {
		return history;
	}

	/**
	 * Executes the enter actions of a state.
	 * 
	 * @param srcState
	 * @param event
	 * @param destState
	 * @throws FSMActionException
	 */
	private void performEnterActions(State<TEvent> srcState, TEvent event, State<TEvent> destState)
			throws FSMActionException {
		// perform enter actions
		for (Action<TEvent> action : destState.getEntryActions()) {
			action.perform(srcState, event, destState);
		}
	}

	/**
	 * Returns the state path from top parent state to the given child state
	 * (eg. <code>parentStateName/childStateName</code>).
	 * 
	 * @param state
	 * @return The new created {@link StringBuilder}
	 */
	private StringBuilder getStatePath(State<TEvent> state) {
		StringBuilder statePath;
		if (state.getParent() != null) {
			statePath = getStatePath(state.getParent());
			statePath.append("/");
		} else {
			statePath = new StringBuilder();
		}
		statePath.append(state.getName());
		return statePath;
	}
}
