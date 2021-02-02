package havis.llrpservice.server.event;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides a Queue for events. Put adds a event to the queue (at the end) and
 * take removes it (from beginning) and returns the event. To inform user of
 * action on the queue, listeners must be added.
 * 
 */
public class EventQueue {
	public static final int NO_TIMEOUT = 0;

	private static final Logger log = Logger.getLogger(EventQueue.class.getName());
	// Maps eventType to listener
	private Map<EventType, List<EventQueueListener>> listeners;
	// prio -> event queue
	private Map<Integer, List<Event>> eventQueues;
	private Integer maxPrio;
	// Condition for the reentrant lock
	private Condition eventQueuesCondition;
	private final Lock lock = new ReentrantLock(/* fair */false);

	/**
	 * Initializes the queue
	 */
	public EventQueue() {
		listeners = new HashMap<EventType, List<EventQueueListener>>();
		eventQueues = new HashMap<>();
		eventQueuesCondition = lock.newCondition();
	}

	/**
	 * Puts a event in the queue with priority {@link EventPriority#DEFAULT} and
	 * informs the listeners about the added event.
	 * 
	 * @param event
	 */
	public void put(Event event) {
		put(event, EventPriority.DEFAULT);
	}

	/**
	 * Puts a event in the queue and informs the listeners about the added
	 * event.
	 * 
	 * @param event
	 * @param priority
	 */
	public void put(Event event, int priority) {
		lock.lock();
		try {
			List<Event> eventQueue = eventQueues.get(priority);
			if (eventQueue == null) {
				eventQueue = new ArrayList<>();
				eventQueues.put(priority, eventQueue);
			}
			eventQueue.add(event);
			if (maxPrio == null || priority > maxPrio) {
				maxPrio = priority;
			}
			eventQueuesCondition.signal();
			if (log.isLoggable(Level.FINE)) {
				log.log(Level.FINE, "Added " + event);
			}
		} finally {
			lock.unlock();
		}

		List<EventQueueListener> list = getListener(event.getEventType());
		if (list != null) {
			for (EventQueueListener listener : list) {
				listener.added(this, event);
			}
		}

	}

	/**
	 * Removes an event from the queue and returns the event. The events with
	 * the highest priority are returned first. The listeners are informed about
	 * the removed event.
	 * 
	 * @param timeout
	 *            <ul>
	 *            <li>&lt;= 0: wait until an event is queued (see
	 *            {@link #NO_TIMEOUT})
	 *            <li>&gt; 0: wait until an event is queued or the specified
	 *            waiting time elapses (in milliseconds)
	 *            </ul>
	 * @return first event queued
	 * @throws InterruptedException
	 * @throws TimeoutException
	 *             if a time out is set and the specified waiting time elapses
	 */
	public Event take(long timeout) throws InterruptedException,
			TimeoutException {
		Event event = null;
		lock.lock();
		try {
			while (eventQueues.size() == 0) {
				if (timeout <= 0) {
					eventQueuesCondition.await();
				} else if (!eventQueuesCondition.await(timeout,
						TimeUnit.MILLISECONDS)) {
					throw new TimeoutException("No event available within "
							+ timeout + " "
							+ TimeUnit.MILLISECONDS.toString().toLowerCase());
				}
			}
			// get event queue with highest prio
			List<Event> eventQueue = eventQueues.get(maxPrio);
			// remove first event
			event = eventQueue.remove(0);
			// if event queue is empty
			if (eventQueue.isEmpty()) {
				// remove the queue
				eventQueues.remove(maxPrio);
				// get new max. prio
				maxPrio = null;
				for (int prio : eventQueues.keySet()) {
					if (maxPrio == null || prio > maxPrio) {
						maxPrio = prio;
					}
				}
			}

			if (log.isLoggable(Level.FINE)) {
				log.log(Level.FINE, "Removed " + event);
			}
		} finally {
			lock.unlock();
		}

		List<EventQueueListener> list = getListener(event.getEventType());
		if (list != null) {
			for (EventQueueListener listener : list) {
				listener.removed(this, event);
			}
		}
		return event;
	}

	/**
	 * Adds a listener to the event queue. Listeners can be added separately for
	 * each event type.
	 * 
	 * @param listener
	 * @param eventTypes
	 */
	public synchronized void addListener(EventQueueListener listener,
			List<EventType> eventTypes) {
		for (EventType type : eventTypes) {
			List<EventQueueListener> list = getListener(type);
			if (list == null) {
				list = new CopyOnWriteArrayList<EventQueueListener>();
				listeners.put(type, list);
			}
			list.add(listener);
		}
	}

	/**
	 * Removes a listener from the event queue. Listeners can be removed
	 * separately for each event type.
	 * 
	 * @param listener
	 * @param eventTypes
	 */
	public synchronized void removeListener(EventQueueListener listener,
			List<EventType> eventTypes) {
		for (EventType type : eventTypes) {
			List<EventQueueListener> list = getListener(type);
			List<EventQueueListener> removed = new ArrayList<EventQueueListener>();
			if (list != null) {
				for (EventQueueListener entry : list) {
					if (listener == entry) {
						removed.add(entry);
					}
				}
				list.removeAll(removed);
			}
		}

	}

	/**
	 * Internal synchronized function to get the listeners of an event type.
	 * 
	 * @param type
	 * @return The listeners
	 */
	private synchronized List<EventQueueListener> getListener(EventType type) {
		return listeners.get(type);
	}

	/**
	 * Removes all events from event queue
	 */
	public void clear() {
		lock.lock();
		try {
			if (log.isLoggable(Level.FINE) && !eventQueues.isEmpty()) {
				// sort priorities
				Integer[] prios = eventQueues.keySet().toArray(
						new Integer[eventQueues.size()]);
				Arrays.sort(prios);
				// for each event queue starting with highest priority
				for (int i = prios.length - 1; i >= 0; i--) {
					// for each event
					for (Event event : eventQueues.get(prios[i])) {
						log.log(Level.FINE, "Removed " + event);
					}
				}
			}
			eventQueues.clear();
		} finally {
			lock.unlock();
		}
	}
}
