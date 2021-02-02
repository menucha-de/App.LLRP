package havis.llrpservice.server.service;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.testng.annotations.Test;

import havis.device.rf.tag.TagData;
import havis.llrpservice.data.message.parameter.AccessSpec;
import havis.llrpservice.data.message.parameter.ProtocolId;
import havis.llrpservice.data.message.parameter.ROSpec;
import havis.llrpservice.data.message.parameter.ROSpecCurrentState;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.sbc.rfc.RFCException;
import havis.llrpservice.sbc.rfc.message.ExecuteResponse;
import havis.llrpservice.sbc.rfc.message.MessageHeader;
import havis.llrpservice.server.event.Event;
import havis.llrpservice.server.event.EventQueue;
import havis.llrpservice.server.event.EventQueueListener;
import havis.llrpservice.server.event.EventType;
import havis.llrpservice.server.event.RFCMessageEvent;
import havis.llrpservice.server.rfc.UnsupportedAccessOperationException;
import havis.llrpservice.server.rfc.UnsupportedAirProtocolException;
import havis.llrpservice.server.rfc.UnsupportedSpecTypeException;
import havis.llrpservice.server.rfc.messageData.ExecuteResponseData;
import mockit.Deencapsulation;
import mockit.Delegate;
import mockit.Mocked;
import mockit.NonStrictExpectations;
import mockit.Verifications;

public class InstanceCleanupTest {

	@Test
	@SuppressWarnings("unchecked")	
	public void cleanUp(@Mocked final ROSpecsManager roSpecsManager,
			@Mocked final AccessSpecsManager accessSpecsManager,
			@Mocked final ROAccessReportDepot reportDepot,
			@Mocked final EventQueueListener eventQueueListener) throws Exception {
		// create ROSpecs: the first ROSpec is disabled, the second one can be
		// canceled directly and the third ROSpec requires the waiting for the
		// execute response
		final List<ROSpec> roSpecs = new ArrayList<>();
		final long roSpecId1 = 1;
		roSpecs.add(new ROSpec(new TLVParameterHeader((byte) 0/* reserved */), roSpecId1,
				(short) 2 /* priority */, ROSpecCurrentState.DISABLED /* currentState */,
				null /* roBoundarySpec */, null /* specList */));
		final long roSpecId2 = 2;
		roSpecs.add(new ROSpec(new TLVParameterHeader((byte) 0/* reserved */), roSpecId2,
				(short) 2 /* priority */, ROSpecCurrentState.ACTIVE /* currentState */,
				null /* roBoundarySpec */, null /* specList */));
		final long roSpecId3 = 3;
		roSpecs.add(new ROSpec(new TLVParameterHeader((byte) 0/* reserved */), roSpecId3,
				(short) 2 /* priority */, ROSpecCurrentState.ACTIVE /* currentState */,
				null /* roBoundarySpec */, null /* specList */));

		// create an AccessSpec
		final List<AccessSpec> accessSpecs = new ArrayList<>();
		accessSpecs.add(new AccessSpec());

		// create entity ids
		final List<String> entityIds = new ArrayList<>();
		entityIds.add("huhu");

		// create event queue and add a listener
		final EventQueue eventQueue = new EventQueue();
		eventQueue.addListener(eventQueueListener,
				Arrays.asList(new EventType[] { EventType.RFC_MESSAGE }));
		final List<Long> pendingROSpecIds = new ArrayList<>();
		// add an execute response for the first ROSpec to the queue
		final RFCMessageEvent executeResponse1 = new RFCMessageEvent(
				new ExecuteResponse(new MessageHeader(3 /* id */), new ArrayList<TagData>(),
						null /* timeStamp */),
				new ExecuteResponseData(roSpecId1, 0 /* specIndex */, 0 /* invParamSpecId */,
						0 /* antennaId */, ProtocolId.EPC_GLOBAL_C1G2,
						null /* tagDataAccessSpecIds */, true /* isLastResponse */));
		eventQueue.put(executeResponse1);
		// add first ROSpec to list of pending ROSpecs
		pendingROSpecIds.add(roSpecId1);
		// add an unused event to the queue
		final RFCMessageEvent executeResponse2 = new RFCMessageEvent(
				new ExecuteResponse(new MessageHeader(4 /* id */), new ArrayList<TagData>(),
						null /* timeStamp */),
				new ExecuteResponseData(568 /* roSpecId */, 0 /* specIndex */,
						0 /* invParamSpecId */, 0 /* antennaId */, ProtocolId.EPC_GLOBAL_C1G2,
						null /* tagDataAccessSpecIds */, true /* isLastResponse */));
		eventQueue.put(executeResponse2);
		// create an execute response for the third ROSpec (the message is added
		// to the queue later)
		final RFCMessageEvent executeResponse3 = new RFCMessageEvent(
				new ExecuteResponse(new MessageHeader(5 /* id */), new ArrayList<TagData>(),
						null /* timeStamp */),
				new ExecuteResponseData(roSpecId3, 0 /* specIndex */, 0 /* antennaId */,
						0 /* invParamSpecId */, ProtocolId.EPC_GLOBAL_C1G2,
						null /* tagDataAccessSpecIds */, true /* isLastResponse */));

		new NonStrictExpectations() {
			{
				roSpecsManager.getROSpecs();
				result = roSpecs;

				roSpecsManager.getPendingROSpecIds();
				result = pendingROSpecIds;

				roSpecsManager.setState(roSpecId3, ROSpecCurrentState.DISABLED);
				result = new Delegate<ROSpecsManager>() {
					@SuppressWarnings("unused")
					public void setState(long roSpecId, ROSpecCurrentState newState)
							throws RFCException, UnsupportedSpecTypeException,
							UnsupportedAccessOperationException, UnsupportedAirProtocolException {
						if (roSpecId == roSpecId3 && newState == ROSpecCurrentState.DISABLED) {
							// add ROSpec3 to list of pending ROSpecs
							pendingROSpecIds.add(roSpecId);
							// add an execute response to the queue
							eventQueue.put(executeResponse3);
						}
					}
				};

				accessSpecsManager.getAccessSpecs();
				result = accessSpecs;

				reportDepot.getEntityIds();
				result = entityIds;
			}
		};

		final InstanceCleanup cleanUp = new InstanceCleanup(roSpecsManager, accessSpecsManager,
				reportDepot, eventQueue);
		cleanUp.cleanUp(3000);
		new Verifications() {
			{
				// the execute responses for the first and third ROSpec and the
				// unused message have been removed from the event queue
				List<Event> events = new ArrayList<>();
				eventQueueListener.removed(eventQueue, withCapture(events));
				times = 3;
				assertEquals(events.get(0), executeResponse1);
				assertEquals(events.get(1), executeResponse2);
				assertEquals(events.get(2), executeResponse3);

				// all ROSpec executions have been finished
				roSpecsManager.executionResponseReceived(anyLong, withInstanceOf(List.class),
						anyBoolean);
				times = 3;

				// all ROSpecs have been removed from the manager
				roSpecsManager.remove(anyLong);
				times = roSpecs.size();

				// all AccessSpecs have been removed from the manager
				accessSpecsManager.remove(anyLong);
				times = accessSpecs.size();

				// all entities have been removed from the report depot
				List<String> ids;
				reportDepot.remove(ids = withCapture());
				times = 1;
				assertEquals(ids, entityIds);
			}
		};
		// the event queue is empty
		try {
			eventQueue.take(1);
			fail();
		} catch (TimeoutException e) {
		}
	}

	@Test
	public void cleanUpError(@Mocked final ROSpecsManager roSpecsManager,
			@Mocked final AccessSpecsManager accessSpecsManager,
			@Mocked final ROAccessReportDepot reportDepot, final @Mocked Logger log)
			throws Exception {
		// create a disabled ROSpec
		final List<ROSpec> roSpecs = new ArrayList<>();
		final long roSpecId1 = 1;
		roSpecs.add(new ROSpec(new TLVParameterHeader((byte) 0/* reserved */), roSpecId1,
				(short) 2 /* priority */, ROSpecCurrentState.DISABLED /* currentState */,
				null /* roBoundarySpec */, null /* specList */));
		// add ROSpec to list of pending ROSpecs but do not enqueue an execute
		// response
		final List<Long> pendingROSpecIds = new ArrayList<>();
		pendingROSpecIds.add(roSpecId1);

		new NonStrictExpectations() {
			{
				roSpecsManager.getROSpecs();
				result = roSpecs;

				roSpecsManager.getPendingROSpecIds();
				result = pendingROSpecIds;
			}
		};
		InstanceCleanup cleanUp = new InstanceCleanup(roSpecsManager, accessSpecsManager,
				reportDepot, new EventQueue());
		Logger origLog = Deencapsulation.getField(cleanUp, "log");
		Deencapsulation.setField(cleanUp, "log", log);

		cleanUp.cleanUp(1000);

		new Verifications() {
			{
				// The first exception is logged (waiting for IO operation).
				log.log(Level.WARNING, "Could not collect all RFC messages",
						withInstanceOf(TimeoutException.class));
				times = 1;

				// all ROSpec executions have been finished
				roSpecsManager.executionResponseReceived(anyLong, new ArrayList<TagData>(),
						true /* isLastResponse */);
				times = 1;

				// all ROSpecs have been removed from the manager
				roSpecsManager.remove(anyLong);
				times = roSpecs.size();
			}
		};

		Deencapsulation.setField(cleanUp, "log", origLog);
	}
}
