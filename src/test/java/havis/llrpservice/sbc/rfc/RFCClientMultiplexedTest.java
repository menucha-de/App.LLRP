package havis.llrpservice.sbc.rfc;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.testng.Assert;
import org.testng.annotations.Test;

import havis.device.rf.RFConsumer;
import havis.device.rf.RFDevice;
import havis.device.rf.capabilities.CapabilityType;
import havis.device.rf.configuration.Configuration;
import havis.device.rf.configuration.ConfigurationType;
import havis.device.rf.configuration.KeepAliveConfiguration;
import havis.device.rf.exception.CommunicationException;
import havis.device.rf.exception.ConnectionException;
import havis.device.rf.exception.ImplementationException;
import havis.device.rf.exception.ParameterException;
import havis.device.rf.tag.Filter;
import havis.device.rf.tag.TagData;
import havis.device.rf.tag.operation.ReadOperation;
import havis.device.rf.tag.operation.RequestOperation;
import havis.device.rf.tag.operation.TagOperation;
import havis.device.rf.tag.result.OperationResult;
import havis.device.rf.tag.result.ReadResult;
import havis.llrpservice.sbc.rfc.event.RFCChannelClosedEvent;
import havis.llrpservice.sbc.rfc.event.RFCChannelOpenedEvent;
import havis.llrpservice.sbc.rfc.event.RFCDataReceivedNotifyEvent;
import havis.llrpservice.sbc.rfc.event.RFCDataSentEvent;
import havis.llrpservice.sbc.rfc.message.Execute;
import havis.llrpservice.sbc.rfc.message.ExecuteResponse;
import havis.llrpservice.sbc.rfc.message.GetCapabilities;
import havis.llrpservice.sbc.rfc.message.GetCapabilitiesResponse;
import havis.llrpservice.sbc.rfc.message.GetConfiguration;
import havis.llrpservice.sbc.rfc.message.GetConfigurationResponse;
import havis.llrpservice.sbc.rfc.message.GetOperations;
import havis.llrpservice.sbc.rfc.message.GetOperationsResponse;
import havis.llrpservice.sbc.rfc.message.Message;
import havis.llrpservice.sbc.rfc.message.MessageHeader;
import havis.llrpservice.sbc.rfc.message.MessageType;
import havis.llrpservice.sbc.rfc.message.ResetConfiguration;
import havis.llrpservice.sbc.rfc.message.ResetConfigurationResponse;
import havis.llrpservice.sbc.rfc.message.SetConfiguration;
import havis.llrpservice.sbc.rfc.message.SetConfigurationResponse;
import havis.llrpservice.sbc.service.ReflectionServiceFactory;
import havis.llrpservice.sbc.service.ServiceFactory;
import havis.util.platform.Platform;
import mockit.Capturing;
import mockit.Deencapsulation;
import mockit.Delegate;
import mockit.Expectations;
import mockit.Invocation;
import mockit.Mocked;
import mockit.NonStrictExpectations;
import mockit.Verifications;

@Test
public class RFCClientMultiplexedTest {

	private final String STUB_CLASS_NAME = "havis.llrpservice.sbc.rfc._RFDeviceStubTest";
	private final String ADDR_SETTER_METHOD_NAME = "setPort";

	private class RFCEventHandlerTest implements RFCEventHandler {
		private CountDownLatch channelOpenedLatch = new CountDownLatch(1);
		private RFCChannelOpenedEvent channelOpenedEvent;
		private CountDownLatch channelClosedLatch = new CountDownLatch(1);
		private RFCChannelClosedEvent channelClosedEvent;

		public RFCChannelOpenedEvent awaitChannelOpened() throws InterruptedException {
			if (channelOpenedLatch.await(5, TimeUnit.SECONDS)) {
				return channelOpenedEvent;
			}
			return null;
		}

		public boolean isChannelOpened() {
			return channelOpenedLatch.getCount() == 0;
		}

		public RFCChannelClosedEvent awaitChannelClosed() throws InterruptedException {
			if (channelClosedLatch.await(5, TimeUnit.SECONDS)) {
				return channelClosedEvent;
			}
			return null;
		}

		public boolean isChannelClosed() {
			return channelClosedLatch.getCount() == 0;
		}

		@Override
		public void channelOpened(RFCChannelOpenedEvent event) {
			channelOpenedEvent = event;
			channelOpenedLatch.countDown();
		}

		@Override
		public void dataSent(RFCDataSentEvent event) {
		}

		@Override
		public void dataReceived(RFCDataReceivedNotifyEvent event) {
		}

		@Override
		public void channelClosed(RFCChannelClosedEvent event) {
			channelClosedEvent = event;
			channelClosedLatch.countDown();
		}

	};

	@Test
	public void openClose(@Mocked final _RFDeviceStubTest controller, @Mocked Platform platform)
			throws Exception {
		new NonStrictExpectations() {
		};

		// open a client channel
		ServiceFactory<RFDevice> serviceFactory = new ReflectionServiceFactory<>(STUB_CLASS_NAME,
				ADDR_SETTER_METHOD_NAME);

		RFCClientMultiplexed client = new RFCClientMultiplexed(serviceFactory,
				1000 /* openTimeout */, 1000 /* callbackTimeout */, platform);
		RFCEventHandlerTest eventHandler = new RFCEventHandlerTest();
		final String host = InetAddress.getLocalHost().getHostName();
		final int port = 1234;
		client.requestOpeningChannel(host, port, eventHandler);
		RFCChannelOpenedEvent openEvent = eventHandler.awaitChannelOpened();
		Assert.assertNotNull(openEvent);

		new Verifications() {
			{
				// the connection has been opened via RC controller
				controller.openConnection(withInstanceOf(RFConsumer.class), anyInt);
				times = 1;

				controller.setPort(host, port);
				times = 1;
			}
		};

		// add a message to the message queue
		GetCapabilities msg = new GetCapabilities(new MessageHeader(0),
				Arrays.asList(new CapabilityType[] { CapabilityType.ALL }));
		client.requestSendingData(openEvent.getChannel(), msg);

		// close the client channel
		client.requestClosingChannel(openEvent.getChannel());
		RFCChannelClosedEvent closeEvent = eventHandler.awaitChannelClosed();
		// the close event delivers the pending request
		Assert.assertEquals(
				closeEvent.getPendingReceivedData().get(0).getMessageHeader().getMessageType(),
				MessageType.GET_CAPABILITIES);
		Assert.assertNull(closeEvent.getPendingSendingData());

		new Verifications() {
			{
				// the client connection is closed via RC controller
				controller.closeConnection();
				times = 1;
			}
		};
	}

	@Test
	public void close(@Mocked Platform platform) throws Exception {
		// open a client channel
		ServiceFactory<RFDevice> serviceFactory = new ReflectionServiceFactory<>(STUB_CLASS_NAME,
				ADDR_SETTER_METHOD_NAME);
		final RFCClientMultiplexed client = new RFCClientMultiplexed(serviceFactory,
				1000 /* openTimeout */, 1000 /* callbackTimeout */, platform);
		RFCEventHandlerTest eventHandler = new RFCEventHandlerTest();
		final String host = InetAddress.getLocalHost().getHostName();
		final int port = 1234;
		client.requestOpeningChannel(host, port, eventHandler);
		RFCChannelOpenedEvent openEvent = eventHandler.awaitChannelOpened();
		Assert.assertNotNull(openEvent);
		final SocketChannel channel = openEvent.getChannel();

		// start waiting for received data in 2 separate threads
		ExecutorService threadPool = Executors.newFixedThreadPool(2);
		Callable<Exception> c = new Callable<Exception>() {

			@Override
			public Exception call() throws Exception {
				try {
					client.awaitReceivedData(channel, 3000);
					Assert.fail();
				} catch (RFCUnknownChannelException e) {
					// waiting has been interrupted by closing the channel
				}
				return null;
			}
		};
		Future<?> future1 = threadPool.submit(c);
		Future<?> future2 = threadPool.submit(c);
		Thread.sleep(1000);

		// close the client channel without sending any data
		client.requestClosingChannel(channel);
		eventHandler.awaitChannelClosed();

		future1.get(5, TimeUnit.SECONDS);
		future2.get(5, TimeUnit.SECONDS);
		threadPool.shutdown();
	}

	@Test
	public void openError1(@Mocked final ServiceFactory<RFDevice> serviceFactory,
			@Capturing final RFDevice controller, @Mocked Platform platform,
			@Mocked final Logger log) throws Exception {
		final SocketChannel channel = SocketChannel.open();
		new NonStrictExpectations(SocketChannel.class) {
			{
				serviceFactory.getService(anyString /* host */, anyInt /* port */,
						anyLong /* timeout */);
				result = controller;

				controller.openConnection(withInstanceOf(RFConsumer.class), anyInt);
				result = new Exception("huhu");
				channel.close();
				result = new Delegate<SocketChannel>() {
					@SuppressWarnings("unused")
					public SocketChannel close(Invocation inv) throws IOException {
						inv.proceed();
						throw new IOException("oha");
					}
				};
			}
		};

		// try to open a client: the opening of the connection via the RF
		// controller fails
		RFCClientMultiplexed client = new RFCClientMultiplexed(serviceFactory,
				1000 /* openTimeout */, 1000 /* callbackTimeout */, platform);
		Logger origLog = Deencapsulation.getField(client, "log");
		Deencapsulation.setField(client, "log", log);
		RFCEventHandlerTest eventHandler = new RFCEventHandlerTest();
		try {
			client.requestOpeningChannel(InetAddress.getLocalHost().getHostName(), /* port */
					0, eventHandler);
			Assert.fail();
		} catch (RFCConnectionException e) {
			new Verifications() {
				{
					// an opened socket channel must be closed
					channel.close();
					times = 1;

					// a close exception must be logged
					log.log(Level.SEVERE, "Cannot close socket channel",
							withInstanceOf(IOException.class));
					times = 1;
				}
			};
			// no open event is sent
			Assert.assertFalse(eventHandler.isChannelOpened());
		}

		Deencapsulation.setField(client, "log", origLog);
	}

	@Test
	public void closeError1(@Mocked Platform platform) throws Exception {
		final SocketChannel channel = SocketChannel.open();
		new NonStrictExpectations(SocketChannel.class) {
			{
				channel.close();
				result = new Delegate<SocketChannel>() {
					@SuppressWarnings("unused")
					public SocketChannel close(Invocation inv) throws IOException {
						inv.proceed();
						throw new IOException("oha");
					}
				};
			}
		};

		ServiceFactory<RFDevice> serviceFactory = new ReflectionServiceFactory<>(STUB_CLASS_NAME,
				null/* addressSetterMethodName */);

		// open a client channel
		RFCClientMultiplexed client = new RFCClientMultiplexed(serviceFactory,
				1000 /* openTimeout */, 1000 /* callbackTimeout */, platform);
		RFCEventHandlerTest eventHandler = new RFCEventHandlerTest();
		client.requestOpeningChannel(InetAddress.getLocalHost().getHostName(), 0 /* port */,
				eventHandler);
		RFCChannelOpenedEvent event = eventHandler.awaitChannelOpened();
		// close the client channel: an exception is thrown
		try {
			client.requestClosingChannel(event.getChannel());
			Assert.fail();
		} catch (RFCConnectionException e) {
			Assert.assertTrue(e.getMessage().contains("Cannot close channel"));
			// no close event is sent
			Assert.assertFalse(eventHandler.isChannelClosed());
		}
	}

	@Test
	public void sendReceive(@Mocked final _RFDeviceStubTest sendReceiveController,
			@Mocked final Platform sendReceivePlatform, @Mocked final Logger sendReceiveLog)
			throws Exception {
		new Expectations() {
			{
				sendReceivePlatform.hasUTCClock();
				result = true;

				sendReceiveLog.isLoggable(Level.INFO);
				result = true;

				sendReceiveLog.isLoggable(Level.FINE);
				result = true;

				sendReceiveLog.isLoggable(Level.FINER);
				result = true;
			}
		};
		ServiceFactory<RFDevice> serviceFactory = new ReflectionServiceFactory<>(STUB_CLASS_NAME,
				ADDR_SETTER_METHOD_NAME);
		RFCClientMultiplexed client = new RFCClientMultiplexed(serviceFactory,
				1000 /* openTimeout */, 1000 /* callbackTimeout */, sendReceivePlatform);
		Logger origLog = Deencapsulation.getField(client, "log");
		Deencapsulation.setField(client, "log", sendReceiveLog);
		sendReceive(client, /* port */0, sendReceiveController);

		Deencapsulation.setField(client, "log", origLog);
	}

	@Test
	public void concurrentSendReceive(
			@Mocked final _RFDeviceStubTest concurrentSendReceiveController,
			@Mocked final Platform concurrentSendReceivePlatform) throws Exception {
		new Expectations() {
			{
				concurrentSendReceivePlatform.hasUTCClock();
				result = true;
			}
		};
		// create RFC client
		ServiceFactory<RFDevice> serviceFactory = new ReflectionServiceFactory<>(STUB_CLASS_NAME,
				ADDR_SETTER_METHOD_NAME);
		final RFCClientMultiplexed client = new RFCClientMultiplexed(serviceFactory,
				1000 /* openTimeout */, 1000 /* callbackTimeout */, concurrentSendReceivePlatform);
		// start multiple threads
		List<Future<?>> futures = new ArrayList<>();
		ExecutorService threadPool = Executors.newFixedThreadPool(10);
		int threadCount = 100;
		for (int i = 0; i < threadCount; i++) {
			final int port = i;
			futures.add(threadPool.submit(new Runnable() {

				@Override
				public void run() {
					try {
						// send and receive messages
						sendReceive(client, port, /* mockedController */null);
					} catch (Exception e) {
						Assert.fail();
					}
				}
			}));
		}
		for (Future<?> future : futures) {
			future.get(3000, TimeUnit.MILLISECONDS);
		}
		threadPool.shutdown();
	}

	private void sendReceive(RFCClientMultiplexed client, int port, final RFDevice mockedController)
			throws Exception {
		RFCEventHandlerTest eventHandler = new RFCEventHandlerTest();
		client.requestOpeningChannel(InetAddress.getLocalHost().getHostName(), port, eventHandler);
		RFCChannelOpenedEvent openEvent = eventHandler.awaitChannelOpened();

		// send messages: GetCapabilities, GetConfiguration, SetConfiguration,
		// ResetConfiguration, Execute
		GetCapabilities cap = new GetCapabilities(new MessageHeader(1),
				Arrays.asList(new CapabilityType[] { CapabilityType.ALL }));
		client.requestSendingData(openEvent.getChannel(), cap);

		GetConfiguration conf = new GetConfiguration(new MessageHeader(3),
				Arrays.asList(new ConfigurationType[] { ConfigurationType.ALL }),
				(short) 0 /* antennaID */);
		client.requestSendingData(openEvent.getChannel(), conf);

		KeepAliveConfiguration keepAlive = new KeepAliveConfiguration();
		final List<Configuration> configuration = Arrays.asList(new Configuration[] { keepAlive });
		SetConfiguration sconf = new SetConfiguration(new MessageHeader(1), configuration);
		client.requestSendingData(openEvent.getChannel(), sconf);

		ResetConfiguration res = new ResetConfiguration(new MessageHeader(2));
		client.requestSendingData(openEvent.getChannel(), res);

		Filter filter = new Filter();
		filter.setBank((short) 1);
		filter.setBitOffset((short) 2);
		filter.setBitLength((short) 3);
		filter.setMask(new byte[] { 4 });
		filter.setData(new byte[] { 4 });
		filter.setMatch(true);

		ReadOperation readOp = new ReadOperation();
		readOp.setOperationId("1");
		readOp.setBank((short) 1);
		readOp.setOffset((short) 2);
		readOp.setLength((short) 3);
		readOp.setPassword(4);

		long startTimeStamp = System.currentTimeMillis();

		Execute ex = new Execute(new MessageHeader(3), /* antennas */
				Arrays.asList(new Short[] { 1 }), Arrays.asList(new Filter[] { filter }),
				Arrays.asList(new TagOperation[] { readOp }));
		client.requestSendingData(openEvent.getChannel(), ex);

		// get GetCapabilitiesResponse with correct message id
		Message response = client.awaitReceivedData(openEvent.getChannel(), 3000);
		Assert.assertTrue(response instanceof GetCapabilitiesResponse);
		Assert.assertEquals(response.getMessageHeader().getId(), 1);
		// get GetConfigurationResponse with correct message id
		response = client.awaitReceivedData(openEvent.getChannel(), 3000);
		Assert.assertTrue(response instanceof GetConfigurationResponse);
		Assert.assertEquals(response.getMessageHeader().getId(), 3);
		// get SetConfigurationResponse with correct message id
		response = client.awaitReceivedData(openEvent.getChannel(), 3000);
		Assert.assertTrue(response instanceof SetConfigurationResponse);
		Assert.assertEquals(response.getMessageHeader().getId(), 1);
		// get ResetConfigurationResponse with correct message id
		response = client.awaitReceivedData(openEvent.getChannel(), 3000);
		Assert.assertTrue(response instanceof ResetConfigurationResponse);
		Assert.assertEquals(response.getMessageHeader().getId(), 2);
		// get ExecuteResponse with correct message id
		response = client.awaitReceivedData(openEvent.getChannel(), 3000);
		Assert.assertTrue(response instanceof ExecuteResponse);
		Assert.assertEquals(response.getMessageHeader().getId(), 3);
		ExecuteResponse execResponse = (ExecuteResponse) response;
		Assert.assertTrue(execResponse.getTimeStamp().isUtc());
		Assert.assertTrue(execResponse.getTimeStamp().getTimestamp() >= startTimeStamp);
		if (mockedController != null) {
			new Verifications() {
				{
					// the GetCapabilities message has been processed
					mockedController.getCapabilities(CapabilityType.ALL);
					times = 1;

					// the GetConfiguration message has been processed
					mockedController.getConfiguration(ConfigurationType.ALL,
							(short) 0 /* antennaId */, (short) 0 /* gpiPort */,
							(short) 0 /* gpoPort */);
					times = 1;

					// the SetConfiguration message has been processed
					mockedController.setConfiguration(configuration);
					times = 1;

					// the ResetConfiguration message has been processed
					mockedController.resetConfiguration();
					times = 1;
				}
			};
		}

		// close the client channel
		client.requestClosingChannel(openEvent.getChannel());
		RFCChannelClosedEvent closeEvent = eventHandler.awaitChannelClosed();
		Assert.assertNull(closeEvent.getPendingSendingData());
		Assert.assertNull(closeEvent.getPendingReceivedData());
	}

	@Test
	public void sendReceiveSyncError(@Mocked final ServiceFactory<RFDevice> serviceFactory,
			@Capturing final RFDevice controller, @Mocked Platform platform,
			@Mocked final Logger log) throws Exception {
		final SocketChannel channel = SocketChannel.open();
		new NonStrictExpectations(SocketChannel.class) {
			{
				serviceFactory.getService(anyString /* host */, anyInt /* port */,
						anyLong /* timeout */);
				result = controller;

				controller.getCapabilities(withInstanceOf(CapabilityType.class));
				result = new ImplementationException("huhu");

				channel.close();
				result = new Delegate<SocketChannel>() {
					@SuppressWarnings("unused")
					public SocketChannel close(Invocation inv) throws IOException {
						inv.proceed();
						throw new IOException("oha");
					}
				};
			}
		};
		RFCClientMultiplexed client = new RFCClientMultiplexed(serviceFactory,
				1000 /* openTimeout */, 1000 /* callbackTimeout */, platform);
		Logger origLog = Deencapsulation.getField(client, "log");
		Deencapsulation.setField(client, "log", log);
		RFCEventHandlerTest eventHandler = new RFCEventHandlerTest();
		client.requestOpeningChannel(InetAddress.getLocalHost().getHostName(), 0 /* port */,
				eventHandler);
		RFCChannelOpenedEvent openEvent = eventHandler.awaitChannelOpened();

		// send GetCapabilities
		GetCapabilities cap = new GetCapabilities(new MessageHeader(1),
				Arrays.asList(new CapabilityType[] { CapabilityType.ALL }));
		client.requestSendingData(openEvent.getChannel(), cap);

		// try to receive GetCapabilitiesResponse
		try {
			client.awaitReceivedData(openEvent.getChannel(), 3000);
			Assert.fail();
		} catch (RFCException e) {
			Assert.assertTrue(e.getMessage().contains("Processing of"));
			new Verifications() {
				{
					// an opened socket channel must be closed
					channel.close();
					times = 1;

					// a close exception must be logged
					log.log(Level.SEVERE, "Cannot close channel",
							withInstanceOf(IOException.class));
					times = 1;
				}
			};
		}
		// get close event
		RFCChannelClosedEvent closeEvent = eventHandler.awaitChannelClosed();
		Assert.assertNull(closeEvent.getPendingReceivedData());
		Assert.assertNull(closeEvent.getPendingSendingData());

		// the closing of the channel must not fail
		client.requestClosingChannel(openEvent.getChannel());

		Deencapsulation.setField(client, "log", origLog);
	}

	@Test
	public void sendReceiveAsync(@Mocked final _RFDeviceStubTest controller,
			@Mocked Platform platform) throws Exception {
		RFCClientMultiplexed client = prepareSendReceiveAsync(controller, platform);
		sendReceiveAsync(client, /* port */0);
	}

	@Test
	public void concurrentSendReceiveAsync(@Mocked final _RFDeviceStubTest controller,
			@Mocked Platform platform) throws Exception {
		final RFCClientMultiplexed client = prepareSendReceiveAsync(controller, platform);

		List<Future<?>> futures = new ArrayList<>();
		ExecutorService threadPool = Executors.newFixedThreadPool(10);
		int threadCount = 2;
		for (int i = 0; i < threadCount; i++) {
			final int port = i;
			futures.add(threadPool.submit(new Runnable() {

				@Override
				public void run() {
					try {
						sendReceiveAsync(client, port);
					} catch (Exception e) {
						e.printStackTrace();
						Assert.fail();
					}
				}
			}));
		}
		for (Future<?> future : futures) {
			future.get(3000, TimeUnit.MILLISECONDS);
		}
		threadPool.shutdown();
	}

	@SuppressWarnings("unchecked")
	private RFCClientMultiplexed prepareSendReceiveAsync(final RFDevice controller,
			Platform platform) throws Exception {
		new NonStrictExpectations() {
			Map<Object, RFConsumer> consumers = new ConcurrentHashMap<>();
			{
				controller.openConnection(withInstanceOf(RFConsumer.class), anyInt);
				result = new Delegate<RFDevice>() {
					@SuppressWarnings("unused")
					void openConnection(Invocation inv, RFConsumer c, int timeout)
							throws ConnectionException, CommunicationException, ParameterException,
							ImplementationException {
						consumers.put(inv.getInvokedInstance(), c);
						// if openConnection is called the caller must be ready
						// to receive events => send an event
						c.keepAlive();
					}
				};

				controller.execute(withInstanceOf(List.class), withInstanceOf(List.class),
						withInstanceOf(List.class));
				result = new Delegate<RFDevice>() {
					@SuppressWarnings("unused")
					List<TagData> execute(Invocation inv, List<Short> antennas,
							List<Filter> filters, List<TagOperation> operations)
							throws ConnectionException, CommunicationException, ParameterException,
							ImplementationException {
						Assert.assertTrue(operations.get(0) instanceof RequestOperation);
						RFConsumer consumer = consumers.get(inv.getInvokedInstance());
						TagData tagData = new TagData();
						// tagData.setId(1);
						tagData.setEpc(new byte[] { 0, 1, 2 });
						tagData.setPc((short) 3);
						tagData.setXpc(4);
						tagData.setAntennaID((short) 5);
						tagData.setRssi((byte) 6);
						tagData.setChannel((short) 7);
						tagData.setResultList(new ArrayList<OperationResult>());

						// use callback to get operations for a tag
						List<TagOperation> ops = consumer.getOperations(tagData);
						Assert.assertTrue(ops.get(0) instanceof ReadOperation);

						// send a ConnectionAttempted event
						consumer.connectionAttempted();

						ReadResult rdRes = new ReadResult();
						rdRes.setOperationId("1"/* id */);
						rdRes.setReadData(new byte[] { 10, 11 });
						rdRes.setResult(ReadResult.Result.SUCCESS);

						// return tag with ReadResult
						tagData.getResultList().add(rdRes);

						return Arrays.asList(new TagData[] { tagData });
					}
				};
			}
		};
		ServiceFactory<RFDevice> serviceFactory = new ReflectionServiceFactory<>(STUB_CLASS_NAME,
				null/* addressSetterMethodName */);
		return new RFCClientMultiplexed(serviceFactory, 1000 /* openTimeout */,
				1000 /* callbackTimeout */, platform);
	}

	private void sendReceiveAsync(RFCClientMultiplexed client, int port) throws Exception {
		RFCEventHandlerTest eventHandler = new RFCEventHandlerTest();
		client.requestOpeningChannel(InetAddress.getLocalHost().getHostName(), port, eventHandler);
		RFCChannelOpenedEvent openEvent = eventHandler.awaitChannelOpened();

		// wait for KEEP_ALIVE event
		Message message = client.awaitReceivedData(openEvent.getChannel(), 3000);
		Assert.assertEquals(message.getMessageHeader().getMessageType(), MessageType.KEEP_ALIVE);

		Filter filter = new Filter();
		filter.setBank((short) 10);
		filter.setBitOffset((short) 11);
		filter.setBitLength((short) 12);
		filter.setMask(new byte[] { 13 });
		filter.setData(new byte[] { 13 });
		filter.setMatch(true);

		RequestOperation reqOp = new RequestOperation();
		reqOp.setOperationId("1");

		// send Execute message with a RequestOperation
		Execute ex = new Execute(new MessageHeader(100), /* antennas */
				Arrays.asList(new Short[] { 1 }), Arrays.asList(new Filter[] { filter }),
				Arrays.asList(new TagOperation[] { reqOp }));
		client.requestSendingData(openEvent.getChannel(), ex);

		// wait for GetOperations callback and return a ReadOperation
		GetOperations ops = (GetOperations) client.awaitReceivedData(openEvent.getChannel(), 3000);
		Assert.assertEquals(ops.getTag().getRssi(), 6);
		Assert.assertEquals(ops.getTag().getResultList().size(), 0);

		ReadOperation readOp = new ReadOperation();
		readOp.setOperationId("1");
		readOp.setBank((short) 20);
		readOp.setOffset((short) 21);
		readOp.setLength((short) 22);
		readOp.setPassword(23);

		GetOperationsResponse opsResponse = new GetOperationsResponse(new MessageHeader(101),
				Arrays.asList(new TagOperation[] { readOp }));
		client.requestSendingData(openEvent.getChannel(), opsResponse);

		// wait for CONNECTION_ATTEMPTED event
		message = client.awaitReceivedData(openEvent.getChannel(), 3000);
		Assert.assertEquals(message.getMessageHeader().getMessageType(),
				MessageType.CONNECTION_ATTEMPTED);

		// get ExecuteResponse with correct message id
		ExecuteResponse exResponse = (ExecuteResponse) client
				.awaitReceivedData(openEvent.getChannel(), 3000);
		Assert.assertEquals(exResponse.getMessageHeader().getId(), 100);
		TagData tagData = exResponse.getTagData().get(0);
		Assert.assertEquals(tagData.getAntennaID(), 5);
		ReadResult result = (ReadResult) tagData.getResultList().get(0);
		Assert.assertEquals(result.getResult(), ReadResult.Result.SUCCESS);

		// close the client channel
		client.requestClosingChannel(openEvent.getChannel());
		RFCChannelClosedEvent closeEvent = eventHandler.awaitChannelClosed();
		Assert.assertNull(closeEvent.getPendingReceivedData());
		Assert.assertNull(closeEvent.getPendingSendingData());
	}

	@Test
	public void sendReceiveAsyncGetOperationsError(@Mocked _RFDeviceStubTest controller,
			@Mocked Platform platform) throws Exception {
		final int callbackTimeout = 100;
		ServiceFactory<RFDevice> serviceFactory = new ReflectionServiceFactory<RFDevice>(
				STUB_CLASS_NAME, ADDR_SETTER_METHOD_NAME);
		RFCClientMultiplexed client = new RFCClientMultiplexed(serviceFactory,
				1000 /* openTimeout */, callbackTimeout, platform);

		prepareSendReceiveAsyncGetOperationsError(controller);
		sendReceiveAsyncGetOperationsError(client, /* port */0, callbackTimeout);
	}

	@Test
	public void concurrentSendReceiveAsyncGetOperationsError(@Mocked _RFDeviceStubTest controller,
			@Mocked Platform platform) throws Exception {
		final int callbackTimeout = 1;
		ServiceFactory<RFDevice> serviceFactory = new ReflectionServiceFactory<RFDevice>(
				STUB_CLASS_NAME, ADDR_SETTER_METHOD_NAME);

		final RFCClientMultiplexed client = new RFCClientMultiplexed(serviceFactory,
				1000 /* openTimeout */, callbackTimeout, platform);

		prepareSendReceiveAsyncGetOperationsError(controller);

		List<Future<?>> futures = new ArrayList<>();
		ExecutorService threadPool = Executors.newFixedThreadPool(5);
		int threadCount = 10;
		for (int i = 0; i < threadCount; i++) {
			final int port = i;
			futures.add(threadPool.submit(new Runnable() {

				@Override
				public void run() {
					try {
						sendReceiveAsyncGetOperationsError(client, port, callbackTimeout);
					} catch (Exception e) {
						Assert.fail("", e);
					}
				}
			}));
		}
		for (Future<?> future : futures) {
			future.get(3000, TimeUnit.MILLISECONDS);
		}
		threadPool.shutdown();
	}

	@SuppressWarnings("unchecked")
	private void prepareSendReceiveAsyncGetOperationsError(final RFDevice controller)
			throws Exception {
		new NonStrictExpectations() {
			Map<Object, RFConsumer> consumers = new ConcurrentHashMap<>();
			{
				controller.openConnection(withInstanceOf(RFConsumer.class), anyInt);
				result = new Delegate<RFDevice>() {
					@SuppressWarnings("unused")
					void openConnection(Invocation inv, RFConsumer consumer, int timeout)
							throws ConnectionException, CommunicationException, ParameterException,
							ImplementationException {
						consumers.put(inv.getInvokedInstance(), consumer);
					}
				};

				controller.execute(withInstanceOf(List.class), withInstanceOf(List.class),
						withInstanceOf(List.class));
				result = new Delegate<RFDevice>() {
					@SuppressWarnings("unused")
					List<TagData> execute(Invocation inv, List<Short> antennas,
							List<Filter> filters, List<TagOperation> operations)
							throws ConnectionException, CommunicationException, ParameterException,
							ImplementationException {
						Assert.assertTrue(operations.get(0) instanceof RequestOperation);
						RFConsumer consumer = consumers.get(inv.getInvokedInstance());

						TagData tagData = new TagData();
						// tagData.setId(1);
						tagData.setEpc(new byte[] { 0, 1, 2 });
						tagData.setPc((short) 3);
						tagData.setXpc(4);
						tagData.setAntennaID((short) 5);
						tagData.setRssi((byte) 6);
						tagData.setChannel((short) 7);
						tagData.setResultList(new ArrayList<OperationResult>());

						// use callback to get operations for a tag
						List<TagOperation> ops = consumer.getOperations(tagData);
						// no operations due to exception
						Assert.assertEquals(ops.size(), 0);
						return new ArrayList<>();
					}
				};
			}
		};
	}

	private void sendReceiveAsyncGetOperationsError(RFCClientMultiplexed client, int port,
			int callbackTimeout) throws Exception {
		RFCEventHandlerTest eventHandler = new RFCEventHandlerTest();
		client.requestOpeningChannel(InetAddress.getLocalHost().getHostName(), port, eventHandler);
		RFCChannelOpenedEvent openEvent = eventHandler.awaitChannelOpened();

		Filter filter = new Filter();
		filter.setBank((short) 10);
		filter.setBitOffset((short) 11);
		filter.setBitLength((short) 12);
		filter.setMask(new byte[] { 13 });
		filter.setData(new byte[] { 13 });
		filter.setMatch(true);

		RequestOperation reqOp = new RequestOperation();
		reqOp.setOperationId("1");

		// send Execute message with a RequestOperation
		Execute execute = new Execute(new MessageHeader(100),
				Arrays.asList(new Short[] { 1 }) /* antennas */,
				Arrays.asList(new Filter[] { filter }),
				Arrays.asList(new TagOperation[] { reqOp }));
		client.requestSendingData(openEvent.getChannel(), execute);

		// wait for GetOperations callback
		GetOperations ops = (GetOperations) client.awaitReceivedData(openEvent.getChannel(), 3000);
		Assert.assertEquals(ops.getTag().getRssi(), 6);
		Assert.assertEquals(ops.getTag().getResultList().size(), 0);

		// wait for callback time out
		Thread.sleep(callbackTimeout + 500);

		// get ExecuteResponse with correct message id
		try {
			client.awaitReceivedData(openEvent.getChannel(), 3000);
			Assert.fail();
		} catch (RFCTimeoutException e) {
			Assert.assertTrue(e.getMessage().contains("Time out after " + callbackTimeout));
		}

		// get close event with GetOperations message
		RFCChannelClosedEvent closeEvent = eventHandler.awaitChannelClosed();
		Assert.assertNull(closeEvent.getPendingReceivedData());
		Assert.assertNull(closeEvent.getPendingSendingData());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void receiveAsyncExecutionError(@Mocked final ServiceFactory<RFDevice> serviceFactory,
			@Capturing final RFDevice controller, @Mocked Platform platform) throws Exception {
		final SocketChannel channel = SocketChannel.open();
		new NonStrictExpectations(SocketChannel.class) {
			{
				serviceFactory.getService(anyString /* host */, anyInt /* port */,
						anyLong /* timeout */);
				result = controller;

				controller.execute(withInstanceOf(List.class), withInstanceOf(List.class),
						withInstanceOf(List.class));
				result = new ConnectionException("huhu");

				channel.close();
				result = new Delegate<SocketChannel>() {
					@SuppressWarnings("unused")
					public SocketChannel close(Invocation inv) {
						return inv.proceed();
					}
				};
			}
		};

		// open a client channel
		RFCClientMultiplexed client = new RFCClientMultiplexed(serviceFactory,
				1000 /* openTimeout */, 1000 /* callbackTimeout */, platform);
		RFCEventHandlerTest eventHandler = new RFCEventHandlerTest();
		client.requestOpeningChannel(InetAddress.getLocalHost().getHostName(), /* port */
				0, eventHandler);
		RFCChannelOpenedEvent openEvent = eventHandler.awaitChannelOpened();

		Filter filter = new Filter();
		filter.setBank((short) 10);
		filter.setBitOffset((short) 11);
		filter.setBitLength((short) 12);
		filter.setMask(new byte[] { 13 });
		filter.setData(new byte[] { 13 });
		filter.setMatch(true);

		RequestOperation reqOp = new RequestOperation();
		reqOp.setOperationId("1");

		// send Execute message: the processing fails and the connection to the
		// RF controller is closed with an exception
		Execute execute = new Execute(new MessageHeader(100), /* antennas */
				Arrays.asList(new Short[] { 1 }), Arrays.asList(new Filter[] { filter }),
				Arrays.asList(new TagOperation[] { reqOp }));
		client.requestSendingData(openEvent.getChannel(), execute);

		try {
			client.awaitReceivedData(openEvent.getChannel(), 3000);
		} catch (RFCException e) {
			Assert.assertTrue(e.getMessage().contains("Processing of"));
			new Verifications() {
				{
					// an opened socket channel must be closed
					channel.close();
					times = 1;
				}
			};
		}
		// get close event
		RFCChannelClosedEvent closeEvent = eventHandler.awaitChannelClosed();
		Assert.assertNull(closeEvent.getPendingReceivedData());
		Assert.assertNull(closeEvent.getPendingSendingData());

		// the closing of the channel must not fail
		client.requestClosingChannel(openEvent.getChannel());
	}

	@Test
	public void awaitReceivedDataTimeout(@Capturing final RFDevice controller,
			@Mocked Platform platform) throws Exception {
		final SocketChannel channel = SocketChannel.open();
		new NonStrictExpectations() {
			{
				channel.close();
			}
		};

		// open a client channel
		ServiceFactory<RFDevice> serviceFactory = new ReflectionServiceFactory<>(STUB_CLASS_NAME,
				ADDR_SETTER_METHOD_NAME);
		final RFCClientMultiplexed client = new RFCClientMultiplexed(serviceFactory,
				1000 /* openTimeout */, 1000 /* callbackTimeout */, platform);
		RFCEventHandlerTest eventHandler = new RFCEventHandlerTest();
		client.requestOpeningChannel(InetAddress.getLocalHost().getHostName(), 0 /* port */,
				eventHandler);
		final RFCChannelOpenedEvent openEvent = eventHandler.awaitChannelOpened();

		// try to get a message directly without waiting
		Message msg = client.awaitReceivedData(openEvent.getChannel(),
				RFCClientMultiplexed.RETURN_IMMEDIATELY);
		Assert.assertNull(msg);

		// try to get a message with a time out
		try {
			client.awaitReceivedData(openEvent.getChannel(), 1000);
			Assert.fail();
		} catch (RFCTimeoutException e) {
			Assert.assertTrue(e.getMessage().contains("Time out after"));
		}

		// wait for a message without a time out
		// (send a message to release the waiting thread after some time)
		long endTime = System.currentTimeMillis() + 500;
		ExecutorService threadPool = Executors.newFixedThreadPool(1);
		Future<Object> future = threadPool.submit(new Callable<Object>() {

			@Override
			public Object call() throws Exception {
				Thread.sleep(500);
				// send a message
				ResetConfiguration ex = new ResetConfiguration(new MessageHeader(100));
				client.requestSendingData(openEvent.getChannel(), ex);
				return null;
			}

		});

		msg = client.awaitReceivedData(openEvent.getChannel(), RFCClientMultiplexed.NO_TIMEOUT);
		Assert.assertEquals(msg.getMessageHeader().getId(), 100);
		Assert.assertTrue(System.currentTimeMillis() >= endTime);
		future.get(3000, TimeUnit.MILLISECONDS);
		threadPool.shutdown();

		// close the client channel
		client.requestClosingChannel(openEvent.getChannel());
		eventHandler.awaitChannelClosed();
	}
}
