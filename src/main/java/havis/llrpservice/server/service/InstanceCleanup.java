package havis.llrpservice.server.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import havis.device.rf.tag.TagData;
import havis.llrpservice.common.entityManager.EntityManagerException;
import havis.llrpservice.data.message.parameter.AccessSpec;
import havis.llrpservice.data.message.parameter.ROSpec;
import havis.llrpservice.data.message.parameter.ROSpecCurrentState;
import havis.llrpservice.sbc.rfc.RFCException;
import havis.llrpservice.sbc.rfc.message.ExecuteResponse;
import havis.llrpservice.sbc.rfc.message.MessageType;
import havis.llrpservice.server.event.Event;
import havis.llrpservice.server.event.EventQueue;
import havis.llrpservice.server.event.EventType;
import havis.llrpservice.server.event.RFCMessageEvent;
import havis.llrpservice.server.rfc.UnsupportedAccessOperationException;
import havis.llrpservice.server.rfc.UnsupportedAirProtocolException;
import havis.llrpservice.server.rfc.UnsupportedSpecTypeException;
import havis.llrpservice.server.rfc.messageData.ExecuteResponseData;

/**
 * Cleans up data container from {@link LLRPServiceInstance}.
 * <p>
 * Active ROSpec executions are canceled. If executions cannot be canceled
 * directly then the {@link #cleanUp(long)} method waits for the execute
 * response events at the event queue.
 * </p>
 */
public class InstanceCleanup {

	private static final Logger log = Logger.getLogger(InstanceCleanup.class.getName());

	private final ROSpecsManager roSpecsManager;
	private final AccessSpecsManager accessSpecsManager;
	private final ROAccessReportDepot reportDepot;
	private final EventQueue queue;

	public InstanceCleanup(ROSpecsManager roSpecsManager, AccessSpecsManager accessSpecsManager,
			ROAccessReportDepot reportDepot, EventQueue queue) {
		this.roSpecsManager = roSpecsManager;
		this.accessSpecsManager = accessSpecsManager;
		this.reportDepot = reportDepot;
		this.queue = queue;
	}

	/**
	 * Cleans up the data container (see
	 * {@link #InstanceCleanup(ROSpecsManager, AccessSpecsManager, ROAccessReportDepot, EventQueue)}
	 * ).
	 * <p>
	 * Active ROSpec executions are canceled. If executions cannot be canceled
	 * directly then this method waits for the execute response events at the
	 * event queue.
	 * </p>
	 * 
	 * @param timeout
	 *            <ul>
	 *            <li>&lt;= 0: wait until an event is queued (see
	 *            {@link #NO_TIMEOUT})
	 *            <li>&gt; 0: wait until an event is queued or the specified
	 *            waiting time elapses (in milliseconds)
	 *            </ul>
	 * @throws InterruptedException
	 * @throws RFCException
	 * @throws UnsupportedSpecTypeException
	 * @throws UnsupportedAccessOperationException
	 * @throws UnsupportedAirProtocolException
	 * @throws EntityManagerException
	 * @throws InvalidIdentifierException
	 * @throws UtcClockException
	 */
	public void cleanUp(long timeout) throws InterruptedException, TimeoutException, RFCException,
			UnsupportedSpecTypeException, UnsupportedAccessOperationException,
			UnsupportedAirProtocolException, EntityManagerException, InvalidIdentifierException,
			UtcClockException {
		if (log.isLoggable(Level.INFO)) {
			log.log(Level.INFO, "Cleaning up server instance (up to " + timeout + " ms)");
		}
		long endTime = System.currentTimeMillis() + timeout;
		// for each ROSpec
		for (ROSpec roSpec : roSpecsManager.getROSpecs()) {
			long roSpecId = roSpec.getRoSpecID();
			// if the ROSpec is NOT disabled
			if (ROSpecCurrentState.DISABLED != roSpec.getCurrentState()) {
				// disable the ROSpec (a running execution is cancelled)
				roSpecsManager.setState(roSpecId, ROSpecCurrentState.DISABLED);
			}
		}
		// get pending roSpecIds
		List<Long> pendingROSpecIds = roSpecsManager.getPendingROSpecIds();
		try {
			// for each missing execute response
			while (!pendingROSpecIds.isEmpty()) {
				// get next event
				long remainingTimeout = timeout;
				// if a timeout is set
				if (timeout > 0) {
					remainingTimeout = endTime - System.currentTimeMillis();
					if (remainingTimeout <= 0) {
						throw new TimeoutException(
								String.format("Time out after %d ms while waiting for RFC message",
										timeout - remainingTimeout));
					}
				}
				Event event = null;
				try {
					event = queue.take(remainingTimeout);
				} catch (TimeoutException e) {
					remainingTimeout = endTime - System.currentTimeMillis();
					throw new TimeoutException(
							String.format("Time out after %d ms while waiting for RFC message",
									timeout - remainingTimeout));
				}
				// if RFC message with execute response
				if (EventType.RFC_MESSAGE == event.getEventType()) {
					RFCMessageEvent rfcMessageEvent = (RFCMessageEvent) event;
					if (MessageType.EXECUTE_RESPONSE == rfcMessageEvent.getMessage()
							.getMessageHeader().getMessageType()) {
						ExecuteResponse executeResponse = (ExecuteResponse) rfcMessageEvent
								.getMessage();
						ExecuteResponseData executeResponseData = (ExecuteResponseData) rfcMessageEvent
								.getData();
						roSpecsManager.executionResponseReceived(executeResponseData.getRoSpecId(),
								executeResponse.getTagData(), true /* isLastResponse */);
						if (executeResponseData.isLastResponse()) {
							pendingROSpecIds.remove(executeResponseData.getRoSpecId());
						}
					}
				}
			}
		} catch (TimeoutException e) {
			// the response was not sent eg. due to an exception in RF
			// controller
			log.log(Level.WARNING, "Could not collect all RFC messages", e);
			// finish the remaining ROSpec executions
			for (Long roSpecId : pendingROSpecIds) {
				roSpecsManager.executionResponseReceived(roSpecId, new ArrayList<TagData>(),
						true /* isLastResponse */);
			}
		}
		// clear the ROSpecsManager
		for (ROSpec roSpec : roSpecsManager.getROSpecs()) {
			roSpecsManager.remove(roSpec.getRoSpecID());
		}
		// clear the AccessSpecsManager
		for (AccessSpec accessSpec : accessSpecsManager.getAccessSpecs()) {
			accessSpecsManager.remove(accessSpec.getAccessSpecId());
		}
		// clear the report depot
		reportDepot.remove(reportDepot.getEntityIds());
		// clear queue
		queue.clear();
	}
}
