package havis.llrpservice.common.concurrent;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import org.testng.annotations.Test;

public class EventPipeTest {

	@Test
	public void await() throws Exception {
		ExecutorService threadPool = Executors.newFixedThreadPool(1);
		final EventPipe<Integer> es = new EventPipe<Integer>(new ReentrantLock());

		// get current events (RETURN_IMMEDIATELY)
		List<Integer> events = es.await(EventPipe.RETURN_IMMEDIATELY);
		// an empty list is received
		assertEquals(events.size(), 0);
		// fire an event and get it without a waiting time
		es.fire(1);
		events = es.await(EventPipe.RETURN_IMMEDIATELY);
		// the event is received
		assertEquals(events.size(), 1);
		assertEquals(events.get(0).intValue(), 1);

		// wait without a time out and fire an event
		final List<Integer> threadEvents1 = new ArrayList<>();
		final CountDownLatch threadStarted1 = new CountDownLatch(1);
		Future<?> future1 = threadPool.submit(new Runnable() {

			@Override
			public void run() {
				try {
					threadStarted1.countDown();
					threadEvents1.addAll(es.await(EventPipe.NO_TIMEOUT));
				} catch (Exception e) {

				}
			}
		});
		assertTrue(threadStarted1.await(3, TimeUnit.SECONDS));
		Thread.sleep(500);
		es.fire(2);
		future1.get(3, TimeUnit.SECONDS);
		// the event is received
		assertEquals(threadEvents1.size(), 1);
		assertEquals(threadEvents1.get(0).intValue(), 2);

		// wait with a time out and fire an event before the waiting time
		// elapses
		final List<Integer> threadEvents2 = new ArrayList<>();
		final CountDownLatch threadStarted2 = new CountDownLatch(1);
		Future<?> future2 = threadPool.submit(new Runnable() {

			@Override
			public void run() {
				try {
					threadStarted2.countDown();
					threadEvents2.addAll(es.await(5000));
				} catch (Exception e) {
					fail();
				}
			}
		});
		assertTrue(threadStarted2.await(3, TimeUnit.SECONDS));
		Thread.sleep(500);
		es.fire(2);
		future2.get(3, TimeUnit.SECONDS);
		// the event is received
		assertEquals(threadEvents1.size(), 1);
		assertEquals(threadEvents1.get(0).intValue(), 2);

		// wait with a time out but do not send any event
		try {
			es.await(1000);
			fail();
		} catch (TimeoutException e) {
			// an exception occurs
			assertTrue(e.getMessage().contains("1000"));
		}

		threadPool.shutdown();
	}
}
