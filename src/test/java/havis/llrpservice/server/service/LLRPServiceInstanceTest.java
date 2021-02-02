package havis.llrpservice.server.service;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jibx.runtime.JiBXException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import havis.device.rf.RFDevice;
import havis.device.rf.capabilities.Capabilities;
import havis.device.rf.capabilities.CapabilityType;
import havis.device.rf.capabilities.DeviceCapabilities;
import havis.device.rf.capabilities.FixedFreqTable;
import havis.device.rf.capabilities.FreqHopTable;
import havis.device.rf.capabilities.ReceiveSensitivityTable;
import havis.device.rf.capabilities.RegulatoryCapabilities;
import havis.device.rf.capabilities.TransmitPowerTable;
import havis.device.rf.configuration.Configuration;
import havis.device.rf.configuration.ConfigurationType;
import havis.device.rf.tag.TagData;
import havis.llrpservice.common.concurrent.EventPipes;
import havis.llrpservice.common.entityManager.EntityManagerException;
import havis.llrpservice.common.io.PathHandler;
import havis.llrpservice.common.io.XMLFile;
import havis.llrpservice.common.tcp.TCPClientMultiplexed;
import havis.llrpservice.common.tcp.TCPServerMultiplexed;
import havis.llrpservice.csc.llrp.LLRPClientMultiplexed;
import havis.llrpservice.csc.llrp.LLRPEventHandler;
import havis.llrpservice.csc.llrp.LLRPTimeoutException;
import havis.llrpservice.csc.llrp.LLRPUnknownChannelException;
import havis.llrpservice.csc.llrp.event.LLRPChannelClosedEvent;
import havis.llrpservice.csc.llrp.event.LLRPChannelOpenedEvent;
import havis.llrpservice.csc.llrp.event.LLRPDataReceivedNotifyEvent;
import havis.llrpservice.csc.llrp.event.LLRPDataSentEvent;
import havis.llrpservice.data.message.AddAccessSpec;
import havis.llrpservice.data.message.AddAccessSpecResponse;
import havis.llrpservice.data.message.AddROSpec;
import havis.llrpservice.data.message.AddROSpecResponse;
import havis.llrpservice.data.message.CloseConnection;
import havis.llrpservice.data.message.CloseConnectionResponse;
import havis.llrpservice.data.message.DeleteAccessSpec;
import havis.llrpservice.data.message.DeleteAccessSpecResponse;
import havis.llrpservice.data.message.DeleteROSpec;
import havis.llrpservice.data.message.DeleteROSpecResponse;
import havis.llrpservice.data.message.DisableAccessSpec;
import havis.llrpservice.data.message.DisableAccessSpecResponse;
import havis.llrpservice.data.message.DisableROSpec;
import havis.llrpservice.data.message.DisableROSpecResponse;
import havis.llrpservice.data.message.EnableAccessSpec;
import havis.llrpservice.data.message.EnableAccessSpecResponse;
import havis.llrpservice.data.message.EnableEventsAndReports;
import havis.llrpservice.data.message.EnableROSpec;
import havis.llrpservice.data.message.EnableROSpecResponse;
import havis.llrpservice.data.message.GetAccessSpecs;
import havis.llrpservice.data.message.GetAccessSpecsResponse;
import havis.llrpservice.data.message.GetROSpecs;
import havis.llrpservice.data.message.GetROSpecsResponse;
import havis.llrpservice.data.message.GetReaderCapabilities;
import havis.llrpservice.data.message.GetReaderCapabilitiesRequestedData;
import havis.llrpservice.data.message.GetReaderCapabilitiesResponse;
import havis.llrpservice.data.message.GetReaderConfig;
import havis.llrpservice.data.message.GetReaderConfigRequestedData;
import havis.llrpservice.data.message.GetReaderConfigResponse;
import havis.llrpservice.data.message.GetReport;
import havis.llrpservice.data.message.GetSupportedVersion;
import havis.llrpservice.data.message.GetSupportedVersionResponse;
import havis.llrpservice.data.message.MessageHeader;
import havis.llrpservice.data.message.ProtocolVersion;
import havis.llrpservice.data.message.ROAccessReport;
import havis.llrpservice.data.message.ReaderEventNotification;
import havis.llrpservice.data.message.SetProtocolVersion;
import havis.llrpservice.data.message.SetProtocolVersionResponse;
import havis.llrpservice.data.message.SetReaderConfig;
import havis.llrpservice.data.message.SetReaderConfigResponse;
import havis.llrpservice.data.message.StartROSpec;
import havis.llrpservice.data.message.StartROSpecResponse;
import havis.llrpservice.data.message.StopROSpec;
import havis.llrpservice.data.message.StopROSpecResponse;
import havis.llrpservice.data.message.parameter.AISpec;
import havis.llrpservice.data.message.parameter.AISpecStopTrigger;
import havis.llrpservice.data.message.parameter.AISpecStopTriggerType;
import havis.llrpservice.data.message.parameter.AccessCommand;
import havis.llrpservice.data.message.parameter.AccessReportSpec;
import havis.llrpservice.data.message.parameter.AccessReportTrigger;
import havis.llrpservice.data.message.parameter.AccessSpec;
import havis.llrpservice.data.message.parameter.AccessSpecStopTrigger;
import havis.llrpservice.data.message.parameter.AccessSpecStopTriggerType;
import havis.llrpservice.data.message.parameter.C1G2Read;
import havis.llrpservice.data.message.parameter.C1G2TagSpec;
import havis.llrpservice.data.message.parameter.C1G2TargetTag;
import havis.llrpservice.data.message.parameter.ConnectionAttemptEventStatusType;
import havis.llrpservice.data.message.parameter.EPCData;
import havis.llrpservice.data.message.parameter.EventNotificationState;
import havis.llrpservice.data.message.parameter.EventNotificationStateEventType;
import havis.llrpservice.data.message.parameter.EventsAndReports;
import havis.llrpservice.data.message.parameter.InventoryParameterSpec;
import havis.llrpservice.data.message.parameter.LLRPStatusCode;
import havis.llrpservice.data.message.parameter.Parameter;
import havis.llrpservice.data.message.parameter.ProtocolId;
import havis.llrpservice.data.message.parameter.ROBoundarySpec;
import havis.llrpservice.data.message.parameter.ROReportSpec;
import havis.llrpservice.data.message.parameter.ROReportTrigger;
import havis.llrpservice.data.message.parameter.ROSpec;
import havis.llrpservice.data.message.parameter.ROSpecCurrentState;
import havis.llrpservice.data.message.parameter.ROSpecStartTrigger;
import havis.llrpservice.data.message.parameter.ROSpecStartTriggerType;
import havis.llrpservice.data.message.parameter.ROSpecStopTrigger;
import havis.llrpservice.data.message.parameter.ROSpecStopTriggerType;
import havis.llrpservice.data.message.parameter.ReaderEventNotificationSpec;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.data.message.parameter.TagReportContentSelector;
import havis.llrpservice.data.message.parameter.TagReportData;
import havis.llrpservice.data.message.parameter.serializer.InvalidParameterTypeException;
import havis.llrpservice.data.message.serializer.ByteBufferSerializer;
import havis.llrpservice.sbc.rfc.message.ExecuteResponse;
import havis.llrpservice.sbc.rfc.message.GetCapabilitiesResponse;
import havis.llrpservice.sbc.rfc.message.GetConfigurationResponse;
import havis.llrpservice.sbc.rfc.message.ResetConfigurationResponse;
import havis.llrpservice.sbc.rfc.message.SetConfigurationResponse;
import havis.llrpservice.sbc.service.ServiceFactory;
import havis.llrpservice.server.configuration.ConfigurationException;
import havis.llrpservice.server.configuration.ServerConfiguration;
import havis.llrpservice.server.configuration.ServerInstanceConfiguration;
import havis.llrpservice.server.event.Event;
import havis.llrpservice.server.event.EventQueue;
import havis.llrpservice.server.event.RFCMessageEvent;
import havis.llrpservice.server.persistence.PersistenceException;
import havis.llrpservice.server.platform.PlatformManager;
import havis.llrpservice.server.rfc.RFCMessageHandler;
import havis.llrpservice.server.rfc.messageData.ExecuteResponseData;
import havis.llrpservice.server.service.LLRPServiceInstance.LLRPServiceInstanceListener;
import havis.llrpservice.server.service.data.ROAccessReportEntity;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;
import havis.llrpservice.xml.properties.DefaultsGroup;
import havis.llrpservice.xml.properties.IdentificationSourceType;
import havis.llrpservice.xml.properties.IdentificationTypeEnumeration;
import havis.llrpservice.xml.properties.LLRPCapabilitiesType;
import havis.util.platform.Platform;
import mockit.Delegate;
import mockit.Expectations;
import mockit.Invocation;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;

public class LLRPServiceInstanceTest {

	private long timeout = 3000;

	private class TestLLRPEventHandler implements LLRPEventHandler {
		private EventPipes latches = new EventPipes(new ReentrantLock());

		public LLRPChannelOpenedEvent awaitChannelOpened()
				throws InterruptedException, TimeoutException {
			return latches.await(LLRPChannelOpenedEvent.class, timeout).get(0);
		}

		public LLRPChannelClosedEvent awaitChannelClosed()
				throws InterruptedException, TimeoutException {
			return latches.await(LLRPChannelClosedEvent.class, timeout).get(0);
		}

		@Override
		public void channelOpened(LLRPChannelOpenedEvent event) {
			latches.fire(event);
		}

		@Override
		public void dataSent(LLRPDataSentEvent event) {
		}

		@Override
		public void dataReceived(LLRPDataReceivedNotifyEvent event) {
		}

		@Override
		public void channelClosed(LLRPChannelClosedEvent event) {
			latches.fire(event);
		}
	}

	private class TestLLRPServiceInstanceListener implements LLRPServiceInstanceListener {
		private class ClosedEvent {
			Throwable exception;
			boolean isRestarting;

			public ClosedEvent(Throwable exception, boolean isRestarting) {
				this.exception = exception;
				this.isRestarting = isRestarting;
			}
		}

		private EventPipes latches = new EventPipes(new ReentrantLock());

		public int awaitOpened(long timeout) throws InterruptedException, TimeoutException {
			return latches.await(Integer.class, timeout).get(0);
		}

		public ClosedEvent awaitClosed(long timeout) throws InterruptedException, TimeoutException {
			return latches.await(ClosedEvent.class, timeout).get(0);
		}

		@Override
		public void opened(int llrpPort) {
			latches.fire(llrpPort);
		}

		@Override
		public void closed(Throwable t, boolean isRestarting) {
			// if (t != null) t.printStackTrace();
			latches.fire(new ClosedEvent(t, isRestarting));
		}

	}

	private class Instance {
		ServerConfiguration serverConf;
		LLRPServiceInstance instance;
		ExecutorService threadPool;
		Future<?> future;
	}

	private static final Path SERVER_CONFIG_INIT_PATH = new PathHandler()
			.toAbsolutePath("havis/llrpservice/server/service/LLRPServerConfiguration.xml");
	private static final Path SERVER_INSTANCE_CONFIG_INIT_PATH = new PathHandler()
			.toAbsolutePath("havis/llrpservice/server/service/LLRPServerInstanceConfiguration.xml");

	private static final Path BASE_PATH = new PathHandler()
			.toAbsolutePath("havis/llrpservice/server/service");
	private static final Path BASE_OUTPUT_PATH = BASE_PATH.resolve("../../../../../output");
	private static final Path SERVER_CONFIG_LATEST_PATH = BASE_OUTPUT_PATH
			.resolve("config/newConfig.xml");
	private static final Path SERVER_INSTANCE_CONFIG_LATEST_PATH = BASE_OUTPUT_PATH
			.resolve("config/newInstanceConfig.xml");

	private ExecutorService threadPool;
	private LLRPClientMultiplexed llrpClient;
	private TCPServerMultiplexed tcpServerLLRP;

	@BeforeClass
	public void init() throws Exception {
		// create a LLRP client and start it
		TCPClientMultiplexed tcpClient = new TCPClientMultiplexed();
		threadPool = Executors.newFixedThreadPool(2);
		threadPool.submit(tcpClient);
		llrpClient = new LLRPClientMultiplexed(tcpClient);
		// create TCP server for LLRP servers
		tcpServerLLRP = new TCPServerMultiplexed();
		tcpServerLLRP.setReadBufferSize(2048);
		threadPool.submit(tcpServerLLRP);
	}

	@AfterClass
	public void cleanUp() {
		threadPool.shutdown();
	}

	// @Mocked RFCMessageHandler rfcMessageHandler;

	@Test
	public void addRemoveListenersWithEvents(//
			@Mocked final RFCMessageHandler rfcMessageHandler//
	) throws Exception {
		// open an instance with a listener
		TestLLRPServiceInstanceListener listener1 = new TestLLRPServiceInstanceListener();
		Instance instance = openInstance(new TestLLRPServiceInstanceListener[] { listener1 },
				3000 /* openTimeout */);
		// wait for the open event
		int llrpPort = listener1.awaitOpened(3000);
		assertEquals(llrpPort, 4321);
		// remove the listener
		instance.instance.removeListener(listener1);
		// add another listener
		TestLLRPServiceInstanceListener listener2 = new TestLLRPServiceInstanceListener();
		instance.instance.addListener(listener2);
		// close the instance
		// only the second listener receives a close event
		closeInstance(instance, 3000);
		try {
			listener1.awaitClosed(1000);
			fail();
		} catch (TimeoutException e) {
		}
		TestLLRPServiceInstanceListener.ClosedEvent event = listener2.awaitClosed(3000);
		assertNull(event.exception);
	}

	// @Mocked RFCMessageHandler rfcMessageHandler;

	@Test
	public void addRemoveListenersWithLoggingInfo(//
			@Mocked final RFCMessageHandler rfcMessageHandler//
	) throws Exception {
		class Data {
			boolean isStarted = false;
			boolean isStopped = false;
		}
		final Data data = new Data();

		new MockUp<Logger>() {
			@Mock
			void log(Level level, String msg) {
				if (msg.matches("^Stopped instance.*")) {
					data.isStopped = true;
				}
				if (msg.matches("^Started instance.*")) {
					data.isStarted = true;
				}
			}

			@Mock
			boolean isLoggable(Level level) {
				return Level.INFO == level;
			}
		};

		// open an instance without a listener => events are logged
		Instance instance = openInstance(new TestLLRPServiceInstanceListener[] {},
				3000 /* openTimeout */);
		Thread.sleep(1000);
		// close the instance
		closeInstance(instance, 3000);
		assertTrue(data.isStarted);
		assertTrue(data.isStopped);
	}

	// @Mocked
	// RFCMessageHandler rfcMessageHandler;
	// @Mocked
	// EventQueue eventQueue;

	@Test
	public void addRemoveListenersWithLoggingError(//
			@Mocked final RFCMessageHandler rfcMessageHandler, @Mocked final EventQueue eventQueue//
	) throws Exception {
		class Data {
			boolean isLatchEnabled = true;
			boolean isStopped = false;
		}
		final Data data = new Data();

		new MockUp<Logger>() {
			@Mock
			void log(Level level, String msg, Throwable thrown) {
				if (msg.matches("^Stopped instance.*")) {
					data.isStopped = true;
				}
			}
		};

		final Semaphore latch = new Semaphore(0);
		final TimeoutException exception = new TimeoutException("huhu");
		new Expectations() {
			{
				eventQueue.take(anyLong);
				result = new Delegate<EventQueue>() {
					@SuppressWarnings("unused")
					Event take(Invocation inv, long timeout)
							throws InterruptedException, TimeoutException {
						boolean isLatchEnabled;
						synchronized (data) {
							isLatchEnabled = data.isLatchEnabled;
						}
						if (isLatchEnabled) {
							latch.tryAcquire(3000, TimeUnit.MILLISECONDS);
						}
						throw exception;
					}
				};
			}
		};

		// open an instance without a listener => events are logged
		Instance instance = openInstance(new TestLLRPServiceInstanceListener[] {},
				3000 /* openTimeout */);
		Thread.sleep(1000);
		// the instance waits for an event at the event queue due to the latch
		synchronized (data) {
			// disable the latch
			data.isLatchEnabled = false;
		}
		// release the latch
		latch.release();
		// close the instance with exceptions from the event queue
		closeInstance(instance, 3000);
		assertTrue(data.isStopped);
	}

	// @Mocked
	// RFCMessageHandler rfcMessageHandler;
	// @Mocked
	// EventQueue eventQueue;

	@Test
	public void restartRetries(//
			@Mocked final RFCMessageHandler rfcMessageHandler, @Mocked final EventQueue eventQueue//
	) throws Exception {
		class Data {
			boolean isLatchEnabled = true;
		}
		final Data data = new Data();
		final Semaphore latch = new Semaphore(0);
		final TimeoutException exception = new TimeoutException("huhu");
		new Expectations() {
			{
				eventQueue.take(anyLong);
				result = new Delegate<EventQueue>() {
					@SuppressWarnings("unused")
					Event take(long timeout) throws InterruptedException, TimeoutException {
						boolean isLatchEnabled;
						synchronized (data) {
							isLatchEnabled = data.isLatchEnabled;
						}
						if (isLatchEnabled) {
							latch.tryAcquire(3000, TimeUnit.MILLISECONDS);
						}
						throw exception;
					}
				};
			}
		};
		// open an instance with a listener
		TestLLRPServiceInstanceListener listener1 = new TestLLRPServiceInstanceListener();
		Instance instance = openInstance(new TestLLRPServiceInstanceListener[] { listener1 },
				3000 /* openTimeout */);
		// wait for the open event
		listener1.awaitOpened(3000);
		// the instance waits for an event at the event queue due to the latch
		// release the latch to throw an exception
		// the server is closed and opened with a delay of 1 sec.
		latch.release();
		long start = System.currentTimeMillis();
		TestLLRPServiceInstanceListener.ClosedEvent ev = listener1.awaitClosed(3000);
		assertTrue(ev.isRestarting);
		assertEquals(ev.exception, exception);
		int llrpPort = listener1.awaitOpened(3000);
		assertEquals(llrpPort, 4321);
		long diff = System.currentTimeMillis() - start;
		assertTrue(diff > 1000 && diff < 2000);

		// the instance waits for an event at the event queue due to the latch
		// release the latch to throw an exception
		// the server is closed and opened with a delay of 2 sec.
		latch.release();
		start = System.currentTimeMillis();
		listener1.awaitClosed(3000);
		listener1.awaitOpened(3000);
		diff = System.currentTimeMillis() - start;
		assertTrue(diff > 2000 && diff < 3000);

		synchronized (data) {
			// disable the latch
			data.isLatchEnabled = false;
		}
		// release the latch
		latch.release();
		// close the instance with exceptions from the event queue
		closeInstance(instance, 3000);
	}

	// @Mocked
	// RFCMessageHandler rfcMessageHandler;
	// @Mocked
	// PlatformManager platformManager;
	// @Mocked
	// Platform platform;
	// @Mocked
	// InstanceCleanup instanceCleanUp;

	@Test
	@SuppressWarnings("unchecked")
	public void processLLRPMessages(//
			@Mocked final RFCMessageHandler rfcMessageHandler,
			@Mocked final PlatformManager platformManager, @Mocked final Platform platform,
			@Mocked InstanceCleanup instanceCleanUp//
	) throws Exception {

		new Expectations() {
			EventQueue eventQueue;
			int getConfCounter = 0;
			int setConfCounter = 0;
			int getCapsCounter = 0;
			{
				// get the event queue when the RFC message handler is created
				new RFCMessageHandler(withInstanceOf(ServerConfiguration.class),
						withInstanceOf(ServerInstanceConfiguration.class),
						withInstanceOf(EventQueue.class), null /* rfcServiceFactory */, platform);
				result = new Delegate<RFCMessageHandler>() {
					@SuppressWarnings("unused")
					public void RFCMessageHandler(ServerConfiguration serverConfiguration,
							ServerInstanceConfiguration instanceConfig, EventQueue queue,
							ServiceFactory<RFDevice> rfcServiceFactory, Platform platform) {
						eventQueue = queue;
					}
				};

				// put a GetCapabilitiesResponse to the event queue
				// only the first response does NOT contain an exception
				rfcMessageHandler.requestCapabilities(withInstanceOf(ArrayList.class));
				result = new Delegate<RFCMessageHandler>() {
					@SuppressWarnings("unused")
					public long requestCapabilities(List<CapabilityType> capTypes)
							throws Exception {
						Map<Short, Short> receiveSensitivityTable = new HashMap<>();
						receiveSensitivityTable.put((short) 1, (short) 125);

						DeviceCapabilities devCaps = new DeviceCapabilities();
						devCaps.setManufacturer((short) 1);
						devCaps.setModel((short) 2);
						devCaps.setFirmware("0.0.9");
						devCaps.setNumberOfAntennas((short) 1);
						devCaps.setMaxReceiveSensitivity((short) 1);
						devCaps.setReceiveSensitivityTable(new ReceiveSensitivityTable());

						RegulatoryCapabilities regCaps = new RegulatoryCapabilities();
						regCaps.setCommunicationStandard((short) 2);
						regCaps.setCountryCode((short) 276);
						regCaps.setHopping(false);
						regCaps.setFixedFreqTable(new FixedFreqTable());
						regCaps.setFreqHopTable(new FreqHopTable());
						regCaps.setTransmitPowerTable(new TransmitPowerTable());

						long id = 25;
						GetCapabilitiesResponse response = new GetCapabilitiesResponse(
								new havis.llrpservice.sbc.rfc.message.MessageHeader(id),
								new ArrayList<Capabilities>(Arrays.asList(devCaps, regCaps)));

						if (getCapsCounter > 0) {
							response.setException(new Exception("Peter"));
						}
						eventQueue.put(new RFCMessageEvent(response));
						getCapsCounter++;
						return id;
					}
				};

				// put a GetConfigurationResponse to the event queue
				// only the first response does NOT contain an exception
				rfcMessageHandler.requestConfiguration(withInstanceOf(ArrayList.class),
						anyShort /* antennaId */);
				result = new Delegate<RFCMessageHandler>() {
					@SuppressWarnings("unused")
					public long requestConfiguration(List<ConfigurationType> confTypes,
							short antennaId) {
						long id = 26;
						GetConfigurationResponse response = new GetConfigurationResponse(
								new havis.llrpservice.sbc.rfc.message.MessageHeader(id),
								new ArrayList<Configuration>());
						if (getConfCounter > 0) {
							response.setException(new Exception("Hans"));
						}
						eventQueue.put(new RFCMessageEvent(response));
						getConfCounter++;
						return id;
					}
				};

				// put a ResetConfigurationResponse + SetConfigurationResponse
				// to the event queue
				// only the first SetConfigurationResponse response does NOT
				// contain an exception
				rfcMessageHandler.requestExecution(anyBoolean /* reset */,
						withInstanceOf(ArrayList.class));
				result = new Delegate<RFCMessageHandler>() {
					@SuppressWarnings("unused")
					public long requestExecution(boolean reset,
							List<Configuration> configurations) {
						long id = 26;
						if (reset) {
							eventQueue.put(new RFCMessageEvent(new ResetConfigurationResponse(
									new havis.llrpservice.sbc.rfc.message.MessageHeader(
											0 /* id */))));
						}
						SetConfigurationResponse response = new SetConfigurationResponse(
								new havis.llrpservice.sbc.rfc.message.MessageHeader(id));
						if (setConfCounter > 0) {
							response.setException(new Exception("Hans"));
						}
						eventQueue.put(new RFCMessageEvent(response));
						setConfCounter++;
						return id;
					}
				};
			}
		};

		Instance instance = openInstance(null /* listener */, 3000 /* openTimeout */);
		// open LLRP client
		TestLLRPEventHandler llrpEventHandler = new TestLLRPEventHandler();
		llrpClient.requestOpeningChannel("localhost", 4321, llrpEventHandler);
		SocketChannel channel = llrpEventHandler.awaitChannelOpened().getChannel();
		if (channel == null) {
			fail();
		}

		// receive connection attempt event (success)
		ReaderEventNotification success = (ReaderEventNotification) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(
				success.getReaderEventNotificationData().getConnectionAttemptEvent().getStatus(),
				ConnectionAttemptEventStatusType.SUCCESS);

		// Send GetSupportedVersion and verify response (success)
		MessageHeader header = new MessageHeader((byte) 0x00, ProtocolVersion.LLRP_V1_1,
				11 /* id */);
		GetSupportedVersion getVersion = new GetSupportedVersion(header);
		llrpClient.requestSendingData(channel, getVersion);
		GetSupportedVersionResponse getVersionResponse = (GetSupportedVersionResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(getVersionResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);
		assertEquals(getVersionResponse.getSupportedVersion(), ProtocolVersion.LLRP_V1_1);
		assertEquals(getVersionResponse.getMessageHeader().getId(), header.getId());

		// Send SetProtocolVersion and verify response (success)
		header.setId(header.getId() + 1);
		SetProtocolVersion setVersion = new SetProtocolVersion(header, ProtocolVersion.LLRP_V1_1);
		llrpClient.requestSendingData(channel, setVersion);
		SetProtocolVersionResponse setVersionResponse = (SetProtocolVersionResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(setVersionResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);
		assertEquals(setVersionResponse.getMessageHeader().getId(), header.getId());

		// Send SetProtocolVersion and verify response (version already set)
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, setVersion);
		setVersionResponse = (SetProtocolVersionResponse) llrpClient.awaitReceivedData(channel,
				timeout);
		assertEquals(setVersionResponse.getStatus().getStatusCode(),
				LLRPStatusCode.M_UNEXPECTED_MESSAGE);
		assertEquals(setVersionResponse.getStatus().getErrorDescription(),
				"The protocol version has already been set");
		assertEquals(setVersionResponse.getMessageHeader().getId(), header.getId());

		// Send GetReaderCapabilities and verify response (Version is not
		// negotiated)
		header.setId(header.getId() + 1);
		header.setVersion(ProtocolVersion.LLRP_V1_0_1);
		llrpClient.requestSendingData(channel, new GetReaderCapabilities(header,
				GetReaderCapabilitiesRequestedData.GENERAL_DEVICE_CAPABILITIES));
		GetReaderCapabilitiesResponse getCapabilitiesResponse = (GetReaderCapabilitiesResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(getCapabilitiesResponse.getStatus().getStatusCode(),
				LLRPStatusCode.M_UNSUPPORTED_VERSION);
		assertTrue(getCapabilitiesResponse.getStatus().getErrorDescription()
				.contains("Invalid protocol version"));
		assertNull(getCapabilitiesResponse.getRegulatoryCap());
		assertNull(getCapabilitiesResponse.getGeneralDeviceCap());
		assertNull(getCapabilitiesResponse.getLlrpCap());
		assertNull(getCapabilitiesResponse.getC1g2llrpCap());
		assertEquals(getCapabilitiesResponse.getMessageHeader().getId(), header.getId());

		// Send GetReaderCapabilities and verify response (success)
		header.setId(header.getId() + 1);
		header.setVersion(ProtocolVersion.LLRP_V1_1);
		llrpClient.requestSendingData(channel, new GetReaderCapabilities(header,
				GetReaderCapabilitiesRequestedData.GENERAL_DEVICE_CAPABILITIES));
		getCapabilitiesResponse = (GetReaderCapabilitiesResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(getCapabilitiesResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);
		assertNull(getCapabilitiesResponse.getRegulatoryCap());
		assertNotNull(getCapabilitiesResponse.getGeneralDeviceCap());
		assertNull(getCapabilitiesResponse.getLlrpCap());
		assertNull(getCapabilitiesResponse.getC1g2llrpCap());
		assertEquals(getCapabilitiesResponse.getMessageHeader().getId(), header.getId());

		// Send GetReaderCapabilities and verify response (R_DEVICE_ERROR)
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new GetReaderCapabilities(header,
				GetReaderCapabilitiesRequestedData.GENERAL_DEVICE_CAPABILITIES));
		getCapabilitiesResponse = (GetReaderCapabilitiesResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(getCapabilitiesResponse.getStatus().getStatusCode(),
				LLRPStatusCode.R_DEVICE_ERROR);
		assertTrue(getCapabilitiesResponse.getStatus().getErrorDescription().contains("Peter"));
		assertNull(getCapabilitiesResponse.getRegulatoryCap());
		assertNull(getCapabilitiesResponse.getGeneralDeviceCap());
		assertNull(getCapabilitiesResponse.getLlrpCap());
		assertNull(getCapabilitiesResponse.getC1g2llrpCap());
		assertEquals(getCapabilitiesResponse.getMessageHeader().getId(), header.getId());

		// Send GetReaderConfig and verify response (Version is not
		// negotiated)
		header.setId(header.getId() + 1);
		header.setVersion(ProtocolVersion.LLRP_V1_0_1);
		llrpClient.requestSendingData(channel, new GetReaderConfig(header, 0 /* antenna */,
				GetReaderConfigRequestedData.ALL, 0 /* gpiPort */, 0 /* gpoPort */));
		GetReaderConfigResponse getReaderConfigResponse = (GetReaderConfigResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(getReaderConfigResponse.getStatus().getStatusCode(),
				LLRPStatusCode.M_UNSUPPORTED_VERSION);
		assertTrue(getReaderConfigResponse.getStatus().getErrorDescription()
				.contains("Invalid protocol version"));
		assertNull(getReaderConfigResponse.getIdentification());
		assertNull(getReaderConfigResponse.getAccessReportSpec());
		assertEquals(getReaderConfigResponse.getMessageHeader().getId(), header.getId());

		// Send GetReaderConfig and verify response (success)
		header.setId(header.getId() + 1);
		header.setVersion(ProtocolVersion.LLRP_V1_1);
		llrpClient.requestSendingData(channel, new GetReaderConfig(header, 0 /* antennaId */,
				GetReaderConfigRequestedData.ALL, 0 /* gpiPort */, 0 /* gpoPort */));
		getReaderConfigResponse = (GetReaderConfigResponse) llrpClient.awaitReceivedData(channel,
				timeout);
		assertEquals(getReaderConfigResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);
		assertNotNull(getReaderConfigResponse.getIdentification());
		assertNotNull(getReaderConfigResponse.getAccessReportSpec());
		assertEquals(getReaderConfigResponse.getMessageHeader().getId(), header.getId());

		// Send GetReaderConfig and verify response (R_DEVICE_ERROR)
		header.setId(header.getId() + 1);
		header.setVersion(ProtocolVersion.LLRP_V1_1);
		llrpClient.requestSendingData(channel, new GetReaderConfig(header, 0 /* antennaId */,
				GetReaderConfigRequestedData.ALL, 0 /* gpiPort */, 0 /* gpoPort */));
		getReaderConfigResponse = (GetReaderConfigResponse) llrpClient.awaitReceivedData(channel,
				timeout);
		assertEquals(getReaderConfigResponse.getStatus().getStatusCode(),
				LLRPStatusCode.R_DEVICE_ERROR);
		assertEquals(getReaderConfigResponse.getStatus().getErrorDescription(), "Hans");
		assertNull(getReaderConfigResponse.getIdentification());
		assertNull(getReaderConfigResponse.getAccessReportSpec());
		assertEquals(getReaderConfigResponse.getMessageHeader().getId(), header.getId());

		// Send GetReaderConfig without accessing any back end and verify
		// response (success)
		header.setId(header.getId() + 1);
		header.setVersion(ProtocolVersion.LLRP_V1_1);
		llrpClient.requestSendingData(channel, new GetReaderConfig(header, 0 /* antennaId */,
				GetReaderConfigRequestedData.IDENTIFICATION, 0 /* gpiPort */, 0 /* gpoPort */));
		getReaderConfigResponse = (GetReaderConfigResponse) llrpClient.awaitReceivedData(channel,
				timeout);
		assertEquals(getReaderConfigResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);
		assertNotNull(getReaderConfigResponse.getIdentification());
		assertNull(getReaderConfigResponse.getAccessReportSpec());
		assertEquals(getReaderConfigResponse.getMessageHeader().getId(), header.getId());

		// Send SetReaderConfig and verify response (Version is not
		// negotiated)
		header.setId(header.getId() + 1);
		header.setVersion(ProtocolVersion.LLRP_V1_0_1);
		llrpClient.requestSendingData(channel,
				new SetReaderConfig(header, false /* resetToFactoryDefaults */));
		SetReaderConfigResponse setReaderConfigResponse = (SetReaderConfigResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(setReaderConfigResponse.getStatus().getStatusCode(),
				LLRPStatusCode.M_UNSUPPORTED_VERSION);
		assertTrue(setReaderConfigResponse.getStatus().getErrorDescription()
				.contains("Invalid protocol version"));

		// Send SetReaderConfig and verify response (success)
		header.setId(header.getId() + 1);
		header.setVersion(ProtocolVersion.LLRP_V1_1);
		llrpClient.requestSendingData(channel,
				new SetReaderConfig(header, true /* resetToFactoryDefaults */));
		setReaderConfigResponse = (SetReaderConfigResponse) llrpClient.awaitReceivedData(channel,
				timeout);
		assertEquals(setReaderConfigResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);

		// Send SetReaderConfig and verify response (R_DEVICE_ERROR)
		header.setId(header.getId() + 1);
		header.setVersion(ProtocolVersion.LLRP_V1_1);
		llrpClient.requestSendingData(channel,
				new SetReaderConfig(header, true /* resetToFactoryDefaults */));
		setReaderConfigResponse = (SetReaderConfigResponse) llrpClient.awaitReceivedData(channel,
				timeout);
		assertEquals(setReaderConfigResponse.getStatus().getStatusCode(),
				LLRPStatusCode.R_DEVICE_ERROR);
		assertEquals(setReaderConfigResponse.getStatus().getErrorDescription(), "Hans");
		assertEquals(setReaderConfigResponse.getMessageHeader().getId(), header.getId());

		// Send SetReaderConfig without accessing any back end and verify
		// response (success)
		header.setId(header.getId() + 1);
		header.setVersion(ProtocolVersion.LLRP_V1_1);
		llrpClient.requestSendingData(channel,
				new SetReaderConfig(header, false /* resetToFactoryDefaults */));
		setReaderConfigResponse = (SetReaderConfigResponse) llrpClient.awaitReceivedData(channel,
				timeout);
		assertEquals(setReaderConfigResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);

		// Send AddROSpec and verify response (success)
		header.setId(header.getId() + 1);
		ROSpec roSpec = createROSpec();
		llrpClient.requestSendingData(channel, new AddROSpec(header, roSpec));
		AddROSpecResponse addROSpecResponse = (AddROSpecResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(addROSpecResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);

		// Send AddROSpec and verify response (invalid ROSpecId)
		llrpClient.requestSendingData(channel, new AddROSpec(header, roSpec));
		addROSpecResponse = (AddROSpecResponse) llrpClient.awaitReceivedData(channel, timeout);
		assertEquals(addROSpecResponse.getStatus().getStatusCode(), LLRPStatusCode.A_INVALID);

		// Send EnableROSpec and verify response (success)
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new EnableROSpec(header, roSpec.getRoSpecID()));
		EnableROSpecResponse enableROSpecResponse = (EnableROSpecResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(enableROSpecResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);

		// Send EnableROSpec and verify response (invalid ROSpecId)
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new EnableROSpec(header, 1000 /* roSpecId */));
		enableROSpecResponse = (EnableROSpecResponse) llrpClient.awaitReceivedData(channel,
				timeout);
		assertEquals(enableROSpecResponse.getStatus().getStatusCode(), LLRPStatusCode.A_INVALID);

		// Send DisableROSpec and verify response (success)
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new DisableROSpec(header, roSpec.getRoSpecID()));
		DisableROSpecResponse disableROSpecResponse = (DisableROSpecResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(disableROSpecResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);

		// Send DisableROSpec and verify response (invalid ROSpecId)
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new DisableROSpec(header, 1000 /* roSpecId */));
		disableROSpecResponse = (DisableROSpecResponse) llrpClient.awaitReceivedData(channel,
				timeout);
		assertEquals(disableROSpecResponse.getStatus().getStatusCode(), LLRPStatusCode.A_INVALID);

		// Send GetROSpecs and verify response (version is not negotiated)
		header.setId(header.getId() + 1);
		header.setVersion(ProtocolVersion.LLRP_V1_0_1);
		llrpClient.requestSendingData(channel, new GetROSpecs(header));
		GetROSpecsResponse getROSpecResponse = (GetROSpecsResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(getROSpecResponse.getStatus().getStatusCode(),
				LLRPStatusCode.M_UNSUPPORTED_VERSION);
		assertTrue(getROSpecResponse.getStatus().getErrorDescription()
				.contains("Invalid protocol version"));

		// Send GetROSpecs and verify response (success)
		header.setId(header.getId() + 1);
		header.setVersion(ProtocolVersion.LLRP_V1_1);
		llrpClient.requestSendingData(channel, new GetROSpecs(header));
		getROSpecResponse = (GetROSpecsResponse) llrpClient.awaitReceivedData(channel, timeout);
		assertEquals(getROSpecResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);
		assertEquals(getROSpecResponse.getRoSpecList().get(0).toString(), roSpec.toString());

		// Send StartROSpec and verify response (success)
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new StartROSpec(header, roSpec.getRoSpecID()));
		StartROSpecResponse startROSpecResponse = (StartROSpecResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(startROSpecResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);

		// Send StartROSpec and verify response (invalid ROSpecId)
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new StartROSpec(header, 1000 /* roSpecId */));
		startROSpecResponse = (StartROSpecResponse) llrpClient.awaitReceivedData(channel, timeout);
		assertEquals(startROSpecResponse.getStatus().getStatusCode(), LLRPStatusCode.A_INVALID);

		// Send StopROSpec and verify response (success)
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new StopROSpec(header, roSpec.getRoSpecID()));
		StopROSpecResponse stopROSpecResponse = (StopROSpecResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(stopROSpecResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);

		// Send StopROSpec and verify response (invalid ROSpecId)
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new StopROSpec(header, 1000 /* roSpecId */));
		stopROSpecResponse = (StopROSpecResponse) llrpClient.awaitReceivedData(channel, timeout);
		assertEquals(stopROSpecResponse.getStatus().getStatusCode(), LLRPStatusCode.A_INVALID);

		// Send DeleteROSpec and verify response (success)
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new DeleteROSpec(header, roSpec.getRoSpecID()));
		DeleteROSpecResponse deleteROSpecResponse = (DeleteROSpecResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(deleteROSpecResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);

		// Send DeleteROSpec and verify response (invalid ROSpec identifier)
		llrpClient.requestSendingData(channel, new DeleteROSpec(header, 1000 /* ROSpecId */));
		deleteROSpecResponse = (DeleteROSpecResponse) llrpClient.awaitReceivedData(channel,
				timeout);
		assertEquals(deleteROSpecResponse.getStatus().getStatusCode(), LLRPStatusCode.A_INVALID);

		// Send AddAccessSpec and verify response (success)
		header.setId(header.getId() + 1);
		AccessSpec accessSpec = createAccessSpec();
		llrpClient.requestSendingData(channel, new AddAccessSpec(header, accessSpec));
		AddAccessSpecResponse addAccessSpecResponse = (AddAccessSpecResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(addAccessSpecResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);

		// Send AddAccessSpec and verify response (invalid AccessSpecId)
		header.setId(header.getId() + 1);
		accessSpec = createAccessSpec();
		llrpClient.requestSendingData(channel, new AddAccessSpec(header, accessSpec));
		addAccessSpecResponse = (AddAccessSpecResponse) llrpClient.awaitReceivedData(channel,
				timeout);
		assertEquals(addAccessSpecResponse.getStatus().getStatusCode(), LLRPStatusCode.A_INVALID);

		// Send EnableAccessSpec and verify response (success)
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel,
				new EnableAccessSpec(header, accessSpec.getAccessSpecId()));
		EnableAccessSpecResponse enableAccessSpecResponse = (EnableAccessSpecResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(enableAccessSpecResponse.getStatus().getStatusCode(),
				LLRPStatusCode.M_SUCCESS);

		// Send EnableAccessSpec and verify response (invalid AccessSpecId)
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel,
				new EnableAccessSpec(header, 1000 /* accessSpecId */));
		enableAccessSpecResponse = (EnableAccessSpecResponse) llrpClient.awaitReceivedData(channel,
				timeout);
		assertEquals(enableAccessSpecResponse.getStatus().getStatusCode(),
				LLRPStatusCode.A_INVALID);

		// Send DisableAccessSpec and verify response (success)
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel,
				new DisableAccessSpec(header, accessSpec.getAccessSpecId()));
		DisableAccessSpecResponse disableAccessSpecResponse = (DisableAccessSpecResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(disableAccessSpecResponse.getStatus().getStatusCode(),
				LLRPStatusCode.M_SUCCESS);

		// Send DisableAccessSpec and verify response (invalid AccessSpecId)
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel,
				new DisableAccessSpec(header, 1000 /* accessSpecId */));
		disableAccessSpecResponse = (DisableAccessSpecResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(disableAccessSpecResponse.getStatus().getStatusCode(),
				LLRPStatusCode.A_INVALID);

		// Send GetAccessSpecs and verify response (Version is not
		// negotiated)
		header.setId(header.getId() + 1);
		header.setVersion(ProtocolVersion.LLRP_V1_0_1);
		llrpClient.requestSendingData(channel, new GetAccessSpecs(header));
		GetAccessSpecsResponse getAccessSpecsResponse = (GetAccessSpecsResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(getAccessSpecsResponse.getStatus().getStatusCode(),
				LLRPStatusCode.M_UNSUPPORTED_VERSION);
		assertTrue(getAccessSpecsResponse.getStatus().getErrorDescription()
				.contains("Invalid protocol version"));

		// Send GetAccessSpecs and verify response (success)
		header.setId(header.getId() + 1);
		header.setVersion(ProtocolVersion.LLRP_V1_1);
		llrpClient.requestSendingData(channel, new GetAccessSpecs(header));
		getAccessSpecsResponse = (GetAccessSpecsResponse) llrpClient.awaitReceivedData(channel,
				timeout);
		assertEquals(getAccessSpecsResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);
		assertEquals(getAccessSpecsResponse.getAccessSpecList().get(0).toString(),
				accessSpec.toString());

		// Send DeleteAccessSpec and verify response (success)
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel,
				new DeleteAccessSpec(header, createAccessSpec().getAccessSpecId()));
		DeleteAccessSpecResponse deleteAccessSpecResponse = (DeleteAccessSpecResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(deleteAccessSpecResponse.getStatus().getStatusCode(),
				LLRPStatusCode.M_SUCCESS);

		// Send DeleteAccessSpec and verify response (invalid AccessSpecId)
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel,
				new DeleteAccessSpec(header, 1000 /* accessSpecId */));
		deleteAccessSpecResponse = (DeleteAccessSpecResponse) llrpClient.awaitReceivedData(channel,
				timeout);
		assertEquals(deleteAccessSpecResponse.getStatus().getStatusCode(),
				LLRPStatusCode.A_INVALID);

		// Send CloseConnection and verify response (success)
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new CloseConnection(header));
		CloseConnectionResponse closeConnectionResponse;
		try {
			closeConnectionResponse = (CloseConnectionResponse) llrpClient
					.awaitReceivedData(channel, timeout);
		} catch (LLRPUnknownChannelException e) {
			LLRPChannelClosedEvent event = llrpEventHandler.awaitChannelClosed();
			if (event == null) {
				fail();
			}
			ByteBuffer buffer = event.getPendingReceivedData();
			// create serializer
			ByteBufferSerializer serializer = new ByteBufferSerializer();
			// deserialize the header
			MessageHeader dHeader = serializer.deserializeMessageHeader(buffer);
			// deserialize message
			closeConnectionResponse = (CloseConnectionResponse) serializer
					.deserializeMessage(dHeader, buffer);
		}
		assertEquals(closeConnectionResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);

		closeInstance(instance, 3000);
	}

	// @Mocked
	// RFCMessageHandler rfcMessageHandler;
	// @Mocked
	// PlatformManager platformManager;
	// @Mocked
	// Platform platform;

	@Test
	public void getReportsAsync(//
			@Mocked final RFCMessageHandler rfcMessageHandler,
			@Mocked final PlatformManager platformManager, @Mocked final Platform platform//
	) throws Exception {
		// create an EPC for reports
		final BitSet epc = new BitSet();
		epc.set(7);
		final byte[] epcBytes = { 1 };

		class Data {
			boolean isLastResponse = false;
		}
		final Data data = new Data();

		new Expectations() {
			EventQueue eventQueue;
			{
				// get the event queue when the RFC message handler is created
				new RFCMessageHandler(withInstanceOf(ServerConfiguration.class),
						withInstanceOf(ServerInstanceConfiguration.class),
						withInstanceOf(EventQueue.class), null /* rfcServiceFactory */, platform);
				result = new Delegate<RFCMessageHandler>() {
					@SuppressWarnings("unused")
					public void RFCMessageHandler(ServerConfiguration serverConfiguration,
							ServerInstanceConfiguration instanceConfig, EventQueue queue,
							ServiceFactory<RFDevice> rfcServiceFactory, Platform platform) {
						eventQueue = queue;
					}
				};

				// enqueue execute response messages with the defined EPC after
				// ROSpec has been started
				rfcMessageHandler.requestExecution(withInstanceOf(ROSpec.class));
				result = new Delegate<RFCMessageHandler>() {
					@SuppressWarnings("unused")
					public void requestExecution(final ROSpec roSpec) {
						new Thread(new Runnable() {

							@Override
							public void run() {
								boolean isLastResponse = false;
								while (!isLastResponse) {
									synchronized (data) {
										isLastResponse = data.isLastResponse;
										data.isLastResponse = false;
									}
									TagData tagData = new TagData();
									tagData.setEpc(epcBytes);
									ExecuteResponse executeResponse = new ExecuteResponse(
											new havis.llrpservice.sbc.rfc.message.MessageHeader(
													4 /* id */),
											Arrays.asList(tagData), null /* timeStamp */);
									eventQueue.put(new RFCMessageEvent(executeResponse,
											new ExecuteResponseData(roSpec.getRoSpecID(),
													1 /* specIndex */,
													1 /* inventoryParameterSpecId */,
													1 /* antennaId */, ProtocolId.EPC_GLOBAL_C1G2,
													new HashMap<Long, Long>() /* tagDataAccessSpecIds */,
													isLastResponse)));
									if (!isLastResponse) {
										try {
											Thread.sleep(100);
										} catch (InterruptedException e) {
											e.printStackTrace();
											fail();
										}
									}
								}
							}
						}).start();
					}
				};
			}
		};

		// open LLRP client
		Instance instance = openInstance(null /* listener */, 3000 /* openTimeout */);
		TestLLRPEventHandler llrpEventHandler = new TestLLRPEventHandler();
		llrpClient.requestOpeningChannel("localhost", 4321, llrpEventHandler);
		SocketChannel channel = llrpEventHandler.awaitChannelOpened().getChannel();
		if (channel == null) {
			fail();
		}
		// receive connection attempt event
		ReaderEventNotification readerEventNotificationResponse = (ReaderEventNotification) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(
				readerEventNotificationResponse.getReaderEventNotificationData()
						.getConnectionAttemptEvent().getStatus(),
				ConnectionAttemptEventStatusType.SUCCESS);
		// send SetProtocolVersion
		MessageHeader header = new MessageHeader((byte) 0x00, ProtocolVersion.LLRP_V1_1,
				11 /* id */);
		header.setId(header.getId() + 1);
		SetProtocolVersion setVersion = new SetProtocolVersion(header, ProtocolVersion.LLRP_V1_1);
		llrpClient.requestSendingData(channel, setVersion);
		SetProtocolVersionResponse setVersionResponse = (SetProtocolVersionResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(setVersionResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);

		// send AddROSpec (tags are reported each 500 ms or at end of ROSpec)
		header.setId(header.getId() + 1);
		ROSpec roSpec = createROSpec();
		roSpec.getRoReportSpec()
				.setRoReportTrigger(ROReportTrigger.UPON_N_MILLISECONDS_OR_END_OF_ROSPEC);
		roSpec.getRoReportSpec().setN(500);
		llrpClient.requestSendingData(channel, new AddROSpec(header, roSpec));
		AddROSpecResponse addROSpecResponse = (AddROSpecResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(addROSpecResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);
		// send EnableROSpec
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new EnableROSpec(header, roSpec.getRoSpecID()));
		EnableROSpecResponse enableROSpecResponse = (EnableROSpecResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(enableROSpecResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);
		// send EnableEventsAndReports
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new EnableEventsAndReports(header));
		ROAccessReport enableEventsAndReportsResponse = (ROAccessReport) llrpClient
				.awaitReceivedData(channel, timeout);
		assertTrue(enableEventsAndReportsResponse.getTagReportDataList().isEmpty());

		// start ROSpec
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new StartROSpec(header, roSpec.getRoSpecID()));
		StartROSpecResponse startROSpecResponse = (StartROSpecResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(startROSpecResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);
		Thread.sleep(100);
		// stop ROSpec before the first trigger
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new StopROSpec(header, roSpec.getRoSpecID()));
		StopROSpecResponse stopROSpecResponse = (StopROSpecResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(stopROSpecResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);
		synchronized (data) {
			data.isLastResponse = true;
		}
		// receive report
		ROAccessReport roAccessReport = (ROAccessReport) llrpClient.awaitReceivedData(channel,
				timeout);
		assertEquals(roAccessReport.getTagReportDataList().size(), 1);
		assertEquals(roAccessReport.getTagReportDataList().get(0).getEpcData().getEpc(), epc);

		// start ROSpec
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new StartROSpec(header, roSpec.getRoSpecID()));
		startROSpecResponse = (StartROSpecResponse) llrpClient.awaitReceivedData(channel, timeout);
		assertEquals(startROSpecResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);
		Thread.sleep(250);
		// get report before the first trigger
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new GetReport(header));
		ROAccessReport getReportResponse = (ROAccessReport) llrpClient.awaitReceivedData(channel,
				timeout);
		assertEquals(getReportResponse.getTagReportDataList().size(), 1);
		assertEquals(getReportResponse.getTagReportDataList().get(0).getEpcData().getEpc(), epc);
		// wait for first trigger
		Thread.sleep(500);
		// receive report
		roAccessReport = (ROAccessReport) llrpClient.awaitReceivedData(channel, timeout);
		assertEquals(roAccessReport.getTagReportDataList().size(), 1);
		assertEquals(roAccessReport.getTagReportDataList().get(0).getEpcData().getEpc(), epc);
		// stop the ROSpec
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new StopROSpec(header, roSpec.getRoSpecID()));
		stopROSpecResponse = (StopROSpecResponse) llrpClient.awaitReceivedData(channel, timeout);
		assertEquals(stopROSpecResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);
		synchronized (data) {
			data.isLastResponse = true;
		}
		// receive report
		roAccessReport = (ROAccessReport) llrpClient.awaitReceivedData(channel, timeout);
		assertEquals(roAccessReport.getTagReportDataList().size(), 1);
		assertEquals(roAccessReport.getTagReportDataList().get(0).getEpcData().getEpc(), epc);

		// send DeleteROSpec
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new DeleteROSpec(header, roSpec.getRoSpecID()));
		DeleteROSpecResponse deleteROSpecResponse = (DeleteROSpecResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(deleteROSpecResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);

		// Send CloseConnection
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new CloseConnection(header));
		CloseConnectionResponse closeConnectionResponse;
		try {
			closeConnectionResponse = (CloseConnectionResponse) llrpClient
					.awaitReceivedData(channel, timeout);
		} catch (LLRPUnknownChannelException e) {
			LLRPChannelClosedEvent event = llrpEventHandler.awaitChannelClosed();
			if (event == null) {
				fail();
			}
			ByteBuffer buf = event.getPendingReceivedData();
			// create serializer
			ByteBufferSerializer serializer = new ByteBufferSerializer();
			// deserialize the header
			MessageHeader dHeader = serializer.deserializeMessageHeader(buf);
			// deserialize message
			closeConnectionResponse = (CloseConnectionResponse) serializer
					.deserializeMessage(dHeader, buf);
		}
		assertEquals(closeConnectionResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);

		closeInstance(instance, 3000);
	}

	// @Mocked
	// RFCMessageHandler rfcMessageHandler;
	// @Mocked
	// PlatformManager platformManager;
	// @Mocked
	// Platform platform;
	// @Mocked
	// ReaderEventNotificationSpec rens;

	@Test
	public void getReportsAsyncError(//
			@Mocked final RFCMessageHandler rfcMessageHandler,
			@Mocked final PlatformManager platformManager, @Mocked final Platform platform,
			@Mocked final ReaderEventNotificationSpec rens//
	) throws Exception {

		// create an EPC for reports
		final BitSet epc = new BitSet();
		epc.set(7);
		final byte[] epcBytes = { 1 };

		class Data {
			EntityManagerException addException = null;
			EntityManagerException removeException = null;
			List<EventNotificationState> states = new ArrayList<>();
			boolean isLastResponse = false;
		}
		final Data data = new Data();

		new MockUp<ROAccessReportDepot>() {
			// mock the "add" method which is called after an execution
			// response is received
			@Mock
			List<String> add(Invocation inv, List<ROAccessReportEntity> entities)
					throws EntityManagerException {
				if (data.addException == null) {
					return inv.proceed(entities);
				}
				throw data.addException;
			}

			// mock the "remove" method which is called to create a report
			@Mock
			List<ROAccessReportEntity> remove(Invocation inv, List<String> entityIds)
					throws EntityManagerException {
				if (data.removeException == null) {
					return inv.proceed(entityIds);
				}
				throw data.removeException;
			}
		};

		new Expectations() {
			EventQueue eventQueue;
			{
				// get the event queue when the RFC message handler is created
				new RFCMessageHandler(withInstanceOf(ServerConfiguration.class),
						withInstanceOf(ServerInstanceConfiguration.class),
						withInstanceOf(EventQueue.class), null /* rfcServiceFactory */, platform);
				result = new Delegate<RFCMessageHandler>() {
					@SuppressWarnings("unused")
					public void RFCMessageHandler(ServerConfiguration serverConfiguration,
							ServerInstanceConfiguration instanceConfig, EventQueue queue,
							ServiceFactory<RFDevice> rfcServiceFactory, Platform platform) {
						eventQueue = queue;
					}
				};

				// enqueue execute response messages with the defined EPC after
				// ROSpec has been started
				rfcMessageHandler.requestExecution(withInstanceOf(ROSpec.class));
				result = new Delegate<RFCMessageHandler>() {
					@SuppressWarnings("unused")
					public void requestExecution(final ROSpec roSpec) {
						new Thread(new Runnable() {

							@Override
							public void run() {
								boolean isLastResponse = false;
								while (!isLastResponse) {
									synchronized (data) {
										isLastResponse = data.isLastResponse;
										data.isLastResponse = false;
									}
									try {
										Thread.sleep(250);
									} catch (InterruptedException e) {
										fail();
									}
									TagData tagData = new TagData();
									tagData.setEpc(epcBytes);
									ExecuteResponse executeResponse = new ExecuteResponse(
											new havis.llrpservice.sbc.rfc.message.MessageHeader(
													4 /* id */),
											Arrays.asList(tagData), null /* timeStamp */);
									eventQueue.put(new RFCMessageEvent(executeResponse,
											new ExecuteResponseData(roSpec.getRoSpecID(),
													1 /* specIndex */,
													1 /* inventoryParameterSpecId */,
													1 /* antennaId */, ProtocolId.EPC_GLOBAL_C1G2,
													new HashMap<Long, Long>() /* tagDataAccessSpecIds */,
													isLastResponse)));
									try {
										Thread.sleep(250);
									} catch (InterruptedException e) {
										fail();
									}
								}
							}
						}).start();
					}
				};

				rens.getEventNotificationStateList();
				result = new Delegate<ReaderEventNotificationSpec>() {
					@SuppressWarnings("unused")
					public List<EventNotificationState> getEventNotificationStateList() {
						return data.states;
					}
				};
			}
		};

		// open LLRP client
		Instance instance = openInstance(null /* listener */, 3000 /* openTimeout */);
		TestLLRPEventHandler llrpEventHandler = new TestLLRPEventHandler();
		llrpClient.requestOpeningChannel("localhost", 4321, llrpEventHandler);
		SocketChannel channel = llrpEventHandler.awaitChannelOpened().getChannel();
		if (channel == null) {
			fail();
		}
		// receive connection attempt event
		ReaderEventNotification readerEventNotificationResponse = (ReaderEventNotification) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(
				readerEventNotificationResponse.getReaderEventNotificationData()
						.getConnectionAttemptEvent().getStatus(),
				ConnectionAttemptEventStatusType.SUCCESS);
		// send SetProtocolVersion
		MessageHeader header = new MessageHeader((byte) 0x00, ProtocolVersion.LLRP_V1_1,
				11 /* id */);
		header.setId(header.getId() + 1);
		SetProtocolVersion setVersion = new SetProtocolVersion(header, ProtocolVersion.LLRP_V1_1);
		llrpClient.requestSendingData(channel, setVersion);
		SetProtocolVersionResponse setVersionResponse = (SetProtocolVersionResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(setVersionResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);

		// send AddROSpec (tags are reported each 500 ms or at end of ROSpec)
		header.setId(header.getId() + 1);
		ROSpec roSpec = createROSpec();
		roSpec.getRoReportSpec()
				.setRoReportTrigger(ROReportTrigger.UPON_N_MILLISECONDS_OR_END_OF_ROSPEC);
		roSpec.getRoReportSpec().setN(500);
		llrpClient.requestSendingData(channel, new AddROSpec(header, roSpec));
		AddROSpecResponse addROSpecResponse = (AddROSpecResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(addROSpecResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);
		// send EnableROSpec
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new EnableROSpec(header, roSpec.getRoSpecID()));
		EnableROSpecResponse enableROSpecResponse = (EnableROSpecResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(enableROSpecResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);
		// send EnableEventsAndReports
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new EnableEventsAndReports(header));
		ROAccessReport enableEventsAndReportsResponse = (ROAccessReport) llrpClient
				.awaitReceivedData(channel, timeout);
		assertTrue(enableEventsAndReportsResponse.getTagReportDataList().isEmpty());

		// avoid adding of reports to depot
		data.addException = new EntityManagerException("huhu1");

		// start ROSpec
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new StartROSpec(header, roSpec.getRoSpecID()));
		StartROSpecResponse startROSpecResponse = (StartROSpecResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(startROSpecResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);

		// 250 ms: 1. report cannot been added
		// 500 ms: no message is sent due to exception and disabled reader
		// exception events
		try {
			llrpClient.awaitReceivedData(channel, 625 /* timeout */);
			fail();
		} catch (LLRPTimeoutException e) {
		}

		// allow adding of reports to depot but avoid to remove them
		data.addException = null;
		data.removeException = new EntityManagerException("huhu2");

		// 750 ms: 2. report has been added
		// 1000ms: the report cannot be removed but no message is sent due to
		// disabled reader exception events
		try {
			llrpClient.awaitReceivedData(channel, 500 /* timeout */);
			fail();
		} catch (LLRPTimeoutException e) {
		}

		// avoid adding of reports to depot
		data.addException = new EntityManagerException("huhu3");
		data.removeException = null;

		// enable reader exception events
		data.states.add(new EventNotificationState(new TLVParameterHeader((byte) 0),
				EventNotificationStateEventType.READER_EXCEPTION_EVENT, true /* state */));

		// 1250ms: 3. report cannot be added
		// 1500ms: an exception event is received
		ReaderEventNotification notification = (ReaderEventNotification) llrpClient
				.awaitReceivedData(channel, timeout);
		assertTrue(notification.getReaderEventNotificationData().getReaderExceptionEvent()
				.getStringMessage().contains("Cannot process RF execution response"));

		// allow adding of reports to depot but avoid to remove them
		data.addException = null;
		data.removeException = new EntityManagerException("huhu4");

		// 1750ms: 4. report is added
		// 2000ms: an exception event is received because the report cannot be
		// removed from the depot
		notification = (ReaderEventNotification) llrpClient.awaitReceivedData(channel, timeout);
		assertTrue(notification.getReaderEventNotificationData().getReaderExceptionEvent()
				.getStringMessage().contains("Cannot create report"));

		// disable reader exception events to avoid an event at the end of the
		// ROSpec execution
		data.states.clear();
		// enable adding and removing of reports to/from depot => a report can
		// be created at the end of ROSpec
		data.addException = null;
		data.removeException = null;

		// stop ROSpec
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new StopROSpec(header, roSpec.getRoSpecID()));
		StopROSpecResponse stopROSpecResponse = (StopROSpecResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(stopROSpecResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);
		synchronized (data) {
			data.isLastResponse = true;
		}
		// receive report
		ROAccessReport accessReport = (ROAccessReport) llrpClient.awaitReceivedData(channel,
				timeout);
		assertEquals(epc, accessReport.getTagReportDataList().get(0).getEpcData().getEpc());

		// send DeleteROSpec
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new DeleteROSpec(header, roSpec.getRoSpecID()));
		DeleteROSpecResponse deleteROSpecResponse = (DeleteROSpecResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(deleteROSpecResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);

		// Send CloseConnection
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new CloseConnection(header));
		CloseConnectionResponse closeConnectionResponse;
		try {
			closeConnectionResponse = (CloseConnectionResponse) llrpClient
					.awaitReceivedData(channel, timeout);
		} catch (LLRPUnknownChannelException e) {
			LLRPChannelClosedEvent event = llrpEventHandler.awaitChannelClosed();
			if (event == null) {
				fail();
			}
			ByteBuffer buf = event.getPendingReceivedData();
			// create serializer
			ByteBufferSerializer serializer = new ByteBufferSerializer();
			// deserialize the header
			MessageHeader dHeader = serializer.deserializeMessageHeader(buf);
			// deserialize message
			closeConnectionResponse = (CloseConnectionResponse) serializer
					.deserializeMessage(dHeader, buf);
		}
		assertEquals(closeConnectionResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);

		closeInstance(instance, 3000);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void getReportSync(//
			@Mocked final RFCMessageHandler rfcMessageHandler,
			@Mocked final ROAccessReportDepot reportDepot//
	) throws Exception {
		// create an EPC for reports
		final BitSet epc = new BitSet();
		epc.set(7);
		final byte[] epcBytes = { 1 };

		new Expectations() {
			{
				reportDepot.remove(withInstanceOf(List.class));
				result = new Delegate<ROAccessReportDepot>() {
					@SuppressWarnings("unused")
					public List<ROAccessReportEntity> remove(List<String> entityIds)
							throws EntityManagerException {
						// create report
						List<ROAccessReportEntity> reports = new ArrayList<>();
						ROAccessReport report = new ROAccessReport(new MessageHeader((byte) 0,
								ProtocolVersion.LLRP_V1_0_1, 2 /* id */));
						List<TagReportData> tagReportDataList = new ArrayList<>();
						EPCData epcData = new EPCData(new TLVParameterHeader((byte) 0), epc);
						epcData.setEpcLengthBits(epcBytes.length * 8);
						tagReportDataList
								.add(new TagReportData(new TLVParameterHeader((byte) 0), epcData));
						report.setTagReportDataList(tagReportDataList);
						ROAccessReportEntity reportEntity = new ROAccessReportEntity();
						reportEntity.setRoSpecId(9999);
						reportEntity.setReport(report);
						reports.add(reportEntity);
						return reports;
					}
				};
			}
		};

		// open LLRP client
		Instance instance = openInstance(null /* listener */, 3000 /* openTimeout */);
		TestLLRPEventHandler llrpEventHandler = new TestLLRPEventHandler();
		llrpClient.requestOpeningChannel("localhost", 4321, llrpEventHandler);
		SocketChannel channel = llrpEventHandler.awaitChannelOpened().getChannel();
		if (channel == null) {
			fail();
		}
		// receive connection attempt event
		ReaderEventNotification readerEventNotificationResponse = (ReaderEventNotification) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(
				readerEventNotificationResponse.getReaderEventNotificationData()
						.getConnectionAttemptEvent().getStatus(),
				ConnectionAttemptEventStatusType.SUCCESS);
		// send SetProtocolVersion
		MessageHeader header = new MessageHeader((byte) 0x00, ProtocolVersion.LLRP_V1_1,
				11 /* id */);
		header.setId(header.getId() + 1);
		SetProtocolVersion setVersion = new SetProtocolVersion(header, ProtocolVersion.LLRP_V1_1);
		llrpClient.requestSendingData(channel, setVersion);
		SetProtocolVersionResponse setVersionResponse = (SetProtocolVersionResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(setVersionResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);

		// send GetReport and verify response
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new GetReport(header));
		ROAccessReport getReportResponse = (ROAccessReport) llrpClient.awaitReceivedData(channel,
				timeout);
		assertEquals(getReportResponse.getMessageHeader().getVersion(), ProtocolVersion.LLRP_V1_1);
		assertEquals(getReportResponse.getTagReportDataList().size(), 1);
		assertEquals(getReportResponse.getTagReportDataList().get(0).getEpcData().getEpc(), epc);

		// send EnableEventsAndReports and verify response
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new EnableEventsAndReports(header));
		ROAccessReport enableEventsAndReportsResponse = (ROAccessReport) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(enableEventsAndReportsResponse.getMessageHeader().getVersion(),
				ProtocolVersion.LLRP_V1_1);
		assertEquals(enableEventsAndReportsResponse.getTagReportDataList().size(), 1);
		assertEquals(getReportResponse.getTagReportDataList().get(0).getEpcData().getEpc(), epc);

		// send CloseConnection
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new CloseConnection(header));
		CloseConnectionResponse closeConnectionResponse;
		try {
			closeConnectionResponse = (CloseConnectionResponse) llrpClient
					.awaitReceivedData(channel, timeout);
		} catch (LLRPUnknownChannelException e) {
			LLRPChannelClosedEvent event = llrpEventHandler.awaitChannelClosed();
			if (event == null) {
				fail();
			}
			ByteBuffer buf = event.getPendingReceivedData();
			// create serializer
			ByteBufferSerializer serializer = new ByteBufferSerializer();
			// deserialize the header
			MessageHeader dHeader = serializer.deserializeMessageHeader(buf);
			// deserialize message
			closeConnectionResponse = (CloseConnectionResponse) serializer
					.deserializeMessage(dHeader, buf);
		}
		assertEquals(closeConnectionResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);

		closeInstance(instance, 3000);
	}

	// @Mocked
	// RFCMessageHandler rfcMessageHandler;
	// @Mocked
	// ROAccessReportDepot reportDepot;
	// @Mocked
	// ReaderEventNotificationSpec rens;

	@Test
	@SuppressWarnings("unchecked")
	public void getReportSyncError(//
			@Mocked final RFCMessageHandler rfcMessageHandler,
			@Mocked final ROAccessReportDepot reportDepot,
			@Mocked final ReaderEventNotificationSpec rens//
	) throws Exception {
		class Data {
			List<EventNotificationState> states = new ArrayList<>();
		}
		final Data data = new Data();

		new Expectations() {
			{
				// throw an exception if the reports are removed from the depot
				reportDepot.remove(withInstanceOf(List.class));
				result = new EntityManagerException("huhu");

				rens.getEventNotificationStateList();
				result = new Delegate<ReaderEventNotificationSpec>() {
					@SuppressWarnings("unused")
					public List<EventNotificationState> getEventNotificationStateList() {
						return data.states;
					}
				};
			}
		};

		// open LLRP client
		Instance instance = openInstance(null /* listener */, 3000 /* openTimeout */);
		TestLLRPEventHandler llrpEventHandler = new TestLLRPEventHandler();
		llrpClient.requestOpeningChannel("localhost", 4321, llrpEventHandler);
		SocketChannel channel = llrpEventHandler.awaitChannelOpened().getChannel();
		if (channel == null) {
			fail();
		}
		// receive connection attempt event
		ReaderEventNotification readerEventNotificationResponse = (ReaderEventNotification) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(
				readerEventNotificationResponse.getReaderEventNotificationData()
						.getConnectionAttemptEvent().getStatus(),
				ConnectionAttemptEventStatusType.SUCCESS);
		// send SetProtocolVersion
		MessageHeader header = new MessageHeader((byte) 0x00, ProtocolVersion.LLRP_V1_1,
				11 /* id */);
		header.setId(header.getId() + 1);
		SetProtocolVersion setVersion = new SetProtocolVersion(header, ProtocolVersion.LLRP_V1_1);
		llrpClient.requestSendingData(channel, setVersion);
		SetProtocolVersionResponse setVersionResponse = (SetProtocolVersionResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(setVersionResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);

		// send SetReaderConfig to enable holding of events and reports
		header.setId(header.getId() + 1);
		SetReaderConfig src = new SetReaderConfig(header, false /* resetToFactoryDefaults */);
		src.setEventAndReports(
				new EventsAndReports(new TLVParameterHeader((byte) 0), true /* hold */));
		llrpClient.requestSendingData(channel, src);
		SetReaderConfigResponse setReaderConfigResponse = (SetReaderConfigResponse) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(setReaderConfigResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);

		// send GetReport and verify response (no exception event is received)
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new GetReport(header));
		ROAccessReport getReportResponse = (ROAccessReport) llrpClient.awaitReceivedData(channel,
				timeout);
		assertEquals(getReportResponse.getMessageHeader().getVersion(), ProtocolVersion.LLRP_V1_1);
		assertEquals(getReportResponse.getTagReportDataList().size(), 0);

		// send EnableEventsAndReports and verify response (the holding of
		// events is disabled with this message but sending of exception events
		// is still disabled)
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new EnableEventsAndReports(header));
		ROAccessReport enableEventsAndReportsResponse = (ROAccessReport) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(enableEventsAndReportsResponse.getMessageHeader().getVersion(),
				ProtocolVersion.LLRP_V1_1);
		assertEquals(enableEventsAndReportsResponse.getTagReportDataList().size(), 0);

		// send GetReport and verify response (no exception event is received)
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new GetReport(header));
		getReportResponse = (ROAccessReport) llrpClient.awaitReceivedData(channel, timeout);
		assertEquals(getReportResponse.getMessageHeader().getVersion(), ProtocolVersion.LLRP_V1_1);
		assertEquals(getReportResponse.getTagReportDataList().size(), 0);

		// enable sending of exception events
		data.states.add(new EventNotificationState(new TLVParameterHeader((byte) 0),
				EventNotificationStateEventType.READER_EXCEPTION_EVENT, true /* state */));

		// send GetReport and verify responses (an exception event is received)
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new GetReport(header));
		ReaderEventNotification notification = (ReaderEventNotification) llrpClient
				.awaitReceivedData(channel, timeout);
		assertTrue(notification.getReaderEventNotificationData().getReaderExceptionEvent()
				.getStringMessage().contains("Cannot create report"));
		getReportResponse = (ROAccessReport) llrpClient.awaitReceivedData(channel, timeout);
		assertEquals(getReportResponse.getMessageHeader().getVersion(), ProtocolVersion.LLRP_V1_1);
		assertEquals(getReportResponse.getTagReportDataList().size(), 0);

		// Send EnableEventsAndReports and verify response (an exception event
		// is received)
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new EnableEventsAndReports(header));
		notification = (ReaderEventNotification) llrpClient.awaitReceivedData(channel, timeout);
		assertTrue(notification.getReaderEventNotificationData().getReaderExceptionEvent()
				.getStringMessage().contains("Cannot create report"));
		enableEventsAndReportsResponse = (ROAccessReport) llrpClient.awaitReceivedData(channel,
				timeout);
		assertEquals(enableEventsAndReportsResponse.getMessageHeader().getVersion(),
				ProtocolVersion.LLRP_V1_1);
		assertEquals(enableEventsAndReportsResponse.getTagReportDataList().size(), 0);

		// send CloseConnection
		header.setId(header.getId() + 1);
		llrpClient.requestSendingData(channel, new CloseConnection(header));
		CloseConnectionResponse closeConnectionResponse;
		try {
			closeConnectionResponse = (CloseConnectionResponse) llrpClient
					.awaitReceivedData(channel, timeout);
		} catch (LLRPUnknownChannelException e) {
			LLRPChannelClosedEvent event = llrpEventHandler.awaitChannelClosed();
			if (event == null) {
				fail();
			}
			ByteBuffer buf = event.getPendingReceivedData();
			// create serializer
			ByteBufferSerializer serializer = new ByteBufferSerializer();
			// deserialize the header
			MessageHeader dHeader = serializer.deserializeMessageHeader(buf);
			// deserialize message
			closeConnectionResponse = (CloseConnectionResponse) serializer
					.deserializeMessage(dHeader, buf);
		}
		assertEquals(closeConnectionResponse.getStatus().getStatusCode(), LLRPStatusCode.M_SUCCESS);

		closeInstance(instance, 3000);
	}

	// @Mocked RFCMessageHandler rfcMessageHandler;

	@Test
	public void cancelExecution(//
			@Mocked final RFCMessageHandler rfcMessageHandler//
	) throws Exception {
		// open an instance
		Instance instance = openInstance(null /* listener */, 3000 /* openTimeout */);
		Thread.sleep(1000);
		// the instance is waiting for events at the event queue
		// cancel the execution
		instance.instance.cancelExecution();
		// the instance thread finishes
		instance.future.get(3000, TimeUnit.MILLISECONDS);
		instance.serverConf.close();
		instance.threadPool.shutdown();

		// open an instance
		instance = openInstance(null /* listener */, 3000 /* openTimeout */);
		// open a LLRP client
		TestLLRPEventHandler llrpEventHandler = new TestLLRPEventHandler();
		llrpClient.requestOpeningChannel("localhost", 4321, llrpEventHandler);
		SocketChannel channel = llrpEventHandler.awaitChannelOpened().getChannel();
		if (channel == null) {
			fail();
		}

		// a connection attempt event is received (success)
		ReaderEventNotification connectionAttemptEvent = (ReaderEventNotification) llrpClient
				.awaitReceivedData(channel, timeout);
		assertEquals(connectionAttemptEvent.getReaderEventNotificationData()
				.getConnectionAttemptEvent().getStatus(), ConnectionAttemptEventStatusType.SUCCESS);

		// the instance is waiting for a LLRP message from the client
		// cancel the execution
		instance.instance.cancelExecution();

		// a connection close event is received
		ReaderEventNotification connectionCloseEvent;
		try {
			connectionCloseEvent = (ReaderEventNotification) llrpClient.awaitReceivedData(channel,
					timeout);
		} catch (LLRPUnknownChannelException e) {
			LLRPChannelClosedEvent channelCloseEvent = llrpEventHandler.awaitChannelClosed();
			if (channelCloseEvent == null) {
				fail();
			}
			ByteBuffer data = channelCloseEvent.getPendingReceivedData();
			// create serializer
			ByteBufferSerializer serializer = new ByteBufferSerializer();
			// deserialize the header
			MessageHeader dHeader = serializer.deserializeMessageHeader(data);
			// deserialize message
			connectionCloseEvent = (ReaderEventNotification) serializer.deserializeMessage(dHeader,
					data);
		}
		assertNotNull(
				connectionCloseEvent.getReaderEventNotificationData().getConnectionCloseEvent());

		// the instance thread finishes
		instance.future.get(3000, TimeUnit.MILLISECONDS);
		instance.serverConf.close();
		instance.threadPool.shutdown();
	}

	private Instance openInstance(TestLLRPServiceInstanceListener[] listeners, long openTimeout)
			throws JiBXException, ConfigurationException, EntityManagerException,
			PersistenceException, InterruptedException, TimeoutException {
		// Create ServerConfigFile
		XMLFile<LLRPServerConfigurationType> serverConfigFile = new XMLFile<>(
				LLRPServerConfigurationType.class, SERVER_CONFIG_INIT_PATH,
				SERVER_CONFIG_LATEST_PATH);

		Instance ret = new Instance();
		// Create ServerConfiguration
		ret.serverConf = new ServerConfiguration(serverConfigFile);
		ret.serverConf.open();

		// Create ServerInstanceConfiguration file
		XMLFile<LLRPServerInstanceConfigurationType> instanceConfigFile = new XMLFile<>(
				LLRPServerInstanceConfigurationType.class, SERVER_INSTANCE_CONFIG_INIT_PATH,
				SERVER_INSTANCE_CONFIG_LATEST_PATH);

		DefaultsGroup groupProperties = new DefaultsGroup();
		IdentificationSourceType identificationSource = new IdentificationSourceType();
		identificationSource.setType(IdentificationTypeEnumeration.MAC_ADDRESS);
		groupProperties.setIdentificationSource(identificationSource);
		groupProperties.setLLRPCapabilities(new LLRPCapabilitiesType());
		groupProperties.setMaxStartupRetries(5);

		ret.instance = new LLRPServiceInstance(ret.serverConf, instanceConfigFile, groupProperties,
				60000, tcpServerLLRP, null /* scServiceFactory */, null /* rfcServiceFactory */,
				null /* gpioServiceFactory */);

		TestLLRPServiceInstanceListener listener = null;
		if (listeners == null) {
			listener = new TestLLRPServiceInstanceListener();
			ret.instance.addListener(listener);
		} else {
			for (TestLLRPServiceInstanceListener l : listeners) {
				ret.instance.addListener(l);
			}
		}
		ret.threadPool = Executors.newFixedThreadPool(1);
		ret.future = ret.threadPool.submit(ret.instance);
		if (listener != null) {
			listener.awaitOpened(openTimeout);
		}
		return ret;
	}

	private void closeInstance(Instance instance, long timeout) throws Exception {
		instance.instance.cancelExecution();
		instance.future.get(timeout, TimeUnit.MILLISECONDS);
		instance.serverConf.close();
		instance.threadPool.shutdown();
	}

	private ROSpec createROSpec() throws InvalidParameterTypeException {
		// ROBoundarySpec
		ROSpecStartTrigger roSpecStartTrigger = new ROSpecStartTrigger(
				new TLVParameterHeader((byte) 0), ROSpecStartTriggerType.NULL_NO_START_TRIGGER);
		ROSpecStopTrigger stopTrigger = new ROSpecStopTrigger(new TLVParameterHeader((byte) 0),
				ROSpecStopTriggerType.NULL, 0 /* durationTriggerValue */);
		ROBoundarySpec roBoundarySpec = new ROBoundarySpec(new TLVParameterHeader((byte) 0),
				roSpecStartTrigger, stopTrigger);

		// AISpec
		AISpecStopTrigger aiSpecStopTrigger = new AISpecStopTrigger(
				new TLVParameterHeader((byte) 0), AISpecStopTriggerType.NULL,
				(long) 0 /* durationTrigger */);

		List<InventoryParameterSpec> invParamSpecs = new ArrayList<>();
		invParamSpecs.add(new InventoryParameterSpec(new TLVParameterHeader((byte) 0),
				4444 /* specId */, ProtocolId.EPC_GLOBAL_C1G2));

		AISpec aiSpec = new AISpec(new TLVParameterHeader((byte) 0),
				Arrays.asList(1) /* antennaIds */, aiSpecStopTrigger, invParamSpecs);

		// ROSpec
		ROSpec roSpec = new ROSpec(new TLVParameterHeader((byte) 0), 9999 /* roSpecId */,
				(short) 1 /* priority */, ROSpecCurrentState.DISABLED, roBoundarySpec,
				Arrays.asList((Parameter) aiSpec));
		roSpec.setRoReportSpec(new ROReportSpec(new TLVParameterHeader((byte) 0x00),
				ROReportTrigger.UPON_N_TAGREPORTDATA_PARAMETERS_OR_END_OF_ROSPEC, 0 /* n */,
				new TagReportContentSelector(new TLVParameterHeader((byte) 0x00),
						false /* enableROSpecID */, false /* enableSpecIndex */,
						false /* enableInventoryParameterSpecID */, false /* enableAntennaID */,
						false /* enableChannelIndex */, false /* enablePeakRSSI */,
						false /* enableFirstSeenTimestamp */, false /* enableLastSeenTimestamp */,
						false /* enableTagSeenCount */, false /* enableAccessSpecID */)));

		havis.llrpservice.data.message.parameter.serializer.ByteBufferSerializer serializer = new havis.llrpservice.data.message.parameter.serializer.ByteBufferSerializer();
		int length = serializer.getLength(roSpec);
		ByteBuffer data = ByteBuffer.allocate(length);
		serializer.serialize(roSpec, data);
		data.rewind();
		roSpec = serializer.deserializeROSpec(
				(TLVParameterHeader) serializer.deserializeParameterHeader(data), data);

		return roSpec;
	}

	private AccessSpec createAccessSpec() throws InvalidParameterTypeException {
		AccessSpecStopTrigger accessSpecStopTrigger = new AccessSpecStopTrigger(
				new TLVParameterHeader((byte) 0), AccessSpecStopTriggerType.OPERATION_COUNT, 555);

		// 1111000010101010
		BitSet tagMask = new BitSet();
		tagMask.set(0);
		tagMask.set(1);
		tagMask.set(2);
		tagMask.set(3);
		tagMask.set(8);
		tagMask.set(10);
		tagMask.set(12);
		tagMask.set(14);

		// 0111111011001100
		BitSet tagData = new BitSet();
		tagData.set(1);
		tagData.set(2);
		tagData.set(3);
		tagData.set(4);
		tagData.set(5);
		tagData.set(6);
		tagData.set(8);
		tagData.set(9);
		tagData.set(12);
		tagData.set(13);

		C1G2TagSpec c1g2TagSpec = new C1G2TagSpec(new TLVParameterHeader((byte) 0),
				new C1G2TargetTag(new TLVParameterHeader((byte) 0), (byte) 3 /* memoryBank */,
						true /* isMatch */, 100 /* pointer */, tagMask, tagData));

		C1G2Read c1g2Read = new C1G2Read(new TLVParameterHeader((byte) 0), 33333/* opSpecId */,
				111111111L/* accessPasswd */, (byte) 3 /* memoryBank */, 1234 /* wordPointer */,
				10000/* wordCount */);

		AccessCommand accessCommand = new AccessCommand(new TLVParameterHeader((byte) 0),
				c1g2TagSpec, Arrays.asList((Parameter) c1g2Read));

		AccessSpec accessSpec = new AccessSpec(new TLVParameterHeader((byte) 0),
				11111111L /* accessSpecId */, 7777 /* antennaId */, ProtocolId.EPC_GLOBAL_C1G2,
				false /* currentState */, 22222222L /* roSpecId */, accessSpecStopTrigger,
				accessCommand);
		accessSpec.setAccessReportSpec(new AccessReportSpec(new TLVParameterHeader((byte) 0),
				AccessReportTrigger.END_OF_ACCESSSPEC));

		havis.llrpservice.data.message.parameter.serializer.ByteBufferSerializer serializer = new havis.llrpservice.data.message.parameter.serializer.ByteBufferSerializer();
		int length = serializer.getLength(accessSpec);
		ByteBuffer data = ByteBuffer.allocate(length);
		serializer.serialize(accessSpec, data);
		data.rewind();
		accessSpec = serializer.deserializeAccessSpec(
				(TLVParameterHeader) serializer.deserializeParameterHeader(data), data);

		return accessSpec;
	}
}
