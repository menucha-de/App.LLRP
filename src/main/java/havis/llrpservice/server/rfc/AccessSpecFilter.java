package havis.llrpservice.server.rfc;

import havis.device.rf.tag.TagData;
import havis.device.rf.tag.operation.ReadOperation;
import havis.device.rf.tag.operation.TagOperation;
import havis.device.rf.tag.result.OperationResult;
import havis.device.rf.tag.result.ReadResult;
import havis.device.rf.tag.result.ReadResult.Result;
import havis.llrpservice.data.message.parameter.AccessSpec;
import havis.llrpservice.data.message.parameter.C1G2TagSpec;
import havis.llrpservice.data.message.parameter.C1G2TargetTag;
import havis.llrpservice.data.message.parameter.ProtocolId;

import java.util.BitSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AccessSpecFilter {

	private Logger log = Logger.getLogger(AccessSpecFilter.class.getName());

	private final List<AccessSpec> accessSpecs;

	public AccessSpecFilter(List<AccessSpec> accessSpecs) {
		this.accessSpecs = accessSpecs;
	}

	/**
	 * Gets the AccessSpec for a ROSpec.
	 * <p>
	 * If there are more than one matching AccessSpec then the first one is
	 * returned.
	 * </p>
	 * 
	 * @param roSpecId
	 * @param antennaId
	 * @param protocolId
	 * @param accessOps
	 * @param tag
	 * @return The AccessSpec
	 * @throws MissingTagDataException
	 */
	public AccessSpec getAccessSpec(long roSpecId, int antennaId, ProtocolId protocolId,
			TagData tag, List<TagOperation> accessOps) throws MissingTagDataException {
		// for each AccessSpec
		for (AccessSpec accessSpec : accessSpecs) {
			// if AccessSpec matches ROSpec, antennaId, protocolId
			if ((accessSpec.getRoSpecId() == 0 || accessSpec.getRoSpecId() == roSpecId)
					&& (accessSpec.getAntennaId() == 0 || accessSpec.getAntennaId() == antennaId)
					&& accessSpec.getProtocolId() == protocolId) {
				// get TagSpec from AccessSpec
				C1G2TagSpec tagSpec = accessSpec.getAccessCommand().getC1g2TagSpec();
				// if TagSpec matches tag
				if (matches(tagSpec.getTagPattern1(), tag, accessOps)
						&& (tagSpec.getTagPattern2() == null
								|| matches(tagSpec.getTagPattern2(), tag, accessOps))) {
					return accessSpec;
				}
			}
		}
		return null;
	}

	/**
	 * See 16.2.1.3.1
	 * 
	 * @param pattern
	 * @param tag
	 * @return True if matched
	 * @throws MissingTagDataException
	 */
	private boolean matches(C1G2TargetTag pattern, TagData tag, List<TagOperation> accessOps)
			throws MissingTagDataException {
		BitSet mask = pattern.getTagMask();
		BitSet data = null;
		BitSet tagData = null;
		boolean isMatched = false;
		try {
			if (mask.isEmpty()) {
				isMatched = pattern.isMatch();
				return isMatched;
			}
			data = pattern.getTagData();
			tagData = getBank(pattern.getMemoryBank(), tag, accessOps);
			// for each bit of mask
			for (int i = mask.nextSetBit(0); i >= 0; i = mask.nextSetBit(i + 1)) {
				boolean dataBit = data.get(i);
				boolean tagDataBit = tagData.get(pattern.getPointer() + i);
				// if filter data do NOT match the data from memory bank
				if (dataBit && !tagDataBit || !dataBit && tagDataBit) {
					isMatched = !pattern.isMatch();
					return isMatched;
				}
			}
			isMatched = pattern.isMatch();
			return isMatched;
		} finally {
			if (log.isLoggable(Level.FINE)) {
				log.log(Level.FINE,
						"mask={0},pointer={1},data={2},isMatch={3},tagData={4},match={5}",
						new Object[] { mask, data == null ? null : pattern.getPointer(), data,
								data == null ? null : pattern.isMatch(), tagData, isMatched });
			}
		}
	}

	/**
	 * Gets the data of a memory bank as {@link BitSet}.
	 * 
	 * @param memoryBank
	 * @param tag
	 *            tag data which contain the data of the EPC bank and the
	 *            results of executed access operations
	 * @param accessOps
	 *            the executed access operations
	 * @return The data of a memory bank
	 * @throws MissingTagDataException
	 */
	private BitSet getBank(byte memoryBank, TagData tag, List<TagOperation> accessOps)
			throws MissingTagDataException {
		// if EPC bank
		if (memoryBank == 1) {
			// convert relating data from TagData structure and return them as
			// BitSet
			BitSet bitSet = new BitSet((4/* CRC, PC */ + tag.getEpc().length) * 8);
			set(bitSet, 0, new short[] { tag.getCrc(), tag.getPc() });

			set(bitSet, 32, tag.getEpc());

			return bitSet;
		}
		List<OperationResult> results = tag.getResultList();
		// for each access operation
		for (int i = 0; i < accessOps.size(); i++) {
			TagOperation op = accessOps.get(i);
			// if read operation
			if (op instanceof ReadOperation) {
				ReadOperation readOp = (ReadOperation) op;
				// if memory bank matches
				if (readOp.getBank() == memoryBank) {
					if (results.size() <= i) {
						throw new MissingTagDataException("Missing tag data from memory bank "
								+ memoryBank + " because of missing access result");
					}
					OperationResult result = results.get(i);
					if (!(result instanceof ReadResult)) {
						throw new MissingTagDataException("Missing tag data from memory bank "
								+ memoryBank + " because of invalid access result type "
								+ result.getClass().getName());
					}
					// get data of memory bank and return them as BitSet
					ReadResult readResult = (ReadResult) results.get(i);
					byte[] data = readResult.getReadData();
					if (!Result.SUCCESS.equals(readResult.getResult())) {
						throw new MissingTagDataException(
								"Missing tag data from memory bank " + memoryBank
										+ " due to access result error " + readResult.getResult());
					}
					BitSet bitSet = new BitSet(data.length * 8);
					set(bitSet, 0, data);
					return bitSet;
				}
			}
		}
		throw new MissingTagDataException("Missing tag data from memory bank " + memoryBank
				+ " because of missing access operation");
	}

	/**
	 * Sets bits of a short array to a bit set.
	 * 
	 * @param bits
	 * @param bitSetStartIndex
	 * @param data
	 */
	private static void set(BitSet bits, int bitSetStartIndex, short[] data) {
		for (int i = 0; i < data.length * 16; i++) {
			if ((data[i / 16] & (0x8000 >> (i % 16))) > 0) {
				bits.set(bitSetStartIndex + i);
			}
		}
	}

	/**
	 * Sets bits of a byte array to a bit set.
	 * 
	 * @param bits
	 * @param bitSetStartIndex
	 * @param data
	 */
	private static void set(BitSet bits, int bitSetStartIndex, byte[] data) {
		for (int i = 0; i < data.length * 8; i++) {
			if ((data[i / 8] & (0x80 >> (i % 8))) > 0) {
				bits.set(bitSetStartIndex + i);
			}
		}
	}
}
