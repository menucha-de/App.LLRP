package havis.llrpservice.server.rfc;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import havis.device.rf.tag.TagData;
import havis.llrpservice.data.message.parameter.AISpec;
import havis.llrpservice.data.message.parameter.AccessSpec;
import havis.llrpservice.data.message.parameter.GPIEvent;
import havis.llrpservice.data.message.parameter.Parameter;
import havis.llrpservice.data.message.parameter.ParameterTypes.ParameterType;
import havis.llrpservice.data.message.parameter.ProtocolId;
import havis.llrpservice.data.message.parameter.ROSpec;
import havis.llrpservice.sbc.rfc.RFCClientMultiplexed;
import havis.llrpservice.sbc.rfc.RFCException;
import havis.llrpservice.sbc.rfc.message.GetOperations;
import havis.llrpservice.sbc.rfc.message.GetOperationsResponse;
import havis.llrpservice.server.event.EventQueue;
import havis.llrpservice.server.rfc.AISpecExecutor.AISpecExecutorListener;

/**
 * The ROSpecExecutor handles the execution of an ROSpec.
 * <p>
 * A spec of the ROSpec can be started asynchronously with
 * {@link #startNextAction()}. The sent and received messages to/from the RF
 * controller are added to an event queue.
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
 * An ROSpec must be stopped with {@link #stop()} to clean up resources like
 * timers for stop triggers.
 * </p>
 * <p>
 * For working stop triggers the methods
 * {@link #executionResponseReceived(long, List)} and
 * {@link #gpiEventReceived(GPIEvent)} must be called by the client. If a
 * trigger stops an ROSpec then {@link ROSpecExecutorListener#isStopped(ROSpec)}
 * is called for each registered listener. A client can register a listener with
 * {@link #addListener(ROSpecExecutorListener)} /
 * {@link #removeListener(ROSpecExecutorListener)}.
 * </p>
 */
public class ROSpecExecutor {

	private final ROSpec roSpec;
	private final AISpecExecutor aiSpecExecutor;
	private final RuntimeData runtimeData;
	private final List<ROSpecExecutorListener> listeners = new ArrayList<>();

	private final Object lock = new Object();

	public interface ROSpecExecutorListener {
		void isStopped(ROSpec roSpec, ROSpecExecutionPosition pos);
	}

	static class ROSpecExecutionPosition {
		private long roSpecId;
		private int specIndex = -1;

		// set by AISpecExecutor
		private int antennaId = -1;
		private ProtocolId protocolId = null;
		private int inventoryParameterSpecId = -1;

		// set by AccessSpecExecutor
		private Long defaultAccessSpecId;
		// tagDataId -> accessSpecId
		private Map<Long, Long> tagDataAccessSpecIds = new HashMap<>();

		ROSpecExecutionPosition(long roSpecId) {
			this.roSpecId = roSpecId;
		}

		long getRoSpecId() {
			return roSpecId;
		}

		int getSpecIndex() {
			return specIndex;
		}

		int getAntennaId() {
			return antennaId;
		}

		void setAntennaId(int antennaId) {
			this.antennaId = antennaId;
		}

		ProtocolId getProtocolId() {
			return protocolId;
		}

		void setProtocolId(ProtocolId protocolId) {
			this.protocolId = protocolId;
		}

		int getInventoryParameterSpecId() {
			return inventoryParameterSpecId;
		}

		void setInventoryParameterSpecId(int invParamSpecId) {
			this.inventoryParameterSpecId = invParamSpecId;
		}

		public Long getDefaultAccessSpecId() {
			return defaultAccessSpecId;
		}

		public void setDefaultAccessSpecId(Long defaultAccessSpecId) {
			this.defaultAccessSpecId = defaultAccessSpecId;
		}

		/**
		 * Returns a map of TagDataIds to AccessSpecIds. If no AccessSpecId
		 * exists for a TagDataId then the default AccessSpec has been used (see
		 * {@link #getDefaultAccessSpecId()}).
		 * 
		 * @return
		 */
		public Map<Long, Long> getTagDataAccessSpecIds() {
			return tagDataAccessSpecIds;
		}
	}

	/**
	 * Holds runtime data for an ROSpec execution.
	 */
	private class RuntimeData {
		ROSpecExecutionPosition pos;
		boolean isSpecRunning;
	}

	public ROSpecExecutor(final ROSpec roSpec, RFCClientMultiplexed rfcClient,
			SocketChannel rfcChannel, EventQueue eventQueue) {
		this.roSpec = roSpec;
		aiSpecExecutor = new AISpecExecutor(rfcClient, rfcChannel, eventQueue);
		aiSpecExecutor.addListener(new AISpecExecutorListener() {

			@Override
			public void isStopped(AISpec aiSpec) {
				synchronized (lock) {
					// if ROSpec has already been stopped
					if (runtimeData.pos.specIndex < 0) {
						return;
					}
					// if the last spec of the ROSpec has been processed
					if (runtimeData.pos.specIndex == roSpec.getSpecList().size()) {
						// stop ROSpec
						stop();
					} else {
						// allow the execution of the next spec
						runtimeData.isSpecRunning = false;
					}
				}
			}
		});
		runtimeData = new RuntimeData();
		resetRuntimeData();
	}

	/**
	 * Adds a listener;
	 * 
	 * @param listener
	 */
	public void addListener(ROSpecExecutorListener listener) {
		listeners.add(listener);
	}

	/**
	 * Removes a listener.
	 * 
	 * @param listener
	 */
	public void removeListener(ROSpecExecutorListener listener) {
		listeners.remove(listener);
	}

	/**
	 * Adds a listener for AccessSpecs.
	 * 
	 * @param listener
	 */
	public void addListener(AccessSpecsListener listener) {
		aiSpecExecutor.addListener(listener);
	}

	/**
	 * Removes a listener for AccessSpecs.
	 * 
	 * @param listener
	 */
	public void removeListener(AccessSpecsListener listener) {
		aiSpecExecutor.removeListener(listener);
	}

	/**
	 * Adds an AccessSpec. The AccessSpec must be enabled.
	 * 
	 * @param accessSpec
	 * @throws UnsupportedAirProtocolException
	 */
	public void add(AccessSpec accessSpec) throws UnsupportedAirProtocolException {
		aiSpecExecutor.add(accessSpec);
	}

	/**
	 * Removes an AccessSpec.
	 * 
	 * @param accessSpecId
	 * @return The removed AccessSpec
	 */
	public AccessSpec remove(long accessSpecId) {
		return aiSpecExecutor.remove(accessSpecId);
	}

	public void executionResponseReceived(List<TagData> tagData) {
		synchronized (lock) {
			// if ROSpec has already been stopped
			if (runtimeData.pos.specIndex < 0) {
				return;
			}
			aiSpecExecutor.executionResponseReceived(tagData);
		}
	}

	public void gpiEventReceived(GPIEvent event) {
		synchronized (lock) {
			// if ROSpec has already been stopped
			if (runtimeData.pos.specIndex < 0) {
				return;
			}
			aiSpecExecutor.gpiEventReceived(event);
		}
	}

	/**
	 * Starts the next action of the ROSpec eg. an inventory for an AISpec.
	 * 
	 * @throws UnsupportedSpecTypeException
	 * @throws UnsupportedAirProtocolException
	 * @throws RFCException
	 * @throws UnsupportedAccessOperationException
	 */
	public void startNextAction() throws UnsupportedSpecTypeException,
			UnsupportedAccessOperationException, RFCException, UnsupportedAirProtocolException {
		synchronized (lock) {
			List<Parameter> specs = roSpec.getSpecList();
			if (!runtimeData.isSpecRunning) {
				// if first call
				if (runtimeData.pos.specIndex < 0) {
					// throw an exception if the ROSpec contains unsupported
					// specs
					for (Parameter spec : specs) {
						ParameterType specType = spec.getParameterHeader().getParameterType();
						switch (specType) {
						case AI_SPEC:
							break;
						default:
							throw new UnsupportedSpecTypeException(
									"Unsupported spec type: " + specType);
						}
					}
					runtimeData.pos.specIndex = 1;
				} else {
					// use next spec
					runtimeData.pos.specIndex++;
				}
				runtimeData.isSpecRunning = true;
			}
			// execute spec
			Parameter spec = specs.get(runtimeData.pos.specIndex - 1);
			switch (spec.getParameterHeader().getParameterType()) {
			case AI_SPEC:
				aiSpecExecutor.startNextInventory(runtimeData.pos, (AISpec) spec);
				break;
			default:
				break;
			}
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
			aiSpecExecutor.startNextAccessOps(getOps);
		}
	}

	/**
	 * Returns the current execution position.
	 * 
	 * @return The current execution position
	 */
	public ROSpecExecutionPosition getExecutionPosition() {
		synchronized (lock) {
			return runtimeData.pos;
		}
	}

	public void stop() {
		ROSpecExecutionPosition pos;
		synchronized (lock) {
			// if ROSpec has already been stopped
			if (runtimeData.pos.specIndex < 0) {
				return;
			}
			// reset runtime data
			pos = runtimeData.pos;
			resetRuntimeData();
			// stop AISpec executor
			aiSpecExecutor.stop();
		}
		// fire events
		for (ROSpecExecutorListener listener : listeners) {
			listener.isStopped(roSpec, pos);
		}
	}

	private void resetRuntimeData() {
		runtimeData.pos = new ROSpecExecutionPosition(roSpec.getRoSpecID());
		runtimeData.isSpecRunning = false;
	}
}
