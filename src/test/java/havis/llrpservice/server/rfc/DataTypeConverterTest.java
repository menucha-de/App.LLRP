package havis.llrpservice.server.rfc;

import havis.device.rf.tag.Filter;
import havis.device.rf.tag.operation.KillOperation;
import havis.device.rf.tag.operation.LockOperation;
import havis.device.rf.tag.operation.LockOperation.Field;
import havis.device.rf.tag.operation.LockOperation.Privilege;
import havis.device.rf.tag.operation.ReadOperation;
import havis.device.rf.tag.operation.TagOperation;
import havis.device.rf.tag.operation.WriteOperation;
import havis.llrpservice.data.message.parameter.C1G2Kill;
import havis.llrpservice.data.message.parameter.C1G2Lock;
import havis.llrpservice.data.message.parameter.C1G2LockPayload;
import havis.llrpservice.data.message.parameter.C1G2LockPayloadDataField;
import havis.llrpservice.data.message.parameter.C1G2LockPayloadPrivilege;
import havis.llrpservice.data.message.parameter.C1G2Read;
import havis.llrpservice.data.message.parameter.C1G2TargetTag;
import havis.llrpservice.data.message.parameter.C1G2Write;
import havis.llrpservice.data.message.parameter.Parameter;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class DataTypeConverterTest {

	public void convertC1G2TargetTag() {
		DataTypeConverter conv = new DataTypeConverter();

		// "null" parameter
		Filter filter = conv.convert(null);
		Assert.assertNull(filter);

		// mask: 0000
		// data: 1000
		BitSet tagMask = new BitSet();
		BitSet tagData = new BitSet();
		tagData.set(0);
		C1G2TargetTag pattern = new C1G2TargetTag(new TLVParameterHeader(
				(byte) 0), (byte) 0 /* memoryBank */, true /* isMatch */,
				0 /* pointer */, tagMask, tagData);
		pattern.setMaskBitCount(4);
		filter = conv.convert(pattern);
		Assert.assertNull(filter);

		// mask: 0110 0001 1100 0000 0
		// data: 0101 0010 1010 0000 0
		tagMask = new BitSet();
		tagMask.set(1, 3);
		tagMask.set(7, 10);
		tagData = new BitSet();
		tagData.set(1);
		tagData.set(3);
		tagData.set(6);
		tagData.set(8);
		tagData.set(10);
		pattern = new C1G2TargetTag(new TLVParameterHeader((byte) 0),
				(byte) 1 /* memoryBank */, false /* isMatch */, 3 /* pointer */,
				tagMask, tagData);
		pattern.setMaskBitCount(17);
		filter = conv.convert(pattern);
		Assert.assertEquals(filter.getBank(), 1);
		Assert.assertFalse(filter.isMatch());
		Assert.assertEquals(filter.getBitOffset(), 3);
		Assert.assertEquals(filter.getBitLength(), pattern.getMaskBitCount());
		Assert.assertEquals(filter.getMask(), new byte[] { (byte) 0x61,
				(byte) 0xC0, 0 });
		Assert.assertEquals(filter.getData(), new byte[] { (byte) 0x52,
				(byte) 0xA0, 0 });
	}

	public void convertAccessOps() throws UnsupportedAccessOperationException {
		DataTypeConverter conv = new DataTypeConverter();

		// "null" parameter
		Assert.assertTrue(conv.convertAccessOps(null).isEmpty());

		// invalid parameter type
		List<Parameter> ops = new ArrayList<>();
		ops.add(new C1G2TargetTag(new TLVParameterHeader((byte) 0),
				(byte) 0 /* memoryBank */, true /* isMatch */, 0 /* pointer */,
				new BitSet() /* tagMask */, new BitSet() /* tagData */));
		try {
			conv.convertAccessOps(ops);
			Assert.fail();
		} catch (UnsupportedAccessOperationException e) {
			e.getMessage().contains(
					ops.get(0).getParameterHeader().getParameterType()
							.toString());
		}

		ops.clear();
		ops.add(new C1G2Read(new TLVParameterHeader((byte) 0), 0 /* opSpecId */,
				1L /* accessPw */, (byte) 2 /* memoryBank */,
				3 /* wordPointer */, 4 /* wordCount */));
		ops.add(new C1G2Write(new TLVParameterHeader((byte) 0), 0 /* opSpecId */,
				1L /* accessPw */, (byte) 2 /* memoryBank */,
				3 /* wordPointer */, null /* writeData */));
		ops.add(new C1G2Write(new TLVParameterHeader((byte) 0), 4 /* opSpecId */,
				5L /* accessPw */, (byte) 6 /* memoryBank */,
				7 /* wordPointer */, new byte[] { 8, 9 } /* writeData */));
		ops.add(new C1G2Kill(new TLVParameterHeader((byte) 0), 0 /* opSpecId */,
				1L /* killPw */));
		ops.add(new C1G2Lock(new TLVParameterHeader((byte) 0), 0 /* opSpecId */,
				1L /* accessPW */, Arrays.asList(new C1G2LockPayload[] {
						new C1G2LockPayload(new TLVParameterHeader((byte) 0),
								C1G2LockPayloadPrivilege.READ_WRITE,
								C1G2LockPayloadDataField.ACCESS_PASSWORD),
						new C1G2LockPayload(new TLVParameterHeader((byte) 0),
								C1G2LockPayloadPrivilege.UNLOCK,
								C1G2LockPayloadDataField.KILL_PASSWORD),
						new C1G2LockPayload(new TLVParameterHeader((byte) 0),
								C1G2LockPayloadPrivilege.PERMALOCK,
								C1G2LockPayloadDataField.TID_MEMORY),
						new C1G2LockPayload(new TLVParameterHeader((byte) 0),
								C1G2LockPayloadPrivilege.PERMAUNLOCK,
								C1G2LockPayloadDataField.EPC_MEMORY),
						new C1G2LockPayload(new TLVParameterHeader((byte) 0),
								C1G2LockPayloadPrivilege.PERMAUNLOCK,
								C1G2LockPayloadDataField.USER_MEMORY) })));
		List<TagOperation> result = conv.convertAccessOps(ops);
		ReadOperation readOp = (ReadOperation) result.get(0);
		Assert.assertEquals(readOp.getPassword(), 1);
		Assert.assertEquals(readOp.getBank(), 2);
		Assert.assertEquals(readOp.getOffset(), 3);
		Assert.assertEquals(readOp.getLength(), 4);

		WriteOperation writeOp = (WriteOperation) result.get(1);
		Assert.assertEquals(writeOp.getPassword(), 1);
		Assert.assertEquals(writeOp.getBank(), 2);
		Assert.assertEquals(writeOp.getOffset(), 3);
		Assert.assertEquals(writeOp.getData(), new byte[] {});

		writeOp = (WriteOperation) result.get(2);
		Assert.assertEquals(writeOp.getPassword(), 5);
		Assert.assertEquals(writeOp.getBank(), 6);
		Assert.assertEquals(writeOp.getOffset(), 7);
		Assert.assertEquals(writeOp.getData(), new byte[] { 8, 9 });

		KillOperation killOp = (KillOperation) result.get(3);
		Assert.assertEquals(killOp.getKillPassword(), 1);

		// the lock operation without payload is ignored while conversion

		Privilege[] privileges = new Privilege[] { Privilege.LOCK,
				Privilege.UNLOCK, Privilege.PERMALOCK, Privilege.PERMAUNLOCK,
				Privilege.PERMAUNLOCK };
		Field[] fields = new Field[] { Field.ACCESS_PASSWORD,
				Field.KILL_PASSWORD, Field.TID_MEMORY, Field.EPC_MEMORY,
				Field.USER_MEMORY };
		for (int i = 0; i < privileges.length; i++) {
			LockOperation lockOp = (LockOperation) result.get(4 + i);
			Assert.assertEquals(lockOp.getPassword(), 1);
			Assert.assertEquals(lockOp.getPrivilege(), privileges[i]);
			Assert.assertEquals(lockOp.getField(), fields[i]);
		}
	}
}
