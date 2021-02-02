package havis.llrpservice.server.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.rits.cloning.Cloner;

import havis.device.rf.tag.TagData;
import havis.llrpservice.data.message.parameter.GPIEvent;
import havis.llrpservice.data.message.parameter.GPITriggerValue;
import havis.llrpservice.data.message.parameter.PeriodicTriggerValue;
import havis.llrpservice.data.message.parameter.ROSpec;
import havis.llrpservice.data.message.parameter.ROSpecCurrentState;
import havis.llrpservice.data.message.parameter.ROSpecEvent;
import havis.llrpservice.data.message.parameter.ROSpecEventType;
import havis.llrpservice.data.message.parameter.ROSpecStartTriggerType;
import havis.llrpservice.data.message.parameter.ROSpecStopTriggerType;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.sbc.rfc.RFCException;
import havis.llrpservice.server.rfc.RFCMessageHandler;
import havis.llrpservice.server.rfc.UnsupportedAccessOperationException;
import havis.llrpservice.server.rfc.UnsupportedAirProtocolException;
import havis.llrpservice.server.rfc.UnsupportedSpecTypeException;
import havis.llrpservice.server.service.ROReportSpecsManager.ROReportSpecsManagerListener;

/**
 * Manages ROSpecs.
 * <p>
 * If a ROSpec is activated then its execution is started. If a ROSpec is
 * deactivated then its execution is canceled. The ROSpecsManager must be
 * informed about all execution responses with
 * {@link #executionResponseReceived(long, int)}.
 * </p>
 * <p>
 * ROSpec events are fired via {@link ROSpecsManagerListener} (see
 * {@link #addListener(ROSpecsManagerListener)},
 * {@link #removeListener(ROSpecsManagerListener)}).
 * </p>
 * <p>
 * The sending of reports is triggered via {@link ROReportSpecsManagerListener}
 * (see {@link ROReportSpecsManager#addListener(ROReportSpecsManagerListener)},
 * {@link ROReportSpecsManager#removeListener(ROReportSpecsManagerListener)}).
 * The ROReportSpecsManager is provided with {@link #getROReportSpecsManager()}.
 * </p>
 * <p>
 * All methods of the class must be called from one thread. The listener
 * callback methods may be called from other threads due to timers.
 * </p>
 */
public class ROSpecsManager {
	private Logger log = Logger.getLogger(ROSpecsManager.class.getName());

	public interface ROSpecsManagerListener {
		void executionChanged(ROSpecEvent event);
	}

	private enum ROSpecState {
		CANCELLED, //
		LAST_RESPONSE_RECEIVED, //
		STARTED
	}

	private final RFCMessageHandler rfcMessageHandler;
	private final boolean hasUTCClock;
	private ROReportSpecsManager roReportSpecsManager;
	private final Map<Long, ROSpec> roSpecs = new HashMap<Long, ROSpec>();
	private final List<ROSpec> roSpecList = new ArrayList<>();
	private final List<ROSpecsManagerListener> listeners = new ArrayList<>();
	private ROSpec activeROSpec;
	private List<ROSpecEvent> pendingROSpecEvents = new ArrayList<>();
	// roSpecId -> state
	private Map<Long, ROSpecState> roSpecStates = new HashMap<>();
	private List<Long> removedROSpecIds = new ArrayList<>();
	private Timer timer = null;
	// roSpecId -> timer task
	private Map<Long, TimerTask> startTasks = new HashMap<>();
	private Map<Long, TimerTask> stopTasks = new HashMap<>();

	public ROSpecsManager(RFCMessageHandler rfcMessageHandler, boolean hasUTCClock) {
		this.rfcMessageHandler = rfcMessageHandler;
		this.hasUTCClock = hasUTCClock;
		roReportSpecsManager = new ROReportSpecsManager();
	}

	public ROReportSpecsManager getROReportSpecsManager() {
		return roReportSpecsManager;
	}

	/**
	 * Gets the ROSpecs in the same order as they have been added.
	 * 
	 * @return The ROSpecs
	 */
	public synchronized List<ROSpec> getROSpecs() {
		return new ArrayList<>(roSpecList);
	}

	/**
	 * Returns a list of pending ROSpecId. The ROSpec executions could not be
	 * stopped completely because of missing execute responses. Calling of
	 * {@link #executionResponseReceived(long, int)} for each missing response
	 * finishes the executions.
	 */
	public synchronized List<Long> getPendingROSpecIds() {
		List<Long> ret = new ArrayList<>();
		for (ROSpecEvent ev : pendingROSpecEvents) {
			if (ev.getEventType() != ROSpecEventType.START_OF_ROSPEC
					&& !ret.contains(ev.getRoSpecID())) {
				ret.add(ev.getRoSpecID());
			}
		}
		return ret;
	}

	public synchronized void addListener(ROSpecsManagerListener listener) {
		listeners.add(listener);
	}

	public synchronized void removeListener(ROSpecsManagerListener listener) {
		listeners.remove(listener);
	}

	/**
	 * Adds a ROSpec.
	 * <p>
	 * The execution of an active ROSpec is started.
	 * </p>
	 * 
	 * @param roSpec
	 * @throws UnsupportedAirProtocolException
	 * @throws UnsupportedAccessOperationException
	 * @throws UnsupportedSpecTypeException
	 * @throws InvalidIdentifierException
	 * @throws RFCException
	 * @throws UtcClockException
	 */
	public synchronized void add(ROSpec roSpec) throws UnsupportedSpecTypeException,
			UnsupportedAccessOperationException, UnsupportedAirProtocolException,
			InvalidIdentifierException, RFCException, UtcClockException {
		if (roSpecs.get(roSpec.getRoSpecID()) != null) {
			throw new InvalidIdentifierException(
					"ROSpec with identifier " + roSpec.getRoSpecID() + " already exists");
		}
		// add the new ROSpec
		roSpecs.put(roSpec.getRoSpecID(), roSpec);
		roSpecList.add(roSpec);
		if (roSpec.getRoReportSpec() != null) {
			roReportSpecsManager.set(roSpec.getRoSpecID(), roSpec.getRoReportSpec());
		}
		// if ROSpec is enabled and start trigger is "immediate"
		if (ROSpecCurrentState.INACTIVE == roSpec.getCurrentState()
				&& ROSpecStartTriggerType.IMMEDIATE == roSpec.getRoBoundarySpec()
						.getRoSStartTrigger().getRoSpecStartTriggerType()) {
			// mark ROSpec as "active"
			roSpec.setCurrentState(ROSpecCurrentState.ACTIVE);
			// update timers
			updateTimerTasks(roSpec, ROSpecCurrentState.INACTIVE, ROSpecCurrentState.ACTIVE);
		}
		// if new ROSpec is active
		if (ROSpecCurrentState.ACTIVE == roSpec.getCurrentState()) {
			// update execution
			updateExecution();
		}
	}

	/**
	 * Removes a ROSpec.
	 * <p>
	 * If the ROSpec is active then its execution is canceled and the state is
	 * set to {@link ROSpecCurrentState#INACTIVE}. The ROSpecsManager must be
	 * informed about the last execution response with
	 * {@link #executionResponseReceived(long, int)}.
	 * </p>
	 * 
	 * @param roSpecId
	 *            0: All ROSpecs are removed
	 * @return the removed ROSpecs
	 * @throws UnsupportedAirProtocolException
	 * @throws UnsupportedAccessOperationException
	 * @throws UnsupportedSpecTypeException
	 * @throws RFCException
	 * @throws UtcClockException
	 * @throws InvalidIdentifierException
	 */
	public synchronized List<ROSpec> remove(long roSpecId)
			throws RFCException, UnsupportedSpecTypeException, UnsupportedAccessOperationException,
			UnsupportedAirProtocolException, UtcClockException, InvalidIdentifierException {
		List<ROSpec> specs = new ArrayList<>();
		if (0 == roSpecId) {
			specs.addAll(roSpecList);
		} else {
			ROSpec roSpec = roSpecs.get(roSpecId);
			if (roSpec == null) {
				throw new InvalidIdentifierException("Missing ROSpec with identifier " + roSpecId);
			}
			specs.add(roSpec);
		}
		boolean changed = false;
		for (ROSpec roSpec : specs) {
			// if ROSpec is active
			if (ROSpecCurrentState.ACTIVE == roSpec.getCurrentState()) {
				removedROSpecIds.add(roSpec.getRoSpecID());
				// mark ROSpec as "inactive" (ROSpec is deactivated with
				// generated end event later)
				roSpec.setCurrentState(ROSpecCurrentState.INACTIVE);
				// update timers
				updateTimerTasks(roSpec, ROSpecCurrentState.ACTIVE, ROSpecCurrentState.INACTIVE);
				changed = true;
			} else {
				// remove ROSpec from ROReportSpecsManager
				roReportSpecsManager.remove(roSpec.getRoSpecID());
				// remove it from internal lists
				roSpecList.remove(roSpec);
				roSpecs.remove(roSpecId);
			}
		}
		if (changed) {
			// update execution
			updateExecution();
		}
		return specs;
	}

	/**
	 * Sets the state of a ROSpec.
	 * <p>
	 * If a ROSpec is activated and another ROSpec with a higher priority is
	 * running then the state of the ROSpec is only set to
	 * {@link ROSpecCurrentState#ACTIVE}. It is executed automatically when all
	 * ROSpecs with higher priority has been deactivated.
	 * </p>
	 * <p>
	 * If a ROSpec is deactivated the execution of the active ROSpec is
	 * canceled. The ROSpecsManager must be informed about the last execution
	 * response with {@link #executionResponseReceived(long, int)}.
	 * </p>
	 * 
	 * @param roSpecId
	 *            0: All ROSpecs are enabled/disabled. ROSpecs cannot be started
	 *            or stopped with identifier 0.
	 * @param newState
	 * @throws RFCException
	 * @throws UnsupportedSpecTypeException
	 * @throws UnsupportedAccessOperationException
	 * @throws UnsupportedAirProtocolException
	 * @throws InvalidIdentifierException
	 * @throws UtcClockException
	 */
	public synchronized void setState(long roSpecId, ROSpecCurrentState newState)
			throws RFCException, UnsupportedSpecTypeException, UnsupportedAccessOperationException,
			UnsupportedAirProtocolException, InvalidIdentifierException, UtcClockException {
		List<ROSpec> specs = new ArrayList<>();
		if (0 == roSpecId) {
			for (ROSpec roSpec : roSpecList) {
				// if ROSpec shall be started or stopped
				if (ROSpecCurrentState.ACTIVE != roSpec.getCurrentState()
						&& ROSpecCurrentState.ACTIVE == newState
						|| ROSpecCurrentState.ACTIVE == roSpec.getCurrentState()
								&& ROSpecCurrentState.ACTIVE != newState) {
					throw new InvalidIdentifierException(
							"ROSpecs cannot be started or stopped with identifier 0");
				}
				specs.add(roSpec);
			}
		} else {
			ROSpec roSpec = roSpecs.get(roSpecId);
			if (roSpec == null) {
				throw new InvalidIdentifierException("Missing ROSpec with identifier " + roSpecId);
			}
			specs.add(roSpec);
		}
		boolean changed = false;
		for (ROSpec roSpec : specs) {
			ROSpecCurrentState oldState = roSpec.getCurrentState();
			if (oldState != newState) {
				// if ROSpec is being enabled and start trigger "immediate"
				// exists
				if (ROSpecCurrentState.DISABLED == oldState
						&& ROSpecCurrentState.INACTIVE == newState
						&& ROSpecStartTriggerType.IMMEDIATE == roSpec.getRoBoundarySpec()
								.getRoSStartTrigger().getRoSpecStartTriggerType()) {
					// activate ROSpec
					newState = ROSpecCurrentState.ACTIVE;
				}
				// set new state
				roSpec.setCurrentState(newState);
				// update timers
				updateTimerTasks(roSpec, oldState, newState);
				changed = true;
			}
		}
		if (changed) {
			// update execution
			updateExecution();
		}
	}

	/**
	 * Informs the ROSpecsManager about the receipt of a new report.
	 * 
	 * @param roSpecId
	 * @param tagData
	 * @param isLastResponse
	 * @throws RFCException
	 * @throws UnsupportedSpecTypeException
	 * @throws UnsupportedAccessOperationException
	 * @throws UnsupportedAirProtocolException
	 * @throws UtcClockException
	 * @throws InvalidIdentifierException
	 */
	public synchronized void executionResponseReceived(long roSpecId, List<TagData> tagData,
			boolean isLastResponse)
			throws RFCException, UnsupportedSpecTypeException, UnsupportedAccessOperationException,
			UnsupportedAirProtocolException, InvalidIdentifierException, UtcClockException {
		boolean isROSpecActive = false;
		if (isLastResponse) {
			if (log.isLoggable(Level.FINE)) {
				log.log(Level.FINE, "Received last execution response for ROSpec " + roSpecId);
			}
			roSpecStates.put(roSpecId, ROSpecState.LAST_RESPONSE_RECEIVED);
			// for each ROSpec
			for (ROSpec roSpec : roSpecList) {
				if (roSpec.getRoSpecID() == roSpecId) {
					if (ROSpecCurrentState.ACTIVE == roSpec.getCurrentState()) {
						isROSpecActive = true;
						break;
					}
				}
			}
		}
		roReportSpecsManager.executionResponseReceived(roSpecId, tagData);
		// if last response and ROSpec is still active eg. if AI stop trigger
		// has stopped it
		if (isROSpecActive) {
			// deactivate ROSpec
			setState(roSpecId, ROSpecCurrentState.INACTIVE);
		} else {
			processPendingROSpecEvents();
		}
	}

	/**
	 * Informs the ROSpecsManager about the receipt of a GPI event.
	 * 
	 * @param event
	 * @throws RFCException
	 * @throws UnsupportedSpecTypeException
	 * @throws UnsupportedAccessOperationException
	 * @throws UnsupportedAirProtocolException
	 * @throws InvalidIdentifierException
	 * @throws UtcClockException
	 */
	public synchronized void gpiEventReceived(GPIEvent event)
			throws RFCException, UnsupportedSpecTypeException, UnsupportedAccessOperationException,
			UnsupportedAirProtocolException, InvalidIdentifierException, UtcClockException {
		// for each ROSpec
		for (ROSpec roSpec : roSpecList) {
			switch (roSpec.getCurrentState()) {
			case INACTIVE:
				// if start trigger "gpi" exists
				if (ROSpecStartTriggerType.GPI == roSpec.getRoBoundarySpec().getRoSStartTrigger()
						.getRoSpecStartTriggerType()) {
					GPITriggerValue gpiTriggerValue = roSpec.getRoBoundarySpec()
							.getRoSStartTrigger().getGpiTV();
					// if ports and state transitions match
					if (event.getGpiPortNumber() == gpiTriggerValue.getGpiPortNum()
							&& gpiTriggerValue.isGpiEvent() == event.isState()) {
						// start ROSpec
						setState(roSpec.getRoSpecID(), ROSpecCurrentState.ACTIVE);
					}
				}
				break;
			case ACTIVE:
				// if stop trigger "gpi" exists
				if (ROSpecStopTriggerType.GPI_WITH_TIMEOUT_VALUE == roSpec.getRoBoundarySpec()
						.getRoSStopTrigger().getRoSpecStopTriggerType()) {
					GPITriggerValue gpiTriggerValue = roSpec.getRoBoundarySpec().getRoSStopTrigger()
							.getGpiTriggerValue();
					// if ports and state transitions match
					if (event.getGpiPortNumber() == gpiTriggerValue.getGpiPortNum()
							&& gpiTriggerValue.isGpiEvent() == event.isState()) {
						// stop ROSpec
						setState(roSpec.getRoSpecID(), ROSpecCurrentState.INACTIVE);
					}
				}
				break;
			default:
			}
		}
		// forward event to RFC message handler (eg. AISpec stop trigger)
		rfcMessageHandler.gpiEventReceived(event);
	}

	private void updateTimerTasks(ROSpec roSpec, ROSpecCurrentState oldState,
			ROSpecCurrentState newState) throws UtcClockException {
		// oldState\newState|disabled.........|inactive....................|active
		// disabled.........|-................|start_start_timer_"periodic"|start_start_timer_"periodic"+start_stop_timer_"duration"/"GPI_with_timeout"
		// inactive.........|stop_start_timers|-...........................|start_stop_timer_"duration"/"GPI_with_timeout"
		// active...........|stop_all_timers..|stop_stop_timers............|-
		switch (newState) {
		case ACTIVE:
			if (ROSpecCurrentState.DISABLED == oldState) {
				updateTimerTasks(roSpec, ROSpecCurrentState.DISABLED, ROSpecCurrentState.INACTIVE);
			}
			// start stop trigger
			switch (roSpec.getRoBoundarySpec().getRoSStopTrigger().getRoSpecStopTriggerType()) {
			case DURATION:
				addTimerTask(roSpec.getRoSpecID(), null /* date */,
						roSpec.getRoBoundarySpec().getRoSStopTrigger()
								.getDurationTriggerValue() /* offset */,
						0 /* period */, ROSpecCurrentState.INACTIVE);
				break;
			case GPI_WITH_TIMEOUT_VALUE:
				long timeout = roSpec.getRoBoundarySpec().getRoSStopTrigger().getGpiTriggerValue()
						.getTimeOut();
				if (timeout > 0) {
					addTimerTask(roSpec.getRoSpecID(), null /* date */, timeout /* offset */,
							0 /* period */, ROSpecCurrentState.INACTIVE);
				}
				break;
			default:
			}
			break;
		case INACTIVE:
			switch (oldState) {
			case DISABLED:
				// start trigger "periodic"
				if (ROSpecStartTriggerType.PERIODIC == roSpec.getRoBoundarySpec()
						.getRoSStartTrigger().getRoSpecStartTriggerType()) {
					PeriodicTriggerValue tv = roSpec.getRoBoundarySpec().getRoSStartTrigger()
							.getPeriodicTV();
					Date date = null;
					if (tv.getUtc() != null) {
						if (hasUTCClock) {
							date = new Date(tv.getUtc().getMicroseconds().longValue() / 1000);
						} else {
							throw new UtcClockException(
									"Cannot start periodic ROSpec trigger due to missing UTC clock");
						}
					}
					addTimerTask(roSpec.getRoSpecID(), date, tv.getOffSet(), tv.getPeriod(),
							ROSpecCurrentState.ACTIVE);
				}
				break;
			case ACTIVE:
				// stop timers for stop triggers
				removeTimerTasks(roSpec.getRoSpecID(), ROSpecCurrentState.INACTIVE);
				break;
			default:
			}
			break;
		case DISABLED:
			// stop timers for start triggers
			removeTimerTasks(roSpec.getRoSpecID(), ROSpecCurrentState.ACTIVE);
			if (ROSpecCurrentState.ACTIVE == oldState) {
				updateTimerTasks(roSpec, ROSpecCurrentState.ACTIVE, ROSpecCurrentState.INACTIVE);
			}
			break;
		default:
		}
	}

	/**
	 * @param roSpecId
	 * @param date
	 *            optional
	 * @param offset
	 *            in ms
	 * @param period
	 *            in ms
	 * @param newState
	 */
	private void addTimerTask(final long roSpecId, Date date, long offset, long period,
			final ROSpecCurrentState newState) {
		if (timer == null) {
			timer = new Timer("ROSpecsTimerThread");
		}
		TimerTask task = new TimerTask() {

			@Override
			public void run() {
				try {
					setState(roSpecId, newState);
				} catch (Exception e) {
					log.log(Level.SEVERE,
							"Cannot change state of ROSpec " + roSpecId + " to " + newState, e);
				}
			}
		};
		boolean isStartTask = ROSpecCurrentState.ACTIVE == newState;
		if (isStartTask) {
			startTasks.put(roSpecId, task);
		} else {
			stopTasks.put(roSpecId, task);
		}
		if (period == 0) {
			if (date == null) {
				timer.schedule(task, offset);
				if (log.isLoggable(Level.FINE)) {
					log.log(Level.FINE,
							"Scheduled timer for " + (isStartTask ? "starting" : "stopping")
									+ " of ROSpec " + roSpecId + ": offset=" + offset);
				}
			} else {
				timer.schedule(task, new Date(date.getTime() + offset));
				if (log.isLoggable(Level.FINE)) {
					log.log(Level.FINE,
							"Scheduled timer for " + (isStartTask ? "starting" : "stopping")
									+ " of ROSpec " + roSpecId + ": date="
									+ new Date(date.getTime() + offset).getTime());
				}
			}
		} else {
			if (date == null) {
				timer.scheduleAtFixedRate(task, offset, period);
				if (log.isLoggable(Level.FINE)) {
					log.log(Level.FINE,
							"Scheduled timer for " + (isStartTask ? "starting" : "stopping")
									+ " of ROSpec " + roSpecId + ": offset=" + offset + ",period="
									+ period);
				}
			} else {
				timer.scheduleAtFixedRate(task, new Date(date.getTime() + offset), period);
				if (log.isLoggable(Level.FINE)) {
					log.log(Level.FINE,
							"Scheduled timer for " + (isStartTask ? "starting" : "stopping")
									+ " of ROSpec " + roSpecId + ": date="
									+ new Date(date.getTime() + offset) + ",period=" + period);
				}
			}
		}
	}

	private void removeTimerTasks(long roSpecId, ROSpecCurrentState newState) {
		if (timer == null) {
			return;
		}
		if (ROSpecCurrentState.ACTIVE == newState) {
			TimerTask task = startTasks.remove(roSpecId);
			if (task != null) {
				task.cancel();
				if (log.isLoggable(Level.FINE)) {
					log.log(Level.FINE, "Canceled timer for starting of ROSpec " + roSpecId);
				}
			}
		} else {
			TimerTask task = stopTasks.remove(roSpecId);
			if (task != null) {
				task.cancel();
				if (log.isLoggable(Level.FINE)) {
					log.log(Level.FINE, "Canceled timer for stopping of ROSpec " + roSpecId);
				}
			}
		}
		if (startTasks.isEmpty() && stopTasks.isEmpty()) {
			timer.cancel();
			timer = null;
		}
	}

	/**
	 * Updates the current active ROSpec.
	 * 
	 * @throws RFCException
	 * @throws UnsupportedSpecTypeException
	 * @throws UnsupportedAccessOperationException
	 * @throws UnsupportedAirProtocolException
	 */
	private void updateExecution() throws RFCException, UnsupportedSpecTypeException,
			UnsupportedAccessOperationException, UnsupportedAirProtocolException {
		// determine the active ROSpec
		Short prio = null;
		ROSpec roSpec = null;
		for (ROSpec currentROSpec : roSpecList) {
			if (currentROSpec.getCurrentState() == ROSpecCurrentState.ACTIVE
					&& (prio == null || currentROSpec.getPriority() < prio)) {
				prio = currentROSpec.getPriority();
				roSpec = currentROSpec;
			}
		}
		// the execution must be changed
		if (activeROSpec != roSpec) {
			List<ROSpecEvent> roSpecEvents = new ArrayList<ROSpecEvent>();
			// cancel an active ROSpec
			if (activeROSpec != null) {
				// if the state of the active ROSpec has not been changed:
				// the ROSpec gets preempted (LLRPSpec 6.1)
				if (roSpec != null && activeROSpec.getCurrentState() == ROSpecCurrentState.ACTIVE) {
					// deactivate active ROSpec
					activeROSpec.setCurrentState(ROSpecCurrentState.INACTIVE);
					// add preemption event
					roSpecEvents.add(new ROSpecEvent(new TLVParameterHeader((byte) 0),
							ROSpecEventType.PREEMPTION_OF_ROSPEC, activeROSpec.getRoSpecID(),
							roSpec.getRoSpecID()));
				} else {
					// add end event for active ROSpec
					roSpecEvents.add(new ROSpecEvent(new TLVParameterHeader((byte) 0),
							ROSpecEventType.END_OF_ROSPEC, activeROSpec.getRoSpecID(), 0));
				}
			}
			// start a new ROSpec
			if (roSpec != null) {
				// add start event for new ROSpec
				roSpecEvents.add(new ROSpecEvent(new TLVParameterHeader((byte) 0),
						ROSpecEventType.START_OF_ROSPEC, roSpec.getRoSpecID(), 0));

			}
			if (log.isLoggable(Level.FINE)) {
				log.log(Level.FINE,
						"Triggered changing of active ROSpec: "
								+ (activeROSpec == null ? "<NULL>" : activeROSpec.getRoSpecID())
								+ " -> " + (roSpec == null ? "<NULL>" : roSpec.getRoSpecID()));
			}
			activeROSpec = roSpec;
			pendingROSpecEvents.addAll(roSpecEvents);
			processPendingROSpecEvents();
		}
	}

	private void processPendingROSpecEvents() throws RFCException, UnsupportedSpecTypeException,
			UnsupportedAccessOperationException, UnsupportedAirProtocolException {
		// filter event list
		pendingROSpecEvents = filterROSpecEvents(pendingROSpecEvents);
		while (!pendingROSpecEvents.isEmpty()) {
			// get first event
			ROSpecEvent roSpecEvent = pendingROSpecEvents.get(0);
			long roSpecId = roSpecEvent.getRoSpecID();
			switch (roSpecEvent.getEventType()) {
			case END_OF_ROSPEC:
			case PREEMPTION_OF_ROSPEC:
				ROSpecState roSpecState = roSpecStates.get(roSpecId);
				// if last execution response has been received
				if (roSpecState != null && roSpecState == ROSpecState.LAST_RESPONSE_RECEIVED) {
					if (log.isLoggable(Level.FINE)) {
						log.log(Level.FINE, "Stopping ROSpec " + roSpecId);
					}
					// deactivate report triggers
					roReportSpecsManager.roSpecStopped(roSpecId);
					// if ROSpec has been removed
					if (removedROSpecIds.contains(roSpecId)) {
						// remove it from ROReportSpecsManager
						roReportSpecsManager.remove(roSpecId);
						// remove it from internal lists
						ROSpec roSpec = roSpecs.remove(roSpecId);
						roSpecList.remove(roSpec);
						removedROSpecIds.remove(roSpecId);
					}
					// fire execution changed event
					fireExecutionChangedEvent(roSpecEvent);
					// remove event from internal lists
					roSpecStates.remove(roSpecId);
					pendingROSpecEvents.remove(0);
					// filter events list
					pendingROSpecEvents = filterROSpecEvents(pendingROSpecEvents);
				} else {
					if (roSpecState == null || roSpecState == ROSpecState.STARTED) {
						// cancel execution
						rfcMessageHandler.cancelExecution(roSpecId);
						roSpecStates.put(roSpecId, ROSpecState.CANCELLED);
					}
					// wait for execution response
					return;
				}
				break;
			case START_OF_ROSPEC:
				if (log.isLoggable(Level.FINE)) {
					log.log(Level.FINE, "Starting ROSpec " + roSpecId);
				}
				// fire execution changed event
				fireExecutionChangedEvent(roSpecEvent);
				// activate report triggers
				roReportSpecsManager.roSpecStarted(roSpecId);
				// start execution
				ROSpec roSpec = roSpecs.get(roSpecId);
				rfcMessageHandler.requestExecution(new Cloner().deepClone(roSpec));
				roSpecStates.put(roSpecId, ROSpecState.STARTED);
				// remove event from internal lists
				pendingROSpecEvents.remove(0);
				// wait for execution response
				return;
			}
		}
	}

	private List<ROSpecEvent> filterROSpecEvents(List<ROSpecEvent> roSpecEvents) {
		Map<Long, ROSpecEvent> prevEvents = new HashMap<>();
		List<ROSpecEvent> events = new ArrayList<>();
		// for each event
		for (ROSpecEvent roSpecEvent : roSpecEvents) {
			long roSpecId = roSpecEvent.getRoSpecID();
			// get previous event
			ROSpecEvent prevEvent = prevEvents.get(roSpecId);
			// if no previous event
			if (prevEvent == null) {
				// add event
				prevEvents.put(roSpecId, roSpecEvent);
				events.add(roSpecEvent);
			} // else if ROSpec has not been started or cancelled yet
				// and start -> end or end -> start
			else if (roSpecStates.get(roSpecId) == null
					&& (ROSpecEventType.START_OF_ROSPEC == prevEvent.getEventType()
							&& ROSpecEventType.START_OF_ROSPEC != roSpecEvent.getEventType()
							|| ROSpecEventType.START_OF_ROSPEC != prevEvent.getEventType()
									&& ROSpecEventType.START_OF_ROSPEC == roSpecEvent
											.getEventType())) {
				// remove previous event
				prevEvents.remove(roSpecId);
				events.remove(prevEvent);
			}
		}
		return events;
	}

	/**
	 * Fires an execution changed event.
	 * 
	 * @param type
	 * @param roSpecId
	 * @param preemptingROSpecId
	 */
	private void fireExecutionChangedEvent(ROSpecEvent event) {
		for (ROSpecsManagerListener listener : listeners) {
			listener.executionChanged(new ROSpecEvent(new TLVParameterHeader((byte) 0),
					event.getEventType(), event.getRoSpecID(), event.getPreemptingROSpecID()));
		}
	}
}
