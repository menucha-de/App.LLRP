package havis.llrpservice.common.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Synchronizes the sending and receiving of events. The events must be of the
 * same type.
 * 
 * @param <TEvent>
 *            the event type
 */
public class EventPipe<TEvent> {

	public static final int NO_TIMEOUT = -1;
	public static final int RETURN_IMMEDIATELY = 0;

	private final ReentrantLock lock;
	private Condition isFired;
	private List<TEvent> events = new ArrayList<>();

	public EventPipe(ReentrantLock lock) {
		this.lock = lock;
		isFired = lock.newCondition();
	}

	/**
	 * Waits for events.
	 * 
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
	public List<TEvent> await(long timeout) throws InterruptedException, TimeoutException {
		lock.lock();
		try {
			while (events.size() == 0 && timeout != 0) {
				if (timeout < 0) {
					isFired.await();
				} else if (!isFired.await(timeout, TimeUnit.MILLISECONDS)) {
					throw new TimeoutException(String
							.format("Time out after %d ms while waiting for events", timeout));
				}
			}
			List<TEvent> ret = events;
			events = new ArrayList<>();
			return ret;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Fires an event. The event must be read with {@link #await(long)} or
	 * deleted with {@link #cancel()}.
	 * 
	 * @param event
	 */
	public void fire(TEvent event) {
		lock.lock();
		try {
			events.add(event);
			isFired.signal();
		} finally {
			lock.unlock();
		}
	}
}
