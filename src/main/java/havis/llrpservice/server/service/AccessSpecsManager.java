package havis.llrpservice.server.service;

import havis.llrpservice.data.message.parameter.AccessSpec;
import havis.llrpservice.server.rfc.RFCMessageHandler;
import havis.llrpservice.server.rfc.RFCMessageHandlerListener;
import havis.llrpservice.server.rfc.UnsupportedAirProtocolException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.rits.cloning.Cloner;

/**
 * Manages AccessSpecs. If an AccessSpec is enabled then a copy of the
 * AccessSpec is added to the {@link RFCMessageHandler}. If it is disabled then
 * it is removed from the {@link RFCMessageHandler}.
 */
public class AccessSpecsManager {
	private final RFCMessageHandler rfcMessageHandler;
	// rfcMessageHandler runs in another thread => the access to the local lists
	// must be synchronized if an event is received
	private final Object lock = new Object();
	private final Map<Long, AccessSpec> accessSpecs = new HashMap<>();
	private final List<AccessSpec> accessSpecList = new ArrayList<>();

	public AccessSpecsManager(RFCMessageHandler rfcMessageHandler) {
		this.rfcMessageHandler = rfcMessageHandler;
		// register as listener for changes of accessSpec list
		// (eg. an AccessSpec is removed if the max. operation count has
		// reached)
		rfcMessageHandler.addListener(new RFCMessageHandlerListener() {

			@Override
			public void removedAccessSpec(long accessSpecId) {
				synchronized (lock) {
					// remove AccessSpec from local list
					AccessSpec accessSpec = accessSpecs.remove(accessSpecId);
					if (accessSpec != null) {
						accessSpecList.remove(accessSpec);
					}
				}
			}

			@Override
			public void opened() {
			}

			@Override
			public void closed(List<Object> pendingRequests, Throwable t) {
			}
		});
	}

	/**
	 * Adds an AccessSpec. If it is enabled then a copy of the AccessSpec is
	 * added to the {@link RFCMessageHandler}.
	 * 
	 * @param accessSpec
	 * @throws UnsupportedAirProtocolException
	 * @throws InvalidIdentifierException
	 */
	public void add(AccessSpec accessSpec)
			throws UnsupportedAirProtocolException, InvalidIdentifierException {
		synchronized (lock) {
			if (accessSpecs.get(accessSpec.getAccessSpecId()) != null) {
				throw new InvalidIdentifierException("AccessSpec with identifier "
						+ accessSpec.getAccessSpecId() + " already exists");
			}
			accessSpecs.put(accessSpec.getAccessSpecId(), accessSpec);
			accessSpecList.add(accessSpec);
			// if the AccessSpec is already enabled
			if (accessSpec.isCurrentState()) {
				// enable it by using "setState" method
				accessSpec.setCurrentState(false);
				setState(accessSpec.getAccessSpecId(), true /* enabled */);
			}
		}
	}

	/**
	 * Removes an AccessSpec. An enabled AccessSpec is disabled and removed from
	 * the {@link RFCMessageHandler}.
	 * 
	 * @param accessSpecId
	 *            0: All AccessSpecs are removed
	 * @return The removed AccessSpec
	 * @throws UnsupportedAirProtocolException
	 * @throws InvalidIdentifierException
	 */
	public List<AccessSpec> remove(long accessSpecId)
			throws UnsupportedAirProtocolException, InvalidIdentifierException {
		synchronized (lock) {
			List<AccessSpec> specs = new ArrayList<>();
			if (0 == accessSpecId) {
				specs.addAll(accessSpecList);
			} else {
				AccessSpec accessSpec = accessSpecs.get(accessSpecId);
				if (accessSpec == null) {
					throw new InvalidIdentifierException(
							"Missing AccessSpec with identifier " + accessSpecId);
				}
				specs.add(accessSpec);
			}
			for (AccessSpec accessSpec : specs) {
				// if the AccessSpec is enabled
				if (accessSpec.isCurrentState()) {
					// disable the AccessSpec
					setState(accessSpecId, false /* enabled */);
				}
				accessSpecs.remove(accessSpec.getAccessSpecId());
				accessSpecList.remove(accessSpec);
			}
			return specs;
		}
	}

	/**
	 * Sets the state of the AccessSpec. If it is enabled then a copy of the
	 * AccessSpec is added to the {@link RFCMessageHandler}. If it is disabled
	 * then it is removed from the {@link RFCMessageHandler}.
	 * 
	 * @param accessSpecId
	 *            0: All AccessSpecs are enabled/disabled
	 * @param enabled
	 * @throws UnsupportedAirProtocolException
	 * @throws InvalidIdentifierException
	 */
	public void setState(long accessSpecId, boolean enabled)
			throws UnsupportedAirProtocolException, InvalidIdentifierException {
		synchronized (lock) {
			List<AccessSpec> specs = new ArrayList<>();
			if (0 == accessSpecId) {
				specs.addAll(accessSpecList);
			} else {
				AccessSpec accessSpec = accessSpecs.get(accessSpecId);
				if (accessSpec == null) {
					throw new InvalidIdentifierException(
							"Missing AccessSpec with identifier " + accessSpecId);
				}
				specs.add(accessSpec);
			}
			for (AccessSpec accessSpec : specs) {
				if (enabled) {
					// if the AccessSpec is disabled
					if (!accessSpec.isCurrentState()) {
						// enable the AccessSpec and add it to the RFC message
						// handler
						accessSpec.setCurrentState(true);
						rfcMessageHandler.add(new Cloner().deepClone(accessSpec));
					}
				} else if (accessSpec.isCurrentState()) {
					// remove AccessSpec from RFC message handler
					rfcMessageHandler.remove(accessSpec.getAccessSpecId());
					// disable the AccessSpec
					accessSpec.setCurrentState(false);
				}
			}
		}
	}

	/**
	 * Gets all added AccessSpecs in the same order as they have been added.
	 * 
	 * @return The AccessSpecs
	 */
	public List<AccessSpec> getAccessSpecs() {
		synchronized (lock) {
			return new ArrayList<>(accessSpecList);
		}
	}

}
