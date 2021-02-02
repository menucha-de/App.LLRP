package havis.llrpservice.server.rfc;

import havis.device.rf.RFDevice;
import havis.device.rf.tag.Filter;
import havis.device.rf.tag.operation.ReadOperation;
import havis.device.rf.tag.operation.RequestOperation;
import havis.device.rf.tag.operation.TagOperation;
import havis.llrpservice.common.ids.IdGenerator;
import havis.llrpservice.data.message.parameter.AccessSpec;
import havis.llrpservice.data.message.parameter.AccessSpecStopTrigger;
import havis.llrpservice.data.message.parameter.C1G2TagSpec;
import havis.llrpservice.sbc.rfc.RFCClientMultiplexed;
import havis.llrpservice.sbc.rfc.RFCException;
import havis.llrpservice.sbc.rfc.message.GetOperations;
import havis.llrpservice.sbc.rfc.message.GetOperationsResponse;
import havis.llrpservice.sbc.rfc.message.MessageHeader;
import havis.llrpservice.server.event.EventQueue;
import havis.llrpservice.server.event.RFCMessageEvent;
import havis.llrpservice.server.rfc.ROSpecExecutor.ROSpecExecutionPosition;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * The AccessSpecExecutor handles the selection and execution of AccessSpecs.
 * <p>
 * AccessSpecs can be added and removed with {@link #add(AccessSpec)},
 * {@link #remove(long)}. These modifications to the AccessSpec list are saved
 * temporary until {@link #getNextInventoryAccessOps(ROSpecExecutionPosition)}
 * is called.
 * </p>
 * <p>
 * {@link #getNextInventoryAccessOps(ROSpecExecutionPosition)} provides the
 * filter and access operations for an inventory call
 * {@link RFDevice#execute(List, List, List)}.
 * </p>
 * <p>
 * While an inventory is running received callback messages
 * {@link GetOperations} must be processed immediately by calling
 * {@link #startNextAccessOps(GetOperations, List, ROSpecExecutionPosition)}.
 * </p>
 * <p>
 * The implementation is thread safe.
 * </p>
 */
public class AccessSpecExecutor {
	/**
	 * The prefix for identifiers used for generated access operations. For all
	 * operations of an AccessSpec the OpSpecIds are used.
	 */
	public final static String GENERATED_ID_PREFIX = "g";

	private final RFCClientMultiplexed rfcClient;
	private final EventQueue eventQueue;
	private final List<AccessSpec> accessSpecs = new ArrayList<>();
	private List<AccessSpec> inventoryAccessSpecs = new ArrayList<>();
	private final Map<AccessSpec, RuntimeData> runtimeData = new HashMap<>();
	private final SocketChannel rfcChannel;
	private final List<AccessSpecsListener> listeners = new ArrayList<>();

	/**
	 * A container with a tag filter and access operations. The data can be used
	 * to start an inventory via the RF controller interface.
	 */
	public class InventoryAccessOps {
		private List<Filter> tagFilter;
		private List<TagOperation> tagOperations;

		public List<Filter> getTagFilter() {
			return tagFilter;
		}

		void setTagFilter(List<Filter> tagFilter) {
			this.tagFilter = tagFilter;
		}

		public List<TagOperation> getTagOperations() {
			return tagOperations;
		}

		void setTagOperations(List<TagOperation> tagOperations) {
			this.tagOperations = tagOperations;
		}
	}

	/**
	 * Holds runtime data for an AccessSpec execution.
	 */
	private class RuntimeData {
		int operationCount = 0;
	}

	public AccessSpecExecutor(RFCClientMultiplexed rfcClient, SocketChannel rfcChannel,
			EventQueue eventQueue) {
		this.rfcClient = rfcClient;
		this.rfcChannel = rfcChannel;
		this.eventQueue = eventQueue;
	}

	/**
	 * Adds a listener for AccessSpecs.
	 * 
	 * @param listener
	 */
	public synchronized void addListener(AccessSpecsListener listener) {
		listeners.add(listener);
	}

	/**
	 * Removes a listener for AccessSpecs.
	 * 
	 * @param listener
	 */
	public synchronized void removeListener(AccessSpecsListener listener) {
		listeners.remove(listener);
	}

	/**
	 * Adds an AccessSpec. The AccessSpec must be enabled.
	 * 
	 * @param accessSpec
	 * @throws UnsupportedAirProtocolException
	 */
	public synchronized void add(AccessSpec accessSpec) throws UnsupportedAirProtocolException {
		switch (accessSpec.getProtocolId()) {
		case EPC_GLOBAL_C1G2:
			accessSpecs.add(accessSpec);
			runtimeData.put(accessSpec, new RuntimeData());
			break;
		default:
			throw new UnsupportedAirProtocolException(
					"Unknown air protocol " + accessSpec.getProtocolId() + "  cannot be handled");
		}
	}

	/**
	 * Removes an AccessSpec.
	 * 
	 * @param accessSpecId
	 * @return The AccessSpec
	 */
	public synchronized AccessSpec remove(long accessSpecId) {
		AccessSpec remove = null;
		for (AccessSpec accessSpec : accessSpecs) {
			if (accessSpec.getAccessSpecId() == accessSpecId) {
				remove = accessSpec;
				break;
			}
		}
		if (remove != null) {
			accessSpecs.remove(remove);
			runtimeData.remove(remove);
			// fire events to listeners
			for (AccessSpecsListener listener : listeners) {
				listener.removed(remove.getAccessSpecId());
			}
		}
		return remove;
	}

	/**
	 * Gets the filter and access operations for an inventory call
	 * {@link RFDevice#execute(List, List, List)} by using the current list of
	 * AccessSpecs. The returned access operations are executed with the
	 * inventory call.
	 * <p>
	 * A callback message {@link GetOperations} is requested if any AccessSpecs
	 * are added for the current ROSpec. In this case a list of
	 * {@link ReadOperation} for existing filters and a {@link RequestOperation}
	 * is returned by this method.
	 * </p>
	 * 
	 * @return The InventoryAccessOps
	 * @param pos
	 *            the current execution position of the ROSpec with the current
	 *            ROSpecId, specIndex, antenna and air protocol. The
	 *            AccessSpecIds of the returned access operations are set here.
	 * @throws UnsupportedAccessOperationException
	 */
	public synchronized InventoryAccessOps getNextInventoryAccessOps(ROSpecExecutionPosition pos)
			throws UnsupportedAccessOperationException {
		// clear ROSpecExecutionPosition data
		pos.setDefaultAccessSpecId(null);
		pos.getTagDataAccessSpecIds().clear();
		// get the AccessSpecs for the active ROSpec
		// all following calls of startNextAccessOps use this list of
		// AccessSpecs
		inventoryAccessSpecs = new ArrayList<>();
		for (AccessSpec accessSpec : accessSpecs) {
			if (accessSpec.getRoSpecId() == 0 || accessSpec.getRoSpecId() == pos.getRoSpecId()) {
				inventoryAccessSpecs.add(accessSpec);
			}
		}
		InventoryAccessOps iao = new InventoryAccessOps();
		iao.setTagFilter(new ArrayList<Filter>());
		if (inventoryAccessSpecs.isEmpty()) {
			// return empty tag operation list
			iao.setTagOperations(new ArrayList<TagOperation>());
			return iao;
		}
		// get all existing tag filters
		List<Filter> filters = new ArrayList<>();
		for (AccessSpec accessSpec : inventoryAccessSpecs) {
			filters.addAll(getFilter(accessSpec));
		}
		// create read operations for filters + RequestOperation
		List<TagOperation> tagOps = getReadOperations(filters);
		RequestOperation reqOp = new RequestOperation();
		reqOp.setOperationId(GENERATED_ID_PREFIX + IdGenerator.getNextLongId());
		tagOps.add(reqOp);
		iao.setTagOperations(tagOps);
		return iao;
	}

	/**
	 * Starts the next access operations by sending a
	 * {@link GetOperationsResponse} message as response to a
	 * {@link GetOperations} request.
	 * 
	 * @param getOps
	 *            the request message
	 * @param accessOps
	 *            the access operations correlating to the read results in the
	 *            {@link GetOperations} request
	 * @param pos
	 *            the current execution position of the ROSpec with the current
	 *            ROSpecId, specIndex, antenna and air protocol. The
	 *            AccessSpecIds used for the inventory are set here.
	 * @throws MissingTagDataException
	 * @throws UnsupportedAccessOperationException
	 * @throws RFCException
	 */
	public synchronized void startNextAccessOps(GetOperations getOps, List<TagOperation> accessOps,
			ROSpecExecutionPosition pos)
			throws MissingTagDataException, UnsupportedAccessOperationException, RFCException {
		// get AccessSpec
		AccessSpecFilter filter = new AccessSpecFilter(inventoryAccessSpecs);
		AccessSpec accessSpec = filter.getAccessSpec(pos.getRoSpecId(), pos.getAntennaId(),
				pos.getProtocolId(), getOps.getTag(), accessOps);
		List<TagOperation> ops = new ArrayList<>();
		if (accessSpec != null) {
			// add AccessSpecId for the tag to ROSpecExecutionPosition
			Map<Long, Long> tagDataAccessSpecIds = pos.getTagDataAccessSpecIds();
			tagDataAccessSpecIds.put(getOps.getTag().getTagDataId(), accessSpec.getAccessSpecId());
			// get RFC operations from AccessSpec
			ops = getOperations(accessSpec);
			// get runtime data for AccessSpec
			RuntimeData rd = runtimeData.get(accessSpec);
			if (rd != null) {
				// increase operation count for the AccessSpec
				rd.operationCount++;
			}
			// if operation limit is reached
			if (isAccessSpecStopped(accessSpec)) {
				// remove the AccessSpec
				inventoryAccessSpecs.remove(accessSpec);
				remove(accessSpec.getAccessSpecId());
			}
		}
		// create response message
		GetOperationsResponse response = new GetOperationsResponse(
				new MessageHeader(getOps.getMessageHeader().getId()), ops);
		// enqueue the message to the event queue (for info only)
		eventQueue.put(new RFCMessageEvent(response));
		// start the processing of the operations
		rfcClient.requestSendingData(rfcChannel, response);
	}

	private boolean isAccessSpecStopped(AccessSpec accessSpec) {
		AccessSpecStopTrigger trigger = accessSpec.getAccessSpecStopTrigger();
		switch (trigger.getAccessSpecStopTriggerType()) {
		case NULL:
			break;
		case OPERATION_COUNT:
			if (trigger.getOperationCountValue() != 0) {
				RuntimeData rd = runtimeData.get(accessSpec);
				return rd == null || trigger.getOperationCountValue() == rd.operationCount;
			}
		}
		return false;
	}

	/**
	 * Checks if an AccessSpec has a non-empty TagSpec and returns it as filter.
	 * 
	 * @param accessSpec
	 * @return <code>null</code> if no TagSpec exists or it is empty
	 */
	private static List<Filter> getFilter(AccessSpec accessSpec) {
		List<Filter> ret = new ArrayList<>();
		C1G2TagSpec tagSpec = accessSpec.getAccessCommand().getC1g2TagSpec();
		BitSet mask = tagSpec.getTagPattern1().getTagMask();
		if (mask != null && !mask.isEmpty()) {
			DataTypeConverter conv = new DataTypeConverter();
			Filter filter = conv.convert(tagSpec.getTagPattern1());
			if (filter != null) {
				ret.add(filter);
			}
			filter = conv.convert(tagSpec.getTagPattern2());
			if (filter != null) {
				ret.add(filter);
			}
		}
		return ret;
	}

	/**
	 * Gets the operations of an AccessSpec.
	 * 
	 * @param accessSpec
	 * @return The tag operations
	 * @throws UnsupportedAccessOperationException
	 * @throws UnsupportedAirProtocolException
	 */
	private static List<TagOperation> getOperations(AccessSpec accessSpec)
			throws UnsupportedAccessOperationException {
		return new DataTypeConverter()
				.convertAccessOps(accessSpec.getAccessCommand().getOpSpecList());
	}

	private static List<TagOperation> getReadOperations(List<Filter> filters) {
		List<TagOperation> ret = new ArrayList<>();
		// memory bank => index of last set bit
		Map<Short, Integer> memoryBanks = new HashMap<>();

		// for each pattern
		for (Filter filter : filters) {
			// get existing index of last set bit for the memory bank
			short memoryBank = filter.getBank();
			Integer prevIndexLastSetBit = memoryBanks.get(memoryBank);
			// get index of last set bit from the current mask
			int indexLastSetBit = filter.getBitOffset() + filter.getBitLength();
			// if more data must be read
			if (prevIndexLastSetBit == null || indexLastSetBit > prevIndexLastSetBit) {
				// increase the index
				memoryBanks.put(memoryBank, indexLastSetBit);
			}
		}

		// for each memory bank
		for (Entry<Short, Integer> entry : memoryBanks.entrySet()) {
			short memoryBank = entry.getKey();
			Integer indexLastSetBit = entry.getValue();
			// create ReadOperation
			int length = indexLastSetBit / 16;
			if (indexLastSetBit % 16 > 0) {
				length++;
			}
			ReadOperation rdOp = new ReadOperation();
			rdOp.setOperationId(GENERATED_ID_PREFIX + IdGenerator.getNextLongId());
			rdOp.setBank(memoryBank);
			rdOp.setOffset((short) 0);
			rdOp.setLength((short) length);
			rdOp.setPassword(0);
			ret.add(rdOp);
		}
		return ret;
	}
}
