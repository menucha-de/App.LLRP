package havis.llrpservice.server.rfc;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

import havis.device.rf.tag.TagData;
import havis.llrpservice.data.message.parameter.AISpec;
import havis.llrpservice.data.message.parameter.AISpecStopTrigger;
import havis.llrpservice.data.message.parameter.AISpecStopTriggerType;
import havis.llrpservice.data.message.parameter.AccessSpec;
import havis.llrpservice.data.message.parameter.GPIEvent;
import havis.llrpservice.data.message.parameter.GPITriggerValue;
import havis.llrpservice.data.message.parameter.InventoryParameterSpec;
import havis.llrpservice.data.message.parameter.Parameter;
import havis.llrpservice.data.message.parameter.ParameterTypes.ParameterType;
import havis.llrpservice.data.message.parameter.ProtocolId;
import havis.llrpservice.data.message.parameter.RFSurveySpec;
import havis.llrpservice.data.message.parameter.ROSpec;
import havis.llrpservice.data.message.parameter.ROSpecCurrentState;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.data.message.parameter.TagObservationTrigger;
import havis.llrpservice.data.message.parameter.TagObservationTriggerType;
import havis.llrpservice.sbc.rfc.RFCClientMultiplexed;
import havis.llrpservice.sbc.rfc.message.Execute;
import havis.llrpservice.server.event.EventQueue;
import havis.llrpservice.server.event.RFCMessageEvent;
import havis.llrpservice.server.rfc.ROSpecExecutor.ROSpecExecutionPosition;
import havis.llrpservice.server.rfc.ROSpecExecutor.ROSpecExecutorListener;
import mockit.Mocked;

@Test
public class ROSpecExecutorTest {

	// @Mocked
	// RFCClientMultiplexed rfcClient;

	public void addRemove(//
			@Mocked final RFCClientMultiplexed rfcClient//
	) throws Exception {

		// add an AccessSpec
		final SocketChannel rfcChannel = SocketChannel.open();
		ROSpec roSpec = new ROSpec();
		roSpec.setRoSpecID(1);
		ROSpecExecutor executor = new ROSpecExecutor(roSpec, rfcClient, rfcChannel,
				new EventQueue());
		AccessSpec accessSpec = new AccessSpec(new TLVParameterHeader((byte) 0),
				567L /* accessSpecID */, 1 /* antennaID */, ProtocolId.EPC_GLOBAL_C1G2,
				true /* currentState */, 2L /* roSpecID */, null /* accessSpecStopTrigger */,
				null /* AccessCommand */);
		executor.add(accessSpec);
		// remove the AccessSpec
		Assert.assertNotNull(executor.remove(567));

		rfcChannel.close();
	}

	 @Mocked
	 RFCClientMultiplexed rfcClient;

	public void startNextX(//
//			@Mocked final RFCClientMultiplexed rfcClient//
	) throws Exception {
		EventQueue eventQueue = new EventQueue();
		SocketChannel rfcChannel = SocketChannel.open();
		List<Parameter> specList = new ArrayList<>();
		AISpecStopTrigger stopTrigger = new AISpecStopTrigger(new TLVParameterHeader((byte) 0),
				AISpecStopTriggerType.TAG_OBSERVATION, 0 /* durationTrigger */);
		stopTrigger.setTagOT(new TagObservationTrigger(new TLVParameterHeader((byte) 0),
				TagObservationTriggerType.UPON_SEEING_N_TAG_OBSERVATIONS_OR_TIMEOUT,
				1 /* numberOfTags */, 0 /* numberOfAttempts */, 0 /* t */, 1000 /* timeOut */));
		final AISpec aiSpec1 = new AISpec(new TLVParameterHeader((byte) 0),
				Arrays.asList(0) /* antennaIdList */, stopTrigger,
				Arrays.asList(new InventoryParameterSpec(new TLVParameterHeader((byte) 0),
						1 /* specID */, ProtocolId.EPC_GLOBAL_C1G2)));
		specList.add(aiSpec1);
		ROSpec roSpec = new ROSpec(new TLVParameterHeader((byte) 0), 1 /* roSpecID */, (short) 1,
				ROSpecCurrentState.ACTIVE, null /* ROBoundarySpec */, specList);
		ROSpecExecutor ex = new ROSpecExecutor(roSpec, rfcClient, rfcChannel, eventQueue);
		// start inventory
		ex.startNextAction();
		RFCMessageEvent ev = (RFCMessageEvent) eventQueue.take(1000);
		Execute execute = (Execute) ev.getMessage();
		Assert.assertEquals(execute.getAntennas().get(0).intValue(), 0);

		// add an additional AISpec and start the next inventory
		final AISpec aiSpec2 = new AISpec(new TLVParameterHeader((byte) 0),
				Arrays.asList(1) /* antennaIdList */,
				new AISpecStopTrigger(new TLVParameterHeader((byte) 0), AISpecStopTriggerType.NULL,
						0 /* durationTrigger */),
				Arrays.asList(new InventoryParameterSpec(new TLVParameterHeader((byte) 0),
						2 /* specID */, ProtocolId.EPC_GLOBAL_C1G2)));
		specList.add(aiSpec2);
		ex.startNextAction();
		// the first AISpec has been executed again
		ev = (RFCMessageEvent) eventQueue.take(1000);
		execute = (Execute) ev.getMessage();
		Assert.assertEquals(execute.getAntennas().get(0).intValue(), 0);

		// report a tag and start next inventory
		TagData tagData = new TagData();
		tagData.setEpc(new byte[] { 1, 2 });
		ex.executionResponseReceived(Arrays.asList(tagData));
		ex.startNextAction();
		// the second AISpec has been executed
		ev = (RFCMessageEvent) eventQueue.take(1000);
		execute = (Execute) ev.getMessage();
		Assert.assertEquals(execute.getAntennas().get(0).intValue(), 1);

		rfcChannel.close();
	}

	// @Mocked RFCClientMultiplexed rfcClient;
	public void startNextXError(//
			@Mocked final RFCClientMultiplexed rfcClient//
	) throws Exception {
		// start inventory
		SocketChannel rfcChannel = SocketChannel.open();
		List<Parameter> specList = new ArrayList<>();
		specList.add(new RFSurveySpec(new TLVParameterHeader((byte) 0), 0 /* antennaID */,
				0 /* startFreq */, 0/* endFreq */, null /* RFSurveySpecStopTrigger */));
		ROSpec roSpec = new ROSpec(new TLVParameterHeader((byte) 0), 1 /* roSpecID */, (short) 1,
				ROSpecCurrentState.ACTIVE, null /* ROBoundarySpec */, specList);
		ROSpecExecutor ex = new ROSpecExecutor(roSpec, rfcClient, rfcChannel, new EventQueue());
		try {
			ex.startNextAction();
			Assert.fail();
		} catch (UnsupportedSpecTypeException e) {
			Assert.assertTrue(e.getMessage().contains(ParameterType.RF_SURVEY_SPEC.toString()));
		}

		rfcChannel.close();
	}

	// @Mocked
	// RFCClientMultiplexed rfcClient;

	public void stop(//
			@Mocked final RFCClientMultiplexed rfcClient//
	) throws Exception {
		SocketChannel rfcChannel = SocketChannel.open();
		List<Parameter> specList = new ArrayList<>();
		final AISpec aiSpec = new AISpec(new TLVParameterHeader((byte) 0),
				Arrays.asList(0) /* antennaIdList */,
				new AISpecStopTrigger(new TLVParameterHeader((byte) 0), AISpecStopTriggerType.NULL,
						0 /* durationTrigger */),
				Arrays.asList(new InventoryParameterSpec(new TLVParameterHeader((byte) 0),
						1 /* specID */, ProtocolId.EPC_GLOBAL_C1G2)));
		specList.add(aiSpec);
		ROSpec roSpec = new ROSpec(new TLVParameterHeader((byte) 0), 1 /* roSpecID */, (short) 1,
				ROSpecCurrentState.ACTIVE, null /* ROBoundarySpec */, specList);
		ROSpecExecutor ex = new ROSpecExecutor(roSpec, rfcClient, rfcChannel, new EventQueue());
		// start inventory
		ex.startNextAction();
		class Data {
			long endTime = 0;
		}
		final Data data = new Data();
		ROSpecExecutorListener listener = new ROSpecExecutorListener() {

			@Override
			public void isStopped(ROSpec roSpec, ROSpecExecutionPosition pos) {
				data.endTime = System.currentTimeMillis();
			}
		};
		ex.addListener(listener);
		long startTime = System.currentTimeMillis();
		// stop ROSpec manually
		ex.stop();
		long diff = data.endTime - startTime;
		Assert.assertTrue(diff >= 0 && diff < 100);
		data.endTime = 0;

		ex.removeListener(listener);
		// start inventory and stop the ROSpec manually
		ex.startNextAction();
		ex.stop();
		// no event is received
		Thread.sleep(500);
		Assert.assertEquals(data.endTime, 0);
	}

	// @Mocked
	// RFCClientMultiplexed rfcClient;

	public void aiSpecStopTrigger(//
			@Mocked final RFCClientMultiplexed rfcClient//
	) throws Exception {
		SocketChannel rfcChannel = SocketChannel.open();
		List<Parameter> specList = new ArrayList<>();
		AISpecStopTrigger stopTrigger = new AISpecStopTrigger(new TLVParameterHeader((byte) 0),
				AISpecStopTriggerType.GPI_WITH_TIMEOUT, 0 /* durationTrigger */);
		stopTrigger.setGpiTV(new GPITriggerValue(new TLVParameterHeader((byte) 0),
				0 /* gpiPortNum */, true /* gpiEvent */ , 1000 /* timeOut */));
		final AISpec aiSpec = new AISpec(new TLVParameterHeader((byte) 0),
				Arrays.asList(0) /* antennaIdList */, stopTrigger,
				Arrays.asList(new InventoryParameterSpec(new TLVParameterHeader((byte) 0),
						1 /* specID */, ProtocolId.EPC_GLOBAL_C1G2)));
		specList.add(aiSpec);
		ROSpec roSpec = new ROSpec(new TLVParameterHeader((byte) 0), 1 /* roSpecID */, (short) 1,
				ROSpecCurrentState.ACTIVE, null /* ROBoundarySpec */, specList);
		ROSpecExecutor ex = new ROSpecExecutor(roSpec, rfcClient, rfcChannel, new EventQueue());
		// start inventory
		long startTime = System.currentTimeMillis();
		ex.startNextAction();
		class Data {
			long endTime = 0;
		}
		final Data data = new Data();
		ex.addListener(new ROSpecExecutorListener() {

			@Override
			public void isStopped(ROSpec roSpec, ROSpecExecutionPosition pos) {
				data.endTime = System.currentTimeMillis();
			}
		});
		// stop ROSpec manually before AISpec is stopped by trigger
		Thread.sleep(100);
		ex.stop();
		long diff = data.endTime - startTime;
		Assert.assertTrue(diff > 0 && diff < 200);
		data.endTime = 0;

		// start inventory again
		startTime = System.currentTimeMillis();
		ex.startNextAction();
		// stop AISpec by GPI event
		Thread.sleep(100);
		ex.gpiEventReceived(new GPIEvent(new TLVParameterHeader((byte) 0), 0 /* gpiPortNumber */,
				true /* state */));
		diff = data.endTime - startTime;
		Assert.assertTrue(diff >= 0 && diff < 200);
		data.endTime = 0;
	}
}
