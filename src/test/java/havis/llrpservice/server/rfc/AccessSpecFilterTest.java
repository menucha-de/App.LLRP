package havis.llrpservice.server.rfc;

import havis.device.rf.tag.TagData;
import havis.device.rf.tag.operation.ReadOperation;
import havis.device.rf.tag.operation.TagOperation;
import havis.device.rf.tag.result.OperationResult;
import havis.device.rf.tag.result.ReadResult;
import havis.device.rf.tag.result.ReadResult.Result;
import havis.device.rf.tag.result.WriteResult;
import havis.llrpservice.data.message.parameter.AccessCommand;
import havis.llrpservice.data.message.parameter.AccessSpec;
import havis.llrpservice.data.message.parameter.C1G2TagSpec;
import havis.llrpservice.data.message.parameter.C1G2TargetTag;
import havis.llrpservice.data.message.parameter.ProtocolId;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class AccessSpecFilterTest {

	public void getAccessSpecMB134() throws Exception {
		// create AccessSpec with an empty TagSpec
		BitSet tagMask1 = new BitSet();
		BitSet tagData1 = new BitSet();
		C1G2TargetTag pattern1 = new C1G2TargetTag(new TLVParameterHeader(
				(byte) 0), (byte) 3 /* memoryBank */, true /* isMatch */,
				0 /* pointer */, tagMask1, tagData1);
		C1G2TagSpec tagSpec = new C1G2TagSpec(new TLVParameterHeader((byte) 0),
				pattern1);
		List<AccessSpec> accessSpecs = new ArrayList<>();
		accessSpecs.add(new AccessSpec(new TLVParameterHeader((byte) 0),
				0L /* accessSpecID */, 1 /* antennaID */,
				ProtocolId.EPC_GLOBAL_C1G2, true /* currentState */,
				2L /* roSpecID */, null /* accessSpecStopTrigger */,
				new AccessCommand(new TLVParameterHeader((byte) 0),
						tagSpec/* C1G2TagSpec */, null /* opSpecList */)));

		AccessSpecFilter filter = new AccessSpecFilter(accessSpecs);
		// get AccessSpec with existing "roSpecId/antennaId/protocolId"
		// combination
		AccessSpec accessSpec = filter.getAccessSpec(2 /* roSpecId */,
				1 /* antennaId */, ProtocolId.EPC_GLOBAL_C1G2, null/* tagData */,
				null /* accessOps */);
		Assert.assertNotNull(accessSpec);
		// try to get AccessSpec with unknown roSpecId
		Assert.assertNull(filter.getAccessSpec(99 /* roSpecId */,
				1 /* antennaId */, ProtocolId.EPC_GLOBAL_C1G2, null/* tagData */,
				null /* accessOps */));
		// try to get AccessSpec with unknown antennaId
		Assert.assertNull(filter.getAccessSpec(2 /* roSpecId */,
				99 /* antennaId */, ProtocolId.EPC_GLOBAL_C1G2,
				null/* tagData */, null /* accessOps */));
		// try get AccessSpec with unknown protocolId
		Assert.assertNull(filter.getAccessSpec(2 /* roSpecId */,
				1 /* antennaId */, ProtocolId.UNSPECIFIED_AIR_PROTOCOL,
				null/* tagData */, null /* accessOps */));

		// get AccessSpec with a matching tag
		// mask: 1
		// data: 1
		// tag_: 1000 0000 0001
		tagMask1.set(0);
		tagData1.set(0);
		List<TagOperation> accessOps = new ArrayList<TagOperation>();
		ReadOperation rdOp = new ReadOperation(); 
		
		rdOp.setOperationId("1");
		rdOp.setBank((short) 3);
		rdOp.setOffset((short) 0);
		rdOp.setLength((short) 12);
		rdOp.setPassword(0);
		accessOps.add(rdOp);
		List<OperationResult> accessResults = new ArrayList<OperationResult>();
		
		
		ReadResult rdRes = new ReadResult();
		rdRes.setOperationId("1");
		rdRes.setReadData(new byte[] { (byte)0x80, 0x10 });
		rdRes.setResult(Result.SUCCESS);		
		accessResults.add(rdRes);
		
		TagData tag = new TagData();
		tag.setTagDataId(1);
		tag.setEpc(new byte[] { 0, 1, 0, 2});
		tag.setPc((short) 3);
		tag.setXpc(4);
		tag.setAntennaID((short) 5);
		tag.setRssi((byte) 6);
		tag.setChannel((short) 7);
		tag.setCrc((short)8);
		tag.setResultList(accessResults);
		
		Assert.assertNotNull(filter.getAccessSpec(2 /* roSpecId */,
				1 /* antennaId */, ProtocolId.EPC_GLOBAL_C1G2, tag, accessOps));

		// try to get AccessSpec with a non-matching tag by setting a pointer
		// mask: _100 0000 0000 0
		// data: _100 0000 0000 0
		// tag_: 1000 0000 0001
		pattern1.setPointer(1);
		Assert.assertNull(filter.getAccessSpec(2 /* roSpecId */,
				1 /* antennaId */, ProtocolId.EPC_GLOBAL_C1G2, tag, accessOps));

		// mask: _100 0000 0000 0
		// data: _000 0000 0000 0
		// tag_: 1000 0000 0001
		tagData1.set(0, false);
		Assert.assertNotNull(filter.getAccessSpec(2 /* roSpecId */,
				1 /* antennaId */, ProtocolId.EPC_GLOBAL_C1G2, tag, accessOps));

		// try to get AccessSpec with a non-matching tag by setting an
		// additional flag in the mask
		// mask: _100 0000 0001 0
		// data: _000 0000 0000 0
		// tag_: 1000 0000 0001
		tagMask1.set(10);
		Assert.assertNull(filter.getAccessSpec(2 /* roSpecId */,
				1 /* antennaId */, ProtocolId.EPC_GLOBAL_C1G2, tag, accessOps));

		// set "match" flag to false => the tag matches
		pattern1.setMatch(false);
		Assert.assertNotNull(filter.getAccessSpec(2 /* roSpecId */,
				1 /* antennaId */, ProtocolId.EPC_GLOBAL_C1G2, tag, accessOps));

		// mask: _100 0000 0001 0
		// data: _000 0000 0001 0
		// tag_: 1000 0000 0001
		tagData1.set(10);
		Assert.assertNull(filter.getAccessSpec(2 /* roSpecId */,
				1 /* antennaId */, ProtocolId.EPC_GLOBAL_C1G2, tag, accessOps));
	}

	public void getAccessSpecMB1() throws Exception {
		List<AccessSpec> accessSpecs = new ArrayList<>();

		// create AccessSpec with an empty TagSpec
		BitSet tagMask1 = new BitSet();
		BitSet tagData1 = new BitSet();
		C1G2TargetTag pattern1 = new C1G2TargetTag(new TLVParameterHeader(
				(byte) 0), (byte) 1 /* memoryBank */, true /* isMatch */,
				0 /* pointer */, tagMask1, tagData1);
		C1G2TagSpec tagSpec = new C1G2TagSpec(new TLVParameterHeader((byte) 0),
				pattern1);
		accessSpecs.add(new AccessSpec(new TLVParameterHeader((byte) 0),
				0L /* accessSpecID */, 1 /* antennaID */,
				ProtocolId.EPC_GLOBAL_C1G2, true /* currentState */,
				2L /* roSpecID */, null /* accessSpecStopTrigger */,
				new AccessCommand(new TLVParameterHeader((byte) 0),
						tagSpec/* C1G2TagSpec */, null /* opSpecList */)));

		// get AccessSpec with a matching tag
		// set mask + data for PC (0x8003)
		tagMask1.set(16, 32);
		tagData1.set(16);
		tagData1.set(30);
		tagData1.set(31);
		List<TagOperation> accessOps = new ArrayList<TagOperation>();
		
		ReadOperation rdOp = new ReadOperation();
		rdOp.setOperationId("2");
		rdOp.setBank((short) 1);
		rdOp.setOffset((short) 0);
		rdOp.setLength((short) 18);
		rdOp.setPassword(0);
		
		accessOps.add(rdOp);
		List<OperationResult> accessResults = new ArrayList<OperationResult>();
		
		ReadResult rdRes = new ReadResult();
		rdRes.setOperationId("1");
		rdRes.setReadData(new byte[] { 0/* crc */, (byte)0x80, 0x03 /* pc */, 0x30, 0x06, 0x18, 0x40 });
		rdRes.setResult(Result.SUCCESS);
		accessResults.add(rdRes);
		
		
		TagData tag = new TagData();
		tag.setAntennaID((short) 5);
		tag.setChannel((short)7);
		tag.setCrc((short)0);
		tag.setEpc(new byte[] { 0x30, 0x06, 0x18, 0x40 });
		tag.setPc((short) 0x8003);
		tag.setResultList(accessResults);
		tag.setRssi(6);
		tag.setTagDataId(1);
		tag.setXpc(4);

		AccessSpecFilter filter = new AccessSpecFilter(accessSpecs);
		Assert.assertNotNull(filter.getAccessSpec(2 /* roSpecId */,
				1 /* antennaId */, ProtocolId.EPC_GLOBAL_C1G2, tag, accessOps));

		// extend mask + data for EPC (0x3006, 0x1840)
		tagMask1.set(32, 64);
		tagData1.set(34);
		tagData1.set(35);
		tagData1.set(45);
		tagData1.set(46);
		tagData1.set(51);
		tagData1.set(52);
		tagData1.set(57);
		Assert.assertNotNull(filter.getAccessSpec(2 /* roSpecId */,
				1 /* antennaId */, ProtocolId.EPC_GLOBAL_C1G2, tag, accessOps));

		// use a pointer
		// set mask + data for PC/EPC (0x...3, 0x3...)
		tagMask1.set(0, 64, false);
		tagMask1.set(0);
		tagMask1.set(1);
		tagMask1.set(4);
		tagMask1.set(5);
		tagData1.set(0, 64, false);
		tagData1.set(0);
		tagData1.set(1);
		tagData1.set(4);
		tagData1.set(5);
		pattern1.setPointer(30);
		Assert.assertNotNull(filter.getAccessSpec(2 /* roSpecId */,
				1 /* antennaId */, ProtocolId.EPC_GLOBAL_C1G2, tag, accessOps));
	}

	public void getAccessSpecError() {
		List<AccessSpec> accessSpecs = new ArrayList<>();

		// create AccessSpec
		BitSet tagMask1 = new BitSet();
		tagMask1.set(0);
		BitSet tagData1 = new BitSet();
		tagData1.set(0);
		C1G2TargetTag pattern1 = new C1G2TargetTag(new TLVParameterHeader(
				(byte) 0), (byte) 3 /* memoryBank */, true /* isMatch */,
				0 /* pointer */, tagMask1, tagData1);
		C1G2TagSpec tagSpec = new C1G2TagSpec(new TLVParameterHeader((byte) 0),
				pattern1);
		accessSpecs.add(new AccessSpec(new TLVParameterHeader((byte) 0),
				0L /* accessSpecID */, 1 /* antennaID */,
				ProtocolId.EPC_GLOBAL_C1G2, true /* currentState */,
				2L /* roSpecID */, null /* accessSpecStopTrigger */,
				new AccessCommand(new TLVParameterHeader((byte) 0),
						tagSpec/* C1G2TagSpec */, null /* opSpecList */)));

		// try to get AccessSpec without an access operation for the memory bank
		List<TagOperation> accessOps = new ArrayList<TagOperation>();
		List<OperationResult> accessResults = new ArrayList<OperationResult>();
		
		ReadResult readResult = new ReadResult();
		readResult.setOperationId("1");
		readResult.setReadData(new byte[] { 0/* crc */, (byte)0x80, 0x03 /* pc */, 0x30, 0x06, 0x18, 0x40 });
		readResult.setResult(Result.SUCCESS);
		
		accessResults.add(readResult);
		
		
		TagData tag = new TagData();
		tag.setTagDataId(1);
		tag.setEpc(new byte[] { 0x30, 0x06, 0x18, 0x40 });
		tag.setPc((short) 0x8003);
		tag.setXpc(4);
		tag.setAntennaID((short) 5);
		tag.setRssi(6); 
		tag.setChannel((short) 7);
		tag.setResultList(accessResults);
		AccessSpecFilter filter = new AccessSpecFilter(accessSpecs);
		try {
			filter.getAccessSpec(2 /* roSpecId */, 1 /* antennaId */,
					ProtocolId.EPC_GLOBAL_C1G2, tag, accessOps);
			Assert.fail();
		} catch (MissingTagDataException e) {
			Assert.assertTrue(e.getMessage().contains(
					"missing access operation"));
		}

		// try to get AccessSpec with an access result containing an error
		ReadOperation rdOp = new ReadOperation();
		rdOp.setOperationId("3");
		rdOp.setBank((short) 3);
		rdOp.setOffset((short) 0);
		rdOp.setLength((short) 18);
		rdOp.setPassword(0);
				
		accessOps.add(rdOp);
		readResult.setResult(Result.MEMORY_LOCKED_ERROR);
		try {
			filter.getAccessSpec(2 /* roSpecId */, 1 /* antennaId */,
					ProtocolId.EPC_GLOBAL_C1G2, tag, accessOps);
			Assert.fail();
		} catch (MissingTagDataException e) {
			Assert.assertTrue(e.getMessage().contains(
					"due to access result error " + Result.MEMORY_LOCKED_ERROR));
		}

		// try to get AccessSpec without an access result
		accessResults.clear();
		try {
			filter.getAccessSpec(2 /* roSpecId */, 1 /* antennaId */,
					ProtocolId.EPC_GLOBAL_C1G2, tag, accessOps);
			Assert.fail();
		} catch (MissingTagDataException e) {
			Assert.assertTrue(e.getMessage().contains("missing access result"));
		}

		// try to get AccessSpec with an invalid access result type
		
		WriteResult wrRes = new WriteResult();
		wrRes.setOperationId("1");
		wrRes.setWordsWritten((short) 1);
		wrRes.setResult(WriteResult.Result.SUCCESS);
		accessResults.add(wrRes);
		try {
			filter.getAccessSpec(2 /* roSpecId */, 1 /* antennaId */,
					ProtocolId.EPC_GLOBAL_C1G2, tag, accessOps);
			Assert.fail();
		} catch (MissingTagDataException e) {
			Assert.assertTrue(e.getMessage().contains(
					"invalid access result type"));
		}
	}

	public void getAccessSpecTagPattern2() throws MissingTagDataException {
		// create AccessSpec with an TagSpec containing 2 patterns
		BitSet tagMask1 = new BitSet();
		BitSet tagData1 = new BitSet();
		C1G2TargetTag pattern1 = new C1G2TargetTag(new TLVParameterHeader(
				(byte) 0), (byte) 3 /* memoryBank */, true /* isMatch */,
				0 /* pointer */, tagMask1, tagData1);
		BitSet tagMask2 = new BitSet();
		BitSet tagData2 = new BitSet();
		C1G2TargetTag pattern2 = new C1G2TargetTag(new TLVParameterHeader(
				(byte) 0), (byte) 3 /* memoryBank */, true /* isMatch */,
				0 /* pointer */, tagMask2, tagData2);
		C1G2TagSpec tagSpec = new C1G2TagSpec(new TLVParameterHeader((byte) 0),
				pattern1);
		tagSpec.setTagPattern2(pattern2);
		List<AccessSpec> accessSpecs = new ArrayList<>();
		accessSpecs.add(new AccessSpec(new TLVParameterHeader((byte) 0),
				0L /* accessSpecID */, 1 /* antennaID */,
				ProtocolId.EPC_GLOBAL_C1G2, true /* currentState */,
				2L /* roSpecID */, null /* accessSpecStopTrigger */,
				new AccessCommand(new TLVParameterHeader((byte) 0),
						tagSpec/* C1G2TagSpec */, null /* opSpecList */)));

		// get AccessSpec with a matching tag
		// mask1: 1
		// data1: 1
		// mask2: 0000 0000 0001
		// data2: 0000 0000 0001
		// tag__: 1000 0000 0001
		tagMask1.set(0);
		tagData1.set(0);
		tagMask2.set(11);
		tagData2.set(11);
		List<TagOperation> accessOps = new ArrayList<TagOperation>();
		ReadOperation rdOp = new ReadOperation();
		rdOp.setOperationId("4");
		rdOp.setBank((short) 3);
		rdOp.setLength((short) 12);
		rdOp.setOffset((short) 0);
		rdOp.setPassword(0);
		
		accessOps.add(rdOp);
		List<OperationResult> accessResults = new ArrayList<OperationResult>();
		
		ReadResult rdRes = new ReadResult();
		rdRes.setOperationId("1");
		rdRes.setReadData(new byte[] { (byte) 0x80, 0x10 });
		rdRes.setResult(Result.SUCCESS);
		accessResults.add(rdRes);
		
		TagData tag = new TagData();
		tag.setTagDataId(1);
		tag.setEpc(new byte[] { 1, 2 });
		tag.setPc((short) 3);
		tag.setXpc(4);
		tag.setAntennaID((short) 5);
		tag.setRssi((byte) 6);
		tag.setChannel((short) 7);
		tag.setResultList(accessResults);
		
		AccessSpecFilter filter = new AccessSpecFilter(accessSpecs);
		Assert.assertNotNull(filter.getAccessSpec(2 /* roSpecId */,
				1 /* antennaId */, ProtocolId.EPC_GLOBAL_C1G2, tag, accessOps));

		// try to get AccessSpec with a non-matching tag
		// mask1: 1
		// data1: 1
		// mask2: 0000 0000 0001
		// data2: 0000 0000 0010
		// tag__: 1000 0000 0001
		tagData2.set(11, false);
		tagData2.set(10);
		Assert.assertNull(filter.getAccessSpec(2 /* roSpecId */,
				1 /* antennaId */, ProtocolId.EPC_GLOBAL_C1G2, tag, accessOps));

		// set "match" flag to false for non-matching pattern => the AccessSpec
		// matches
		pattern2.setMatch(false);
		Assert.assertNotNull(filter.getAccessSpec(2 /* roSpecId */,
				1 /* antennaId */, ProtocolId.EPC_GLOBAL_C1G2, tag, accessOps));
	}
}
