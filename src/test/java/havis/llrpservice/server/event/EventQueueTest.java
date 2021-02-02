package havis.llrpservice.server.event;

import havis.llrpservice.data.message.GetSupportedVersion;
import havis.llrpservice.data.message.Message;
import havis.llrpservice.data.message.MessageHeader;
import havis.llrpservice.data.message.ProtocolVersion;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import org.testng.Assert;
import org.testng.annotations.Test;

public class EventQueueTest {

	public class QueueTestListener implements EventQueueListener {
		private boolean addedCalled = false;
		private boolean removedCalled = false;

		@Override
		public void added(EventQueue src, Event event) {
			addedCalled = true;
		}

		@Override
		public void removed(EventQueue src, Event event) {
			removedCalled = true;
		}
	}

	@Test
	public void events() throws InterruptedException, TimeoutException {
		EventQueue queue = new EventQueue();

		Message msg = new GetSupportedVersion(new MessageHeader((byte) 0,
				ProtocolVersion.LLRP_V1_0_1, 1));
		// create events with different priorities and put them to queue
		LLRPMessageEvent event1 = new LLRPMessageEvent(msg);
		queue.put(event1);
		LLRPMessageEvent event2 = new LLRPMessageEvent(msg);
		queue.put(event2, 1 /* prio */);
		LLRPMessageEvent event3 = new LLRPMessageEvent(msg);
		queue.put(event3, -1 /* prio */);
		// get events
		LLRPMessageEvent compare = (LLRPMessageEvent) queue.take(500);
		Assert.assertEquals(event2, compare);
		compare = (LLRPMessageEvent) queue.take(500);
		Assert.assertEquals(event1, compare);
		compare = (LLRPMessageEvent) queue.take(500);
		Assert.assertEquals(event3, compare);
		// Another take will fail (no events in queue)
		try {
			queue.take(500);
			Assert.fail();
		} catch (TimeoutException e) {
			Assert.assertTrue(e.getMessage().contains(
					"No event available within 500 milliseconds"));
		}
	}

	@Test
	public void listeners() throws InterruptedException, TimeoutException {
		EventQueue queue = new EventQueue();
		Message msg = new GetSupportedVersion(new MessageHeader((byte) 0,
				ProtocolVersion.LLRP_V1_0_1, 1));
		LLRPMessageEvent event = new LLRPMessageEvent(msg);
		QueueTestListener listener = new QueueTestListener();
		// Put event in queue (without listeners)
		queue.put(event);
		Assert.assertFalse(listener.addedCalled);
		queue.take(500);
		Assert.assertFalse(listener.removedCalled);
		// Put event in queue (with listeners)
		List<EventType> list = new ArrayList<EventType>();
		list.add(EventType.LLRP_MESSAGE);
		listener.addedCalled = false;
		listener.removedCalled = false;
		queue.addListener(listener, list);
		queue.put(event);
		Assert.assertTrue(listener.addedCalled);
		queue.take(500);
		Assert.assertTrue(listener.removedCalled);
		// Remove Listeners and check, if events won't be fired
		queue.removeListener(listener, list);
		listener.addedCalled = false;
		listener.removedCalled = false;
		queue.put(event);
		Assert.assertFalse(listener.addedCalled);
		queue.take(500);
		Assert.assertFalse(listener.removedCalled);
	}

	@Test
	public void multiThreads() throws InterruptedException, ExecutionException {
		int threads = 100;
		int poolSize = 4;
		ExecutorService pool = Executors.newFixedThreadPool(poolSize);
		List<Future<Long>> futuresProducers = new ArrayList<Future<Long>>(
				threads);
		List<Future<Long>> futuresConsumers = new ArrayList<Future<Long>>(
				threads);
		List<Long> elementsProducers = new ArrayList<>();
		List<Long> elementsConsumers = new ArrayList<>();

		EventQueue queue = new EventQueue();

		for (int i = 0; i < threads; i++) {
			Message msg = new GetSupportedVersion(new MessageHeader((byte) 0,
					ProtocolVersion.LLRP_V1_0_1, i));
			futuresProducers.add(pool.submit(new MessageProducer(msg, queue)));
			futuresConsumers.add(pool.submit(new MessageConsumer(queue)));
		}

		// wait for end of all threads and check, if all events with correct id
		// was taken
		for (Future<Long> future : futuresProducers) {
			elementsProducers.add(future.get());
		}
		Assert.assertEquals(elementsProducers.size(), threads);
		for (Future<Long> future : futuresConsumers) {
			elementsConsumers.add(future.get());
		}
		Assert.assertEquals(elementsConsumers.size(), threads);
		pool.shutdown();
	}

	public class MessageProducer implements Callable<Long> {
		private final Message msg;
		private final EventQueue queue;

		public MessageProducer(Message msg, EventQueue queue) {
			this.msg = msg;
			this.queue = queue;
		}

		@Override
		public Long call() throws Exception {
			LLRPMessageEvent event = new LLRPMessageEvent(msg);
			int randSleep = new Random().nextInt(50);
			Thread.sleep(randSleep);
			queue.put(event);
			return event.getMessage().getMessageHeader().getId();
		}
	}

	public class MessageConsumer implements Callable<Long> {

		private final EventQueue queue;

		public MessageConsumer(EventQueue queue) {
			this.queue = queue;
		}

		@Override
		public Long call() throws Exception {
			int randSleep = new Random().nextInt(25);
			Thread.sleep(randSleep);
			LLRPMessageEvent event = (LLRPMessageEvent) queue.take(500);
			return event.getMessage().getMessageHeader().getId();
		}
	}

}
