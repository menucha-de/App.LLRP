package havis.llrpservice.sbc.gpio;

import havis.device.io.Configuration;
import havis.device.io.IOConsumer;
import havis.device.io.IODevice;
import havis.device.io.KeepAliveConfiguration;
import havis.device.io.StateEvent;
import havis.device.io.Type;
import havis.device.io.exception.ConnectionException;
import havis.device.io.exception.ImplementationException;
import havis.device.io.exception.ParameterException;
import havis.llrpservice.sbc.gpio.event.GPIOChannelClosedEvent;
import havis.llrpservice.sbc.gpio.event.GPIOChannelOpenedEvent;
import havis.llrpservice.sbc.gpio.event.GPIODataReceivedNotifyEvent;
import havis.llrpservice.sbc.gpio.event.GPIODataSentEvent;
import havis.llrpservice.sbc.gpio.message.GetConfiguration;
import havis.llrpservice.sbc.gpio.message.GetConfigurationResponse;
import havis.llrpservice.sbc.gpio.message.Message;
import havis.llrpservice.sbc.gpio.message.MessageHeader;
import havis.llrpservice.sbc.gpio.message.MessageType;
import havis.llrpservice.sbc.gpio.message.ResetConfiguration;
import havis.llrpservice.sbc.gpio.message.SetConfiguration;
import havis.llrpservice.sbc.service.ReflectionServiceFactory;
import havis.llrpservice.sbc.service.ServiceFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import mockit.Capturing;
import mockit.Deencapsulation;
import mockit.Delegate;
import mockit.Invocation;
import mockit.Mocked;
import mockit.NonStrictExpectations;
import mockit.Verifications;

import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class GPIOClientMultiplexedTest {

	private final String STUB_CLASS_NAME = "havis.llrpservice.sbc.gpio._GPIOControllerStubTest";
	private final String ADDR_SETTER_METHOD_NAME = "setPort";

	private class GPIOEventHandlerTest implements GPIOEventHandler {
		private CountDownLatch channelOpenedLatch = new CountDownLatch(1);
		private GPIOChannelOpenedEvent channelOpenedEvent;
		private CountDownLatch channelClosedLatch = new CountDownLatch(1);
		private GPIOChannelClosedEvent channelClosedEvent;

		public GPIOChannelOpenedEvent awaitChannelOpened()
				throws InterruptedException {
			if (channelOpenedLatch.await(5, TimeUnit.SECONDS)) {
				return channelOpenedEvent;
			}
			return null;
		}

		public boolean isChannelOpened() {
			return channelOpenedLatch.getCount() == 0;
		}

		public GPIOChannelClosedEvent awaitChannelClosed()
				throws InterruptedException {
			if (channelClosedLatch.await(5, TimeUnit.SECONDS)) {
				return channelClosedEvent;
			}
			return null;
		}

		public boolean isChannelClosed() {
			return channelClosedLatch.getCount() == 0;
		}

		@Override
		public void channelOpened(GPIOChannelOpenedEvent event) {
			channelOpenedEvent = event;
			channelOpenedLatch.countDown();
		}

		@Override
		public void dataSent(GPIODataSentEvent event) {
		}

		@Override
		public void dataReceived(GPIODataReceivedNotifyEvent event) {
		}

		@Override
		public void channelClosed(GPIOChannelClosedEvent event) {
			channelClosedEvent = event;
			channelClosedLatch.countDown();
		}
	};

	@Test
	public void openClose(@Mocked final _GPIOControllerStubTest controller)
			throws Exception {
		new NonStrictExpectations() {
		};

		// open a client channel
		ServiceFactory<IODevice> serviceFactory = new ReflectionServiceFactory<>(
				STUB_CLASS_NAME, ADDR_SETTER_METHOD_NAME);

		GPIOClientMultiplexed client = new GPIOClientMultiplexed(
				serviceFactory, 1000 /* openTimeout */);
		GPIOEventHandlerTest eventHandler = new GPIOEventHandlerTest();
		final String host = InetAddress.getLocalHost().getHostName();
		final int port = 1234;
		client.requestOpeningChannel(host, port, eventHandler);
		GPIOChannelOpenedEvent openEvent = eventHandler.awaitChannelOpened();
		Assert.assertNotNull(openEvent);

		new Verifications() {
			{
				// the connection has been opened via GPIO controller
				controller.openConnection(withInstanceOf(IOConsumer.class),
						anyInt);
				times = 1;

				controller.setPort(host, port);
				times = 1;
			}
		};

		// add a message to the message queue
		GetConfiguration msg = new GetConfiguration(new MessageHeader(0),
				Arrays.asList(new Type[] { Type.ALL }), (short) 1);
		client.requestSendingData(openEvent.getChannel(), msg);

		// close the client channel
		client.requestClosingChannel(openEvent.getChannel());
		GPIOChannelClosedEvent closeEvent = eventHandler.awaitChannelClosed();
		// the close event delivers the pending request
		Assert.assertEquals(closeEvent.getPendingReceivedData().get(0)
				.getMessageHeader().getMessageType(),
				MessageType.GET_CONFIGURATION);
		Assert.assertNull(closeEvent.getPendingSendingData());

		new Verifications() {
			{
				// the client connection is closed via GPIO controller
				controller.closeConnection();
				times = 1;
			}
		};
	}

	@Test
	public void close() throws Exception {
		// open a client channel
		ServiceFactory<IODevice> serviceFactory = new ReflectionServiceFactory<>(
				STUB_CLASS_NAME, ADDR_SETTER_METHOD_NAME);
		final GPIOClientMultiplexed client = new GPIOClientMultiplexed(
				serviceFactory, 1000 /* openTimeout */);
		GPIOEventHandlerTest eventHandler = new GPIOEventHandlerTest();
		final String host = InetAddress.getLocalHost().getHostName();
		final int port = 1234;
		client.requestOpeningChannel(host, port, eventHandler);
		GPIOChannelOpenedEvent openEvent = eventHandler.awaitChannelOpened();
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
				} catch (GPIOUnknownChannelException e) {
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
	public void openError1(
			@Mocked final ServiceFactory<IODevice> serviceFactory,
			@Capturing final IODevice controller, @Mocked final Logger log)
			throws Exception {
		final SocketChannel channel = SocketChannel.open();
		new NonStrictExpectations(SocketChannel.class) {
			{
				serviceFactory.getService(anyString /* host */,
						anyInt /* port */, anyLong /* timeout */);
				result = controller;

				controller.openConnection(withInstanceOf(IOConsumer.class),
						anyInt);
				result = new Exception("huhu");

				channel.close();
				result = new Delegate<SocketChannel>() {
					@SuppressWarnings("unused")
					public SocketChannel close(Invocation inv)
							throws IOException {
						inv.proceed();
						throw new IOException("oha");
					}
				};
			}
		};

		// try to open a client: the opening of the connection via the RF
		// controller fails
		GPIOClientMultiplexed client = new GPIOClientMultiplexed(
				serviceFactory, 1000 /* openTimeout */);
		Logger origLog = Deencapsulation.getField(client, "log");
		Deencapsulation.setField(client, "log", log);
		GPIOEventHandlerTest eventHandler = new GPIOEventHandlerTest();
		try {
			client.requestOpeningChannel(InetAddress.getLocalHost()
					.getHostName(), /* port */
					0, eventHandler);
			Assert.fail();
		} catch (GPIOConnectionException e) {
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
	public void closeError1() throws Exception {
		final SocketChannel channel = SocketChannel.open();
		new NonStrictExpectations(SocketChannel.class) {
			{
				channel.close();
				result = new Delegate<SocketChannel>() {
					@SuppressWarnings("unused")
					public SocketChannel close(Invocation inv)
							throws IOException {
						inv.proceed();
						throw new IOException("oha");
					}
				};
			}
		};

		ServiceFactory<IODevice> serviceFactory = new ReflectionServiceFactory<>(
				STUB_CLASS_NAME, null/* addressSetterMethodName */);

		// open a client channel
		GPIOClientMultiplexed client = new GPIOClientMultiplexed(
				serviceFactory, 1000 /* openTimeout */);
		GPIOEventHandlerTest eventHandler = new GPIOEventHandlerTest();
		client.requestOpeningChannel(InetAddress.getLocalHost().getHostName(),
				0 /* port */, eventHandler);
		GPIOChannelOpenedEvent event = eventHandler.awaitChannelOpened();
		// close the client channel: an exception is thrown
		try {
			client.requestClosingChannel(event.getChannel());
			Assert.fail();
		} catch (GPIOConnectionException e) {
			Assert.assertTrue(e.getMessage().contains("Cannot close channel"));
			// no close event is sent
			Assert.assertFalse(eventHandler.isChannelClosed());
		}
	}

	@Test
	public void sendReceive(
			@Mocked final ServiceFactory<IODevice> serviceFactory,
			@Capturing final IODevice controller, @Mocked final Logger log)
			throws Exception {
		new NonStrictExpectations() {
			{
				serviceFactory.getService(anyString /* host */,
						anyInt /* port */, anyLong /* timeout */);
				log.isLoggable(Level.INFO);
				result = true;

				log.isLoggable(Level.FINE);
				result = true;

				log.isLoggable(Level.FINER);
				result = true;
			}
		};
		GPIOClientMultiplexed client = new GPIOClientMultiplexed(
				serviceFactory, 1000 /* openTimeout */);
		Logger origLog = Deencapsulation.getField(client, "log");
		Deencapsulation.setField(client, "log", log);
		sendReceive(client, /* port */0, controller);

		Deencapsulation.setField(client, "log", origLog);
	}

	@Test
	public void concurrentSendReceive(@Capturing IODevice controller)
			throws Exception {
		// create GPIO client
		ServiceFactory<IODevice> serviceFactory = new ReflectionServiceFactory<>(
				STUB_CLASS_NAME, ADDR_SETTER_METHOD_NAME);
		final GPIOClientMultiplexed client = new GPIOClientMultiplexed(
				serviceFactory, 1000 /* openTimeout */);
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

	private void sendReceive(GPIOClientMultiplexed client, int port,
			final IODevice mockedController) throws Exception {
		GPIOEventHandlerTest eventHandler = new GPIOEventHandlerTest();
		client.requestOpeningChannel(InetAddress.getLocalHost().getHostName(),
				port, eventHandler);
		GPIOChannelOpenedEvent openEvent = eventHandler.awaitChannelOpened();

		// send messages: GetConfiguration, SetConfiguration, ResetConfiguration
		GetConfiguration conf = new GetConfiguration(new MessageHeader(3),
				Arrays.asList(new Type[] { Type.ALL }), (short) 2 /* pinId */);
		client.requestSendingData(openEvent.getChannel(), conf);

		KeepAliveConfiguration keepAlive = new KeepAliveConfiguration();
		final List<Configuration> configuration = Arrays
				.asList(new Configuration[] { keepAlive });
		SetConfiguration sconf = new SetConfiguration(new MessageHeader(1),
				configuration);
		client.requestSendingData(openEvent.getChannel(), sconf);

		ResetConfiguration res = new ResetConfiguration(new MessageHeader(2));
		client.requestSendingData(openEvent.getChannel(), res);

		// get GetConfigurationResponse with correct message id
		Message response = client.awaitReceivedData(openEvent.getChannel(),
				3000);
		Assert.assertEquals(response.getMessageHeader().getMessageType(),
				MessageType.GET_CONFIGURATION_RESPONSE);
		Assert.assertEquals(response.getMessageHeader().getId(), 3);
		// get SetConfigurationResponse with correct message id
		response = client.awaitReceivedData(openEvent.getChannel(), 3000);
		Assert.assertEquals(response.getMessageHeader().getMessageType(),
				MessageType.SET_CONFIGURATION_RESPONSE);
		Assert.assertEquals(response.getMessageHeader().getId(), 1);
		// get ResetConfigurationResponse with correct message id
		response = client.awaitReceivedData(openEvent.getChannel(), 3000);
		Assert.assertEquals(response.getMessageHeader().getMessageType(),
				MessageType.RESET_CONFIGURATION_RESPONSE);
		Assert.assertEquals(response.getMessageHeader().getId(), 2);
		if (mockedController != null) {
			new Verifications() {
				{
					// the GetConfiguration message has been processed
					mockedController.getConfiguration(Type.ALL, (short) 2);
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
		GPIOChannelClosedEvent closeEvent = eventHandler.awaitChannelClosed();
		Assert.assertNull(closeEvent.getPendingSendingData());
		Assert.assertNull(closeEvent.getPendingReceivedData());
	}

	@Test
	public void sendReceiveSyncError(
			final @Mocked ServiceFactory<IODevice> serviceFactory,
			@Capturing final IODevice controller, @Mocked final Logger log)
			throws Exception {
		final SocketChannel channel = SocketChannel.open();
		class Data {
			ConnectionException ce;
			ParameterException pe;
		}
		final Data data = new Data();
		new NonStrictExpectations(SocketChannel.class) {
			{
				serviceFactory.getService(anyString /* host */,
						anyInt /* port */, anyLong /* timeout */);

				controller.getConfiguration(withInstanceOf(Type.class),
						anyShort);
				result = new Delegate<IODevice>() {
					@SuppressWarnings("unused")
					public List<Configuration> getConfiguration(Type arg0,
							short arg1) throws ConnectionException,
							ParameterException, ImplementationException {
						if (data.ce != null) {
							throw data.ce;
						} else {
							throw data.pe;
						}
					}
				};

				channel.close();
				result = new Delegate<SocketChannel>() {
					@SuppressWarnings("unused")
					public SocketChannel close(Invocation inv)
							throws IOException {
						inv.proceed();
						throw new IOException("oha");
					}
				};
			}
		};
		GPIOClientMultiplexed client = new GPIOClientMultiplexed(
				serviceFactory, 1000 /* openTimeout */);
		Logger origLog = Deencapsulation.getField(client, "log");
		Deencapsulation.setField(client, "log", log);
		GPIOEventHandlerTest eventHandler = new GPIOEventHandlerTest();
		client.requestOpeningChannel(InetAddress.getLocalHost().getHostName(),
				0 /* port */, eventHandler);
		GPIOChannelOpenedEvent openEvent = eventHandler.awaitChannelOpened();

		// send GetConfiguration
		data.pe = new ParameterException("huhu");
		GetConfiguration conf = new GetConfiguration(new MessageHeader(3),
				Arrays.asList(new Type[] { Type.ALL }), (short) 2 /* pinId */);
		client.requestSendingData(openEvent.getChannel(), conf);

		// try to receive GetConfigurationResponse
		try {
			GetConfigurationResponse gcr = (GetConfigurationResponse) client
					.awaitReceivedData(openEvent.getChannel(), 3000);
			Assert.assertNotNull(gcr.getException());
		} catch (GPIOException e) {
			Assert.fail();
		}

		data.ce = new ConnectionException("huhu");
		data.pe = null;
		client.requestSendingData(openEvent.getChannel(), conf);

		// try to receive GetConfigurationResponse
		try {
			client.awaitReceivedData(openEvent.getChannel(), 3000);
		} catch (GPIOException e) {
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
		GPIOChannelClosedEvent closeEvent = eventHandler.awaitChannelClosed();
		Assert.assertNull(closeEvent.getPendingReceivedData());
		Assert.assertNull(closeEvent.getPendingSendingData());

		// the closing of the channel must not fail
		client.requestClosingChannel(openEvent.getChannel());

		Deencapsulation.setField(client, "log", origLog);
	}

	@Test
	public void sendReceiveAsync(@Mocked _GPIOControllerStubTest controller)
			throws Exception {
		GPIOClientMultiplexed client = prepareSendReceiveAsync(controller);
		sendReceiveAsync(client, /* port */0);
	}

	@Test
	public void concurrentSendReceiveAsync(
			@Mocked _GPIOControllerStubTest controller) throws Exception {
		final GPIOClientMultiplexed client = prepareSendReceiveAsync(controller);

		List<Future<?>> futures = new ArrayList<>();
		ExecutorService threadPool = Executors.newFixedThreadPool(10);
		int threadCount = 100;
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

	private GPIOClientMultiplexed prepareSendReceiveAsync(
			final IODevice controller) throws Exception {
		new NonStrictExpectations() {
			{
				controller.openConnection(withInstanceOf(IOConsumer.class),
						anyInt);
				result = new Delegate<IODevice>() {
					@SuppressWarnings("unused")
					void openConnection(Invocation inv, IOConsumer c,
							int timeout) {
						// if openConnection is called the caller must be ready
						// to receive events => send some events
						c.keepAlive();
						c.connectionAttempted();
						c.stateChanged(new StateEvent((short) 3 /* pinId */,
								havis.device.io.State.HIGH));
					}
				};
			}
		};
		ServiceFactory<IODevice> serviceFactory = new ReflectionServiceFactory<>(
				STUB_CLASS_NAME, null/* addressSetterMethodName */);
		return new GPIOClientMultiplexed(serviceFactory, 1000 /* openTimeout */);
	}

	private void sendReceiveAsync(GPIOClientMultiplexed client, int port)
			throws Exception {
		GPIOEventHandlerTest eventHandler = new GPIOEventHandlerTest();
		client.requestOpeningChannel(InetAddress.getLocalHost().getHostName(),
				port, eventHandler);
		GPIOChannelOpenedEvent openEvent = eventHandler.awaitChannelOpened();

		// wait for KEEP_ALIVE event
		Message message = client
				.awaitReceivedData(openEvent.getChannel(), 3000);
		Assert.assertEquals(message.getMessageHeader().getMessageType(),
				MessageType.KEEP_ALIVE);

		// wait for CONNECTION_ATTEMPTED event
		message = client.awaitReceivedData(openEvent.getChannel(), 3000);
		Assert.assertEquals(message.getMessageHeader().getMessageType(),
				MessageType.CONNECTION_ATTEMPTED);

		// wait for STATE_CHANGED event
		message = client.awaitReceivedData(openEvent.getChannel(), 3000);
		Assert.assertEquals(message.getMessageHeader().getMessageType(),
				MessageType.STATE_CHANGED);

		// close the client channel
		client.requestClosingChannel(openEvent.getChannel());
		GPIOChannelClosedEvent closeEvent = eventHandler.awaitChannelClosed();
		Assert.assertNull(closeEvent.getPendingReceivedData());
		Assert.assertNull(closeEvent.getPendingSendingData());
	}

	@Test
	public void awaitReceivedDataTimeout() throws Exception {
		// open a client channel
		ServiceFactory<IODevice> serviceFactory = new ReflectionServiceFactory<>(
				STUB_CLASS_NAME, ADDR_SETTER_METHOD_NAME);
		final GPIOClientMultiplexed client = new GPIOClientMultiplexed(
				serviceFactory, 1000 /* openTimeout */);
		GPIOEventHandlerTest eventHandler = new GPIOEventHandlerTest();
		client.requestOpeningChannel(InetAddress.getLocalHost().getHostName(),
				0 /* port */, eventHandler);
		final GPIOChannelOpenedEvent openEvent = eventHandler
				.awaitChannelOpened();

		// try to get a message directly without waiting
		Message msg = client.awaitReceivedData(openEvent.getChannel(),
				GPIOClientMultiplexed.RETURN_IMMEDIATELY);
		Assert.assertNull(msg);

		// try to get a message with a time out
		try {
			client.awaitReceivedData(openEvent.getChannel(), 1000);
			Assert.fail();
		} catch (GPIOTimeoutException e) {
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
				ResetConfiguration ex = new ResetConfiguration(
						new MessageHeader(100));
				client.requestSendingData(openEvent.getChannel(), ex);
				return null;
			}

		});

		msg = client.awaitReceivedData(openEvent.getChannel(),
				GPIOClientMultiplexed.NO_TIMEOUT);
		Assert.assertEquals(msg.getMessageHeader().getId(), 100);
		Assert.assertTrue(System.currentTimeMillis() >= endTime);
		future.get(3000, TimeUnit.MILLISECONDS);
		threadPool.shutdown();

		// close the client channel
		client.requestClosingChannel(openEvent.getChannel());
	}
}
