package havis.llrpservice.server.rfc;

import havis.device.rf.tag.TagData;
import havis.device.rf.tag.operation.ReadOperation;
import havis.device.rf.tag.operation.RequestOperation;
import havis.device.rf.tag.operation.TagOperation;
import havis.device.rf.tag.result.OperationResult;
import havis.device.rf.tag.result.ReadResult;
import havis.device.rf.tag.result.ReadResult.Result;
import havis.llrpservice.data.message.parameter.AccessCommand;
import havis.llrpservice.data.message.parameter.AccessSpec;
import havis.llrpservice.data.message.parameter.AccessSpecStopTrigger;
import havis.llrpservice.data.message.parameter.AccessSpecStopTriggerType;
import havis.llrpservice.data.message.parameter.C1G2Read;
import havis.llrpservice.data.message.parameter.C1G2TagSpec;
import havis.llrpservice.data.message.parameter.C1G2TargetTag;
import havis.llrpservice.data.message.parameter.Parameter;
import havis.llrpservice.data.message.parameter.ProtocolId;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.sbc.rfc.RFCClientMultiplexed;
import havis.llrpservice.sbc.rfc.message.GetOperations;
import havis.llrpservice.sbc.rfc.message.GetOperationsResponse;
import havis.llrpservice.sbc.rfc.message.MessageHeader;
import havis.llrpservice.server.event.EventQueue;
import havis.llrpservice.server.event.RFCMessageEvent;
import havis.llrpservice.server.rfc.AccessSpecExecutor.InventoryAccessOps;
import havis.llrpservice.server.rfc.ROSpecExecutor.ROSpecExecutionPosition;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import mockit.Mocked;
import mockit.Verifications;

import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class AccessSpecExecutorTest {

	public void addRemove(@Mocked final RFCClientMultiplexed rfcClient,
			@Mocked final AccessSpecsListener listener) throws Exception {

		// add an AccessSpec
		final SocketChannel rfcChannel = SocketChannel.open();
		EventQueue eventQueue = new EventQueue();
		AccessSpecExecutor executor = new AccessSpecExecutor(rfcClient,
				rfcChannel, eventQueue);
		executor.addListener(listener);
		final long accessSpecId = 567;
		AccessSpec accessSpec = new AccessSpec(
				new TLVParameterHeader((byte) 0), accessSpecId,
				1 /* antennaID */, ProtocolId.EPC_GLOBAL_C1G2,
				true /* currentState */, 2L /* roSpecID */,
				null /* accessSpecStopTrigger */, null /* AccessCommand */);
		executor.add(accessSpec);
		// remove the AccessSpec
		Assert.assertNotNull(executor.remove(567));
		
		new Verifications() {
			{
				// an "removed" event is sent
				listener.removed(accessSpecId);
				times = 1;
			}
		};
		
		// remove the listener and add + remove an AccessSpec
		executor.removeListener(listener);
		executor.add(accessSpec);
		Assert.assertNotNull(executor.remove(567));
		new Verifications() {
			{
				// no further "removed" event is sent
				listener.removed(accessSpecId);
				times = 1;
			}
		};

		// add an AccessSpec with an unsupported protocol
		accessSpec.setProtocolId(ProtocolId.UNSPECIFIED_AIR_PROTOCOL);
		try {
			executor.add(accessSpec);
			Assert.fail();
		} catch (UnsupportedAirProtocolException e) {
			// an exception must be thrown
			Assert.assertTrue(e.getMessage().contains(
					"Unknown air protocol "
							+ ProtocolId.UNSPECIFIED_AIR_PROTOCOL));
		}
	}

	public void startNextAccessOps(@Mocked final RFCClientMultiplexed rfcClient)
			throws Exception {

		// start access ops without an AccessSpec
		final SocketChannel rfcChannel = SocketChannel.open();
		EventQueue eventQueue = new EventQueue();
		AccessSpecExecutor executor = new AccessSpecExecutor(rfcClient,
				rfcChannel, eventQueue);
		List<OperationResult> accessResults = new ArrayList<OperationResult>();

		TagData tag = new TagData();
		tag.setTagDataId(123);
		tag.setEpc(new byte[] { 0, 1, 0, 2 });
		tag.setPc((short) 3);
		tag.setXpc(4);
		tag.setAntennaID((short) 5);
		tag.setRssi((byte) 6);
		tag.setChannel((short) 7);
		tag.setResultList(accessResults);
		GetOperations getOps = new GetOperations(
				new MessageHeader(123 /* id */), tag);
		ROSpecExecutionPosition pos = new ROSpecExecutionPosition(1 /* roSpecId */);
		executor.getNextInventoryAccessOps(pos);
		Assert.assertNull(pos.getDefaultAccessSpecId());
		Assert.assertTrue(pos.getTagDataAccessSpecIds().isEmpty());
		pos.setAntennaId(2);
		pos.setProtocolId(ProtocolId.EPC_GLOBAL_C1G2);
		executor.startNextAccessOps(getOps,
				new ArrayList<TagOperation>() /* accessOps */, pos);
		Assert.assertNull(pos.getDefaultAccessSpecId());
		Assert.assertTrue(pos.getTagDataAccessSpecIds().isEmpty());

		RFCMessageEvent event = (RFCMessageEvent) eventQueue.take(3000);
		final GetOperationsResponse response = (GetOperationsResponse) event
				.getMessage();
		Assert.assertEquals(response.getMessageHeader().getId(), 123);
		Assert.assertEquals(response.getOperations().size(), 0);

		new Verifications() {
			{
				// nothing happens
				rfcClient.requestSendingData(rfcChannel,
						withInstanceOf(GetOperationsResponse.class));
				times = 1;
			}
		};

		// start an access operation of an AccessSpec using a valid tag filter
		BitSet tagMask1 = new BitSet();
		tagMask1.set(0);
		BitSet tagData1 = new BitSet();
		tagData1.set(0);
		C1G2TargetTag pattern1 = new C1G2TargetTag(new TLVParameterHeader(
				(byte) 0), (byte) 3 /* memoryBank */, true /* isMatch */,
				0 /* pointer */, tagMask1, tagData1);
		C1G2TagSpec tagSpec = new C1G2TagSpec(new TLVParameterHeader((byte) 0),
				pattern1);
		List<Parameter> ops = new ArrayList<>();
		ops.add(new C1G2Read(new TLVParameterHeader((byte) 0), 0 /* opSpecId */,
				1L /* accessPw */, (byte) 3 /* memoryBank */,
				3 /* wordPointer */, 4 /* wordCount */));
		AccessSpecStopTrigger accessSpecStopTrigger = new AccessSpecStopTrigger(
				new TLVParameterHeader((byte) 0),
				AccessSpecStopTriggerType.NULL, 0/* operationCountValue */);
		long accessSpecId1 = 567;
		AccessSpec accessSpec1 = new AccessSpec(
				new TLVParameterHeader((byte) 0), accessSpecId1,
				1 /* antennaID */, ProtocolId.EPC_GLOBAL_C1G2,
				true /* currentState */, 2L /* roSpecID */,
				accessSpecStopTrigger, new AccessCommand(
						new TLVParameterHeader((byte) 0), tagSpec, ops));
		executor.add(accessSpec1);		

		ReadResult rdRes = new ReadResult();
		rdRes.setOperationId("1");
		rdRes.setReadData(new byte[] { (byte) 0x80, 0x10 });
		rdRes.setResult(Result.SUCCESS);
		accessResults.add(rdRes);
		List<TagOperation> accessOps = new ArrayList<>();
		ReadOperation rdOp = new ReadOperation();
		rdOp.setOperationId("1");
		rdOp.setBank((short) 3);
		rdOp.setOffset((short) 0);
		rdOp.setLength((short) 12);
		rdOp.setPassword(0);
		accessOps.add(rdOp);
		pos = new ROSpecExecutionPosition(2 /* roSpecId */);
		executor.getNextInventoryAccessOps(pos);
		Assert.assertNull(pos.getDefaultAccessSpecId());
		Assert.assertTrue(pos.getTagDataAccessSpecIds().isEmpty());
		pos.setAntennaId(1);
		pos.setProtocolId(ProtocolId.EPC_GLOBAL_C1G2);
		executor.startNextAccessOps(getOps, accessOps, pos);
		Assert.assertNull(pos.getDefaultAccessSpecId());
		Assert.assertEquals(pos.getTagDataAccessSpecIds().get(123L),
				Long.valueOf(accessSpecId1));

		// a GetOperationsResponse is enqueued
		event = (RFCMessageEvent) eventQueue.take(3000);
		final GetOperationsResponse response2 = (GetOperationsResponse) event
				.getMessage();
		Assert.assertEquals(response2.getMessageHeader().getId(), 123);
		// the GetOperationsResponse contains the converted read operation of
		// the AccessSpec
		Assert.assertEquals(response2.getOperations().size(), 1);
		ReadOperation op = (ReadOperation) response2.getOperations().get(0);
		Assert.assertEquals(op.getBank(), 3);
		Assert.assertEquals(op.getOffset(), 3);
		Assert.assertEquals(op.getLength(), 4);
		Assert.assertEquals(op.getPassword(), 1);
		new Verifications() {
			{
				// the GetOperationsResponse is sent
				rfcClient.requestSendingData(rfcChannel, response2);
				times = 1;
			}
		};
		// the AccessSpec has not been stopped
		Assert.assertNotNull(executor.remove(accessSpecId1));
		executor.remove(accessSpecId1 + 1);

		// add AccessSpec again but with a stop trigger and start its operations
		accessSpecStopTrigger
				.setAccessSpecStopTrigger(AccessSpecStopTriggerType.OPERATION_COUNT);
		accessSpecStopTrigger.setOperationCountValue(1);
		executor.add(accessSpec1);
		executor.getNextInventoryAccessOps(pos);
		Assert.assertNull(pos.getDefaultAccessSpecId());
		Assert.assertTrue(pos.getTagDataAccessSpecIds().isEmpty());
		executor.startNextAccessOps(getOps, accessOps, pos);
		Assert.assertNull(pos.getDefaultAccessSpecId());
		Assert.assertEquals(pos.getTagDataAccessSpecIds().get(123L),
				Long.valueOf(accessSpecId1));
		// the AccessSpec must have been removed due to the stop trigger
		Assert.assertNull(executor.remove(accessSpecId1));
	}

	public void getInventoryAccessOps(
			@Mocked final RFCClientMultiplexed rfcClient) throws Exception {
		// get access ops without adding AccessSpecs
		// => no tag operations are returned
		final SocketChannel rfcChannel = SocketChannel.open();
		EventQueue eventQueue = new EventQueue();
		AccessSpecExecutor executor = new AccessSpecExecutor(rfcClient,
				rfcChannel, eventQueue);
		long roSpecId1 = 2;
		ROSpecExecutionPosition pos1 = new ROSpecExecutionPosition(roSpecId1);
		InventoryAccessOps iao = executor.getNextInventoryAccessOps(pos1);
		Assert.assertNull(pos1.getDefaultAccessSpecId());
		Assert.assertTrue(pos1.getTagDataAccessSpecIds().isEmpty());
		Assert.assertTrue(iao.getTagFilter().isEmpty());
		Assert.assertTrue(iao.getTagOperations().isEmpty());

		// add two AccessSpecs without filter
		BitSet tagMask1 = new BitSet();
		BitSet tagData1 = new BitSet();
		C1G2TargetTag pattern1 = new C1G2TargetTag(new TLVParameterHeader(
				(byte) 0), (byte) 2 /* memoryBank */, true /* isMatch */,
				0 /* pointer */, tagMask1, tagData1);
		C1G2TagSpec tagSpec1 = new C1G2TagSpec(
				new TLVParameterHeader((byte) 0), pattern1);
		AccessSpecStopTrigger accessSpecStopTrigger1 = new AccessSpecStopTrigger(
				new TLVParameterHeader((byte) 0),
				AccessSpecStopTriggerType.NULL, 0/* operationCountValue */);
		List<Parameter> ops1 = new ArrayList<>();
		ops1.add(new C1G2Read(new TLVParameterHeader((byte) 0), 0 /* opSpecId */,
				11L /* accessPw */, (byte) 2 /* memoryBank */,
				3 /* wordPointer */, 4 /* wordCount */));
		AccessSpec accessSpec1 = new AccessSpec(
				new TLVParameterHeader((byte) 0), 567L /* accessSpecID */,
				1 /* antennaID */, ProtocolId.EPC_GLOBAL_C1G2,
				true /* currentState */, roSpecId1, accessSpecStopTrigger1,
				new AccessCommand(new TLVParameterHeader((byte) 0),
						tagSpec1/* C1G2TagSpec */, ops1));
		executor.add(accessSpec1);
		BitSet tagMask21 = new BitSet();
		BitSet tagData21 = new BitSet();
		C1G2TargetTag pattern21 = new C1G2TargetTag(new TLVParameterHeader(
				(byte) 0), (byte) 3 /* memoryBank */, true /* isMatch */,
				0 /* pointer */, tagMask21, tagData21);
		C1G2TagSpec tagSpec2 = new C1G2TagSpec(
				new TLVParameterHeader((byte) 0), pattern21);
		BitSet tagMask22 = new BitSet();
		BitSet tagData22 = new BitSet();
		C1G2TargetTag pattern22 = new C1G2TargetTag(new TLVParameterHeader(
				(byte) 0), (byte) 3 /* memoryBank */, true /* isMatch */,
				0 /* pointer */, tagMask22, tagData22);
		tagSpec2.setTagPattern2(pattern22);
		AccessSpecStopTrigger accessSpecStopTrigger2 = new AccessSpecStopTrigger(
				new TLVParameterHeader((byte) 0),
				AccessSpecStopTriggerType.NULL, 0/* operationCountValue */);
		List<Parameter> ops2 = new ArrayList<>();
		ops2.add(new C1G2Read(new TLVParameterHeader((byte) 0), 2 /* opSpecId */,
				22L /* accessPw */, (byte) 1 /* memoryBank */,
				3 /* wordPointer */, 4 /* wordCount */));
		AccessSpec accessSpec2 = new AccessSpec(
				new TLVParameterHeader((byte) 0), 568L /* accessSpecID */,
				1 /* antennaID */, ProtocolId.EPC_GLOBAL_C1G2,
				true /* currentState */, roSpecId1, accessSpecStopTrigger2,
				new AccessCommand(new TLVParameterHeader((byte) 0),
						tagSpec2/* C1G2TagSpec */, ops2));
		executor.add(accessSpec2);
		// get access ops: only a RequestOperation is returned (no filter
		// exists)
		iao = executor.getNextInventoryAccessOps(pos1);
		Assert.assertNull(pos1.getDefaultAccessSpecId());
		Assert.assertTrue(pos1.getTagDataAccessSpecIds().isEmpty());
		Assert.assertTrue(iao.getTagFilter().isEmpty());
		Assert.assertEquals(iao.getTagOperations().size(), 1);
		Assert.assertTrue(iao.getTagOperations().get(0) instanceof RequestOperation);

		// add a filter with 2 patterns to the second AccessSpec
		// set bits in the first word (pattern1) and in the
		// second word using a pointer (pattern2)
		// 0001 0000 0000 0|000 0000 0001 0000 0000
		tagMask21.set(3);
		tagData21.set(3);
		pattern21.setMaskBitCount(4);
		pattern22.setPointer(13);
		tagMask22.set(10);
		tagData22.set(10);
		pattern22.setMaskBitCount(11);
		// get access ops: read operation for the filter and a RequestOperation
		// are returned
		iao = executor.getNextInventoryAccessOps(pos1);
		Assert.assertNull(pos1.getDefaultAccessSpecId());
		Assert.assertTrue(pos1.getTagDataAccessSpecIds().isEmpty());
		Assert.assertTrue(iao.getTagFilter().isEmpty());
		Assert.assertEquals(iao.getTagOperations().size(), 2);
		ReadOperation readOp = (ReadOperation) iao.getTagOperations().get(0);
		Assert.assertEquals(readOp.getBank(), 3);
		Assert.assertEquals(readOp.getOffset(), 0);
		Assert.assertEquals(readOp.getLength(), 2);
		Assert.assertTrue(iao.getTagOperations().get(1) instanceof RequestOperation);		

		// get access ops for a second ROSpecId: no access ops are returned
		long roSpecId2 = 3;
		ROSpecExecutionPosition pos2 = new ROSpecExecutionPosition(roSpecId2);
		iao = executor.getNextInventoryAccessOps(pos2);
		Assert.assertNull(pos2.getDefaultAccessSpecId());
		Assert.assertTrue(pos2.getTagDataAccessSpecIds().isEmpty());
		Assert.assertTrue(iao.getTagFilter().isEmpty());
		Assert.assertTrue(iao.getTagOperations().isEmpty());

		// add an AccessSpec for the second ROSpecId
		BitSet tagMask3 = new BitSet();
		BitSet tagData3 = new BitSet();
		C1G2TargetTag pattern3 = new C1G2TargetTag(new TLVParameterHeader(
				(byte) 0), (byte) 2 /* memoryBank */, true /* isMatch */,
				0 /* pointer */, tagMask3, tagData3);
		C1G2TagSpec tagSpec3 = new C1G2TagSpec(
				new TLVParameterHeader((byte) 0), pattern3);
		AccessSpecStopTrigger accessSpecStopTrigger3 = new AccessSpecStopTrigger(
				new TLVParameterHeader((byte) 0),
				AccessSpecStopTriggerType.NULL, 0/* operationCountValue */);
		List<Parameter> ops3 = new ArrayList<>();
		ops3.add(new C1G2Read(new TLVParameterHeader((byte) 0), 0 /* opSpecId */,
				33L /* accessPw */, (byte) 2 /* memoryBank */,
				3 /* wordPointer */, 4 /* wordCount */));
		AccessSpec accessSpec3 = new AccessSpec(
				new TLVParameterHeader((byte) 0), 569L /* accessSpecID */,
				1 /* antennaID */, ProtocolId.EPC_GLOBAL_C1G2,
				true /* currentState */, roSpecId2, accessSpecStopTrigger3,
				new AccessCommand(new TLVParameterHeader((byte) 0),
						tagSpec3/* C1G2TagSpec */, ops3));
		executor.add(accessSpec3);
		// add a filter to the third AccessSpec
		// set bits in the first word (pattern3)
		// 0000 1000 0000 0000
		tagMask3.set(4);
		tagData3.set(4);
		pattern3.setMaskBitCount(5);		
		// get access ops: read operation for the filter and a RequestOperation
		// are returned
		iao = executor.getNextInventoryAccessOps(pos2);
		Assert.assertNull(pos2.getDefaultAccessSpecId());
		Assert.assertTrue(pos2.getTagDataAccessSpecIds().isEmpty());
		Assert.assertTrue(iao.getTagFilter().isEmpty());
		Assert.assertEquals(iao.getTagOperations().size(), 2);
		ReadOperation readOp3 = (ReadOperation) iao.getTagOperations().get(0);
		Assert.assertEquals(readOp3.getBank(), 2);
		Assert.assertEquals(readOp3.getOffset(), 0);
		Assert.assertEquals(readOp3.getLength(), 1);
		Assert.assertTrue(iao.getTagOperations().get(1) instanceof RequestOperation);
	}
}
