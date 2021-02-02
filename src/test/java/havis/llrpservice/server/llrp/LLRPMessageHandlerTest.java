package havis.llrpservice.server.llrp;

import havis.llrpservice.common.io.PathHandler;
import havis.llrpservice.common.io.XMLFile;
import havis.llrpservice.common.tcp.TCPClientMultiplexed;
import havis.llrpservice.common.tcp.TCPServerMultiplexed;
import havis.llrpservice.csc.llrp.LLRPClientMultiplexed;
import havis.llrpservice.csc.llrp.LLRPEventHandler;
import havis.llrpservice.csc.llrp.LLRPServerMultiplexed;
import havis.llrpservice.csc.llrp.LLRPUnknownChannelException;
import havis.llrpservice.csc.llrp.event.LLRPChannelClosedEvent;
import havis.llrpservice.csc.llrp.event.LLRPChannelOpenedEvent;
import havis.llrpservice.csc.llrp.event.LLRPDataReceivedNotifyEvent;
import havis.llrpservice.csc.llrp.event.LLRPDataSentEvent;
import havis.llrpservice.data.message.GetReaderCapabilities;
import havis.llrpservice.data.message.GetReaderCapabilitiesRequestedData;
import havis.llrpservice.data.message.GetReaderCapabilitiesResponse;
import havis.llrpservice.data.message.Message;
import havis.llrpservice.data.message.MessageHeader;
import havis.llrpservice.data.message.ProtocolVersion;
import havis.llrpservice.data.message.ReaderEventNotification;
import havis.llrpservice.data.message.parameter.C1G2LLRPCapabilities;
import havis.llrpservice.data.message.parameter.ConnectionAttemptEventStatusType;
import havis.llrpservice.data.message.parameter.LLRPStatus;
import havis.llrpservice.data.message.parameter.LLRPStatusCode;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.data.message.serializer.ByteBufferSerializer;
import havis.llrpservice.server.configuration.ServerConfiguration;
import havis.llrpservice.server.configuration.ServerInstanceConfiguration;
import havis.llrpservice.server.event.Event;
import havis.llrpservice.server.event.EventQueue;
import havis.llrpservice.server.event.EventQueueListener;
import havis.llrpservice.server.event.EventType;
import havis.llrpservice.server.persistence._FileHelperTest;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;
import havis.util.platform.Platform;

import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import mockit.Capturing;
import mockit.Delegate;
import mockit.Mocked;
import mockit.NonStrictExpectations;
import mockit.Verifications;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class LLRPMessageHandlerTest {

	private class LLRPTestListener implements LLRPMessageHandlerListener {

		private CountDownLatch clientOpened = new CountDownLatch(1);
		private CountDownLatch clientClosed = new CountDownLatch(1);

		public boolean awaitClientOpening() throws InterruptedException {
			return clientOpened.await(timeout, timeUnit);
		}

		public boolean awaitClientClosing() throws InterruptedException {
			return clientClosed.await(timeout, timeUnit);
		}

		@Override
		public void dataSent(LLRPDataSentEvent event) {
		}

		@Override
		public void opened(LLRPChannelOpenedEvent event) {
			clientOpened.countDown();
		}

		@Override
		public void closed(LLRPChannelClosedEvent event) {
			clientClosed.countDown();
		}

		@Override
		public void opened() {
		}

		@Override
		public void closed(Throwable t) {
		}
	}

	private class LLRPTestQueueListener implements EventQueueListener {
		private boolean eventAdded = false;

		@Override
		public void added(EventQueue src, Event event) {
			eventAdded = true;
		}

		@Override
		public void removed(EventQueue src, Event event) {
		}

	}

	private class TestLLRPEventHandler implements LLRPEventHandler {
		private SocketChannel channel;
		private CountDownLatch channelOpened = new CountDownLatch(1);
		private CountDownLatch channelClosed = new CountDownLatch(1);
		private LLRPChannelClosedEvent closeEvent;
		private CountDownLatch dataReceived = new CountDownLatch(1);

		public boolean awaitClientOpening() throws InterruptedException {
			return channelOpened.await(timeout, timeUnit);
		}

		public boolean awaitClientClosing() throws InterruptedException {
			return channelClosed.await(timeout, timeUnit);
		}

		public boolean awaitDataReceived() throws InterruptedException {
			return dataReceived.await(timeout, timeUnit);
		}

		@Override
		public void channelOpened(LLRPChannelOpenedEvent event) {
			channel = event.getChannel();
			if (channel != null) {
				channelOpened.countDown();
			}
		}

		@Override
		public void dataSent(LLRPDataSentEvent event) {
		}

		@Override
		public void dataReceived(LLRPDataReceivedNotifyEvent event) {
			dataReceived.countDown();
		}

		@Override
		public void channelClosed(LLRPChannelClosedEvent event) {
			closeEvent = event;
			channelClosed.countDown();
		}

	}

	private static final Path SERVER_INIT_PATH = new PathHandler()
			.toAbsolutePath("havis/llrpservice/server/llrp/LLRPServerConfiguration.xml");
	private static final Path INIT_PATH = new PathHandler()
			.toAbsolutePath("havis/llrpservice/server/llrp/LLRPServerInstanceConfiguration.xml");

	private static final Path BASE_PATH = new PathHandler()
			.toAbsolutePath("havis/llrpservice/server/llrp");
	private static final Path BASE_OUTPUT_PATH = BASE_PATH.resolve("../../../../../output")
			.toAbsolutePath();
	private static final Path SERVER_LATEST_PATH = BASE_OUTPUT_PATH.resolve("config/newConfig.xml");
	private static final Path LATEST_PATH = BASE_OUTPUT_PATH
			.resolve("config/newInstanceConfig.xml");

	private long timeout = 1000;
	private TimeUnit timeUnit = TimeUnit.MILLISECONDS;

	// Create ServerConfigFile
	private XMLFile<LLRPServerConfigurationType> serverConfigFile;
	private ServerConfiguration serverConf;

	// Create ServerInstanceConfiguration file
	private XMLFile<LLRPServerInstanceConfigurationType> instanceConfigFile;
	// create ServerInstanceConfiguration ServerInstanceConfiguration
	private ServerInstanceConfiguration instanceConfig;

	private EventQueue queue = new EventQueue();

	private Message capsResponseClient;
	private Message getCaps;

	private LLRPClientMultiplexed llrpClient;
	private TCPServerMultiplexed tcpServerLLRP;

	@BeforeClass
	public void init() throws Exception {
		serverConfigFile = new XMLFile<>(LLRPServerConfigurationType.class, SERVER_INIT_PATH,
				SERVER_LATEST_PATH);
		serverConf = new ServerConfiguration(serverConfigFile);
		serverConf.open();

		instanceConfigFile = new XMLFile<>(LLRPServerInstanceConfigurationType.class, INIT_PATH,
				LATEST_PATH);

		instanceConfig = new ServerInstanceConfiguration(serverConf, instanceConfigFile);
		instanceConfig.open();

		getCaps = new GetReaderCapabilities(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_0_1, 10),
				GetReaderCapabilitiesRequestedData.C1G2_LLRP_CAPABILITIES /*
																			 * Air
																			 * Interface
																			 * capabilities
																			 */);

		capsResponseClient = new GetReaderCapabilitiesResponse(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_0_1, 10),
				new LLRPStatus(new TLVParameterHeader((byte) 0), LLRPStatusCode.M_SUCCESS, ""),
				new C1G2LLRPCapabilities(new TLVParameterHeader((byte) 0), false, false, false,
						false, false, false, 0));

		TCPClientMultiplexed tcpClient = new TCPClientMultiplexed();
		ExecutorService threadPool = Executors.newFixedThreadPool(2);
		threadPool.submit(tcpClient);
		llrpClient = new LLRPClientMultiplexed(tcpClient);

		tcpServerLLRP = new TCPServerMultiplexed();
		tcpServerLLRP.setReadBufferSize(2048);
		threadPool.submit(tcpServerLLRP);
	}

	@Test
	public void clientServerCommunication(@Capturing final Platform systemController)
			throws Exception {
		TestLLRPEventHandler successHandler = new TestLLRPEventHandler();
		TestLLRPEventHandler deniedHandler = new TestLLRPEventHandler();
		LLRPTestQueueListener queueListener = new LLRPTestQueueListener();
		LLRPTestListener clientListener = new LLRPTestListener();
		LLRPMessageHandler llrpMessageHandler = new LLRPMessageHandler(serverConf, instanceConfig,
				queue, tcpServerLLRP);

		// Start TCP Server
		ExecutorService threadPool = Executors.newFixedThreadPool(5);

		// Add listener to queue
		queue.addListener(queueListener, new ArrayList<>(Arrays.asList(EventType.LLRP_MESSAGE)));

		Assert.assertFalse(queueListener.eventAdded);
		// Start and open LLRPMessageHandler
		llrpMessageHandler.open(systemController, threadPool);

		// Add listener to message handler
		llrpMessageHandler.addListener(clientListener);

		// Open LLRP client
		llrpClient.requestOpeningChannel("localhost", 4321, successHandler);
		// Wait for client opened
		if (!successHandler.awaitClientOpening()) {
			Assert.fail();
		}
		if (!clientListener.awaitClientOpening()) {
			Assert.fail();
		}

		// Reopen test with existing connection
		llrpMessageHandler.close();
		llrpMessageHandler.open(systemController, threadPool);

		successHandler = new TestLLRPEventHandler();
		// Open LLRP client
		llrpClient.requestOpeningChannel("localhost", 4321, successHandler);
		// Wait for client opened
		if (!successHandler.awaitClientOpening()) {
			Assert.fail();
		}
		if (!clientListener.awaitClientOpening()) {
			Assert.fail();
		}

		// Receive ReaderNotification with
		// ConnectionAttemptEventStatusType.SUCCESS
		ReaderEventNotification success = (ReaderEventNotification) llrpClient
				.awaitReceivedData(successHandler.channel, 500);
		if (!successHandler.awaitDataReceived()) {
			Assert.fail();
		}

		Assert.assertEquals(
				success.getReaderEventNotificationData().getConnectionAttemptEvent().getStatus(),
				ConnectionAttemptEventStatusType.SUCCESS);

		// Try to open another client on same connection
		llrpClient.requestOpeningChannel("localhost", 4321, deniedHandler);
		// Await opening
		if (!deniedHandler.awaitClientOpening()) {
			Assert.fail();
		}

		// Receive ReaderNotification with
		// ConnectionAttemptEventStatusType.FAILED_CLIENT_CONNECTION_EXISTS
		ReaderEventNotification denied = null;
		try {
			denied = (ReaderEventNotification) llrpClient.awaitReceivedData(deniedHandler.channel,
					timeout);
			// data was received before closing
			// DeniedChannel must be closed immediately
			if (!deniedHandler.awaitClientClosing()) {
				Assert.fail();
			}
		} catch (LLRPUnknownChannelException e) {
			deniedHandler.awaitClientClosing();
			// get binary data
			ByteBuffer data = deniedHandler.closeEvent.getPendingReceivedData();
			// create serializer
			ByteBufferSerializer serializer = new ByteBufferSerializer();
			// deserialize the header
			MessageHeader header = serializer.deserializeMessageHeader(data);
			// deserialize message
			denied = (ReaderEventNotification) serializer.deserializeMessage(header, data);
		}
		Assert.assertEquals(
				denied.getReaderEventNotificationData().getConnectionAttemptEvent().getStatus(),
				ConnectionAttemptEventStatusType.FAILED_CLIENT_CONNECTION_EXISTS);

		// Send get Capabilities to LLRPServer
		llrpClient.requestSendingData(successHandler.channel, getCaps);
		// Send Answer
		llrpMessageHandler.requestSendingData(capsResponseClient);
		// Receive answer in client
		Message received = llrpClient.awaitReceivedData(successHandler.channel, 500);
		// Answer must be equal to the data sent by the server
		Assert.assertEquals(received.toString(), capsResponseClient.toString());

		// Close client
		llrpClient.requestClosingChannel(successHandler.channel, false /* force */);
		if (!clientListener.awaitClientClosing()) {
			Assert.fail();
		}

		// Remove listener
		llrpMessageHandler.removeListener(clientListener);
		// Close server
		llrpMessageHandler.close();

		threadPool.shutdown();
	}

	@Test
	public void exceptions(@Mocked final LLRPServerEventHandler serverHandler,
			@Mocked final LLRPServerMultiplexed llrpServer,
			@Mocked final ServerSocketChannel serverChannel,
			@Mocked final LLRPMessageHandlerListener listener,
			@Mocked final Platform systemController) throws Throwable {
		// Mock serverHandler Run to get exception
		new NonStrictExpectations() {
			int counter = 0;
			{
				serverHandler.awaitReceivedData();
				result = new Exception("Any Exception");

				serverHandler.awaitServerOpening(anyLong);
				// First call should return null as server Channel, each other
				// should return a correct channel
				result = new Delegate<ServerSocketChannel>() {
					@SuppressWarnings("unused")
					public ServerSocketChannel awaitServerOpening(long timeout)
							throws TimeoutException {
						counter++;
						if (counter == 1)
							throw new TimeoutException("huhu");
						else
							return serverChannel;

					}
				};

			}
		};

		// Create new message handler
		final LLRPMessageHandler llrpMessageHandler = new LLRPMessageHandler(serverConf,
				instanceConfig, queue, tcpServerLLRP);
		llrpMessageHandler.addListener(listener);
		ExecutorService threadPool = Executors.newFixedThreadPool(5);
		// Open the message handler (TimeoutException)
		try {
			llrpMessageHandler.open(systemController, threadPool);
			Assert.fail();
		} catch (TimeoutException e) {
			Assert.assertTrue(e.getMessage().contains("Unable to open the server channel"));
		}

		// Open the message handler (success)
		llrpMessageHandler.open(systemController, threadPool);
		// Sleep waiting for serverChannel to be opened
		Thread.sleep(100);
		// close the message handler
		llrpMessageHandler.close();

		new Verifications() {
			{
				serverHandler.awaitReceivedData();
				times = 1;

				listener.opened();
				times = 1;

				// the exception thrown by awaitReceivedData is received via the
				// listener
				Throwable exception;
				listener.closed(exception = withCapture());
				Assert.assertTrue(exception.getMessage().equals("Any Exception"));
			}
		};
	}

	@AfterClass
	public void cleanUp() {
		// Remove output directory
		try {
			_FileHelperTest.deleteFiles(BASE_OUTPUT_PATH.toString());
			BASE_OUTPUT_PATH.toFile().delete();
		} catch (Exception e) {
		}
	}

}
