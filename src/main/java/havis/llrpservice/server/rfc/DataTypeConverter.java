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
import havis.llrpservice.data.message.parameter.ParameterTypes.ParameterType;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class DataTypeConverter {
	/**
	 * Converts a LLRP tag pattern to a LLRP filter.
	 * 
	 * @param pattern
	 * @return The filter
	 */
	public Filter convert(C1G2TargetTag pattern) {
		if (pattern == null || pattern.getTagMask().isEmpty()) {
			return null;
		}
		Filter filter = new Filter();
		filter.setBank(pattern.getMemoryBank());
		filter.setBitOffset((short) pattern.getPointer());
		int bitLength = pattern.getMaskBitCount();
		if (pattern.getDataBitCount() > bitLength) {
			bitLength = pattern.getDataBitCount();
		}
		filter.setBitLength((short) bitLength);
		filter.setMask(toByteArray(pattern.getTagMask(), bitLength));
		filter.setData(toByteArray(pattern.getTagData(), bitLength));
		filter.setMatch(pattern.isMatch());
		return filter;
	}

	/**
	 * Converts LLRP access operations to RFC access operations.
	 * 
	 * @param accessOps
	 * @return The tag operation list
	 * @throws UnsupportedAccessOperationException
	 */
	public List<TagOperation> convertAccessOps(List<Parameter> accessOps)
			throws UnsupportedAccessOperationException {
		List<TagOperation> ret = new ArrayList<>();
		if (accessOps == null) {
			return ret;
		}
		for (Parameter accessOp : accessOps) {
			// convert LLRP operation to RFC operation and add it to list
			ParameterType opType = accessOp.getParameterHeader()
					.getParameterType();
			switch (opType) {
			case C1G2_READ:
				C1G2Read read = (C1G2Read) accessOp;

				ReadOperation rdOp = new ReadOperation();
				rdOp.setOperationId(String.valueOf(read.getOpSpecId()));
				rdOp.setBank(read.getMemoryBank());
				rdOp.setOffset((short) read.getWordPointer());
				rdOp.setLength((short) read.getWordCount());
				rdOp.setPassword((int) read.getAccessPw());

				ret.add(rdOp);
				break;
			case C1G2_WRITE:
				C1G2Write write = (C1G2Write) accessOp;

				WriteOperation wrOp = new WriteOperation();
				wrOp.setOperationId(String.valueOf(write.getOpSpecId()));
				wrOp.setBank(write.getMemoryBank());
				wrOp.setOffset((short) write.getWordPointer());
				wrOp.setData(write.getWriteData() == null ? new byte[] {}
						: write.getWriteData());
				wrOp.setPassword((int) write.getAccessPw());
				ret.add(wrOp);
				break;
			case C1G2_LOCK:
				C1G2Lock lock = (C1G2Lock) accessOp;
				for (C1G2LockPayload payload : lock.getC1g2LockPayloadList()) {

					LockOperation lockOp = new LockOperation();
					lockOp.setOperationId(String.valueOf(lock.getOpSpecId()));
					lockOp.setPrivilege(convertPrivilege(payload.getPrivilege()));
					lockOp.setField(convertLockField(payload.getDataField()));
					lockOp.setPassword((int) lock.getAccessPw());

					ret.add(lockOp);
				}
				break;
			case C1G2_KILL:
				C1G2Kill kill = (C1G2Kill) accessOp;
				KillOperation killOp = new KillOperation();
				killOp.setOperationId(String.valueOf(kill.getOpSpecId()));
				killOp.setKillPassword((int) kill.getKillPw());
				ret.add(killOp);

				break;
			default:
				throw new UnsupportedAccessOperationException(
						"Unsupported access operation: " + opType);
			}
		}
		return ret;
	}

	/**
	 * Converts a LLRP privilege to a RFC privilege.
	 * 
	 * @param privilege
	 * @return The privilege
	 */
	private static Privilege convertPrivilege(C1G2LockPayloadPrivilege privilege) {
		switch (privilege) {
		case PERMALOCK:
			return Privilege.PERMALOCK;
		case PERMAUNLOCK:
			return Privilege.PERMAUNLOCK;
		case READ_WRITE:
			return Privilege.LOCK;
		case UNLOCK:
			return Privilege.UNLOCK;
		}
		return null;
	}

	/**
	 * Converts a LLRP lock field to a RFC lock field.
	 * 
	 * @param field
	 * @return The field
	 */
	private static Field convertLockField(C1G2LockPayloadDataField field) {
		switch (field) {
		case ACCESS_PASSWORD:
			return Field.ACCESS_PASSWORD;
		case EPC_MEMORY:
			return Field.EPC_MEMORY;
		case KILL_PASSWORD:
			return Field.KILL_PASSWORD;
		case TID_MEMORY:
			return Field.TID_MEMORY;
		case USER_MEMORY:
			return Field.USER_MEMORY;
		}
		return null;
	}

	/**
	 * Converts a bit set to a byte array.
	 * 
	 * @param bs
	 * @param bitCount
	 * @return The converted byte array
	 */
	private static byte[] toByteArray(BitSet bs, int bitCount) {
		byte[] convBytes = bs.toByteArray();
		byte[] ret = new byte[bitCount / 8 + (bitCount % 8 == 0 ? 0 : 1)];
		for (int i = 0; i < convBytes.length && i < ret.length; i++) {
			ret[i] = reverseBits(convBytes[i]);
		}
		return ret;
	}

	/**
	 * Reverses the bits of a byte.
	 * 
	 * @param b
	 * @return The revised byte
	 */
	private static byte reverseBits(byte b) {
		int in = (b & 0xFF);
		int out = 0;
		for (int i = 0; i < 8; i++) {
			out = (out << 1) | (in & 1);
			in >>= 1;
		}
		return (byte) out;
	}
}
