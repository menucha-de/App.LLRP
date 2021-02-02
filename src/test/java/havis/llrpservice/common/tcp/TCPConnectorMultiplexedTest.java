package havis.llrpservice.common.tcp;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.testng.annotations.Test;

import havis.llrpservice.common.tcp.event.TCPChannelClosedEvent;
import havis.llrpservice.common.tcp.event.TCPChannelOpenedEvent;
import havis.llrpservice.common.tcp.event.TCPDataReceivedNotifyEvent;
import havis.llrpservice.common.tcp.event.TCPDataSentEvent;
import havis.llrpservice.common.tcp.event.TCPEvent;
import mockit.Capturing;
import mockit.Deencapsulation;
import mockit.Delegate;
import mockit.Expectations;
import mockit.Invocation;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import mockit.Verifications;
import test._EnvTest;

public class TCPConnectorMultiplexedTest {

	private class ConnectionData {
		AbstractTCPConnectorMultiplexed connector;
		Future<?> future;

		SelectableChannel lastOpenedChannel;
		List<TCPEvent> tcpEvents = new ArrayList<TCPEvent>();

		CountDownLatch channelOpened = new CountDownLatch(1);
		CountDownLatch dataSent = new CountDownLatch(1);
		CountDownLatch dataReceivedNotify = new CountDownLatch(1);
		CountDownLatch channelClosed = new CountDownLatch(1);

		// objects for internal fields of the tested classes
		Logger log;
		Logger origLog;
		ConcurrentHashMap<SelectableChannel, TCPEventHandler> eventHandlers;
		ArrayList<ServerSocketChannel> serverChannels;
		HashMap<SocketChannel, ServerSocketChannel> channels;
		ArrayList<ChangeRequest> changeRequests;
	}

	/**
	 * Start a server in a separate thread and open a server channel.
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>An opening event is sent.
	 * </ul>
	 * </p>
	 * <p>
	 * Close the server channel with
	 * {@link TCPServerMultiplexed#requestClosingChannel(SelectableChannel)}.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>An closing event is sent.
	 * <li>The server thread must not stop.
	 * </ul>
	 * </p>
	 * <p>
	 * Close the server with {@link TCPServerMultiplexed#requestClosing()}.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>The server thread stops.
	 * </ul>
	 * </p>
	 * 
	 * @throws IOException
	 * @throws ExecutionException
	 * @throws TimeoutException
	 * @throws InterruptedException
	 * @throws TCPUnknownChannelException
	 * @throws TCPConnectorStoppedException
	 */
	@Test
	public void openClose1() throws Exception {
		// Start a server in a separate thread and open a server channel.
		ExecutorService threadPool = Executors.newFixedThreadPool(1);
		ConnectionData serverConnectionData = openServer(threadPool, _EnvTest.SERVER_PORT_1,
				/* awaitOpening */false);
		// An opening event is sent.
		assertTrue(serverConnectionData.channelOpened.await(3, TimeUnit.SECONDS));
		assertEquals(serverConnectionData.tcpEvents.size(), 1);
		TCPEvent event = serverConnectionData.tcpEvents.get(0);
		assertNotNull(event.getServerChannel());
		assertNull(event.getChannel());
		serverConnectionData.tcpEvents.clear();
		assertEquals(serverConnectionData.eventHandlers.size(), 1);
		assertEquals(serverConnectionData.serverChannels.size(), 1);
		// Close the server channel.
		serverConnectionData.connector.requestClosingChannel(serverConnectionData.lastOpenedChannel,
				false /* force */);
		// An closing event is sent.
		assertTrue(serverConnectionData.channelClosed.await(3, TimeUnit.SECONDS));
		assertEquals(serverConnectionData.tcpEvents.size(), 1);
		event = serverConnectionData.tcpEvents.get(0);
		assertEquals(event.getServerChannel(), serverConnectionData.lastOpenedChannel);
		assertNull(event.getChannel());
		assertTrue(serverConnectionData.eventHandlers.isEmpty());
		assertTrue(serverConnectionData.serverChannels.isEmpty());
		// The server thread must not stop
		try {
			serverConnectionData.future.get(1, TimeUnit.SECONDS);
			fail();
		} catch (TimeoutException e) {
		}
		// Close the server
		// The server thread stops.
		closeServer(serverConnectionData);
		threadPool.shutdown();
	}

	/**
	 * Start a server in a separate thread and open a server channel.
	 * <p>
	 * Start a client in a separate thread.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>An opening event for the accepted connection is sent from the server
	 * and from the client.
	 * </ul>
	 * </p>
	 * <p>
	 * Close the client.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>A closing event for the accepted connection is sent from the server
	 * and from the client.
	 * </ul>
	 * </p>
	 * <p>
	 * Close the server.
	 * </p>
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws TimeoutException
	 * @throws ExecutionException
	 * @throws TCPUnknownChannelException
	 * @throws TCPConnectorStoppedException
	 */
	@Test
	public void openClose2() throws Exception {
		// Start a server in a separate thread and open a server channel.
		ExecutorService threadPool = Executors.newFixedThreadPool(2);
		ConnectionData serverConnectionData = openServer(threadPool, _EnvTest.SERVER_PORT_1,
				/* awaitOpening */true);
		SelectableChannel openedServerChannel = serverConnectionData.lastOpenedChannel;
		// Start a client in a separate thread.
		ConnectionData clientConnectionData = openClient(threadPool, _EnvTest.SERVER_PORT_1,
				/* awaitOpening */false);
		// An opening event for the accepted connection is sent from the server
		assertTrue(serverConnectionData.channelOpened.await(3, TimeUnit.SECONDS));
		assertEquals(serverConnectionData.tcpEvents.size(), 1);
		TCPEvent event = serverConnectionData.tcpEvents.get(0);
		assertEquals(event.getServerChannel(), openedServerChannel);
		assertNotNull(event.getChannel());
		assertNotEquals(event.getChannel(), event.getServerChannel());
		serverConnectionData.tcpEvents.clear();
		assertEquals(serverConnectionData.channels.size(), 1);
		// ... and from the client.
		assertTrue(clientConnectionData.channelOpened.await(3, TimeUnit.SECONDS));
		assertEquals(clientConnectionData.tcpEvents.size(), 1);
		event = clientConnectionData.tcpEvents.get(0);
		assertNull(event.getServerChannel());
		assertNotNull(event.getChannel());
		clientConnectionData.tcpEvents.clear();
		assertEquals(clientConnectionData.eventHandlers.size(), 1);
		assertEquals(clientConnectionData.channels.size(), 1);
		// Close the client
		closeClient(clientConnectionData);
		// A closing event for the accepted connection is sent from the server
		assertTrue(serverConnectionData.channelClosed.await(3, TimeUnit.SECONDS));
		assertEquals(serverConnectionData.tcpEvents.size(), 1);
		event = serverConnectionData.tcpEvents.get(0);
		assertEquals(event.getServerChannel(), openedServerChannel);
		assertEquals(event.getChannel(), serverConnectionData.lastOpenedChannel);
		assertTrue(serverConnectionData.channels.isEmpty());
		// ... and from the client.
		assertTrue(clientConnectionData.channelClosed.await(3, TimeUnit.SECONDS));
		assertEquals(clientConnectionData.tcpEvents.size(), 1);
		event = clientConnectionData.tcpEvents.get(0);
		assertNull(event.getServerChannel());
		assertEquals(event.getChannel(), clientConnectionData.lastOpenedChannel);
		assertTrue(clientConnectionData.eventHandlers.isEmpty());
		assertTrue(clientConnectionData.channels.isEmpty());
		// Close the server
		closeServer(serverConnectionData);
		threadPool.shutdown();
	}

	/**
	 * Start a server in a separate thread and open a server channel.
	 * <p>
	 * Call
	 * {@link TCPServerMultiplexed#requestClosingChannel(SelectableChannel)}
	 * twice before the channel is closed.
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>Both close requests are processed without exceptions.
	 * </ul>
	 * </p>
	 * <p>
	 * Close the server.
	 * </p>
	 * 
	 * @throws TCPUnknownChannelException
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws TimeoutException
	 * @throws ExecutionException
	 * @throws TCPConnectorStoppedException
	 * 
	 */
	@Test
	public void close1() throws Exception {
		class Data {
			boolean sleep = true;
			Throwable t;
		}
		final Data data = new Data();

		final Semaphore waitForSelect = new Semaphore(0);
		final CountDownLatch releaseSelect = new CountDownLatch(1);

		new MockUp<SelectorWrapper>() {
			@Mock
			public int select() throws IOException {
				try {
					boolean localSleep;
					synchronized (data) {
						localSleep = data.sleep;
					}
					if (localSleep) {
						Thread.sleep(100);
					} else {
						waitForSelect.release();
						releaseSelect.await();
					}
				} catch (InterruptedException e) {
					data.t = e;
				}
				return 0;
			}
		};

		// Start a server in a separate thread and open a server channel.
		ExecutorService threadPool = Executors.newFixedThreadPool(1);
		ConnectionData serverConnectionData = openServer(threadPool, _EnvTest.SERVER_PORT_1,
				/* awaitOpening */true);
		synchronized (data) {
			data.sleep = false;
		}
		waitForSelect.acquire();
		// Call requestClosingChannel twice before the channel is closed.
		// Both close requests are processed without exceptions.
		serverConnectionData.connector.requestClosingChannel(serverConnectionData.lastOpenedChannel,
				false /* force */);
		assertEquals(serverConnectionData.changeRequests.size(), 1);
		serverConnectionData.connector.requestClosingChannel(serverConnectionData.lastOpenedChannel,
				false /* force */);
		assertEquals(serverConnectionData.changeRequests.size(), 2);
		releaseSelect.countDown();
		// Close the server
		closeServer(serverConnectionData);
		assertNull(data.t);
		threadPool.shutdown();
	}

	/**
	 * Start a server in a separate thread and open a server channel.
	 * <p>
	 * Try to close a foreign server channel.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>{@link TCPUnknownChannelException}: Unknown channel
	 * </ul>
	 * </p>
	 * <p>
	 * Close the server
	 * </p>
	 * 
	 * @param channel
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws TimeoutException
	 * @throws TCPUnknownChannelException
	 * @throws TCPConnectorStoppedException
	 */
	@Test
	public void closeError() throws Exception {
		SocketChannel channel = SocketChannel.open();
		try {
			// Start a server in a separate thread and open a server channel.
			ExecutorService threadPool = Executors.newFixedThreadPool(1);
			ConnectionData serverConnectionData = openServer(threadPool, _EnvTest.SERVER_PORT_1,
					/* awaitOpening */true);
			// Try to close a foreign server channel
			try {
				serverConnectionData.connector.requestClosingChannel(channel, false /* force */);
				fail();
			} catch (TCPUnknownChannelException e) {
				assertTrue(e.getMessage().contains("Unknown channel"));
			}
			// Close the server
			closeServer(serverConnectionData);
			threadPool.shutdown();
		} finally {
			channel.close();
		}
	}

	/**
	 * Start a server in a separate thread and open a server channel.
	 * <p>
	 * When the server channel is started, start a client in a separate thread.
	 * </p>
	 * <p>
	 * Send data from the client to the server.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>An event {@link TCPDataSentEvent} is sent from the client.
	 * </ul>
	 * </p>
	 * <p>
	 * Wait for the first {@link TCPDataReceivedNotifyEvent} from the server.
	 * Collect the received data on server side until the expected byte count is
	 * reached.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>The received data equal the sent data.
	 * </ul>
	 * </p>
	 * <p>
	 * Close the client and the server.
	 * </p>
	 * 
	 * @throws IOException
	 * @throws ExecutionException
	 * @throws TimeoutException
	 * @throws InterruptedException
	 * @throws TCPUnknownChannelException
	 * @throws TCPConnectorStoppedException
	 */
	@Test
	public void clientSendServerReceive() throws Exception {
		// Start a server in a separate thread and open a server channel.
		ExecutorService threadPool = Executors.newFixedThreadPool(2);
		int expectedByteCount = 4;
		ConnectionData serverConnectionData = openServer(threadPool, _EnvTest.SERVER_PORT_1,
				/* awaitOpening */true);
		serverConnectionData.connector.setReadBufferSize(1);
		// When the server channel is started, start a client in a separate
		// thread.
		ConnectionData clientConnectionData = openClient(threadPool, _EnvTest.SERVER_PORT_1,
				/* awaitOpening */true);
		// remove the opening event for the client connection
		assertTrue(serverConnectionData.channelOpened.await(3, TimeUnit.SECONDS));
		serverConnectionData.tcpEvents.clear();
		// Send data from the client to the server
		ByteBuffer data = ByteBuffer.allocate(expectedByteCount);
		data.putInt(0x01020304);
		data.flip();
		clientConnectionData.connector
				.requestSendingData((SocketChannel) clientConnectionData.lastOpenedChannel, data);
		// An TCPDataSentEvent is sent from the client.
		assertTrue(clientConnectionData.dataSent.await(3, TimeUnit.SECONDS));
		TCPDataSentEvent sentEvent = (TCPDataSentEvent) clientConnectionData.tcpEvents.get(0);
		assertEquals(sentEvent.getData(), data);
		assertEquals(data.position(), data.limit());
		// Wait for the first TCPDataReceivedNotifyEvent from the server.
		assertTrue(serverConnectionData.dataReceivedNotify.await(3, TimeUnit.SECONDS));
		// Collect the received data on server side until the expected byte
		// count is reached.
		data = ByteBuffer.allocate(expectedByteCount);
		while (data.remaining() > 0) {
			List<ByteBuffer> dataList = serverConnectionData.connector.awaitReceivedData(
					(SocketChannel) serverConnectionData.lastOpenedChannel, 3000);
			for (ByteBuffer d : dataList) {
				data.put(d);
			}
		}
		data.flip();
		// The received data equal the sent data.
		assertEquals(data.getInt(), 0x01020304);
		// Close the client and the server
		closeClient(clientConnectionData);
		closeServer(serverConnectionData);
		threadPool.shutdown();
	}

	/**
	 * Start a server in a separate thread and open a server channel.
	 * <p>
	 * When the server channel is started, start a client in a separate thread.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>A client channel is opened on the server.
	 * </ul>
	 * </p>
	 * <p>
	 * Send data from the server to the client using the client channel. The
	 * method
	 * {@link TCPServerMultiplexed#requestSendingData(SocketChannel, ByteBuffer)}
	 * shall be used multiple times. Close the connection directly after sending
	 * the data.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>For each call of
	 * {@link TCPServerMultiplexed#requestSendingData(SocketChannel, ByteBuffer)}
	 * an event {@link TCPDataSentEvent} is sent from the server.
	 * </ul>
	 * </p>
	 * <p>
	 * Wait for the first {@link TCPDataReceivedNotifyEvent} from the client.
	 * Collect the received data on client side until the expected byte count is
	 * reached.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>The received data equal the sent data.
	 * </ul>
	 * </p>
	 * <p>
	 * Close the client and the server.
	 * </p>
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws TCPUnknownChannelException
	 * @throws TimeoutException
	 * @throws ExecutionException
	 * @throws TCPConnectorStoppedException
	 */
	@Test
	public void serverSendClientReceive() throws Exception {
		// Start a server in a separate thread and open a server channel.
		ExecutorService threadPool = Executors.newFixedThreadPool(2);
		ConnectionData serverConnectionData = openServer(threadPool, _EnvTest.SERVER_PORT_1,
				/* awaitOpening */true);
		int messageCount = 2;
		serverConnectionData.dataSent = new CountDownLatch(messageCount);
		// When the server channel is started, start a client in a separate
		// thread.
		ConnectionData clientConnectionData = openClient(threadPool, _EnvTest.SERVER_PORT_1,
				/* awaitOpening */true);
		clientConnectionData.connector.setReadBufferSize(1);
		// A client channel is opened on the server.
		assertTrue(serverConnectionData.channelOpened.await(3, TimeUnit.SECONDS));
		// remove the opening event for the client connection
		serverConnectionData.tcpEvents.clear();
		// Send data from the server to the client using the client channel.
		// The data are send with multiple calls of requestSendingData.
		int messageSize = 4;
		ByteBuffer data1 = ByteBuffer.allocate(messageSize);
		data1.putInt(0x01020304);
		data1.flip();
		serverConnectionData.connector
				.requestSendingData((SocketChannel) serverConnectionData.lastOpenedChannel, data1);
		ByteBuffer data2 = ByteBuffer.allocate(messageSize);
		data2.putInt(0x05060708);
		data2.flip();
		serverConnectionData.connector
				.requestSendingData((SocketChannel) serverConnectionData.lastOpenedChannel, data2);
		// Close the connection directly after sending the data.
		serverConnectionData.connector.requestClosingChannel(serverConnectionData.lastOpenedChannel,
				false /* force */);
		// TCPDataSentEvent are sent from the server.
		assertTrue(serverConnectionData.dataSent.await(3, TimeUnit.SECONDS));
		TCPDataSentEvent sentEvent = (TCPDataSentEvent) serverConnectionData.tcpEvents.get(0);
		assertEquals(sentEvent.getData(), data1);
		assertEquals(data1.position(), data1.limit());
		sentEvent = (TCPDataSentEvent) serverConnectionData.tcpEvents.get(1);
		assertEquals(sentEvent.getData(), data2);
		assertEquals(data2.position(), data2.limit());
		// Wait for the first TCPDataReceivedNotifyEvent from the client.
		assertTrue(clientConnectionData.dataReceivedNotify.await(3, TimeUnit.SECONDS));
		// Collect the received data on client side until the expected byte
		// count is reached.
		ByteBuffer receivedData = ByteBuffer.allocate(messageCount * messageSize);
		try {
			while (receivedData.remaining() > 0) {
				List<ByteBuffer> dataList = clientConnectionData.connector.awaitReceivedData(
						(SocketChannel) clientConnectionData.lastOpenedChannel, 3000);
				for (ByteBuffer d : dataList) {
					receivedData.put(d);
				}
			}
		} catch (TCPUnknownChannelException e) {
			// wait for close event
			clientConnectionData.channelClosed.await(3, TimeUnit.SECONDS);
			// append pending data to received ones
			for (TCPEvent event : clientConnectionData.tcpEvents) {
				if (event instanceof TCPChannelClosedEvent) {
					TCPChannelClosedEvent closedEvent = (TCPChannelClosedEvent) event;
					receivedData.put(closedEvent.getPendingReceivedData());
					break;
				}
			}
		}
		receivedData.flip();
		// The received data equal the sent data.
		assertEquals(receivedData.getInt(), 0x01020304);
		assertEquals(receivedData.getInt(), 0x05060708);
		// Close the client and the server
		closeClient(clientConnectionData);
		closeServer(serverConnectionData);
		threadPool.shutdown();
	}

	/**
	 * Start a server in a separate thread and open a server channel.
	 * <p>
	 * Try to send data over a foreign channel.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>{@link TCPUnknownChannelException}: Unknown channel
	 * </ul>
	 * </p>
	 * <p>
	 * Close the server
	 * </p>
	 * 
	 * @param channel
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws TimeoutException
	 * @throws TCPUnknownChannelException
	 * @throws TCPConnectorStoppedException
	 */
	@Test
	public void sendError1() throws Exception {
		SocketChannel channel = SocketChannel.open();
		try {
			// Start a server in a separate thread and open a server channel.
			ExecutorService threadPool = Executors.newFixedThreadPool(1);
			ConnectionData serverConnectionData = openServer(threadPool, _EnvTest.SERVER_PORT_1,
					/* awaitOpening */true);
			// Try to send data over a foreign channel.
			try {
				serverConnectionData.connector.requestSendingData(channel, ByteBuffer.allocate(4));
				fail();
			} catch (TCPUnknownChannelException e) {
				assertTrue(e.getMessage().contains("Unknown channel"));
			}
			// Close the server.
			closeServer(serverConnectionData);
			threadPool.shutdown();
		} finally {
			channel.close();
		}
	}

	/**
	 * Start a server in a separate thread and open a server channel.
	 * <p>
	 * Try to receive data over a foreign channel.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>{@link TCPUnknownChannelException}: Unknown channel
	 * </ul>
	 * </p>
	 * <p>
	 * Close the server
	 * </p>
	 * 
	 * @param channel
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws TimeoutException
	 * @throws TCPUnknownChannelException
	 * @throws TCPConnectorStoppedException
	 */
	@Test
	public void awaitReceivedDataError1() throws Exception {
		SocketChannel channel = SocketChannel.open();
		try {
			// Start a server in a separate thread and open a server channel.
			ExecutorService threadPool = Executors.newFixedThreadPool(1);
			ConnectionData serverConnectionData = openServer(threadPool, _EnvTest.SERVER_PORT_1,
					/* awaitOpening */true);
			// Try to receive data over a foreign channel.
			try {
				serverConnectionData.connector.awaitReceivedData(channel, 3000);
				fail();
			} catch (TCPUnknownChannelException e) {
				assertTrue(e.getMessage().contains("Unknown channel"));
			}
			// Close the server.
			closeServer(serverConnectionData);
			threadPool.shutdown();
		} finally {
			channel.close();
		}
	}

	/**
	 * Start a server in a separate thread and open a server channel.
	 * <p>
	 * Try to receive data.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>{@link TCPTimeoutException}
	 * </ul>
	 * </p>
	 * <p>
	 * Close the server
	 * </p>
	 * 
	 * @param channel
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws TimeoutException
	 * @throws TCPUnknownChannelException
	 * @throws TCPConnectorStoppedException
	 */
	@Test
	public void awaitReceivedDataError2() throws Exception {
		// Start a server in a separate thread and open a server channel.
		ExecutorService threadPool = Executors.newFixedThreadPool(3);
		ConnectionData serverConnectionData = openServer(threadPool, _EnvTest.SERVER_PORT_1,
				/* awaitOpening */true);
		// When the server channel is started, start a client in a separate
		// thread.
		final ConnectionData clientConnectionData = openClient(threadPool, _EnvTest.SERVER_PORT_1,
				/* awaitOpening */true);
		// A client channel is opened on the server.
		assertTrue(serverConnectionData.channelOpened.await(3, TimeUnit.SECONDS));
		// try to receive data immediately
		List<ByteBuffer> data = serverConnectionData.connector.awaitReceivedData(
				(SocketChannel) serverConnectionData.lastOpenedChannel,
				TCPServerMultiplexed.RETURN_IMMEDIATELY);
		assertTrue(data.isEmpty());
		// try to receive data with timeout
		long endTime = System.currentTimeMillis() + 1000;
		try {
			serverConnectionData.connector.awaitReceivedData(
					(SocketChannel) serverConnectionData.lastOpenedChannel, 1000);
			fail();
		} catch (TCPTimeoutException e) {
			assertTrue(System.currentTimeMillis() >= endTime);
			assertTrue(e.getMessage().contains("Time out after 1000 ms"));
		}
		// try to receive data without a timeout
		// (send a message to release the waiting thread after some time)
		endTime = System.currentTimeMillis() + 500;
		Future<Object> future = threadPool.submit(new Callable<Object>() {

			@Override
			public Object call() throws Exception {
				Thread.sleep(500);
				clientConnectionData.connector.requestSendingData(
						(SocketChannel) clientConnectionData.lastOpenedChannel,
						ByteBuffer.allocate(4));
				return null;
			}

		});
		serverConnectionData.connector.awaitReceivedData(
				(SocketChannel) serverConnectionData.lastOpenedChannel,
				TCPServerMultiplexed.NO_TIMEOUT);
		assertTrue(System.currentTimeMillis() >= endTime);
		future.get(3000, TimeUnit.MILLISECONDS);

		// Close the client and server.
		closeClient(clientConnectionData);
		closeServer(serverConnectionData);
		threadPool.shutdown();
	}

	/**
	 * Start a server in a separate thread.
	 * <p>
	 * The selector of the server throws an exception while it is waiting for an
	 * IO operation. The closing of the selector fails too.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>The first exception is logged (waiting for IO operation).
	 * </ul>
	 * </p>
	 * 
	 * @param log
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws TimeoutException
	 * @throws TCPUnknownChannelException
	 */
	@Test
	public void runError1(final @Mocked Logger log) throws Exception {
		// The selector of the server throws an exception while it is
		// waiting for an IO operation. The closing of the selector fails too.
		final IOException selectException = new IOException();

		new MockUp<SelectorWrapper>() {
			@Mock
			int select() throws IOException {
				throw selectException;
			}

			@Mock
			void close() throws IOException {
				throw new IOException();
			}
		};

		// Start a server in a separate thread.
		ExecutorService threadPool = Executors.newFixedThreadPool(2);
		TCPServerMultiplexed server = new TCPServerMultiplexed();
		Logger origLog = Deencapsulation.getField(server, "log");
		Deencapsulation.setField(server, "log", log);
		Future<?> future = threadPool.submit(server);
		future.get(3, TimeUnit.SECONDS);
		new Verifications() {
			{
				// The first exception is logged (waiting for IO operation).
				log.log(Level.SEVERE, "Main loop stopped with exception: ", selectException);
				times = 1;
			}
		};
		threadPool.shutdown();

		Deencapsulation.setField(server, "log", origLog);
	}

	// @Capturing SelectorWrapper selectorWrapper;
	// @Mocked Logger log;

	/**
	 * Start a server in a separate thread.
	 * <p>
	 * Stop the server. The selector throws an exception while it is closing.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>The exception is logged.
	 * </ul>
	 * </p>
	 * 
	 * @param selectorWrapper
	 * @param log
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws TimeoutException
	 * @throws TCPUnknownChannelException
	 * @throws TCPConnectorStoppedException
	 */
	@Test
	public void runError2(//
			@Capturing final SelectorWrapper selectorWrapper, @Mocked final Logger log//
	) throws Exception {
		// The selector throws an exception while it is closing.
		final IOException exception = new IOException();
		new Expectations(SelectorWrapper.class) {
			{
				selectorWrapper.close();
				result = new Delegate<SelectorWrapper>() {
					@SuppressWarnings("unused")
					public void close(Invocation inv) throws IOException {
						inv.proceed();
						throw exception;
					}
				};
			}
		};
		// Start a server in a separate thread.
		ExecutorService threadPool = Executors.newFixedThreadPool(2);
		TCPServerMultiplexed server = new TCPServerMultiplexed();
		Logger origLog = Deencapsulation.getField(server, "log");
		Deencapsulation.setField(server, "log", log);
		Future<?> future = threadPool.submit(server);
		final CountDownLatch channelOpened = new CountDownLatch(1);
		final CountDownLatch channelClosed = new CountDownLatch(1);
		server.requestOpeningChannel(InetAddress.getLocalHost().getHostAddress(),
				_EnvTest.SERVER_PORT_1, new TCPEventHandler() {
					@Override
					public void channelOpened(TCPChannelOpenedEvent event) {
						channelOpened.countDown();
					}

					@Override
					public void dataSent(TCPDataSentEvent event) {
					}

					@Override
					public void dataReceived(TCPDataReceivedNotifyEvent event) {
					}

					@Override
					public void channelClosed(TCPChannelClosedEvent event) {
						channelClosed.countDown();
					}
				});
		assertTrue(channelOpened.await(3, TimeUnit.SECONDS));
		// Stop the server.
		server.requestClosing();
		future.get(3, TimeUnit.SECONDS);
		threadPool.shutdown();
		new Verifications() {
			{
				// The exception is logged.
				log.log(Level.SEVERE, "Main loop stopped with exception: ", exception);
				times = 1;
			}
		};
		threadPool.shutdown();

		Deencapsulation.setField(server, "log", origLog);
	}

	/**
	 * Start a server in a separate thread. The opening of a server channel
	 * fails because the change request cannot be queued (exception in
	 * {@link TCPServerMultiplexed#enqueueChangeRequest(ChangeRequest)}).
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>The server removes the registered event handler.
	 * </ul>
	 * </p>
	 * 
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws TCPUnknownChannelException
	 * @throws ExecutionException
	 * @throws TimeoutException
	 * @throws TCPConnectorStoppedException
	 */
	@Test
	public void serverEnqueueChangeRequestError() throws Exception {
		final TCPConnectorStoppedException exception = new TCPConnectorStoppedException("huhu");

		new MockUp<TCPServerMultiplexed>() {
			@Mock
			void enqueueChangeRequest(ChangeRequest changeRequest)
					throws TCPConnectorStoppedException {
				throw exception;
			}
		};

		ExecutorService threadPool = Executors.newFixedThreadPool(2);
		TCPServerMultiplexed server = new TCPServerMultiplexed();
		Map<SelectableChannel, TCPEventHandler> eventHandlers = new ConcurrentHashMap<SelectableChannel, TCPEventHandler>();
		Deencapsulation.setField(server, "eventHandlers", eventHandlers);
		Future<?> future = threadPool.submit(server);
		try {
			server.requestOpeningChannel(InetAddress.getLocalHost().getHostAddress(),
					_EnvTest.SERVER_PORT_1, new TCPEventHandler() {
						@Override
						public void channelOpened(TCPChannelOpenedEvent event) {
						}

						@Override
						public void dataSent(TCPDataSentEvent event) {
						}

						@Override
						public void dataReceived(TCPDataReceivedNotifyEvent event) {
						}

						@Override
						public void channelClosed(TCPChannelClosedEvent event) {
						}
					});
			fail();
		} catch (Exception e) {
			assertTrue(e.getMessage().contains("huhu"));
		}
		// The server removes the registered event handler.
		assertTrue(eventHandlers.isEmpty());
		// Stop the server.
		server.requestClosing();
		future.get(3, TimeUnit.SECONDS);
		threadPool.shutdown();
	}

	/**
	 * Start a client in a separate thread. The opening of a channel fails
	 * because the change request cannot be queued (
	 * {@link TCPClientMultiplexed#enqueueChangeRequest(ChangeRequest)}).
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>The client removes the registered event handler.
	 * </ul>
	 * </p>
	 * 
	 * @param clientMock
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws TCPUnknownChannelException
	 * @throws ExecutionException
	 * @throws TimeoutException
	 * @throws TCPConnectorStoppedException
	 */
	@Test
	public void clientEnqueueChangeRequestError() throws Exception {
		final TCPConnectorStoppedException exception = new TCPConnectorStoppedException("huhu");

		new MockUp<TCPClientMultiplexed>() {
			@Mock
			void enqueueChangeRequest(ChangeRequest changeRequest)
					throws TCPConnectorStoppedException {
				throw exception;
			}
		};

		ExecutorService threadPool = Executors.newFixedThreadPool(2);
		TCPClientMultiplexed client = new TCPClientMultiplexed();
		Map<SelectableChannel, TCPEventHandler> eventHandlers = new ConcurrentHashMap<SelectableChannel, TCPEventHandler>();
		Deencapsulation.setField(client, "eventHandlers", eventHandlers);
		Future<?> future = threadPool.submit(client);
		try {
			client.requestOpeningChannel(InetAddress.getLocalHost().getHostAddress(),
					_EnvTest.SERVER_PORT_1, new TCPEventHandler() {
						@Override
						public void channelOpened(TCPChannelOpenedEvent event) {
						}

						@Override
						public void dataSent(TCPDataSentEvent event) {
						}

						@Override
						public void dataReceived(TCPDataReceivedNotifyEvent event) {
						}

						@Override
						public void channelClosed(TCPChannelClosedEvent event) {
						}
					});
			fail();
		} catch (Exception e) {
			assertTrue(e.getMessage().contains("huhu"));
		}
		// The client removes the registered event handler.
		assertTrue(eventHandlers.isEmpty());
		// Stop the server.
		client.requestClosing();
		future.get(3, TimeUnit.SECONDS);
		threadPool.shutdown();
	}

	/**
	 * Start a server in a separate thread.
	 * <p>
	 * The registration of the server channel with the selector fails.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>The exception is delivered with the close event.
	 * <li>The server channel is reported with the close event.
	 * </ul>
	 * </p>
	 * <p>
	 * Close the server.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>An open event has not been sent for the server channel.
	 * </ul>
	 * </p>
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws TimeoutException
	 * @throws TCPUnknownChannelException
	 */
	@Test
	public void registerError() throws Exception {
		// The registration of the server channel with the selector fails.
		final ClosedChannelException exception = new ClosedChannelException();

		new MockUp<SelectableChannel>() {
			@Mock
			SelectionKey register(Selector sel, int ops) throws ClosedChannelException {
				throw exception;
			}
		};

		// Start a server in a separate thread.
		ExecutorService threadPool = Executors.newFixedThreadPool(2);
		ConnectionData serverConnectionData = openServer(threadPool, _EnvTest.SERVER_PORT_1,
				/* awaitOpening */false);
		// The exception is delivered with the close event.
		assertTrue(serverConnectionData.channelClosed.await(3, TimeUnit.SECONDS));
		TCPChannelClosedEvent closedEvent = (TCPChannelClosedEvent) serverConnectionData.tcpEvents
				.get(0);
		assertTrue(closedEvent.getException() == exception);
		// The server channel is reported with the close event.
		assertNotNull(closedEvent.getServerChannel());
		assertNull(closedEvent.getChannel());
		assertTrue(serverConnectionData.serverChannels.isEmpty());
		// Close the server.
		closeServer(serverConnectionData);
		// An open event has not been sent for the server channel.
		assertEquals(serverConnectionData.channelOpened.getCount(), 1);
		threadPool.shutdown();
	}

	/**
	 * Start a server in a separate thread and open a server channel.
	 * <p>
	 * When the server channel is started, start a client in a separate thread.
	 * </p>
	 * <p>
	 * The accepting of the client channel fails on the server side.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>The exception is delivered with the close event.
	 * <li>The server channel is reported with the close event.
	 * </ul>
	 * </p>
	 * <p>
	 * Close the server.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>An open event has not been sent for the client channel on the server
	 * side.
	 * </ul>
	 * </p>
	 * 
	 * @param selector
	 * @param sendReceiveLog
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws TimeoutException
	 * @throws TCPUnknownChannelException
	 */
	@Test
	public void acceptError1() throws Exception {
		// The accepting of the client channel fails on the server side.
		final IOException exception = new IOException();

		new MockUp<ServerSocketChannelWrapper>() {
			@Mock
			SocketChannel accept() throws IOException {
				throw exception;
			}
		};

		// Start a server in a separate thread and open a server channel.
		ExecutorService threadPool = Executors.newFixedThreadPool(2);
		ConnectionData serverConnectionData = openServer(threadPool, _EnvTest.SERVER_PORT_1,
				/* awaitOpening */true);
		// When the server channel is started, start a client in a separate
		// thread.
		ConnectionData clientConnectionData = openClient(threadPool, _EnvTest.SERVER_PORT_1,
				/* awaitOpening */false);
		// The exception is delivered with the close event.
		assertTrue(serverConnectionData.channelClosed.await(3, TimeUnit.SECONDS));
		TCPChannelClosedEvent closedEvent = (TCPChannelClosedEvent) serverConnectionData.tcpEvents
				.get(0);
		assertTrue(closedEvent.getException() == exception);
		// The server channel is reported with the close event.
		assertEquals(closedEvent.getServerChannel(), serverConnectionData.lastOpenedChannel);
		assertNull(closedEvent.getChannel());
		assertTrue(serverConnectionData.channels.isEmpty());
		// Close the client and server
		closeClient(clientConnectionData);
		closeServer(serverConnectionData);
		// An open event has not been sent for the client channel on the server
		// side.
		assertEquals(serverConnectionData.channelOpened.getCount(), 1);
		threadPool.shutdown();
	}

	/**
	 * Start a client in a separate thread and open a channel.
	 * <p>
	 * The finishing of the connection fails on the client side.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>The exception is delivered with the close event for the client
	 * channel.
	 * <li>The client channel is reported with the close event.
	 * </ul>
	 * </p>
	 * <p>
	 * Close the client.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>An open event has not been sent for the client channel.
	 * </ul>
	 * </p>
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws TimeoutException
	 * @throws TCPUnknownChannelException
	 * @throws TCPConnectorStoppedException
	 */
	@Test
	public void finishConnectError1() throws Exception {
		// The finishing of the connection fails on the client side.
		final IOException exception = new IOException();

		new MockUp<SocketChannelWrapper>() {
			@Mock
			boolean finishConnect() throws IOException {
				throw exception;
			}
		};

		// Start a client in a separate thread.
		ExecutorService threadPool = Executors.newFixedThreadPool(2);
		ConnectionData clientConnectionData = openClient(threadPool, _EnvTest.SERVER_PORT_1,
				/* awaitOpening */false);
		// The exception is delivered with the close event for the
		// client channel.
		assertTrue(clientConnectionData.channelClosed.await(3, TimeUnit.SECONDS));
		TCPChannelClosedEvent closedEvent = (TCPChannelClosedEvent) clientConnectionData.tcpEvents
				.get(0);
		assertTrue(closedEvent.getException() == exception);
		// The client channel is reported with the close event.
		assertNull(closedEvent.getServerChannel());
		assertNotNull(closedEvent.getChannel());
		assertTrue(clientConnectionData.channels.isEmpty());
		// Close the client
		closeClient(clientConnectionData);
		// An open event has not been sent for the client channel.
		assertEquals(clientConnectionData.channelOpened.getCount(), 1);
		threadPool.shutdown();
	}

	/**
	 * Start a server in a separate thread and open a server channel.
	 * <p>
	 * When the server channel is started, start a client in a separate thread.
	 * </p>
	 * <p>
	 * Send data from the client to the server.
	 * </p>
	 * <p>
	 * The reading of data fails on the server side.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>The exception is delivered with the close event.
	 * <li>The client channel is reported with the close event.
	 * </ul>
	 * </p>
	 * <p>
	 * Close the client and the server.
	 * </p>
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws TimeoutException
	 * @throws TCPUnknownChannelException
	 * @throws TCPConnectorStoppedException
	 */
	@Test
	public void readError1() throws Exception {
		// The reading of data fails on the server side.
		final IOException exception = new IOException();

		new MockUp<SocketChannelWrapper>() {
			@Mock
			int read(ByteBuffer dst) throws IOException {
				throw exception;
			}
		};

		// Start a server in a separate thread.
		ExecutorService threadPool = Executors.newFixedThreadPool(2);
		int expectedByteCount = 4;
		ConnectionData serverConnectionData = openServer(threadPool, _EnvTest.SERVER_PORT_1,
				/* awaitOpening */true);
		SelectableChannel openedServerChannel = serverConnectionData.lastOpenedChannel;
		// When the server channel is started, start a client in a separate
		// thread.
		ConnectionData clientConnectionData = openClient(threadPool, _EnvTest.SERVER_PORT_1,
				/* awaitOpening */true);
		// remove the opening event for the client connection
		assertTrue(serverConnectionData.channelOpened.await(3, TimeUnit.SECONDS));
		serverConnectionData.tcpEvents.clear();
		// Send data from the client to the server
		ByteBuffer data = ByteBuffer.allocate(expectedByteCount);
		data.putInt(0x01020304);
		data.flip();
		clientConnectionData.connector
				.requestSendingData((SocketChannel) clientConnectionData.lastOpenedChannel, data);
		// The exception is delivered with the close event.
		assertTrue(serverConnectionData.channelClosed.await(3, TimeUnit.SECONDS));
		TCPChannelClosedEvent closedEvent = (TCPChannelClosedEvent) serverConnectionData.tcpEvents
				.get(0);
		assertTrue(closedEvent.getException() == exception);
		// The client channel is reported with the close event.
		assertEquals(closedEvent.getServerChannel(), openedServerChannel);
		assertEquals(closedEvent.getChannel(), serverConnectionData.lastOpenedChannel);
		assertTrue(serverConnectionData.channels.isEmpty());
		// Close the client and the server.
		closeClient(clientConnectionData);
		closeServer(serverConnectionData);
		threadPool.shutdown();
	}

	/**
	 * Start a server in a separate thread and open a server channel.
	 * <p>
	 * When the server channel is started, start a client in a separate thread.
	 * </p>
	 * <p>
	 * Send data from the client to the server.
	 * </p>
	 * <p>
	 * The writing of data fails on the client side.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>The exception is delivered with the close event.
	 * <li>The client channel is reported with the close event.
	 * <li>The data which could not be written are reported with the close
	 * event.
	 * </ul>
	 * </p>
	 * <p>
	 * Close the client and the server.
	 * </p>
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws TimeoutException
	 * @throws TCPUnknownChannelException
	 * @throws TCPConnectorStoppedException
	 */
	@Test
	public void writeError1() throws Exception {
		// The writing of data fails on the client side.
		final IOException exception = new IOException();
		new MockUp<SocketChannelWrapper>() {
			@Mock
			int write(ByteBuffer src) throws IOException {
				throw exception;
			}
		};

		// Start a server in a separate thread.
		ExecutorService threadPool = Executors.newFixedThreadPool(2);
		int expectedByteCount = 4;
		ConnectionData serverConnectionData = openServer(threadPool, _EnvTest.SERVER_PORT_1,
				/* awaitOpening */true);
		// When the server channel is started, start a client in a separate
		// thread.
		ConnectionData clientConnectionData = openClient(threadPool, _EnvTest.SERVER_PORT_1,
				/* awaitOpening */true);
		// Send data from the client to the server
		ByteBuffer data = ByteBuffer.allocate(expectedByteCount);
		data.putInt(0x01020304);
		data.flip();
		clientConnectionData.connector
				.requestSendingData((SocketChannel) clientConnectionData.lastOpenedChannel, data);
		// The exception is delivered with the close event.
		assertTrue(clientConnectionData.channelClosed.await(3, TimeUnit.SECONDS));
		TCPChannelClosedEvent closedEvent = (TCPChannelClosedEvent) clientConnectionData.tcpEvents
				.get(0);
		assertTrue(closedEvent.getException() == exception);
		// The client channel is reported with the close event.
		assertNull(closedEvent.getServerChannel());
		assertEquals(closedEvent.getChannel(), clientConnectionData.lastOpenedChannel);
		// The data which could not be written are reported with the close
		// event.
		ByteBuffer pendingSendingData = closedEvent.getPendingSendingData();
		assertEquals(pendingSendingData.getInt(), 0x01020304);
		assertTrue(clientConnectionData.channels.isEmpty());
		// Close the client and the server.
		closeClient(clientConnectionData);
		closeServer(serverConnectionData);
		threadPool.shutdown();
	}

	private ConnectionData createConnectionData(AbstractTCPConnectorMultiplexed connector,
			ExecutorService threadPool) {
		ConnectionData ret = new ConnectionData();
		ret.connector = connector;
		ret.log = Logger.getLogger("testing");
		ret.origLog = Deencapsulation.getField(connector, "log");
		Deencapsulation.setField(connector, "log", ret.log);
		ret.eventHandlers = new ConcurrentHashMap<SelectableChannel, TCPEventHandler>();
		Deencapsulation.setField(connector, "eventHandlers", ret.eventHandlers);
		ret.serverChannels = new ArrayList<ServerSocketChannel>();
		Deencapsulation.setField(connector, "serverChannels", ret.serverChannels);
		ret.channels = new HashMap<SocketChannel, ServerSocketChannel>();
		Deencapsulation.setField(connector, "channels", ret.channels);
		ret.changeRequests = new ArrayList<ChangeRequest>();
		Deencapsulation.setField(connector, "changeRequests", ret.changeRequests);
		ret.future = threadPool.submit(connector);
		return ret;
	}

	private ConnectionData openServer(ExecutorService threadPool, int port, boolean awaitOpening)
			throws IOException, InterruptedException, TCPUnknownChannelException,
			TCPConnectorStoppedException {
		TCPServerMultiplexed server = new TCPServerMultiplexed();
		final ConnectionData ret = createConnectionData(server, threadPool);
		server.requestOpeningChannel(InetAddress.getLocalHost().getHostAddress(), port,
				new TCPEventHandler() {
					@Override
					public void channelOpened(TCPChannelOpenedEvent event) {
						ret.tcpEvents.add(event);
						ret.lastOpenedChannel = event.getChannel() == null
								? event.getServerChannel() : event.getChannel();
						ret.channelOpened.countDown();
					}

					@Override
					public void dataSent(TCPDataSentEvent event) {
						ret.tcpEvents.add(event);
						ret.dataSent.countDown();
					}

					@Override
					public void dataReceived(TCPDataReceivedNotifyEvent event) {
						ret.tcpEvents.add(event);
						ret.dataReceivedNotify.countDown();
					}

					@Override
					public void channelClosed(TCPChannelClosedEvent event) {
						ret.tcpEvents.add(event);
						ret.channelClosed.countDown();
					}
				});
		if (awaitOpening) {
			assertTrue(ret.channelOpened.await(3, TimeUnit.SECONDS));
			ret.tcpEvents.clear();
			ret.channelOpened = new CountDownLatch(1);
		}
		return ret;
	}

	private ConnectionData openClient(ExecutorService threadPool, int port, boolean awaitOpening)
			throws IOException, InterruptedException, TCPUnknownChannelException,
			TCPConnectorStoppedException {
		TCPClientMultiplexed client = new TCPClientMultiplexed();
		final ConnectionData ret = createConnectionData(client, threadPool);
		client.requestOpeningChannel(InetAddress.getLocalHost().getHostAddress(), port,
				new TCPEventHandler() {
					@Override
					public void channelOpened(TCPChannelOpenedEvent event) {
						ret.tcpEvents.add(event);
						ret.lastOpenedChannel = event.getChannel();
						ret.channelOpened.countDown();
					}

					@Override
					public void dataSent(TCPDataSentEvent event) {
						ret.tcpEvents.add(event);
						ret.dataSent.countDown();
					}

					@Override
					public void dataReceived(TCPDataReceivedNotifyEvent event) {
						ret.tcpEvents.add(event);
						ret.dataReceivedNotify.countDown();
					}

					@Override
					public void channelClosed(TCPChannelClosedEvent event) {
						ret.tcpEvents.add(event);
						ret.channelClosed.countDown();
					}
				});
		if (awaitOpening) {
			assertTrue(ret.channelOpened.await(3, TimeUnit.SECONDS));
			ret.tcpEvents.clear();
			ret.channelOpened = new CountDownLatch(1);
		}
		return ret;
	}

	private void closeClient(ConnectionData clientConnectionData)
			throws InterruptedException, ExecutionException, TimeoutException {
		clientConnectionData.connector.requestClosing();
		// the client thread stops
		clientConnectionData.future.get(3, TimeUnit.SECONDS);

		Deencapsulation.setField(clientConnectionData.connector, "log",
				clientConnectionData.origLog);
	}

	private void closeServer(ConnectionData serverConnectionData)
			throws InterruptedException, ExecutionException, TimeoutException {
		serverConnectionData.connector.requestClosing();
		// the server thread stops
		serverConnectionData.future.get(3, TimeUnit.SECONDS);

		Deencapsulation.setField(serverConnectionData.connector, "log",
				serverConnectionData.origLog);
	}
}
