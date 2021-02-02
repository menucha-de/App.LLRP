package havis.llrpservice.server.rfc;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import havis.device.rf.tag.TagData;
import havis.llrpservice.common.ids.IdGenerator;
import havis.llrpservice.data.message.parameter.AISpec;
import havis.llrpservice.data.message.parameter.AISpecStopTrigger;
import havis.llrpservice.data.message.parameter.AccessSpec;
import havis.llrpservice.data.message.parameter.GPIEvent;
import havis.llrpservice.data.message.parameter.InventoryParameterSpec;
import havis.llrpservice.sbc.rfc.RFCClientMultiplexed;
import havis.llrpservice.sbc.rfc.RFCException;
import havis.llrpservice.sbc.rfc.message.Execute;
import havis.llrpservice.sbc.rfc.message.GetOperations;
import havis.llrpservice.sbc.rfc.message.GetOperationsResponse;
import havis.llrpservice.sbc.rfc.message.MessageHeader;
import havis.llrpservice.server.event.EventQueue;
import havis.llrpservice.server.event.RFCMessageEvent;
import havis.llrpservice.server.rfc.AccessSpecExecutor.InventoryAccessOps;
import havis.llrpservice.server.rfc.ROSpecExecutor.ROSpecExecutionPosition;

/**
 * The AISpecExecutor handles the execution of an AISpec.
 * <p>
 * An inventory can be started asynchronously with
 * {@link #startNextInventory(ROSpecExecutionPosition, AISpec)}. The sent and
 * received messages to/from the RF controller are added to an event queue.
 * </p>
 * <p>
 * While an inventory is running received callback messages
 * {@link GetOperations} must be processed immediately by calling
 * {@link #startNextAccessOps(GetOperations)}.
 * </p>
 * <p>
 * AccessSpecs can be added and removed with {@link #add(AccessSpec)},
 * {@link #remove(long)}. If an inventory is running these modifications are
 * saved temporary until the next inventory is started.
 * </p>
 * <p>
 * An AISpec must be stopped with {@link #stop()} to clean up resources like
 * timers for stop triggers.
 * </p>
 * <p>
 * For working stop triggers the methods
 * {@link #executionResponseReceived(long, List)} and
 * {@link #gpiEventReceived(GPIEvent)} must be called by the client. If a
 * trigger stops an AISpec then {@link AISpecExecutorListener#isStopped(AISpec)}
 * is called for each registered listener. A client can register a listener with
 * {@link #addListener(AISpecExecutorListener)} /
 * {@link #removeListener(AISpecExecutorListener)}.
 * </p>
 * <p>
 * The implementation is thread safe.
 * </p>
 */
public class AISpecExecutor {
	private Logger log = Logger.getLogger(AISpecExecutor.class.getName());

	private final RFCClientMultiplexed rfcClient;
	private final EventQueue eventQueue;
	private final SocketChannel rfcChannel;
	private final AccessSpecExecutor accessSpecExecutor;
	private final RuntimeData runtimeData = new RuntimeData();
	private final List<AISpecExecutorListener> listeners = new ArrayList<>();
	private Timer timer;
	private TimerTask stopTask;
	private Date triggerStartDate;

	private final Object lock = new Object();

	public interface AISpecExecutorListener {
		void isStopped(AISpec aiSpec);
	}

	/**
	 * Holds runtime data for an AISpec execution.
	 * <p>
	 * See {@link AISpecExecutor#resetRuntimeData()} for initialization.
	 * </p>
	 */
	private class RuntimeData {
		ROSpecExecutionPosition pos;
		AISpec aiSpec;
		int indexAntennaId;
		int indexInventoryParameterSpec;
		InventoryAccessOps accessOps;
		int inventoryCount;
		// epc -> count
		Map<String, Long> tagCounts = new HashMap<>();
	}

	public AISpecExecutor(RFCClientMultiplexed rfcClient, SocketChannel rfcChannel,
			EventQueue eventQueue) {
		this.rfcClient = rfcClient;
		this.rfcChannel = rfcChannel;
		this.eventQueue = eventQueue;
		accessSpecExecutor = new AccessSpecExecutor(rfcClient, rfcChannel, eventQueue);
		resetRuntimeData();
	}

	/**
	 * Adds a listener;
	 * 
	 * @param listener
	 */
	public void addListener(AISpecExecutorListener listener) {
		listeners.add(listener);
	}

	/**
	 * Removes a listener.
	 * 
	 * @param listener
	 */
	public void removeListener(AISpecExecutorListener listener) {
		listeners.remove(listener);
	}

	/**
	 * Adds a listener for AccessSpecs.
	 * 
	 * @param listener
	 */
	public void addListener(AccessSpecsListener listener) {
		accessSpecExecutor.addListener(listener);
	}

	/**
	 * Removes a listener for AccessSpecs.
	 * 
	 * @param listener
	 */
	public void removeListener(AccessSpecsListener listener) {
		accessSpecExecutor.removeListener(listener);
	}

	/**
	 * Adds an AccessSpec. The AccessSpec must be enabled.
	 * 
	 * @param accessSpec
	 * @throws UnsupportedAirProtocolException
	 */
	public void add(AccessSpec accessSpec) throws UnsupportedAirProtocolException {
		accessSpecExecutor.add(accessSpec);
	}

	/**
	 * Removes an AccessSpec.
	 * 
	 * @param accessSpecId
	 * @return The removed AccessSpec
	 */
	public AccessSpec remove(long accessSpecId) {
		return accessSpecExecutor.remove(accessSpecId);
	}

	public void executionResponseReceived(List<TagData> tagData) {
		synchronized (lock) {
			// if AISpec has already been stopped
			if (runtimeData.aiSpec == null) {
				return;
			}
			long prevUniqueTagCount = runtimeData.tagCounts.size();
			runtimeData.inventoryCount++;
			// add tags to runtimeData.tagCounts
			for (TagData tag : tagData) {
				String epc = toString(tag.getEpc());
				Long count = runtimeData.tagCounts.get(epc);
				if (count == null) {
					count = 1L;
				} else {
					count++;
				}
				runtimeData.tagCounts.put(epc, count);
				count = runtimeData.tagCounts.get(epc);
			}
			boolean resetTrigger = false;
			boolean isProcessed = false;
			AISpecStopTrigger st = runtimeData.aiSpec.getAiSpecStopTrigger();
			if (st == null) {
				return;
			}
			switch (st.getAiSpecStopTriggerType()) {
			case NULL:
				// stopped when ROSpec is done
			case DURATION:
				// stopped by trigger
			case GPI_WITH_TIMEOUT:
				// stopped by gpiEventReceived or trigger
				break;
			case TAG_OBSERVATION:
				switch (st.getTagOT().getTriggerType()) {
				case UPON_SEEING_N_TAG_OBSERVATIONS_OR_TIMEOUT:
					// summarize tag counts
					long totalTagCount = 0;
					for (Long count : runtimeData.tagCounts.values()) {
						totalTagCount += count;
					}
					// if max. tag count is reached
					isProcessed = (totalTagCount >= st.getTagOT().getNumberOfTags());
					break;
				case UPON_SEEING_NO_MORE_NEW_TAG_OBSERVATIONS_FOR_T_MS_OR_TIMEOUT:
					// if new tags are reported
					resetTrigger = (tagData.size() > 0);
					break;
				case N_ATTEMPTS_TO_SEE_ALL_TAGS_IN_THE_FOV_OR_TIMEOUT:
					// if number of attempts is reached
					isProcessed = (runtimeData.inventoryCount == st.getTagOT()
							.getNumberOfAttempts());
					break;
				case UPON_SEEING_N_UNIQUE_TAG_OBSERVATIONS_OR_TIMEOUT:
					// if number of unique tags is reached
					isProcessed = (runtimeData.tagCounts.size() >= st.getTagOT().getNumberOfTags());
					break;
				case UPON_SEEING_NO_MORE_NEW_UNIQUE_TAG_OBSERVATIONS_FOR_T_MS_OR_TIMEOUT:
					// if new unique tags are reported
					resetTrigger = (runtimeData.tagCounts.size() > prevUniqueTagCount);
					break;
				}
				break;
			}
			if (resetTrigger) {
				removeTimerTask();
				Date nextIdleTimeout = new Date(new Date().getTime() + st.getTagOT().getT());
				Date timeout = (st.getTagOT().getTimeOut() == 0) ? null
						: new Date(triggerStartDate.getTime() + st.getTagOT().getTimeOut());
				setTimerTask((timeout == null || nextIdleTimeout.before(timeout)) ? nextIdleTimeout
						: timeout /* date */, 0 /* offset */);
			} else if (isProcessed) {
				stop();
			}
		}
	}

	public void gpiEventReceived(GPIEvent event) {
		synchronized (lock) {
			// if AISpec has already been stopped
			if (runtimeData.aiSpec == null) {
				return;
			}
			AISpecStopTrigger st = runtimeData.aiSpec.getAiSpecStopTrigger();
			if (st == null) {
				return;
			}
			boolean isProcessed = false;
			switch (st.getAiSpecStopTriggerType()) {
			case NULL:
				// stopped when ROSpec is done
			case DURATION:
				// stopped by trigger
				break;
			case GPI_WITH_TIMEOUT:
				isProcessed = (event.getGpiPortNumber() == st.getGpiTV().getGpiPortNum()
						&& (event.isState() && st.getGpiTV().isGpiEvent()
								|| !event.isState() && !st.getGpiTV().isGpiEvent()));
				break;
			case TAG_OBSERVATION:
				// stopped by executionResponseReceived or trigger
				break;
			}
			if (isProcessed) {
				stop();
			}
		}
	}

	/**
	 * Starts the next inventory for an AISpec using the current set of
	 * AccessSpecs.
	 * 
	 * @param pos
	 *            the current execution position of the ROSpec with the current
	 *            ROSpecId and specIndex. The antenna and air protocol used for
	 *            the next inventory is set here.
	 * @param aiSpec
	 * @throws UnsupportedAccessOperationException
	 * @throws RFCException
	 * @throws UnsupportedAirProtocolException
	 */
	public void startNextInventory(ROSpecExecutionPosition pos, AISpec aiSpec)
			throws UnsupportedAccessOperationException, RFCException,
			UnsupportedAirProtocolException {
		synchronized (lock) {
			// select next antenna/inventoryParameterSpec combination:
			// if first call or different AISpec or all
			// antenna/inventoryParameterSpec combinations of previous AISpec
			// have
			// been processed
			boolean isNewAISpec = runtimeData.aiSpec != aiSpec;
			if (isNewAISpec
					|| runtimeData.indexAntennaId == runtimeData.aiSpec.getAntennaIdList().size()
							- 1
							&& runtimeData.indexInventoryParameterSpec == runtimeData.aiSpec
									.getInventoryParameterList().size() - 1) {
				// throw an exception if the new AISpec contains unsupported air
				// protocols
				for (InventoryParameterSpec inventoryParameter : aiSpec
						.getInventoryParameterList()) {
					switch (inventoryParameter.getProtocolID()) {
					case EPC_GLOBAL_C1G2:
						break;
					default:
						throw new UnsupportedAirProtocolException("Unknown air protocol "
								+ inventoryParameter.getProtocolID() + "  cannot be handled");
					}
				}
				runtimeData.pos = pos;
				runtimeData.aiSpec = aiSpec;
				// use first inventoryParameterSpec for first antenna
				runtimeData.indexAntennaId = 0;
				runtimeData.indexInventoryParameterSpec = 0;
				// get current access operations from AccessSpec executor
				runtimeData.accessOps = accessSpecExecutor.getNextInventoryAccessOps(pos);
				if (isNewAISpec) {
					// start an existing stop trigger
					startStopTrigger();
				}
			}
			// else if last inventoryParameterSpec for current antenna has been
			// used
			else if (runtimeData.indexInventoryParameterSpec == runtimeData.aiSpec
					.getInventoryParameterList().size() - 1) {
				// use first inventoryParameterSpec of next antenna
				runtimeData.indexAntennaId++;
				runtimeData.indexInventoryParameterSpec = 0;
			} else {
				// use next inventoryParameterSpec for current antenna
				runtimeData.indexInventoryParameterSpec++;
			}
			// update antennaId, protocolId, invParamSpecId in position data
			Integer antennaId = runtimeData.aiSpec.getAntennaIdList()
					.get(runtimeData.indexAntennaId);
			runtimeData.pos.setAntennaId(antennaId.intValue());
			InventoryParameterSpec invParamSpec = runtimeData.aiSpec.getInventoryParameterList()
					.get(runtimeData.indexInventoryParameterSpec);
			runtimeData.pos.setProtocolId(invParamSpec.getProtocolID());
			runtimeData.pos.setInventoryParameterSpecId(invParamSpec.getSpecID());
			// create inventory request
			Execute request = new Execute(new MessageHeader(IdGenerator.getNextLongId()),
					Arrays.asList(antennaId.shortValue()), runtimeData.accessOps.getTagFilter(),
					runtimeData.accessOps.getTagOperations());
			// add request message to the event queue (for info only)
			eventQueue.put(new RFCMessageEvent(request));
			// start execution
			rfcClient.requestSendingData(rfcChannel, request);
		}
	}

	/**
	 * Starts the next access operations by sending a
	 * {@link GetOperationsResponse} message as response to a
	 * {@link GetOperations} request.
	 * 
	 * @param getOps
	 *            the request message
	 * @throws RFCException
	 * @throws UnsupportedAccessOperationException
	 * @throws MissingTagDataException
	 */
	public void startNextAccessOps(GetOperations getOps)
			throws MissingTagDataException, UnsupportedAccessOperationException, RFCException {
		synchronized (lock) {
			// start next access operations
			accessSpecExecutor.startNextAccessOps(getOps, runtimeData.accessOps.getTagOperations(),
					runtimeData.pos);
		}
	}

	public void stop() {
		AISpec aiSpec;
		synchronized (lock) {
			// if AISpec has already been stopped
			if (runtimeData.aiSpec == null) {
				return;
			}
			removeTimerTask();
			aiSpec = runtimeData.aiSpec;
			resetRuntimeData();
		}
		// fire events
		for (AISpecExecutorListener listener : listeners) {
			listener.isStopped(aiSpec);
		}
	}

	private String toString(byte[] epc) {
		StringBuffer strBuf = new StringBuffer();
		for (byte b : epc) {
			strBuf.append('.').append(b);
		}
		return strBuf.toString();
	}

	private void resetRuntimeData() {
		runtimeData.pos = null;
		runtimeData.aiSpec = null;
		runtimeData.indexAntennaId = -1;
		runtimeData.indexInventoryParameterSpec = -1;
		runtimeData.accessOps = null;
		runtimeData.inventoryCount = 0;
		runtimeData.tagCounts.clear();
	}

	/**
	 * @param date
	 *            optional
	 * @param offset
	 *            in ms
	 */
	private void setTimerTask(Date date, long offset) {
		if (timer == null) {
			timer = new Timer("AISpecsTimerThread");
		}
		stopTask = new TimerTask() {

			@Override
			public void run() {
				stop();
			}
		};
		if (date == null) {
			timer.schedule(stopTask, offset);
			if (log.isLoggable(Level.FINE)) {
				log.log(Level.FINE, "Scheduled timer for stopping of AISpec: offset=" + offset);
			}
		} else {
			timer.schedule(stopTask, new Date(date.getTime() + offset));
			if (log.isLoggable(Level.FINE)) {
				log.log(Level.FINE, "Scheduled timer for stopping of AISpec: date="
						+ new Date(date.getTime() + offset).getTime());
			}
		}
	}

	private void removeTimerTask() {
		if (timer == null) {
			return;
		}
		stopTask.cancel();
		stopTask = null;
		if (log.isLoggable(Level.FINE)) {
			log.log(Level.FINE, "Canceled timer for stopping of AISpec");
		}
		timer.cancel();
		timer = null;
	}

	private void startStopTrigger() {
		AISpecStopTrigger st = runtimeData.aiSpec.getAiSpecStopTrigger();
		if (st == null) {
			return;
		}
		switch (st.getAiSpecStopTriggerType()) {
		case NULL:
			break;
		case DURATION:
			setTimerTask(null /* date */, st.getDurationTrigger() /* offset */);
			break;
		case GPI_WITH_TIMEOUT:
			setTimerTask(null /* date */, st.getGpiTV().getTimeOut() /* offset */);
			break;
		case TAG_OBSERVATION:
			long timeout = st.getTagOT().getTimeOut();
			if (timeout > 0) {
				switch (st.getTagOT().getTriggerType()) {
				case UPON_SEEING_N_TAG_OBSERVATIONS_OR_TIMEOUT: // 0
				case N_ATTEMPTS_TO_SEE_ALL_TAGS_IN_THE_FOV_OR_TIMEOUT: // 2
				case UPON_SEEING_N_UNIQUE_TAG_OBSERVATIONS_OR_TIMEOUT: // 3
					setTimerTask(null /* date */, timeout /* offset */);
					break;
				case UPON_SEEING_NO_MORE_NEW_TAG_OBSERVATIONS_FOR_T_MS_OR_TIMEOUT: // 1
				case UPON_SEEING_NO_MORE_NEW_UNIQUE_TAG_OBSERVATIONS_FOR_T_MS_OR_TIMEOUT: // 4
					setTimerTask(null /* date */, (timeout < st.getTagOT().getT()) ? timeout
							: st.getTagOT().getT() /* offset */);
					break;
				}
			}
			break;
		}
		triggerStartDate = new Date();
	}
}
