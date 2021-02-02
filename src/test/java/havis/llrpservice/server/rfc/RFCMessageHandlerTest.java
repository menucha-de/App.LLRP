package havis.llrpservice.server.rfc;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import havis.device.rf.RFConsumer;
import havis.device.rf.capabilities.Capabilities;
import havis.device.rf.capabilities.CapabilityType;
import havis.device.rf.capabilities.DeviceCapabilities;
import havis.device.rf.capabilities.RegulatoryCapabilities;
import havis.device.rf.configuration.AntennaConfiguration;
import havis.device.rf.configuration.AntennaProperties;
import havis.device.rf.configuration.Configuration;
import havis.device.rf.configuration.ConfigurationType;
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
import havis.device.rf.tag.result.ReadResult.Result;
import havis.llrpservice.common.io.PathHandler;
import havis.llrpservice.common.io.XMLFile;
import havis.llrpservice.data.message.parameter.AISpec;
import havis.llrpservice.data.message.parameter.AISpecStopTrigger;
import havis.llrpservice.data.message.parameter.AISpecStopTriggerType;
import havis.llrpservice.data.message.parameter.AccessCommand;
import havis.llrpservice.data.message.parameter.AccessSpec;
import havis.llrpservice.data.message.parameter.AccessSpecStopTrigger;
import havis.llrpservice.data.message.parameter.AccessSpecStopTriggerType;
import havis.llrpservice.data.message.parameter.C1G2Read;
import havis.llrpservice.data.message.parameter.C1G2TagSpec;
import havis.llrpservice.data.message.parameter.C1G2TargetTag;
import havis.llrpservice.data.message.parameter.GPIEvent;
import havis.llrpservice.data.message.parameter.GPITriggerValue;
import havis.llrpservice.data.message.parameter.InventoryParameterSpec;
import havis.llrpservice.data.message.parameter.Parameter;
import havis.llrpservice.data.message.parameter.ProtocolId;
import havis.llrpservice.data.message.parameter.ROSpec;
import havis.llrpservice.data.message.parameter.ROSpecCurrentState;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.data.message.parameter.TagObservationTrigger;
import havis.llrpservice.data.message.parameter.TagObservationTriggerType;
import havis.llrpservice.sbc.rfc.RFCClientMultiplexed;
import havis.llrpservice.sbc.rfc.RFCConnectionException;
import havis.llrpservice.sbc.rfc.RFCEventHandler;
import havis.llrpservice.sbc.rfc.RFCException;
import havis.llrpservice.sbc.rfc.RFCUnknownChannelException;
import havis.llrpservice.sbc.rfc.event.RFCChannelClosedEvent;
import havis.llrpservice.sbc.rfc.message.ExecuteResponse;
import havis.llrpservice.sbc.rfc.message.GetCapabilitiesResponse;
import havis.llrpservice.sbc.rfc.message.GetConfigurationResponse;
import havis.llrpservice.sbc.rfc.message.Message;
import havis.llrpservice.sbc.rfc.message.MessageType;
import havis.llrpservice.sbc.rfc.message.ResetConfiguration;
import havis.llrpservice.sbc.rfc.message.ResetConfigurationResponse;
import havis.llrpservice.sbc.rfc.message.SetConfiguration;
import havis.llrpservice.sbc.rfc.message.SetConfigurationResponse;
import havis.llrpservice.server.configuration.ServerConfiguration;
import havis.llrpservice.server.configuration.ServerInstanceConfiguration;
import havis.llrpservice.server.event.Event;
import havis.llrpservice.server.event.EventQueue;
import havis.llrpservice.server.event.RFCMessageEvent;
import havis.llrpservice.server.rfc.RFCMessageHandler.InternalRequest;
import havis.llrpservice.server.rfc.messageData.ExecuteResponseData;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;
import havis.util.platform.Platform;
import mockit.Deencapsulation;
import mockit.Delegate;
import mockit.Expectations;
import mockit.Invocation;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import mockit.Verifications;

@Test
public class RFCMessageHandlerTest {

	private static final Path SERVER_CONFIG_PATH = new PathHandler()
			.toAbsolutePath("havis/llrpservice/server/rfc/LLRPServerConfiguration.xml");
	private static final Path INSTANCE_CONFIG_PATH = new PathHandler()
			.toAbsolutePath("havis/llrpservice/server/rfc/LLRPServerInstanceConfiguration.xml");

	private ServerConfiguration serverConfig;
	private ServerInstanceConfiguration serverInstanceConfig;

	@BeforeClass
	public void init() throws Exception {
		serverConfig = new ServerConfiguration(new XMLFile<>(LLRPServerConfigurationType.class,
				SERVER_CONFIG_PATH /* initialPath */, null/* latestPath */));
		serverConfig.open();
		serverInstanceConfig = new ServerInstanceConfiguration(serverConfig,
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
	public void openClose(@Mocked Platform platform) throws Exception {
		EventQueue eventQueue = new EventQueue();
		RFCMessageHandler messageHandler = new RFCMessageHandler(serverConfig, serverInstanceConfig,
				eventQueue, null /* serviceFactory */, platform);
		final Semaphore isOpened = new Semaphore(0);
		final Semaphore isClosed = new Semaphore(0);
		// add a listener
		RFCMessageHandlerListener listener = new RFCMessageHandlerListener() {

			@Override
			public void opened() {
				isOpened.release();
			}

			@Override
			public void closed(List<Object> pendingRequests, Throwable t) {
				isClosed.release();
			}

			@Override
			public void removedAccessSpec(long accessSpecId) {
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
	public void closedByRemote(@Mocked final RFCMessageHandlerListener listener,
			@Mocked Platform platform) throws Exception {
		// create a message handler with a listener
		final RFCMessageHandler messageHandler = new RFCMessageHandler(serverConfig,
				serverInstanceConfig, new EventQueue(), null /* serviceFactory */, platform);
		messageHandler.addListener(listener);
		// replace internal request queue with own instance
		final List<InternalRequest> requestQueue = new ArrayList<>();
		Deencapsulation.setField(messageHandler, "requestQueue", requestQueue);
		final Object pendingRequest = new Object();
		new MockUp<RFCClientMultiplexed>() {
			RFCEventHandler evHandler;

			@Mock
			public void requestOpeningChannel(Invocation inv, String addr, int port,
					RFCEventHandler eventHandler) {
				evHandler = eventHandler;
				inv.proceed(addr, port, eventHandler);
			}

			@Mock
			public Message awaitReceivedData(SocketChannel channel, long timeout)
					throws InterruptedException, RFCException {
				// add a pending request to the _message handler_
				requestQueue.add(messageHandler.new InternalRequest(MessageType.GET_CONFIGURATION,
						pendingRequest));
				// emulate the closing of the connection from remote
				// side
				evHandler.channelClosed(new RFCChannelClosedEvent(null /* serverSocketChannet */,
						channel, null /* pendingSendingData */, null /* pendingReceivedData */,
						new RFCException("oh")));
				throw new RFCUnknownChannelException("huhu");
			}
		};
		// start and open the message handler
		ExecutorService threadPool = Executors.newFixedThreadPool(1);
		messageHandler.open(threadPool);
		Thread.sleep(1);
		// after the message handler has been opened the connection is closed
		// from remote side
		new Verifications() {
			{
				// an "opened" event is sent
				listener.opened();
				times = 1;
				// a "closed" event is sent with a pending request and an
				// exception
				List<Object> pendingRequests;
				Throwable exception;
				listener.closed(pendingRequests = withCapture(), exception = withCapture());
				times = 1;
				Assert.assertEquals(pendingRequests.size(), 1);
				Assert.assertEquals(pendingRequests.get(0), pendingRequest);
				Assert.assertTrue(exception.getMessage().equals("oh"));
			}
		};
		messageHandler.close();
		threadPool.shutdown();
	}

	// @Mocked
	// RFCMessageHandlerListener listener;
	// @Mocked
	// Platform platform;

	public void openError(//
			@Mocked final RFCMessageHandlerListener listener, @Mocked Platform platform//
	) throws Exception {
		final RFCClientMultiplexed client = new RFCClientMultiplexed(null, 0, 0, platform);
		new Expectations(RFCClientMultiplexed.class) {
			{
				client.requestOpeningChannel(anyString, anyInt,
						withInstanceOf(RFCEventHandler.class));
			}
		};
		EventQueue eventQueue = new EventQueue();
		RFCMessageHandler messageHandler = new RFCMessageHandler(serverConfig, serverInstanceConfig,
				eventQueue, null /* serviceFactory */, platform);
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
						withInstanceOf(RFCUnknownChannelException.class));
				times = 0;
			}
		};
		messageHandler.close();
		threadPool.shutdown();
	}

	// @Mocked
	// RFCMessageHandlerListener listener;
	// @Mocked
	// Platform platform;

	public void closeError(//
			@Mocked final RFCMessageHandlerListener listener, @Mocked Platform platform//
	) throws Exception {
		final RFCClientMultiplexed client = new RFCClientMultiplexed(null, 0, 0, platform);
		new Expectations(RFCClientMultiplexed.class) {
			{
				client.requestClosingChannel(withInstanceOf(SelectableChannel.class));
			}
		};
		EventQueue eventQueue = new EventQueue();
		RFCMessageHandler messageHandler = new RFCMessageHandler(serverConfig, serverInstanceConfig,
				eventQueue, null /* serviceFactory */, platform);
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
						withInstanceOf(RFCUnknownChannelException.class));
				times = 0;
			}
		};
		// reset the mocked method and close the message handler cleanly
		new Expectations() {
			{
				client.requestClosingChannel(withInstanceOf(SelectableChannel.class));
				result = new Delegate<RFCMessageHandler>() {
					@SuppressWarnings("unused")
					public void requestClosingChannel(Invocation inv, SelectableChannel channel)
							throws RFCConnectionException {
						inv.proceed(channel);
					}
				};
			}
		};
		messageHandler.close();
		threadPool.shutdown();
	}

	// @Mocked
	// _RFDeviceStubTest controller;
	// @Mocked
	// Platform platform;

	@SuppressWarnings("unchecked")
	public void execute(//
			@Mocked final _RFDeviceStubTest controller, @Mocked final Platform platform//
	) throws Exception {
		final Semaphore latch = new Semaphore(0);
		final long tagDataId = 1;
		final String opSpecId = "2";
		new Expectations() {
			RFConsumer consumer;
			int inventoryCounter = 0;
			{
				controller.openConnection(withInstanceOf(RFConsumer.class), anyInt);
				result = new Delegate<_RFDeviceStubTest>() {
					@SuppressWarnings("unused")
					public void openConnection(RFConsumer c, int timeout)
							throws ConnectionException, CommunicationException, ParameterException,
							ImplementationException {
						consumer = c;
					}
				};

				controller.execute(withInstanceOf(List.class) /* anntennaIds */,
						withInstanceOf(List.class) /* filters */,
						withInstanceOf(List.class) /* tagOperations */);
				result = new Delegate<_RFDeviceStubTest>() {
					@SuppressWarnings("unused")
					public List<TagData> execute(List<Short> antennas, List<Filter> filter,
							List<TagOperation> operations) throws ConnectionException,
							CommunicationException, ParameterException, ImplementationException {
						inventoryCounter++;
						if (inventoryCounter != 2) {
							// read operation for the filter and a
							// RequestOperation exist
							Assert.assertEquals(operations.size(), 2);
							Assert.assertTrue(operations.get(0) instanceof ReadOperation);
						} else {
							Assert.assertEquals(operations.size(), 1);
						}
						Assert.assertTrue(
								operations.get(operations.size() - 1) instanceof RequestOperation);
						// slow down the execution due to AISpecStopTrigger.NULL
						try {
							if (!latch.tryAcquire(500, TimeUnit.SECONDS)) {
								Assert.fail();
							}
						} catch (InterruptedException e) {
							Assert.fail();
						}
						ArrayList<TagData> ret = new ArrayList<TagData>();
						ArrayList<OperationResult> accessResults = new ArrayList<OperationResult>();

						TagData tag = new TagData();
						tag.setTagDataId(tagDataId);
						tag.setEpc(new byte[] { 1, 2 });
						tag.setPc((short) 3);
						tag.setXpc(4);
						tag.setAntennaID((short) 1);
						tag.setRssi(6);
						tag.setChannel((short) 7);
						tag.setResultList(accessResults);

						ret.add(tag);
						if (inventoryCounter != 2) {
							ReadResult rdRes = new ReadResult();
							rdRes.setOperationId(AccessSpecExecutor.GENERATED_ID_PREFIX + "1");
							rdRes.setReadData(new byte[] { 3 });
							rdRes.setResult(Result.SUCCESS);

							accessResults.add(rdRes);
						}
						// call callback for RequestOperation
						consumer.getOperations(ret.get(0));

						ReadResult rdRes = new ReadResult();
						rdRes.setOperationId(opSpecId);
						rdRes.setReadData(new byte[] { 4 });
						rdRes.setResult(Result.SUCCESS);
						accessResults.add(rdRes);
						return ret;
					}
				};

				platform.hasUTCClock();
				result = true;
			}
		};
		EventQueue eventQueue = new EventQueue();
		RFCMessageHandler messageHandler = new RFCMessageHandler(serverConfig, serverInstanceConfig,
				eventQueue, null /* serviceFactory */, platform);
		// open and start the message handler
		ExecutorService threadPool = Executors.newFixedThreadPool(1);
		messageHandler.open(threadPool);
		// add an AccessSpec without a filter and an AccessSpec with a filter to
		// the message handler
		int antennaId = 1;
		ProtocolId protocolId = ProtocolId.EPC_GLOBAL_C1G2;
		long roSpecId = 123;
		long accessSpecId1 = 567;
		messageHandler.add(createAccessSpec(accessSpecId1, antennaId, protocolId, roSpecId,
				false /* setTagFilter */));
		final AccessSpec accessSpec2 = createAccessSpec(accessSpecId1 + 1, antennaId, protocolId,
				roSpecId, true /* setTagFilter */);
		messageHandler.add(accessSpec2);
		long startTimeStamp = System.currentTimeMillis();
		// start a ROSpec (the latch blocks the first inventory execution)
		messageHandler.requestExecution(createROSpec(roSpecId, antennaId, protocolId));
		// enqueue a capability request and a second ROSpec
		messageHandler
				.requestCapabilities(Arrays.asList(new CapabilityType[] { CapabilityType.ALL }));
		messageHandler.requestExecution(createROSpec(roSpecId + 1, antennaId, protocolId));
		// remove the AccessSpec with the filter (the change is ignored for the
		// current inventory)
		messageHandler.remove(accessSpec2.getAccessSpecId());
		messageHandler.add(accessSpec2);
		messageHandler.remove(accessSpec2.getAccessSpecId());
		// dequeue the second ROSpec
		messageHandler.cancelExecution(roSpecId + 1);
		// release the first inventory and wait for the start of the second
		// inventory (the "GetCapabilities" request is processed before the
		// second inventory)
		latch.release();
		await(eventQueue, new MessageType[] { MessageType.EXECUTE, MessageType.GET_OPERATIONS,
				MessageType.GET_OPERATIONS_RESPONSE });
		List<RFCMessageEvent> messageEvents = await(eventQueue,
				new MessageType[] { MessageType.EXECUTE_RESPONSE });
		RFCMessageEvent rfcMessageEvent = (RFCMessageEvent) messageEvents.get(0);
		ExecuteResponse executeResponse = (ExecuteResponse) rfcMessageEvent.getMessage();
		Assert.assertTrue(executeResponse.getTimeStamp().isUtc());
		Assert.assertTrue(executeResponse.getTimeStamp().getTimestamp() >= startTimeStamp);
		ExecuteResponseData executeResponseData = (ExecuteResponseData) rfcMessageEvent.getData();
		Assert.assertEquals(executeResponseData.getRoSpecId(), roSpecId);
		Assert.assertEquals(executeResponseData.getSpecIndex(), 1);
		Assert.assertEquals(executeResponseData.getAntennaId(), antennaId);
		Assert.assertEquals(executeResponseData.getProtocolId(), protocolId);
		Assert.assertEquals(executeResponseData.getTagDataAccessSpecIds().size(), 1);
		Assert.assertEquals(
				executeResponseData.getTagDataAccessSpecIds().get(tagDataId).longValue(),
				accessSpecId1);
		// the result for the requested read operation is returned
		// (the generated read operation for the filter of the second AccessSpec
		// and its result are used internally and must _not_ be returned in the
		// execute response)
		List<OperationResult> opResults = executeResponse.getTagData().get(0).getResultList();
		Assert.assertEquals(opResults.size(), 1);
		Assert.assertEquals(opResults.get(0).getOperationId(), opSpecId);
		await(eventQueue, new MessageType[] { MessageType.GET_CAPABILITIES,
				MessageType.GET_CAPABILITIES_RESPONSE, MessageType.EXECUTE });
		// add the removed AccessSpec with the filter (the change is ignored by
		// the current inventory)
		messageHandler.add(accessSpec2);
		// release the second inventory and wait for the start of the third
		// inventory
		latch.release();
		await(eventQueue, new MessageType[] { MessageType.GET_OPERATIONS,
				MessageType.GET_OPERATIONS_RESPONSE });
		messageEvents = await(eventQueue, new MessageType[] { MessageType.EXECUTE_RESPONSE });
		rfcMessageEvent = (RFCMessageEvent) messageEvents.get(0);
		executeResponse = (ExecuteResponse) rfcMessageEvent.getMessage();
		executeResponseData = (ExecuteResponseData) rfcMessageEvent.getData();
		Assert.assertEquals(executeResponseData.getRoSpecId(), roSpecId);
		Assert.assertEquals(executeResponseData.getSpecIndex(), 1);
		Assert.assertEquals(executeResponseData.getAntennaId(), antennaId);
		Assert.assertEquals(executeResponseData.getProtocolId(), protocolId);
		Assert.assertEquals(executeResponseData.getTagDataAccessSpecIds().size(), 1);
		Assert.assertEquals(
				executeResponseData.getTagDataAccessSpecIds().get(tagDataId).longValue(),
				accessSpecId1);
		opResults = executeResponse.getTagData().get(0).getResultList();
		Assert.assertEquals(opResults.size(), 1);
		Assert.assertEquals(opResults.get(0).getOperationId(), opSpecId);
		await(eventQueue, new MessageType[] { MessageType.EXECUTE });
		// cancel the execution
		messageHandler.cancelExecution(roSpecId);
		// release the third inventory and wait for its end
		// (the AccessSpec with the filter is active again)
		latch.release();
		await(eventQueue, new MessageType[] { MessageType.GET_OPERATIONS,
				MessageType.GET_OPERATIONS_RESPONSE });
		messageEvents = await(eventQueue, new MessageType[] { MessageType.EXECUTE_RESPONSE });
		rfcMessageEvent = (RFCMessageEvent) messageEvents.get(0);
		executeResponse = (ExecuteResponse) rfcMessageEvent.getMessage();
		executeResponseData = (ExecuteResponseData) rfcMessageEvent.getData();
		Assert.assertEquals(executeResponseData.getRoSpecId(), roSpecId);
		Assert.assertEquals(executeResponseData.getSpecIndex(), 1);
		Assert.assertEquals(executeResponseData.getAntennaId(), antennaId);
		Assert.assertEquals(executeResponseData.getProtocolId(), protocolId);
		Assert.assertEquals(executeResponseData.getTagDataAccessSpecIds().size(), 1);
		Assert.assertEquals(
				executeResponseData.getTagDataAccessSpecIds().get(tagDataId).longValue(),
				accessSpecId1);
		opResults = executeResponse.getTagData().get(0).getResultList();
		Assert.assertEquals(opResults.size(), 1);
		Assert.assertEquals(opResults.get(0).getOperationId(), opSpecId);
		// no further event is expected
		try {
			latch.release();
			eventQueue.take(1000);
			Assert.fail();
		} catch (TimeoutException e) {
		}
		// clean up
		messageHandler.close();
		threadPool.shutdown();
	}

	// @Mocked
	// _RFDeviceStubTest controller;
	// @Mocked
	// Platform platform;

	@SuppressWarnings("unchecked")
	public void aiSpecStopTrigger(//
			@Mocked final _RFDeviceStubTest controller, @Mocked final Platform platform//
	) throws Exception {
		new Expectations() {
			{
				controller.execute(withInstanceOf(List.class) /* anntennaIds */,
						withInstanceOf(List.class) /* filters */,
						withInstanceOf(List.class) /* tagOperations */);
				result = new Delegate<_RFDeviceStubTest>() {
					@SuppressWarnings("unused")
					public List<TagData> execute(List<Short> antennas, List<Filter> filter,
							List<TagOperation> operations) throws ConnectionException,
							CommunicationException, ParameterException, ImplementationException {
						// return one tag as inventory result
						TagData tagData = new TagData();
						tagData.setEpc(new byte[] { 1, 2 });
						return Arrays.asList(tagData);
					}
				};
			}
		};
		EventQueue eventQueue = new EventQueue();
		RFCMessageHandler messageHandler = new RFCMessageHandler(serverConfig, serverInstanceConfig,
				eventQueue, null /* serviceFactory */, platform);
		// open and start the message handler
		ExecutorService threadPool = Executors.newFixedThreadPool(1);
		messageHandler.open(threadPool);
		Thread.sleep(1000);
		// create ROSpec containing AISpec stop trigger
		AISpecStopTrigger stopTrigger = new AISpecStopTrigger(new TLVParameterHeader((byte) 0),
				AISpecStopTriggerType.TAG_OBSERVATION, 0 /* durationTrigger */);
		stopTrigger.setTagOT(new TagObservationTrigger(new TLVParameterHeader((byte) 0),
				TagObservationTriggerType.UPON_SEEING_N_TAG_OBSERVATIONS_OR_TIMEOUT,
				2 /* numberOfTags */, 0 /* numberOfAttempts */, 0 /* t */, 1000 /* timeOut */));
		AISpec aiSpec = new AISpec(new TLVParameterHeader((byte) 0),
				Arrays.asList(0) /* antennaId */, stopTrigger,
				Arrays.asList(new InventoryParameterSpec(new TLVParameterHeader((byte) 0),
						10 /* specID */, ProtocolId.EPC_GLOBAL_C1G2)));
		ROSpec roSpec = new ROSpec(new TLVParameterHeader((byte) 0), 1 /* roSpecId */,
				(short) 2 /* priority */, ROSpecCurrentState.DISABLED, null /* ROBoundarySpec */,
				Arrays.asList((Parameter) aiSpec));
		// start ROSpec
		long startTime = System.currentTimeMillis();
		messageHandler.requestExecution(roSpec);
		// read messages from inventory executions until the ROSpec is stopped
		int count = 0;
		long stopTime = 0;
		try {
			Event ev;
			do {
				ev = eventQueue.take(500); // Execute
				ev = eventQueue.take(500); // ExecuteResponse
				count++;
			} while (ev != null);
			Assert.fail();
		} catch (TimeoutException e) {
			stopTime = System.currentTimeMillis();
		}
		// at least two requests/inventories must have been processed
		Assert.assertTrue(count >= 2);
		long diff = stopTime - startTime;
		Assert.assertTrue(diff > 400 && diff < 600);

		threadPool.shutdown();
	}

	// @Mocked
	// _RFDeviceStubTest controller;
	// @Mocked
	// Platform platform;

	public void gpiEventReceived(//
			@Mocked final _RFDeviceStubTest controller, @Mocked final Platform platform//
	) throws Exception {
		EventQueue eventQueue = new EventQueue();
		RFCMessageHandler messageHandler = new RFCMessageHandler(serverConfig, serverInstanceConfig,
				eventQueue, null /* serviceFactory */, platform);
		// open and start the message handler
		ExecutorService threadPool = Executors.newFixedThreadPool(1);
		messageHandler.open(threadPool);
		Thread.sleep(1000);
		// create ROSpec containing AISpec stop trigger
		AISpecStopTrigger stopTrigger = new AISpecStopTrigger(new TLVParameterHeader((byte) 0),
				AISpecStopTriggerType.GPI_WITH_TIMEOUT, 0 /* durationTrigger */);
		stopTrigger.setGpiTV(new GPITriggerValue(new TLVParameterHeader((byte) 0),
				1 /* gpiPortNum */, true /* gpiEvent */, 1000 /* timeOut */));
		AISpec aiSpec = new AISpec(new TLVParameterHeader((byte) 0),
				Arrays.asList(0) /* antennaId */, stopTrigger,
				Arrays.asList(new InventoryParameterSpec(new TLVParameterHeader((byte) 0),
						10 /* specID */, ProtocolId.EPC_GLOBAL_C1G2)));
		ROSpec roSpec = new ROSpec(new TLVParameterHeader((byte) 0), 1 /* roSpecId */,
				(short) 2 /* priority */, ROSpecCurrentState.DISABLED, null /* ROBoundarySpec */,
				Arrays.asList((Parameter) aiSpec));
		// start ROSpec
		long startTime = System.currentTimeMillis();
		messageHandler.requestExecution(roSpec);
		// send GPI event to stop ROSpec
		messageHandler.gpiEventReceived(new GPIEvent(new TLVParameterHeader((byte) 0),
				1 /* gpiPortNumber */, true /* state */));
		// read messages from inventory executions until the ROSpec is stopped
		int count = 0;
		long stopTime = 0;
		try {
			Event ev;
			do {
				ev = eventQueue.take(500); // Execute
				ev = eventQueue.take(500); // ExecuteResponse
				count++;
			} while (ev != null);
			Assert.fail();
		} catch (TimeoutException e) {
			stopTime = System.currentTimeMillis();
		}
		// at least one request must have been processed
		Assert.assertTrue(count >= 1);
		long diff = stopTime - startTime;
		Assert.assertTrue(diff > 400 && diff < 600);

		threadPool.shutdown();
	}

	// @Mocked
	// _RFDeviceStubTest controller;
	// @Mocked
	// Platform platform;

	public void getCapabilities(//
			@Mocked final _RFDeviceStubTest controller, @Mocked Platform platform//
	) throws Exception {
		new Expectations() {
			{
				controller.getCapabilities(CapabilityType.REGULATORY_CAPABILITIES);
				result = Arrays.asList(new Capabilities[] { new RegulatoryCapabilities() });
				controller.getCapabilities(CapabilityType.DEVICE_CAPABILITIES);
				result = Arrays.asList(new Capabilities[] { new DeviceCapabilities() });
			}
		};
		EventQueue eventQueue = new EventQueue();
		RFCMessageHandler messageHandler = new RFCMessageHandler(serverConfig, serverInstanceConfig,
				eventQueue, null /* serviceFactory */, platform);
		// open and start the message handler
		ExecutorService threadPool = Executors.newFixedThreadPool(1);
		messageHandler.open(threadPool);
		messageHandler.requestCapabilities(Arrays.asList(CapabilityType.REGULATORY_CAPABILITIES,
				CapabilityType.DEVICE_CAPABILITIES));
		List<RFCMessageEvent> messageEvents = await(eventQueue, new MessageType[] {
				MessageType.GET_CAPABILITIES, MessageType.GET_CAPABILITIES_RESPONSE });
		GetCapabilitiesResponse capsResponse = (GetCapabilitiesResponse) messageEvents.get(1)
				.getMessage();
		List<Capabilities> caps = capsResponse.getCapabilities();
		Assert.assertEquals(caps.size(), 2);
		Assert.assertTrue(caps.get(0) instanceof RegulatoryCapabilities);
		Assert.assertTrue(caps.get(1) instanceof DeviceCapabilities);

		// clean up
		messageHandler.close();
		threadPool.shutdown();
	}

	// @Mocked
	// _RFDeviceStubTest controller;
	// @Mocked
	// Platform platform;

	public void configuration(//
			@Mocked final _RFDeviceStubTest controller, @Mocked Platform platform//
	) throws Exception {
		new Expectations() {
			{
				controller.getConfiguration(ConfigurationType.ANTENNA_CONFIGURATION, anyShort,
						anyShort, anyShort);
				result = Arrays.asList(new Configuration[] { new AntennaConfiguration() });
				controller.getConfiguration(ConfigurationType.ANTENNA_PROPERTIES, anyShort,
						anyShort, anyShort);
				result = Arrays.asList(new Configuration[] { new AntennaProperties() });
			}
		};
		EventQueue eventQueue = new EventQueue();
		RFCMessageHandler messageHandler = new RFCMessageHandler(serverConfig, serverInstanceConfig,
				eventQueue, null /* serviceFactory */, platform);
		// open and start the message handler
		ExecutorService threadPool = Executors.newFixedThreadPool(1);
		messageHandler.open(threadPool);
		// send a "GetConfiguration", "SetConfiguration" and
		// "ResetConfiguration" message
		messageHandler.requestExecution(false /* reset */, Arrays.asList(
				new Configuration[] { new AntennaConfiguration(), new AntennaProperties() }));
		messageHandler
				.requestConfiguration(
						Arrays.asList(
								new ConfigurationType[] { ConfigurationType.ANTENNA_CONFIGURATION,
										ConfigurationType.ANTENNA_PROPERTIES }),
						(short) 1 /* antennaId */);
		messageHandler.requestExecution(true/* reset */, new ArrayList<Configuration>());
		// await responses
		List<RFCMessageEvent> messageEvents = await(eventQueue,
				new MessageType[] { MessageType.SET_CONFIGURATION,
						MessageType.SET_CONFIGURATION_RESPONSE, MessageType.GET_CONFIGURATION,
						MessageType.GET_CONFIGURATION_RESPONSE, MessageType.RESET_CONFIGURATION,
						MessageType.RESET_CONFIGURATION_RESPONSE, MessageType.SET_CONFIGURATION,
						MessageType.SET_CONFIGURATION_RESPONSE });
		int i = 0;
		SetConfiguration set = (SetConfiguration) messageEvents.get(i++).getMessage();
		SetConfigurationResponse setResponse = (SetConfigurationResponse) messageEvents.get(i++)
				.getMessage();
		Assert.assertEquals(set.getMessageHeader().getId(), setResponse.getMessageHeader().getId());
		i++;
		GetConfigurationResponse confResponse = (GetConfigurationResponse) messageEvents.get(i++)
				.getMessage();
		List<Configuration> conf = confResponse.getConfiguration();
		Assert.assertEquals(conf.size(), 2);
		Assert.assertTrue(conf.get(0) instanceof AntennaConfiguration);
		Assert.assertTrue(conf.get(1) instanceof AntennaProperties);
		ResetConfiguration reset = (ResetConfiguration) messageEvents.get(i++).getMessage();
		ResetConfigurationResponse resetResponse = (ResetConfigurationResponse) messageEvents
				.get(i++).getMessage();
		Assert.assertEquals(reset.getMessageHeader().getId(),
				resetResponse.getMessageHeader().getId());
		set = (SetConfiguration) messageEvents.get(i++).getMessage();
		setResponse = (SetConfigurationResponse) messageEvents.get(i).getMessage();
		Assert.assertEquals(set.getMessageHeader().getId(), setResponse.getMessageHeader().getId());
		// clean up
		messageHandler.close();
		threadPool.shutdown();
	}

	private List<RFCMessageEvent> await(EventQueue eventQueue, MessageType[] messageTypes)
			throws InterruptedException, TimeoutException {
		List<RFCMessageEvent> ret = new ArrayList<>();
		for (MessageType messageType : messageTypes) {
			RFCMessageEvent event = (RFCMessageEvent) eventQueue.take(3000);
			Message msg = event.getMessage();
			Assert.assertEquals(msg.getMessageHeader().getMessageType(), messageType);
			ret.add(event);
		}
		return ret;
	}

	private AccessSpec createAccessSpec(long accessSpecId, int antennaId, ProtocolId protocolId,
			long roSpecId, boolean setTagFilter) {
		AccessSpecStopTrigger accessSpecStopTrigger = new AccessSpecStopTrigger(
				new TLVParameterHeader((byte) 0), AccessSpecStopTriggerType.NULL,
				0/* operationCountValue */);
		BitSet tagMask = new BitSet();
		if (setTagFilter) {
			tagMask.set(0);
		}
		C1G2TargetTag pattern1 = new C1G2TargetTag(new TLVParameterHeader((byte) 0),
				(byte) 0 /* memoryBank */, true /* isMatch */, 0 /* pointer */, tagMask,
				new BitSet() /* tagData */);
		C1G2TagSpec tagSpec = new C1G2TagSpec(new TLVParameterHeader((byte) 0), pattern1);
		final List<Parameter> ops = new ArrayList<>();
		ops.add(new C1G2Read(new TLVParameterHeader((byte) 0), 0 /* opSpecId */, 1L /* accessPw */,
				(byte) 3 /* memoryBank */, 3 /* wordPointer */, 4 /* wordCount */));
		AccessCommand accessCommand = new AccessCommand(new TLVParameterHeader((byte) 0), tagSpec,
				ops);
		return new AccessSpec(new TLVParameterHeader((byte) 0), accessSpecId, antennaId, protocolId,
				true /* currentState */, roSpecId, accessSpecStopTrigger, accessCommand);
	}

	private ROSpec createROSpec(long roSpecId, int antennaId, ProtocolId protocolId) {
		List<Parameter> specList = new ArrayList<>();
		AISpec aiSpec = new AISpec(new TLVParameterHeader((byte) 0),
				Arrays.asList(new Integer[] { antennaId }),
				new AISpecStopTrigger(new TLVParameterHeader((byte) 0), AISpecStopTriggerType.NULL,
						0 /* durationTrigger */),
				Arrays.asList(new InventoryParameterSpec[] { new InventoryParameterSpec(
						new TLVParameterHeader((byte) 0), 10 /* specID */, protocolId) }));
		specList.add(aiSpec);
		return new ROSpec(new TLVParameterHeader((byte) 0), roSpecId, (short) 2 /* priority */,
				ROSpecCurrentState.ACTIVE, null /* ROBoundarySpec */, specList);
	}
}
