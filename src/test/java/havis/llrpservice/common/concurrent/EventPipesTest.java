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

public class EventPipesTest {

	@Test
	public void await() throws Exception {
		ExecutorService threadPool = Executors.newFixedThreadPool(2);
		final EventPipes et = new EventPipes(new ReentrantLock());

		// get current events without a waiting time
		List<Integer> intEvents = et.await(Integer.class, EventPipe.RETURN_IMMEDIATELY);
		// an empty list is received
		assertEquals(intEvents.size(), 0);
		// fire events of different types and get them without a waiting time
		et.fire(1);
		et.fire(2L);
		intEvents = et.await(Integer.class, EventPipe.RETURN_IMMEDIATELY);
		List<Long> longEvents = et.await(Long.class, EventPipe.RETURN_IMMEDIATELY);
		// the events are received
		assertEquals(intEvents.size(), 1);
		assertEquals(intEvents.get(0).intValue(), 1);
		assertEquals(longEvents.size(), 1);
		assertEquals(longEvents.get(0).longValue(), 2);

		// wait without a time out and fire events of different types
		final List<Integer> threadIntEvents1 = new ArrayList<>();
		final List<Long> threadLongEvents1 = new ArrayList<>();
		final CountDownLatch threadIntStarted1 = new CountDownLatch(1);
		final CountDownLatch threadLongStarted1 = new CountDownLatch(1);
		Future<?> futureInt1 = threadPool.submit(new Runnable() {

			@Override
			public void run() {
				try {
					threadIntStarted1.countDown();
					threadIntEvents1.addAll(et.await(Integer.class, EventPipe.NO_TIMEOUT));
				} catch (Exception e) {
					fail();
				}
			}
		});
		Future<?> futureLong1 = threadPool.submit(new Runnable() {

			@Override
			public void run() {
				try {
					threadLongStarted1.countDown();
					threadLongEvents1.addAll(et.await(Long.class, EventPipe.NO_TIMEOUT));
				} catch (Exception e) {
					fail();
				}
			}
		});
		assertTrue(threadIntStarted1.await(3, TimeUnit.SECONDS));
		assertTrue(threadLongStarted1.await(3, TimeUnit.SECONDS));
		Thread.sleep(500);
		et.fire(2);
		et.fire(3L);
		futureInt1.get(3, TimeUnit.SECONDS);
		futureLong1.get(3, TimeUnit.SECONDS);
		// the events are received
		assertEquals(threadIntEvents1.size(), 1);
		assertEquals(threadIntEvents1.get(0).intValue(), 2);
		assertEquals(threadLongEvents1.size(), 1);
		assertEquals(threadLongEvents1.get(0).intValue(), 3L);

		// wait with a time out and fire an event before the waiting time
		// elapses
		final List<Integer> threadIntEvents2 = new ArrayList<>();
		final List<Long> threadLongEvents2 = new ArrayList<>();
		final CountDownLatch threadIntStarted2 = new CountDownLatch(1);
		final CountDownLatch threadLongStarted2 = new CountDownLatch(1);
		Future<?> futureInt2 = threadPool.submit(new Runnable() {

			@Override
			public void run() {
				try {
					threadIntStarted2.countDown();
					threadIntEvents2.addAll(et.await(Integer.class, 5000));
				} catch (Exception e) {
					fail();
				}
			}
		});
		Future<?> futureLong2 = threadPool.submit(new Runnable() {

			@Override
			public void run() {
				try {
					threadLongStarted2.countDown();
					threadLongEvents2.addAll(et.await(Long.class, 5000));
				} catch (Exception e) {
					fail();
				}
			}
		});
		assertTrue(threadIntStarted2.await(3, TimeUnit.SECONDS));
		assertTrue(threadLongStarted2.await(3, TimeUnit.SECONDS));
		Thread.sleep(500);
		et.fire(2);
		et.fire(3L);
		futureInt2.get(3, TimeUnit.SECONDS);
		futureLong2.get(3, TimeUnit.SECONDS);
		// the event is received
		assertEquals(threadIntEvents1.size(), 1);
		assertEquals(threadIntEvents1.get(0).intValue(), 2);
		assertEquals(threadLongEvents1.size(), 1);
		assertEquals(threadLongEvents1.get(0).longValue(), 3);

		// wait with a time out but do not send any event
		try {
			et.await(Integer.class, 1000);
			fail();
		} catch (TimeoutException e) {
			// an exception occurs
			assertTrue(e.getMessage().contains("1000"));
		}

		threadPool.shutdown();
	}
}
