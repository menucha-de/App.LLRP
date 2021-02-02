package havis.llrpservice.server.gpio;

import havis.device.io.Configuration;
import havis.device.io.Direction;
import havis.device.io.IOConfiguration;
import havis.device.io.IODevice;
import havis.device.io.State;
import havis.device.io.Type;
import havis.device.io.exception.ImplementationException;
import havis.llrpservice.common.io.PathHandler;
import havis.llrpservice.common.io.XMLFile;
import havis.llrpservice.sbc.gpio.GPIOClientMultiplexed;
import havis.llrpservice.sbc.gpio.GPIOEventHandler;
import havis.llrpservice.sbc.gpio.GPIOException;
import havis.llrpservice.sbc.gpio.GPIOInvalidPortNumException;
import havis.llrpservice.sbc.gpio.GPIOUnknownChannelException;
import havis.llrpservice.sbc.gpio.event.GPIOChannelClosedEvent;
import havis.llrpservice.sbc.gpio.message.GetConfigurationResponse;
import havis.llrpservice.sbc.gpio.message.Message;
import havis.llrpservice.sbc.gpio.message.MessageType;
import havis.llrpservice.sbc.gpio.message.ResetConfiguration;
import havis.llrpservice.sbc.gpio.message.ResetConfigurationResponse;
import havis.llrpservice.sbc.gpio.message.SetConfiguration;
import havis.llrpservice.sbc.gpio.message.SetConfigurationResponse;
import havis.llrpservice.server.configuration.ServerConfiguration;
import havis.llrpservice.server.configuration.ServerInstanceConfiguration;
import havis.llrpservice.server.event.EventQueue;
import havis.llrpservice.server.event.GPIOMessageEvent;
import havis.llrpservice.server.gpio.GPIOMessageHandler.InternalRequest;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import mockit.Capturing;
import mockit.Deencapsulation;
import mockit.Delegate;
import mockit.Invocation;
import mockit.Mocked;
import mockit.NonStrictExpectations;
import mockit.Verifications;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class GPIOMessageHandlerTest {

	private static final Path BASE_PATH = new PathHandler()
			.toAbsolutePath("havis/llrpservice/server/gpio");
	private static final Path SERVER_CONFIG_PATH = BASE_PATH
			.resolve("LLRPServerConfiguration.xml");
	private static final Path INSTANCE_CONFIG_PATH = BASE_PATH
			.resolve("LLRPServerInstanceConfiguration.xml");

	private ServerConfiguration serverConfig;
	private ServerInstanceConfiguration serverInstanceConfig;

	@BeforeClass
	public void init() throws Exception {
		serverConfig = new ServerConfiguration(new XMLFile<>(
				LLRPServerConfigurationType.class,
				SERVER_CONFIG_PATH /* initialPath */, null/* latestPath */));
		serverConfig.open();
		serverInstanceConfig = new ServerInstanceConfiguration(
				serverConfig,
				new XMLFile<>(LLRPServerInstanceConfigurationType.class,
						INSTANCE_CONFIG_PATH /* initialPath */, null/* latestPath */));
		serverInstanceConfig.open();
	}

	@AfterClass
	public void cleanUp() throws Exception {
		serverInstanceConfig.close();
		serverConfig.close();
	}

	@Test
	public void openClose() throws Exception {
		EventQueue eventQueue = new EventQueue();
		GPIOMessageHandler messageHandler = new GPIOMessageHandler(
				serverConfig, serverInstanceConfig, eventQueue, null /* serviceFactory */);
		final Semaphore isOpened = new Semaphore(0);
		final Semaphore isClosed = new Semaphore(0);
		// add a listener
		GPIOMessageHandlerListener listener = new GPIOMessageHandlerListener() {

			@Override
			public void opened() {
				isOpened.release();
			}

			@Override
			public void closed(List<Object> pendingRequests, Throwable t) {
				isClosed.release();
			}
		};
		messageHandler.addListener(listener);
		// start and open the message handler
		ExecutorService threadPool = Executors.newFixedThreadPool(1);
		messageHandler.open(threadPool);
		// await an "opened" event
		isOpened.tryAcquire(3, TimeUnit.SECONDS);
		// close the message handler
		messageHandler.close();
		// await an "closed" event
		isClosed.tryAcquire(3, TimeUnit.SECONDS);

		// remove the listener
		messageHandler.removeListener(listener);

		// the events were not send to any listeners
		Assert.assertEquals(isOpened.availablePermits(), 0);
		Assert.assertEquals(isClosed.availablePermits(), 0);

		threadPool.shutdown();
	}

	@Test
	public void closedByRemote(@Mocked final GPIOMessageHandlerListener listener)
			throws Exception {

		final GPIOClientMultiplexed client = new GPIOClientMultiplexed(null, 0);

		new NonStrictExpectations(GPIOClientMultiplexed.class) {
			GPIOEventHandler evHandler;
			{
				client.requestOpeningChannel(anyString, anyInt,
						withInstanceOf(GPIOEventHandler.class));
				result = new Delegate<GPIOClientMultiplexed>() {
					@SuppressWarnings("unused")
					public void requestOpeningChannel(Invocation inv,
							String addr, int port, GPIOEventHandler eventHandler) {
						evHandler = eventHandler;
						inv.proceed(addr, port, eventHandler);
					}
				};

				client.awaitReceivedData(withInstanceOf(SocketChannel.class),
						anyLong);
				result = new Delegate<GPIOClientMultiplexed>() {
					@SuppressWarnings("unused")
					public Message awaitReceivedData(SocketChannel channel,
							long timeout) throws InterruptedException,
							GPIOUnknownChannelException {
						evHandler.channelClosed(new GPIOChannelClosedEvent(
								null /* serverSocketChannet */, channel,
								null /* pendingSendingData */,
								null /* pendingReceivedData */,
								new GPIOException("oh")));
						throw new GPIOUnknownChannelException("huhu");
					}
				};
			}
		};
		EventQueue eventQueue = new EventQueue();
		GPIOMessageHandler messageHandler = new GPIOMessageHandler(
				serverConfig, serverInstanceConfig, eventQueue, null /* serviceFactory */);
		// add a listener
		messageHandler.addListener(listener);
		// start and open the message handler
		ExecutorService threadPool = Executors.newFixedThreadPool(1);
		messageHandler.open(threadPool);
		// when the message handler has been opened then close the connection
		// from remote side
		new Verifications() {
			{
				// an "opened" event is sent
				listener.opened();
				times = 1;
				// a "closed" event is sent with an exception
				Throwable exception;
				listener.closed(null /* pendingRequests */,
						exception = withCapture());
				times = 1;
				Assert.assertTrue(exception.getMessage().equals("oh"));
			}
		};
		messageHandler.close();
		threadPool.shutdown();
	}

	@Test
	public void openError(@Mocked final GPIOMessageHandlerListener listener)
			throws Exception {
		final GPIOClientMultiplexed client = new GPIOClientMultiplexed(null, 0);
		new NonStrictExpectations(GPIOClientMultiplexed.class) {
			{
				client.requestOpeningChannel(anyString, anyInt,
						withInstanceOf(GPIOEventHandler.class));
			}
		};
		EventQueue eventQueue = new EventQueue();
		GPIOMessageHandler messageHandler = new GPIOMessageHandler(
				serverConfig, serverInstanceConfig, eventQueue, null /* serviceFactory */);
		messageHandler.addListener(listener);
		// start the message handler
		ExecutorService threadPool = Executors.newFixedThreadPool(1);
		// open the message handler: the opening fails due to timeout
		Deencapsulation.setField(messageHandler, "openCloseTimeout", 500);
		try {
			messageHandler.open(threadPool);
			Assert.fail();
		} catch (TimeoutException e) {
			Assert.assertTrue(e.getMessage().contains("500 ms"));
		}
		new Verifications() {
			{
				// no "opened" event is sent
				listener.opened();
				times = 0;
				// no "closed" event is sent
				listener.closed(null /* pendingRequests */, null /* throwable */);
				times = 0;
				listener.closed(null /* pendingRequests */,
						withInstanceOf(GPIOUnknownChannelException.class));
				times = 0;
			}
		};
		messageHandler.close();
		threadPool.shutdown();
	}

	@Test
	public void closeError(@Mocked final GPIOMessageHandlerListener listener)
			throws Exception {

		final GPIOClientMultiplexed client = new GPIOClientMultiplexed(null, 0);
		new NonStrictExpectations(GPIOClientMultiplexed.class) {
			{
				client.requestClosingChannel(withInstanceOf(SocketChannel.class));
			}
		};
		EventQueue eventQueue = new EventQueue();
		GPIOMessageHandler messageHandler = new GPIOMessageHandler(
				serverConfig, serverInstanceConfig, eventQueue, null /* serviceFactory */);
		messageHandler.addListener(listener);
		// open and close the message handler
		// the closing fails due to timeout
		ExecutorService threadPool = Executors.newFixedThreadPool(1);
		messageHandler.open(threadPool);
		Deencapsulation.setField(messageHandler, "openCloseTimeout", 500);
		try {
			messageHandler.close();
			Assert.fail();
		} catch (TimeoutException e) {
			Assert.assertTrue(e.getMessage().contains("500 ms"));
		}
		new Verifications() {
			{
				// an "opened" event is sent
				listener.opened();
				times = 1;
				// no "closed" event is sent
				listener.closed(null /* pendingRequests */, null /* throwable */);
				times = 0;
				listener.closed(null /* pendingRequests */,
						withInstanceOf(GPIOUnknownChannelException.class));
				times = 0;
			}
		};
		// reset the mocked method and close the message handler cleanly
		new NonStrictExpectations() {
			{
				client.requestClosingChannel(withInstanceOf(SocketChannel.class));
				result = new Delegate<GPIOMessageHandler>() {
					@SuppressWarnings("unused")
					public void requestClosingChannel(Invocation inv,
							SelectableChannel channel) {
						inv.proceed(channel);
					}
				};
			}
		};
		messageHandler.close();
		threadPool.shutdown();
	}

	@Test
	public void configuration(@Mocked final _IODeviceStubTest controller,
			@Mocked final GPIOMessageHandlerListener listener) throws Exception {
		new NonStrictExpectations() {
			{
				controller.getConfiguration(Type.IO, (short) 0 /* pinId */);
				result = Arrays
						.asList(new Configuration[] {
								new IOConfiguration((short) 1 /* pinId */,
										Direction.INPUT, State.HIGH, true /* gpiEventsEnabled */),
								new IOConfiguration((short) 2 /* pinId */,
										Direction.INPUT, State.HIGH, true /* gpiEventsEnabled */),
								new IOConfiguration((short) 3 /* pinId */,
										Direction.OUTPUT, State.HIGH, true /* gpiEventsEnabled */),
								new IOConfiguration((short) 4 /* pinId */,
										Direction.OUTPUT, State.HIGH, true /* gpiEventsEnabled */) });

				controller.getConfiguration(Type.IO, (short) 1 /* pinId */);
				result = Arrays
						.asList(new Configuration[] { new IOConfiguration(
								(short) 1 /* pinId */, Direction.INPUT,
								State.HIGH, true /* gpiEventsEnabled */) });

				controller.getConfiguration(Type.IO, (short) 3 /* pinId */);
				result = Arrays
						.asList(new Configuration[] { new IOConfiguration(
								(short) 3 /* pinId */, Direction.INPUT,
								State.HIGH, true /* gpiEventsEnabled */) });
			}
		};
		EventQueue eventQueue = new EventQueue();
		GPIOMessageHandler messageHandler = new GPIOMessageHandler(
				serverConfig, serverInstanceConfig, eventQueue, null /* serviceFactory */);
		// open and start the message handler
		ExecutorService threadPool = Executors.newFixedThreadPool(1);
		messageHandler.open(threadPool);
		// send "SetConfiguration"
		messageHandler
				.requestExecution(false /* reset */, Arrays
						.asList(new Configuration[] { new IOConfiguration(
								(short) 1 /* pinId */, Direction.INPUT,
								State.HIGH, true /* gpiEventsEnabled */) }));
		// send "GetConfiguration" message
		// try to get configurations without GPIO ports
		messageHandler.requestConfiguration(
				Arrays.asList(new Type[] { Type.IO }), null /* gpiPortNum */,
				null /* gpoPortNum */);
		// send "ResetConfiguration", "SetConfiguration" messages
		messageHandler.requestExecution(true/* reset */,
				new ArrayList<Configuration>());
		// await messages
		List<Message> messages = await(eventQueue, new MessageType[] {
				MessageType.SET_CONFIGURATION,
				MessageType.SET_CONFIGURATION_RESPONSE,
				MessageType.GET_CONFIGURATION,
				MessageType.GET_CONFIGURATION_RESPONSE,
				MessageType.RESET_CONFIGURATION,
				MessageType.RESET_CONFIGURATION_RESPONSE,
				MessageType.SET_CONFIGURATION,
				MessageType.SET_CONFIGURATION_RESPONSE });
		int i = 0;
		SetConfiguration set = (SetConfiguration) messages.get(i++);
		SetConfigurationResponse setResponse = (SetConfigurationResponse) messages
				.get(i++);
		Assert.assertEquals(set.getMessageHeader().getId(), setResponse
				.getMessageHeader().getId());

		i++;
		GetConfigurationResponse confResponse = (GetConfigurationResponse) messages
				.get(i++);
		List<Configuration> conf = confResponse.getConfiguration();
		Assert.assertEquals(conf.size(), 0);

		ResetConfiguration reset = (ResetConfiguration) messages.get(i++);
		ResetConfigurationResponse resetResponse = (ResetConfigurationResponse) messages
				.get(i++);
		Assert.assertEquals(reset.getMessageHeader().getId(), resetResponse
				.getMessageHeader().getId());

		set = (SetConfiguration) messages.get(i++);
		setResponse = (SetConfigurationResponse) messages.get(i);
		Assert.assertEquals(set.getMessageHeader().getId(), setResponse
				.getMessageHeader().getId());

		// send "GetConfiguration" message
		// get all input port configurations
		messageHandler.requestConfiguration(
				Arrays.asList(new Type[] { Type.IO }),
				(short) 0 /* gpiPortNum */, null /* gpoPortNum */);
		messages = await(eventQueue, new MessageType[] {
				MessageType.GET_CONFIGURATION,
				MessageType.GET_CONFIGURATION_RESPONSE });
		confResponse = (GetConfigurationResponse) messages.get(1);
		conf = confResponse.getConfiguration();
		Assert.assertEquals(conf.size(), 2);
		for (int j = 0; j < 2; j++) {
			IOConfiguration conf1 = (IOConfiguration) conf.get(j);
			Assert.assertEquals(conf1.getId(), j + 1);
		}

		// send "GetConfiguration" message
		// get a single input port configuration
		messageHandler.requestConfiguration(
				Arrays.asList(new Type[] { Type.IO }),
				(short) 1 /* gpiPortNum */, null /* gpoPortNum */);
		messages = await(eventQueue, new MessageType[] {
				MessageType.GET_CONFIGURATION,
				MessageType.GET_CONFIGURATION_RESPONSE });
		confResponse = (GetConfigurationResponse) messages.get(1);
		conf = confResponse.getConfiguration();
		Assert.assertEquals(conf.size(), 1);
		IOConfiguration conf1 = (IOConfiguration) conf.get(0);
		Assert.assertEquals(conf1.getId(), 1);

		// send "GetConfiguration" message
		// get all output port configurations
		messageHandler.requestConfiguration(
				Arrays.asList(new Type[] { Type.IO }), null /* gpiPortNum */,
				(short) 0 /* gpoPortNum */);
		messages = await(eventQueue, new MessageType[] {
				MessageType.GET_CONFIGURATION,
				MessageType.GET_CONFIGURATION_RESPONSE });
		confResponse = (GetConfigurationResponse) messages.get(1);
		conf = confResponse.getConfiguration();
		Assert.assertEquals(conf.size(), 2);
		for (int j = 0; j < 2; j++) {
			conf1 = (IOConfiguration) conf.get(j);
			Assert.assertEquals(conf1.getId(), j + 3);
		}

		// send "GetConfiguration" message
		// get a single output port configuration
		messageHandler.requestConfiguration(
				Arrays.asList(new Type[] { Type.IO }),
				(short) 3 /* gpiPortNum */, null /* gpoPortNum */);
		messages = await(eventQueue, new MessageType[] {
				MessageType.GET_CONFIGURATION,
				MessageType.GET_CONFIGURATION_RESPONSE });
		confResponse = (GetConfigurationResponse) messages.get(1);
		conf = confResponse.getConfiguration();
		Assert.assertEquals(conf.size(), 1);
		conf1 = (IOConfiguration) conf.get(0);
		Assert.assertEquals(conf1.getId(), 3);

		// clean up
		messageHandler.close();
		threadPool.shutdown();
	}

	@Test
	public void configurationError1(@Mocked final _IODeviceStubTest controller,
			@Capturing final GPIOMessageHandlerListener listener)
			throws Exception {
		// create a message handler with a listener
		final GPIOMessageHandler messageHandler = new GPIOMessageHandler(
				serverConfig, serverInstanceConfig, new EventQueue(), null /* serviceFactory */);
		messageHandler.addListener(listener);
		// replace internal request queue with own instance
		final List<InternalRequest> requestQueue = new ArrayList<>();
		Deencapsulation.setField(messageHandler, "requestQueue", requestQueue);
		final Object pendingRequest = new Object();
		new NonStrictExpectations() {
			{
				controller.getConfiguration(Type.IO, anyShort /* pinId */);
				result = new Delegate<IODevice>() {
					@SuppressWarnings("unused")
					List<Configuration> getConfiguration(Type type, short pin)
							throws ImplementationException {
						// enqueue a pending request
						requestQueue.add(messageHandler.new InternalRequest(
								MessageType.GET_CONFIGURATION, pendingRequest));
						return Arrays
								.asList(new Configuration[] { new IOConfiguration(
										(short) 1 /* pinId */, Direction.INPUT,
										State.HIGH, true /* gpiEventsEnabled */) });
					}
				};
			}
		};
		ExecutorService threadPool = Executors.newFixedThreadPool(2);
		// open and start the message handler
		messageHandler.open(threadPool);
		Thread.sleep(100);

		// send "GetConfiguration" message
		// try to get an input port configuration for an output port
		messageHandler.requestConfiguration(
				Arrays.asList(new Type[] { Type.IO }), null /* gpiPortNum */,
				(short) 1 /* gpoPortNum */);
		Thread.sleep(100);

		new Verifications() {
			{
				// a close event is received with the pending request and an
				// exception
				List<Object> pendingRequests;
				listener.closed(pendingRequests = withCapture(),
						withInstanceOf(GPIOInvalidPortNumException.class));
				times = 1;
				Assert.assertEquals(pendingRequests.size(), 1);
				Assert.assertEquals(pendingRequests.get(0), pendingRequest);
			}
		};

		// clean up
		messageHandler.close();
		threadPool.shutdown();
	}

	@Test
	public void configurationError2(@Mocked final _IODeviceStubTest controller,
			@Mocked final GPIOMessageHandlerListener listener) throws Exception {
		new NonStrictExpectations() {
			{
				controller.getConfiguration(Type.IO, anyShort /* pinId */);
				result = Arrays
						.asList(new Configuration[] { new IOConfiguration(
								(short) 2 /* pinId */, Direction.OUTPUT,
								State.HIGH, true /* gpiEventsEnabled */) });
			}
		};
		GPIOMessageHandler messageHandler = new GPIOMessageHandler(
				serverConfig, serverInstanceConfig, new EventQueue(), null /* serviceFactory */);
		messageHandler.addListener(listener);
		ExecutorService threadPool = Executors.newFixedThreadPool(1);
		// open and start the message handler
		messageHandler.open(threadPool);
		Thread.sleep(100);

		// send "GetConfiguration" message
		// try to get an output port configuration for an input port
		messageHandler.requestConfiguration(
				Arrays.asList(new Type[] { Type.IO }),
				(short) 2 /* gpiPortNum */, null /* gpoPortNum */);
		Thread.sleep(100);

		new Verifications() {
			{
				// a close event is received with an exception
				List<Object> pendingRequests;
				listener.closed(pendingRequests = withCapture(),
						withInstanceOf(GPIOInvalidPortNumException.class));
				times = 1;
				Assert.assertEquals(pendingRequests.size(), 0);
			}
		};

		// clean up
		messageHandler.close();
		threadPool.shutdown();
	}

	private List<Message> await(EventQueue eventQueue,
			MessageType[] messageTypes) throws InterruptedException,
			TimeoutException {
		List<Message> ret = new ArrayList<>();
		for (MessageType messageType : messageTypes) {
			GPIOMessageEvent event = (GPIOMessageEvent) eventQueue.take(3000);
			Message msg = event.getMessage();
			Assert.assertEquals(msg.getMessageHeader().getMessageType(),
					messageType);
			ret.add(msg);
		}
		return ret;
	}
}
