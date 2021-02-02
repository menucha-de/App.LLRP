package havis.llrpservice.common.concurrent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Synchronizes the sending and receiving of events. The events can be of
 * different types.
 */
public class EventPipes {

	public static final int NO_TIMEOUT = EventPipe.NO_TIMEOUT;
	public static final int RETURN_IMMEDIATELY = EventPipe.RETURN_IMMEDIATELY;

	private final ReentrantLock lock;
	private Map<Class<?>, EventPipe<?>> eventSyncs = new HashMap<>();

	public EventPipes(ReentrantLock lock) {
		this.lock = lock;
	}

	/**
	 * Waits for events.
	 * 
	 * @param clazz
	 * @param timeout
	 *            <ul>
	 *            <li>&lt; 0: wait until an event is received (see
	 *            {@link #NO_TIMEOUT})
	 *            <li>0: return existing events immediately (see
	 *            {@link #RETURN_IMMEDIATELY})
	 *            <li>&gt; 0: wait until an event is received or the specified
	 *            waiting time elapses (in milliseconds)
	 *            </ul>
	 * @return a list of events
	 * @throws InterruptedException
	 * @throws TimeoutException
	 *             if a time out is set and the specified waiting time elapses
	 */
	public <T> List<T> await(Class<T> clazz, long timeout)
			throws InterruptedException, TimeoutException {
		lock.lock();
		try {
			return getEventSync(clazz).await(timeout);
		} finally {
			eventSyncs.remove(clazz);
			lock.unlock();
		}
	}

	/**
	 * Fires an event. The event must be read with {@link #await(Class, long)}
	 * or deleted with {@link #cancel()}.
	 * 
	 * @param event
	 */
	public <T> void fire(T event) {
		lock.lock();
		try {
			@SuppressWarnings("unchecked")
			EventPipe<T> es = getEventSync((Class<T>) event.getClass());
			es.fire(event);
		} finally {
			lock.unlock();
		}
	}

	private <T> EventPipe<T> getEventSync(Class<T> clazz) {
		@SuppressWarnings("unchecked")
		EventPipe<T> es = (EventPipe<T>) eventSyncs.get(clazz);
		if (es == null) {
			es = new EventPipe<>(lock);
			eventSyncs.put(clazz, es);
		}
		return es;
	}
}
