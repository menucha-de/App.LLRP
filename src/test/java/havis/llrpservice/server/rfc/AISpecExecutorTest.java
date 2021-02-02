package havis.llrpservice.server.rfc;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

import havis.device.rf.tag.Filter;
import havis.device.rf.tag.TagData;
import havis.device.rf.tag.operation.ReadOperation;
import havis.device.rf.tag.operation.TagOperation;
import havis.llrpservice.data.message.parameter.AISpec;
import havis.llrpservice.data.message.parameter.AISpecStopTrigger;
import havis.llrpservice.data.message.parameter.AISpecStopTriggerType;
import havis.llrpservice.data.message.parameter.AccessSpec;
import havis.llrpservice.data.message.parameter.GPIEvent;
import havis.llrpservice.data.message.parameter.GPITriggerValue;
import havis.llrpservice.data.message.parameter.InventoryParameterSpec;
import havis.llrpservice.data.message.parameter.ProtocolId;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.data.message.parameter.TagObservationTrigger;
import havis.llrpservice.data.message.parameter.TagObservationTriggerType;
import havis.llrpservice.sbc.rfc.RFCClientMultiplexed;
import havis.llrpservice.sbc.rfc.message.Execute;
import havis.llrpservice.sbc.rfc.message.GetOperations;
import havis.llrpservice.server.event.EventQueue;
import havis.llrpservice.server.rfc.AISpecExecutor.AISpecExecutorListener;
import havis.llrpservice.server.rfc.AccessSpecExecutor.InventoryAccessOps;
import havis.llrpservice.server.rfc.ROSpecExecutor.ROSpecExecutionPosition;
import mockit.Mocked;
import mockit.NonStrictExpectations;
import mockit.Verifications;

@Test
public class AISpecExecutorTest {

	public void addRemove(@Mocked final RFCClientMultiplexed rfcClient) throws Exception {

		// add an AccessSpec
		final SocketChannel rfcChannel = SocketChannel.open();
		EventQueue eventQueue = new EventQueue();
		AISpecExecutor executor = new AISpecExecutor(rfcClient, rfcChannel, eventQueue);
		AccessSpec accessSpec = new AccessSpec(new TLVParameterHeader((byte) 0),
				567L /* accessSpecID */, 1 /* antennaID */, ProtocolId.EPC_GLOBAL_C1G2,
				true /* currentState */, 2L /* roSpecID */, null /* accessSpecStopTrigger */,
				null /* AccessCommand */);
		executor.add(accessSpec);
		// remove the AccessSpec
		Assert.assertNotNull(executor.remove(567));

		rfcChannel.close();
	}

	// @Mocked
	// RFCClientMultiplexed rfcClient;
	// @Mocked
	// AccessSpecExecutor accessSpecEx;
	// @Mocked
	// GetOperations getOps;

	public void startNextX(//
			@Mocked final RFCClientMultiplexed rfcClient,
			@Mocked final AccessSpecExecutor accessSpecEx, @Mocked final GetOperations getOps//
	) throws Exception {
		final InventoryAccessOps invAccessOps = accessSpecEx.new InventoryAccessOps();

		Filter filter = new Filter();
		filter.setBank((short) 0 /* bank */);
		filter.setBitOffset((short) 1);
		filter.setBitLength((short) 2);
		filter.setMask(new byte[] { 3, 4 } /* mask */);
		filter.setData(new byte[] { 3, 4 } /* data */);
		filter.setMatch(true);

		ReadOperation rdOp = new ReadOperation();
		rdOp.setOperationId("1");
		rdOp.setBank((short) 3);
		rdOp.setOffset((short) 0);
		rdOp.setLength((short) 12);
		rdOp.setPassword(0);

		invAccessOps.setTagFilter(Arrays.asList(new Filter[] { filter }));
		invAccessOps.setTagOperations(Arrays.asList(new TagOperation[] { rdOp }));
		new NonStrictExpectations() {
			{
				accessSpecEx
						.getNextInventoryAccessOps(withInstanceOf(ROSpecExecutionPosition.class));
				result = invAccessOps;
			}
		};

		// start inventory with 1 antenna + 1 invParamSpec
		EventQueue queue = new EventQueue();
		SocketChannel rfcChannel = SocketChannel.open();
		AISpecExecutor ex = new AISpecExecutor(rfcClient, rfcChannel, queue);
		final ProtocolId protocolId = ProtocolId.EPC_GLOBAL_C1G2;
		InventoryParameterSpec invParamSpec1 = new InventoryParameterSpec(
				new TLVParameterHeader((byte) 0), 10 /* specID */, protocolId);
		List<InventoryParameterSpec> invParamList = new ArrayList<>();
		invParamList.add(invParamSpec1);
		final int antennaId1 = 3;
		List<Integer> antennaIdList = new ArrayList<>();
		antennaIdList.add(antennaId1);
		AISpec aiSpec = new AISpec(new TLVParameterHeader((byte) 0), antennaIdList,
				new AISpecStopTrigger(new TLVParameterHeader((byte) 0), AISpecStopTriggerType.NULL,
						0 /* durationTrigger */),
				invParamList);
		final ROSpecExecutionPosition pos = new ROSpecExecutionPosition(2 /* roSpecId */);
		ex.startNextInventory(pos, aiSpec);
		Assert.assertEquals(pos.getAntennaId(), antennaId1);
		Assert.assertEquals(pos.getProtocolId(), protocolId);

		new Verifications() {
			{
				// access ops for the new AISpec are requested
				accessSpecEx.getNextInventoryAccessOps(pos);
				times = 1;

				// the inventory has been started
				Execute e;
				rfcClient.requestSendingData(withInstanceOf(SocketChannel.class),
						e = withCapture());
				times = 1;

				Assert.assertEquals((short) e.getAntennas().get(0), antennaId1);
				Assert.assertEquals(e.getFilters(), invAccessOps.getTagFilter());
				Assert.assertEquals(e.getOperations(), invAccessOps.getTagOperations());
			}
		};

		// start additional access operations
		ex.startNextAccessOps(getOps);
		new Verifications() {
			{
				ROSpecExecutionPosition p;
				accessSpecEx.startNextAccessOps(getOps, invAccessOps.getTagOperations(),
						p = withCapture());
				Assert.assertEquals(p.getAntennaId(), antennaId1);
				Assert.assertEquals(p.getProtocolId(), protocolId);
				times = 1;
			}
		};

		// extend AISpec with an additional invParamSpec and start the next
		// inventory (for the same AISpec)
		invParamList.add(new InventoryParameterSpec(new TLVParameterHeader((byte) 0),
				20 /* specID */, protocolId));
		ex.startNextInventory(pos, aiSpec);
		Assert.assertEquals(pos.getAntennaId(), antennaId1);
		Assert.assertEquals(pos.getProtocolId(), protocolId);

		new Verifications() {
			{
				// its the same AISpec -> no further access ops are requested
				accessSpecEx.getNextInventoryAccessOps(pos);
				times = 1;

				// the inventory has been started
				Execute e;
				rfcClient.requestSendingData(withInstanceOf(SocketChannel.class),
						e = withCapture());
				times = 2;

				Assert.assertEquals((short) e.getAntennas().get(0), antennaId1);
				Assert.assertEquals(e.getFilters(), invAccessOps.getTagFilter());
				Assert.assertEquals(e.getOperations(), invAccessOps.getTagOperations());
			}
		};

		// extend AISpec with an additional antenna and start the next
		// inventory (for the same AISpec)
		final int antennaId2 = 4;
		antennaIdList.add(antennaId2);
		ex.startNextInventory(pos, aiSpec);
		// the first invParamSpec is executed for the new antenna => a further
		// invParamSpec exists
		Assert.assertEquals(pos.getAntennaId(), antennaId2);
		Assert.assertEquals(pos.getProtocolId(), protocolId);

		new Verifications() {
			{
				// its the same AISpec -> no further access ops are requested
				accessSpecEx.getNextInventoryAccessOps(pos);
				times = 1;

				// the inventory has been started for the next antenna
				Execute e;
				rfcClient.requestSendingData(withInstanceOf(SocketChannel.class),
						e = withCapture());
				times = 3;

				Assert.assertEquals((short) e.getAntennas().get(0), antennaId2);
				Assert.assertEquals(e.getFilters(), invAccessOps.getTagFilter());
				Assert.assertEquals(e.getOperations(), invAccessOps.getTagOperations());
			}
		};

		// start inventory for the second invParamSpec
		ex.startNextInventory(pos, aiSpec);
		// it was the last inventory for the AISpec but due to AISpecStopTrigger
		// "Null" the loop starts again with the first
		// antenna/inventoryParameterSpec combination
		Assert.assertEquals(pos.getAntennaId(), antennaId2);
		Assert.assertEquals(pos.getProtocolId(), protocolId);

		new Verifications() {
			{
				// its the same AISpec -> no further access ops are requested
				accessSpecEx.getNextInventoryAccessOps(pos);
				times = 1;

				// the inventory has been started
				Execute e;
				rfcClient.requestSendingData(withInstanceOf(SocketChannel.class),
						e = withCapture());
				times = 4;

				Assert.assertEquals((short) e.getAntennas().get(0), antennaId2);
				Assert.assertEquals(e.getFilters(), invAccessOps.getTagFilter());
				Assert.assertEquals(e.getOperations(), invAccessOps.getTagOperations());
			}
		};

		// mocking is necessary again (why?)
		new NonStrictExpectations() {
			{
				accessSpecEx
						.getNextInventoryAccessOps(withInstanceOf(ROSpecExecutionPosition.class));
				result = invAccessOps;
			}
		};

		ex.startNextInventory(pos, aiSpec);
		// the first invParamSpec for the first anntenna has been executed
		Assert.assertEquals(pos.getAntennaId(), antennaId1);
		Assert.assertEquals(pos.getProtocolId(), protocolId);

		new Verifications() {
			{
				// access ops for the new AISpec are requested
				accessSpecEx.getNextInventoryAccessOps(pos);
				times = 1;

				// the inventory has been started for the first antenna
				Execute e;
				rfcClient.requestSendingData(withInstanceOf(SocketChannel.class),
						e = withCapture());
				times = 1;

				Assert.assertEquals((short) e.getAntennas().get(0), antennaId1);
				Assert.assertEquals(e.getFilters(), invAccessOps.getTagFilter());
				Assert.assertEquals(e.getOperations(), invAccessOps.getTagOperations());
			}
		};

		rfcChannel.close();
	}

	public void startNextXError(@Mocked final RFCClientMultiplexed rfcClient,
			@Mocked final AccessSpecExecutor accessSpecEx, @Mocked final GetOperations getOps)
			throws Exception {
		// start inventory with 1 antenna + 1 invParamSpec
		EventQueue queue = new EventQueue();
		SocketChannel rfcChannel = SocketChannel.open();
		AISpecExecutor ex = new AISpecExecutor(rfcClient, rfcChannel, queue);
		List<InventoryParameterSpec> invParamList = new ArrayList<>();
		invParamList.add(new InventoryParameterSpec(new TLVParameterHeader((byte) 0),
				1 /* specID */, ProtocolId.UNSPECIFIED_AIR_PROTOCOL));
		AISpec aiSpec = new AISpec(new TLVParameterHeader((byte) 0),
				Arrays.asList(new Integer[] { 3 }) /* antennaIdList */,
				new AISpecStopTrigger(new TLVParameterHeader((byte) 0), AISpecStopTriggerType.NULL,
						0 /* durationTrigger */),
				invParamList);
		try {
			ROSpecExecutionPosition pos = new ROSpecExecutionPosition(2 /* roSpecId */);
			ex.startNextInventory(pos, aiSpec);
			Assert.fail();
		} catch (UnsupportedAirProtocolException e) {
			Assert.assertTrue(
					e.getMessage().contains(ProtocolId.UNSPECIFIED_AIR_PROTOCOL.toString()));
		}

		rfcChannel.close();
	}

	// @Mocked
	// RFCClientMultiplexed rfcClient;

	public void stop(//
			@Mocked final RFCClientMultiplexed rfcClient//
	) throws Exception {
		SocketChannel rfcChannel = SocketChannel.open();
		AISpecExecutor ex = new AISpecExecutor(rfcClient, rfcChannel, new EventQueue());
		AISpecStopTrigger stopTrigger = new AISpecStopTrigger(new TLVParameterHeader((byte) 0),
				AISpecStopTriggerType.DURATION, 1000 /* durationTrigger */);
		AISpec aiSpec = new AISpec(new TLVParameterHeader((byte) 0),
				Arrays.asList(3) /* antennaIds */, stopTrigger,
				Arrays.asList(new InventoryParameterSpec(new TLVParameterHeader((byte) 0),
						10 /* specID */, ProtocolId.EPC_GLOBAL_C1G2)));
		final ROSpecExecutionPosition pos = new ROSpecExecutionPosition(2 /* roSpecId */);
		// start AISpec
		long startTime = System.currentTimeMillis();
		ex.startNextInventory(pos, aiSpec);
		class Data {
			long endTime = 0;
		}
		final Data data = new Data();
		AISpecExecutorListener listener = new AISpecExecutorListener() {

			@Override
			public void isStopped(AISpec aiSpec) {
				data.endTime = System.currentTimeMillis();
			}
		};
		ex.addListener(listener);
		// stop AISpec manually
		ex.stop();
		long diff = data.endTime - startTime;
		Assert.assertTrue(diff >= 0 && diff < 100);
		data.endTime = 0;

	}

	// @Mocked
	// RFCClientMultiplexed rfcClient;

	public void stopTriggerDuration(//
			@Mocked final RFCClientMultiplexed rfcClient//
	) throws Exception {
		SocketChannel rfcChannel = SocketChannel.open();
		AISpecExecutor ex = new AISpecExecutor(rfcClient, rfcChannel, new EventQueue());
		AISpecStopTrigger stopTrigger = new AISpecStopTrigger(new TLVParameterHeader((byte) 0),
				AISpecStopTriggerType.DURATION, 1000 /* durationTrigger */);
		AISpec aiSpec = new AISpec(new TLVParameterHeader((byte) 0),
				Arrays.asList(3) /* antennaIds */, stopTrigger,
				Arrays.asList(new InventoryParameterSpec(new TLVParameterHeader((byte) 0),
						10 /* specID */, ProtocolId.EPC_GLOBAL_C1G2)));
		final ROSpecExecutionPosition pos = new ROSpecExecutionPosition(2 /* roSpecId */);
		// start AISpec and wait for trigger
		long startTime = System.currentTimeMillis();
		ex.startNextInventory(pos, aiSpec);
		class Data {
			long endTime = 0;
		}
		final Data data = new Data();
		AISpecExecutorListener listener = new AISpecExecutorListener() {

			@Override
			public void isStopped(AISpec aiSpec) {
				data.endTime = System.currentTimeMillis();
			}
		};
		ex.addListener(listener);
		Thread.sleep(2000);
		long diff = data.endTime - startTime;
		Assert.assertTrue(diff > 900 && diff < 1100);
		data.endTime = 0;

		// start AISpec again but without a listener
		ex.removeListener(listener);
		ex.startNextInventory(pos, aiSpec);
		Thread.sleep(2000);
		Assert.assertEquals(data.endTime, 0);

		rfcChannel.close();
	}

	// @Mocked RFCClientMultiplexed rfcClient;
	public void stopTriggerGPI(//
			@Mocked final RFCClientMultiplexed rfcClient//
	) throws Exception {
		SocketChannel rfcChannel = SocketChannel.open();
		AISpecExecutor ex = new AISpecExecutor(rfcClient, rfcChannel, new EventQueue());
		AISpecStopTrigger stopTrigger = new AISpecStopTrigger(new TLVParameterHeader((byte) 0),
				AISpecStopTriggerType.GPI_WITH_TIMEOUT, 0 /* durationTrigger */);
		stopTrigger.setGpiTV(new GPITriggerValue(new TLVParameterHeader((byte) 0),
				1 /* gpiPortNum */, true /* gpiEvent */, 1000 /* timeout */));
		AISpec aiSpec = new AISpec(new TLVParameterHeader((byte) 0),
				Arrays.asList(3) /* antennaIds */, stopTrigger,
				Arrays.asList(new InventoryParameterSpec(new TLVParameterHeader((byte) 0),
						10 /* specID */, ProtocolId.EPC_GLOBAL_C1G2)));
		final ROSpecExecutionPosition pos = new ROSpecExecutionPosition(2 /* roSpecId */);
		class Data {
			long endTime = 0;
		}
		final Data data = new Data();
		ex.addListener(new AISpecExecutorListener() {

			@Override
			public void isStopped(AISpec aiSpec) {
				data.endTime = System.currentTimeMillis();
			}
		});

		// start AISpec
		ex.startNextInventory(pos, aiSpec);
		// send irrelevant events
		ex.gpiEventReceived(new GPIEvent(new TLVParameterHeader((byte) 0), 1 /* gpiPortNumber */,
				false /* state */));
		ex.gpiEventReceived(new GPIEvent(new TLVParameterHeader((byte) 0), 2 /* gpiPortNumber */,
				true /* state */));
		Assert.assertEquals(data.endTime, 0);
		// send stop event
		ex.gpiEventReceived(new GPIEvent(new TLVParameterHeader((byte) 0), 1 /* gpiPortNumber */,
				true /* state */));
		Assert.assertTrue(data.endTime > 0);
		data.endTime = 0;

		// start new AISpec and send irrelevant events until time out
		long startTime = System.currentTimeMillis();
		ex.startNextInventory(pos, aiSpec);
		Thread.sleep(500);
		ex.gpiEventReceived(new GPIEvent(new TLVParameterHeader((byte) 0), 1 /* gpiPortNumber */,
				false /* state */));
		Thread.sleep(500);
		TagData tagData = new TagData();
		tagData.setEpc(new byte[] { 1, 2 });
		ex.executionResponseReceived(Arrays.asList(tagData));
		ex.gpiEventReceived(new GPIEvent(new TLVParameterHeader((byte) 0), 1 /* gpiPortNumber */,
				false /* state */));
		Thread.sleep(500);
		ex.gpiEventReceived(new GPIEvent(new TLVParameterHeader((byte) 0), 1 /* gpiPortNumber */,
				false /* state */));
		long diff = data.endTime - startTime;
		Assert.assertTrue(diff > 900 && diff < 1100);

		rfcChannel.close();
	}

	// @Mocked
	// RFCClientMultiplexed rfcClient;

	public void stopTriggerTagObservation0(//
			@Mocked final RFCClientMultiplexed rfcClient//
	) throws Exception {
		SocketChannel rfcChannel = SocketChannel.open();
		AISpecExecutor ex = new AISpecExecutor(rfcClient, rfcChannel, new EventQueue());
		AISpecStopTrigger stopTrigger = new AISpecStopTrigger(new TLVParameterHeader((byte) 0),
				AISpecStopTriggerType.TAG_OBSERVATION, 0 /* durationTrigger */);
		stopTrigger.setTagOT(new TagObservationTrigger(new TLVParameterHeader((byte) 0),
				TagObservationTriggerType.UPON_SEEING_N_TAG_OBSERVATIONS_OR_TIMEOUT,
				4 /* numberOfTags */, 0 /* numberOfAttempts */, 0 /* t */, 1000 /* timeOut */));
		AISpec aiSpec = new AISpec(new TLVParameterHeader((byte) 0),
				Arrays.asList(3) /* antennaIds */, stopTrigger,
				Arrays.asList(new InventoryParameterSpec(new TLVParameterHeader((byte) 0),
						10 /* specID */, ProtocolId.EPC_GLOBAL_C1G2)));
		final ROSpecExecutionPosition pos = new ROSpecExecutionPosition(2 /* roSpecId */);
		class Data {
			long endTime = 0;
		}
		final Data data = new Data();
		ex.addListener(new AISpecExecutorListener() {

			@Override
			public void isStopped(AISpec aiSpec) {
				data.endTime = System.currentTimeMillis();
			}
		});

		// start AISpec
		ex.startNextInventory(pos, aiSpec);
		// send irrelevant events
		TagData tagData = new TagData();
		tagData.setEpc(new byte[] { 1, 2 });
		ex.executionResponseReceived(Arrays.asList(tagData));
		ex.executionResponseReceived(Arrays.asList(tagData, tagData));
		Assert.assertEquals(data.endTime, 0);
		// send stop event
		ex.executionResponseReceived(Arrays.asList(tagData));
		Assert.assertTrue(data.endTime > 0);
		data.endTime = 0;

		// start new AISpec and do not send any event -> time out
		long startTime = System.currentTimeMillis();
		ex.startNextInventory(pos, aiSpec);
		Thread.sleep(2000);
		long diff = data.endTime - startTime;
		Assert.assertTrue(diff > 900 && diff < 1100);
	}

	// @Mocked
	// RFCClientMultiplexed rfcClient;

	public void stopTriggerTagObservation1(//
			@Mocked final RFCClientMultiplexed rfcClient//
	) throws Exception {
		SocketChannel rfcChannel = SocketChannel.open();
		AISpecExecutor ex = new AISpecExecutor(rfcClient, rfcChannel, new EventQueue());
		AISpecStopTrigger stopTrigger = new AISpecStopTrigger(new TLVParameterHeader((byte) 0),
				AISpecStopTriggerType.TAG_OBSERVATION, 0 /* durationTrigger */);
		stopTrigger.setTagOT(new TagObservationTrigger(new TLVParameterHeader((byte) 0),
				TagObservationTriggerType.UPON_SEEING_NO_MORE_NEW_TAG_OBSERVATIONS_FOR_T_MS_OR_TIMEOUT,
				0 /* numberOfTags */, 0 /* numberOfAttempts */, 1000 /* t */, 2000 /* timeOut */));
		AISpec aiSpec = new AISpec(new TLVParameterHeader((byte) 0),
				Arrays.asList(3) /* antennaIds */, stopTrigger,
				Arrays.asList(new InventoryParameterSpec(new TLVParameterHeader((byte) 0),
						10 /* specID */, ProtocolId.EPC_GLOBAL_C1G2)));
		final ROSpecExecutionPosition pos = new ROSpecExecutionPosition(2 /* roSpecId */);
		class Data {
			long endTime = 0;
		}
		final Data data = new Data();
		ex.addListener(new AISpecExecutorListener() {

			@Override
			public void isStopped(AISpec aiSpec) {
				data.endTime = System.currentTimeMillis();
			}
		});

		// start AISpec
		ex.startNextInventory(pos, aiSpec);
		TagData tagData = new TagData();
		tagData.setEpc(new byte[] { 1, 2 });
		ex.executionResponseReceived(Arrays.asList(tagData));
		// send an event before idle time out
		Thread.sleep(500);
		long startTime = System.currentTimeMillis();
		ex.executionResponseReceived(Arrays.asList(tagData));
		Assert.assertEquals(data.endTime, 0);
		// wait for idle time out
		Thread.sleep(2000);
		long diff = data.endTime - startTime;
		Assert.assertTrue(diff > 900 && diff < 1100);
		data.endTime = 0;

		// start new AISpec and send periodically events until time out
		ex.startNextInventory(pos, aiSpec);
		startTime = System.currentTimeMillis();
		Thread.sleep(500);
		ex.executionResponseReceived(Arrays.asList(tagData));
		ex.gpiEventReceived(new GPIEvent(new TLVParameterHeader((byte) 0), 1 /* gpiPortNumber */,
				false /* state */));
		Thread.sleep(500);
		ex.executionResponseReceived(Arrays.asList(tagData));
		Thread.sleep(500);
		ex.executionResponseReceived(Arrays.asList(tagData));
		Thread.sleep(500);
		ex.executionResponseReceived(Arrays.asList(tagData));
		Thread.sleep(500);
		ex.executionResponseReceived(Arrays.asList(tagData));
		diff = data.endTime - startTime;
		Assert.assertTrue(diff > 1900 && diff < 2100);
	}

	// @Mocked
	// RFCClientMultiplexed rfcClient;

	public void stopTriggerTagObservation2(//
			@Mocked final RFCClientMultiplexed rfcClient//
	) throws Exception {
		SocketChannel rfcChannel = SocketChannel.open();
		AISpecExecutor ex = new AISpecExecutor(rfcClient, rfcChannel, new EventQueue());
		AISpecStopTrigger stopTrigger = new AISpecStopTrigger(new TLVParameterHeader((byte) 0),
				AISpecStopTriggerType.TAG_OBSERVATION, 0 /* durationTrigger */);
		stopTrigger.setTagOT(new TagObservationTrigger(new TLVParameterHeader((byte) 0),
				TagObservationTriggerType.N_ATTEMPTS_TO_SEE_ALL_TAGS_IN_THE_FOV_OR_TIMEOUT,
				0 /* numberOfTags */, 2 /* numberOfAttempts */, 0 /* t */, 1000 /* timeOut */));
		AISpec aiSpec = new AISpec(new TLVParameterHeader((byte) 0),
				Arrays.asList(3) /* antennaIds */, stopTrigger,
				Arrays.asList(new InventoryParameterSpec(new TLVParameterHeader((byte) 0),
						10 /* specID */, ProtocolId.EPC_GLOBAL_C1G2)));
		final ROSpecExecutionPosition pos = new ROSpecExecutionPosition(2 /* roSpecId */);
		class Data {
			long endTime = 0;
		}
		final Data data = new Data();
		ex.addListener(new AISpecExecutorListener() {

			@Override
			public void isStopped(AISpec aiSpec) {
				data.endTime = System.currentTimeMillis();
			}
		});

		// start AISpec
		ex.startNextInventory(pos, aiSpec);
		// send irrelevant event
		TagData tagData = new TagData();
		tagData.setEpc(new byte[] { 1, 2 });
		ex.executionResponseReceived(Arrays.asList(tagData, tagData));
		Assert.assertEquals(data.endTime, 0);
		// send stop event
		ex.executionResponseReceived(Arrays.asList(tagData));
		Assert.assertTrue(data.endTime > 0);

		// start new AISpec and do not send any event -> time out
		long startTime = System.currentTimeMillis();
		ex.startNextInventory(pos, aiSpec);
		Thread.sleep(2000);
		long diff = data.endTime - startTime;
		Assert.assertTrue(diff > 900 && diff < 1100);
	}

	// @Mocked RFCClientMultiplexed rfcClient;
	public void stopTriggerTagObservation3(//
			@Mocked final RFCClientMultiplexed rfcClient//
	) throws Exception {
		SocketChannel rfcChannel = SocketChannel.open();
		AISpecExecutor ex = new AISpecExecutor(rfcClient, rfcChannel, new EventQueue());
		AISpecStopTrigger stopTrigger = new AISpecStopTrigger(new TLVParameterHeader((byte) 0),
				AISpecStopTriggerType.TAG_OBSERVATION, 0 /* durationTrigger */);
		stopTrigger.setTagOT(new TagObservationTrigger(new TLVParameterHeader((byte) 0),
				TagObservationTriggerType.UPON_SEEING_N_UNIQUE_TAG_OBSERVATIONS_OR_TIMEOUT,
				2 /* numberOfTags */, 0 /* numberOfAttempts */, 0 /* t */, 1000 /* timeOut */));
		AISpec aiSpec = new AISpec(new TLVParameterHeader((byte) 0),
				Arrays.asList(3) /* antennaIds */, stopTrigger,
				Arrays.asList(new InventoryParameterSpec(new TLVParameterHeader((byte) 0),
						10 /* specID */, ProtocolId.EPC_GLOBAL_C1G2)));
		final ROSpecExecutionPosition pos = new ROSpecExecutionPosition(2 /* roSpecId */);
		class Data {
			long endTime = 0;
		}
		final Data data = new Data();
		ex.addListener(new AISpecExecutorListener() {

			@Override
			public void isStopped(AISpec aiSpec) {
				data.endTime = System.currentTimeMillis();
			}
		});

		// start AISpec
		ex.startNextInventory(pos, aiSpec);
		// send irrelevant event
		TagData tagData = new TagData();
		tagData.setEpc(new byte[] { 1, 2 });
		ex.executionResponseReceived(Arrays.asList(tagData));
		ex.executionResponseReceived(Arrays.asList(tagData, tagData));
		Assert.assertEquals(data.endTime, 0);
		// send stop event
		tagData.setEpc(new byte[] { 1, 3 });
		ex.executionResponseReceived(Arrays.asList(tagData));
		Assert.assertTrue(data.endTime > 0);

		// start new AISpec and do not send any event -> time out
		long startTime = System.currentTimeMillis();
		ex.startNextInventory(pos, aiSpec);
		Thread.sleep(2000);
		long diff = data.endTime - startTime;
		Assert.assertTrue(diff > 900 && diff < 1100);
	}

	// @Mocked RFCClientMultiplexed rfcClient;
	public void stopTriggerTagObservation4(//
			@Mocked final RFCClientMultiplexed rfcClient//
	) throws Exception {
		SocketChannel rfcChannel = SocketChannel.open();
		AISpecExecutor ex = new AISpecExecutor(rfcClient, rfcChannel, new EventQueue());
		AISpecStopTrigger stopTrigger = new AISpecStopTrigger(new TLVParameterHeader((byte) 0),
				AISpecStopTriggerType.TAG_OBSERVATION, 0 /* durationTrigger */);
		stopTrigger.setTagOT(new TagObservationTrigger(new TLVParameterHeader((byte) 0),
				TagObservationTriggerType.UPON_SEEING_NO_MORE_NEW_UNIQUE_TAG_OBSERVATIONS_FOR_T_MS_OR_TIMEOUT,
				0 /* numberOfTags */, 0 /* numberOfAttempts */, 1000/* t */, 2000 /* timeOut */));
		AISpec aiSpec = new AISpec(new TLVParameterHeader((byte) 0),
				Arrays.asList(3) /* antennaIds */, stopTrigger,
				Arrays.asList(new InventoryParameterSpec(new TLVParameterHeader((byte) 0),
						10 /* specID */, ProtocolId.EPC_GLOBAL_C1G2)));
		final ROSpecExecutionPosition pos = new ROSpecExecutionPosition(2 /* roSpecId */);
		class Data {
			long endTime = 0;
		}
		final Data data = new Data();
		ex.addListener(new AISpecExecutorListener() {

			@Override
			public void isStopped(AISpec aiSpec) {
				data.endTime = System.currentTimeMillis();
			}
		});

		// start AISpec
		ex.startNextInventory(pos, aiSpec);
		// send an event with a new unique tag before idle time out
		Thread.sleep(500);
		TagData tagData = new TagData();
		tagData.setEpc(new byte[] { 1, 2 });
		long startTime = System.currentTimeMillis();
		ex.executionResponseReceived(Arrays.asList(tagData));
		// send an event with the same tag before updated idle time out
		Thread.sleep(500);
		ex.executionResponseReceived(Arrays.asList(tagData));
		Assert.assertEquals(data.endTime, 0);
		// wait for idle time out
		Thread.sleep(1500);
		long diff = data.endTime - startTime;
		Assert.assertTrue(diff > 900 && diff < 1100);
		data.endTime = 0;

		// start new AISpec and send periodically events with different tags
		// until time out
		ex.startNextInventory(pos, aiSpec);
		startTime = System.currentTimeMillis();
		Thread.sleep(500);
		ex.executionResponseReceived(Arrays.asList(tagData));
		Thread.sleep(500);
		tagData.setEpc(new byte[] { 1, 3 });
		ex.executionResponseReceived(Arrays.asList(tagData));
		Thread.sleep(500);
		tagData.setEpc(new byte[] { 1, 4 });
		ex.executionResponseReceived(Arrays.asList(tagData));
		Thread.sleep(1500);
		diff = data.endTime - startTime;
		Assert.assertTrue(diff > 1900 && diff < 2100);
	}
}
