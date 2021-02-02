package havis.llrpservice.server.service;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.testng.annotations.Test;

import havis.device.rf.tag.TagData;
import havis.llrpservice.data.message.parameter.GPIEvent;
import havis.llrpservice.data.message.parameter.GPITriggerValue;
import havis.llrpservice.data.message.parameter.PeriodicTriggerValue;
import havis.llrpservice.data.message.parameter.ROBoundarySpec;
import havis.llrpservice.data.message.parameter.ROReportSpec;
import havis.llrpservice.data.message.parameter.ROSpec;
import havis.llrpservice.data.message.parameter.ROSpecCurrentState;
import havis.llrpservice.data.message.parameter.ROSpecEvent;
import havis.llrpservice.data.message.parameter.ROSpecEventType;
import havis.llrpservice.data.message.parameter.ROSpecStartTrigger;
import havis.llrpservice.data.message.parameter.ROSpecStartTriggerType;
import havis.llrpservice.data.message.parameter.ROSpecStopTrigger;
import havis.llrpservice.data.message.parameter.ROSpecStopTriggerType;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.data.message.parameter.UTCTimestamp;
import havis.llrpservice.server.rfc.RFCMessageHandler;
import havis.llrpservice.server.service.ROSpecsManager.ROSpecsManagerListener;
import mockit.Mocked;
import mockit.Verifications;

public class ROSpecsManagerTest {

	@Test
	public void getROSpecs(@Mocked final RFCMessageHandler rfcMessageHandler) throws Exception {
		// add ROSpecs
		final ROSpec roSpec1 = new ROSpec(new TLVParameterHeader((byte) 0), 2 /* roSpecID */,
				(short) 4 /* priority */, ROSpecCurrentState.DISABLED, null /* ROBoundarySpec */,
				null/* specList */);
		final ROSpec roSpec2 = new ROSpec(new TLVParameterHeader((byte) 0), 3 /* roSpecID */,
				(short) 4 /* priority */, ROSpecCurrentState.DISABLED, null /* ROBoundarySpec */,
				null/* specList */);
		final ROSpec roSpec3 = new ROSpec(new TLVParameterHeader((byte) 0), 4 /* roSpecID */,
				(short) 4 /* priority */, ROSpecCurrentState.DISABLED, null /* ROBoundarySpec */,
				null/* specList */);
		ROSpecsManager rm = new ROSpecsManager(rfcMessageHandler, false /* hasUTCClock */);
		rm.add(roSpec1);
		rm.add(roSpec2);
		rm.add(roSpec3);
		// the ROSpecs must be returned in the same order as they have been
		// added
		List<ROSpec> roSpecs = rm.getROSpecs();
		assertEquals(roSpecs.size(), 3);
		assertEquals(roSpecs.get(0).getRoSpecID(), 2);
		assertEquals(roSpecs.get(1).getRoSpecID(), 3);
		assertEquals(roSpecs.get(2).getRoSpecID(), 4);
	}

	// @Mocked
	// RFCMessageHandler rfcMessageHandler;

	@Test
	public void addRemove(//
			@Mocked final RFCMessageHandler rfcMessageHandler//
	) throws Exception {
		final List<ROSpecEvent> events = new ArrayList<>();
		// add a disabled ROSpec with start trigger "immediate"
		ROBoundarySpec roBoundarySpec = new ROBoundarySpec(
				new TLVParameterHeader((byte) 0 /* reserverd */),
				new ROSpecStartTrigger(new TLVParameterHeader((byte) 0 /* reserved */),
						ROSpecStartTriggerType.IMMEDIATE),
				new ROSpecStopTrigger(new TLVParameterHeader((byte) 0 /* reserved */),
						ROSpecStopTriggerType.NULL, 0 /* durationTriggerValue */));
		final ROSpec roSpec1 = new ROSpec(new TLVParameterHeader((byte) 0), 1 /* roSpecID */,
				(short) 1 /* priority */, ROSpecCurrentState.DISABLED, roBoundarySpec,
				null/* specList */);

		ROSpecsManager rm = new ROSpecsManager(rfcMessageHandler, false /* hasUTCClock */);
		ROSpecsManagerListener listener = new ROSpecsManagerListener() {

			@Override
			public void executionChanged(ROSpecEvent event) {
				events.add(event);
			}
		};
		rm.addListener(listener);

		// add a disabled ROSpec
		rm.add(roSpec1);
		// the "getROSpec" method returns the ROSpec
		assertEquals(rm.getROSpecs().size(), 1);
		// no event has been fired
		assertTrue(events.isEmpty());

		// try to add the same ROSpec again
		try {
			rm.add(roSpec1);
			fail();
		} catch (InvalidIdentifierException e) {
			assertTrue(e.getMessage().contains("already exists"));
		}

		// remove the ROSpec
		assertEquals(rm.remove(roSpec1.getRoSpecID()).get(0), roSpec1);
		// the ROSpec list is empty
		assertEquals(rm.getROSpecs().size(), 0);
		// no event has been fired
		assertTrue(events.isEmpty());

		// add an activated ROSpec
		roSpec1.setCurrentState(ROSpecCurrentState.ACTIVE);
		rm.add(roSpec1);
		new Verifications() {
			{
				// a copy of the ROSpec is added to the RFC message handler
				rfcMessageHandler.requestExecution(roSpec1);
				times = 0;
				ROSpec rs;
				rfcMessageHandler.requestExecution(rs = withCapture());
				times = 1;
				assertEquals(rs.getRoSpecID(), roSpec1.getRoSpecID());
				// a "start" ROSpec event is sent
				ROSpecEvent event = events.get(0);
				assertEquals(event.getEventType(), ROSpecEventType.START_OF_ROSPEC);
				assertEquals(event.getRoSpecID(), roSpec1.getRoSpecID());
				assertEquals(event.getPreemptingROSpecID(), 0);
				events.clear();
			}
		};

		// remove the activated ROSpec
		List<ROSpec> removedROSpecs = rm.remove(roSpec1.getRoSpecID());
		assertEquals(removedROSpecs.get(0), roSpec1);
		// add a second activated ROSpec with same priority before execution of
		// first ROSpec is finished
		final ROSpec roSpec2 = new ROSpec(new TLVParameterHeader((byte) 0), 2 /* roSpecID */,
				(short) 1 /* priority */, ROSpecCurrentState.ACTIVE, roBoundarySpec,
				null/* specList */);
		rm.add(roSpec2);
		assertEquals(rm.getROSpecs().size(), 2);
		new Verifications() {
			{
				// the execution of the first ROSpec is canceled directly
				rfcMessageHandler.cancelExecution(roSpec1.getRoSpecID());
				times = 1;
				// no ROSpec event is sent because the first ROSpec has not been
				// finished yet
				assertEquals(events.size(), 0);
			}
		};
		// finish the execution of first ROSpec with an execution response
		rm.executionResponseReceived(roSpec1.getRoSpecID(), new ArrayList<TagData>(),
				true /* isLastResponse */);
		new Verifications() {
			{
				// "end" + "start" ROSpec events are sent
				assertEquals(2, events.size());
				ROSpecEvent event = events.get(0);
				assertEquals(event.getEventType(), ROSpecEventType.END_OF_ROSPEC);
				assertEquals(event.getRoSpecID(), roSpec1.getRoSpecID());
				assertEquals(event.getPreemptingROSpecID(), 0);
				event = events.get(1);
				assertEquals(event.getEventType(), ROSpecEventType.START_OF_ROSPEC);
				assertEquals(event.getRoSpecID(), roSpec2.getRoSpecID());
				assertEquals(event.getPreemptingROSpecID(), 0);
				events.clear();
			}
		};
		// the second ROSpec is active
		assertEquals(rm.getROSpecs().size(), 1);
		// remove the second ROSpec
		removedROSpecs = rm.remove(roSpec2.getRoSpecID());
		assertEquals(removedROSpecs.get(0), roSpec2);
		// finish the execution of second ROSpec with an execution response
		rm.executionResponseReceived(roSpec2.getRoSpecID(), new ArrayList<TagData>(),
				true /* isLastResponse */);
		assertEquals(rm.getROSpecs().size(), 0);
		// an "end" event is sent
		assertEquals(1, events.size());
		ROSpecEvent event = events.get(0);
		assertEquals(event.getEventType(), ROSpecEventType.END_OF_ROSPEC);
		assertEquals(event.getRoSpecID(), roSpec2.getRoSpecID());
		assertEquals(event.getPreemptingROSpecID(), 0);
		events.clear();

		// remove the listener and add an activated ROSpec using start trigger
		// "immediate"
		rm.removeListener(listener);
		roSpec1.setCurrentState(ROSpecCurrentState.INACTIVE);
		rm.add(roSpec1);
		// no event is received
		assertTrue(events.isEmpty());
		// add listener again and add a further activated ROSpec with higher
		// priority
		rm.addListener(listener);
		ROSpec roSpec3 = new ROSpec(new TLVParameterHeader((byte) 0), 3 /* roSpecID */,
				(short) 0 /* priority */, ROSpecCurrentState.ACTIVE, null /* ROBoundarySpec */,
				null/* specList */);
		rm.add(roSpec3);
		// finish the execution of the first active ROSpec with an execution
		// response
		rm.executionResponseReceived(roSpec1.getRoSpecID(), new ArrayList<TagData>(),
				true /* isLastResponse */);
		// the first active ROSpec is preempted
		assertEquals(events.get(0).getEventType(), ROSpecEventType.PREEMPTION_OF_ROSPEC);
		events.clear();

		// remove all ROSpecs using ROSpecId 0
		assertEquals(rm.getROSpecs().size(), 2);
		removedROSpecs = rm.remove(0);
		assertEquals(removedROSpecs.size(), 2);
		assertEquals(removedROSpecs.get(0), roSpec1);
		assertEquals(removedROSpecs.get(1), roSpec3);
		assertEquals(rm.getROSpecs().size(), 1);
		// finish the execution of the active ROSpec with an execution response
		rm.executionResponseReceived(roSpec3.getRoSpecID(), new ArrayList<TagData>(),
				true /* isLastResponse */);
		// the ROSpec list is empty
		assertEquals(rm.getROSpecs().size(), 0);
	}

	@Test
	public void setState(@Mocked final RFCMessageHandler rfcMessageHandler,
			@Mocked final ROReportSpecsManager roReportSpecsManager,
			@Mocked final ROReportSpec roReportSpec) throws Exception {
		final List<ROSpecEvent> events = new ArrayList<>();
		ROBoundarySpec roBoundarySpec1 = new ROBoundarySpec(
				new TLVParameterHeader((byte) 0 /* reserverd */),
				new ROSpecStartTrigger(new TLVParameterHeader((byte) 0 /* reserved */),
						ROSpecStartTriggerType.NULL_NO_START_TRIGGER),
				new ROSpecStopTrigger(new TLVParameterHeader((byte) 0 /* reserved */),
						ROSpecStopTriggerType.NULL, 0 /* durationTriggerValue */));
		final ROSpec roSpec1 = new ROSpec(new TLVParameterHeader((byte) 0), 1 /* roSpecID */,
				(short) 5 /* priority */, ROSpecCurrentState.DISABLED, roBoundarySpec1,
				null/* specList */);
		roSpec1.setRoReportSpec(roReportSpec);
		ROBoundarySpec roBoundarySpec2 = new ROBoundarySpec(
				new TLVParameterHeader((byte) 0 /* reserverd */),
				new ROSpecStartTrigger(new TLVParameterHeader((byte) 0 /* reserved */),
						ROSpecStartTriggerType.IMMEDIATE),
				new ROSpecStopTrigger(new TLVParameterHeader((byte) 0 /* reserved */),
						ROSpecStopTriggerType.NULL, 0 /* durationTriggerValue */));
		final ROSpec roSpec2 = new ROSpec(new TLVParameterHeader((byte) 0), 2 /* roSpecID */,
				(short) 4 /* priority */, ROSpecCurrentState.DISABLED, roBoundarySpec2,
				null/* specList */);

		ROSpecsManager rm = new ROSpecsManager(rfcMessageHandler, false /* hasUTCClock */);
		rm.addListener(new ROSpecsManagerListener() {

			@Override
			public void executionChanged(ROSpecEvent event) {
				events.add(event);
			}
		});
		// add a disabled ROSpec
		rm.add(roSpec1);
		new Verifications() {
			{
				// the ROReportSpec is added to ROReportSpecsManager
				roReportSpecsManager.set(roSpec1.getRoSpecID(), roReportSpec);
				times = 1;
			}
		};
		// activate the ROSpec
		rm.setState(roSpec1.getRoSpecID(), ROSpecCurrentState.ACTIVE);
		new Verifications() {
			{
				// the execution is started
				rfcMessageHandler.requestExecution(withInstanceOf(ROSpec.class));
				times = 1;
			}
		};
		// a "start" ROSpec event is sent
		ROSpecEvent event = events.get(0);
		assertEquals(event.getEventType(), ROSpecEventType.START_OF_ROSPEC);
		assertEquals(event.getRoSpecID(), roSpec1.getRoSpecID());
		assertEquals(event.getPreemptingROSpecID(), 0);
		events.clear();

		// add a further ROSpec (disabled) with start trigger "immediate" and a
		// higher priority
		rm.add(roSpec2);
		new Verifications() {
			{
				// nothing is added to ROReportSpecsManager
				roReportSpecsManager.set(roSpec2.getRoSpecID(), roReportSpec);
				times = 0;
			}
		};
		// activate the second ROSpec using the start trigger
		rm.setState(roSpec2.getRoSpecID(), ROSpecCurrentState.INACTIVE);
		// finish the execution of the first ROSpec with an execution response
		rm.executionResponseReceived(roSpec1.getRoSpecID(), new ArrayList<TagData>(),
				true /* isLastResponse */);
		new Verifications() {
			{
				// the execution of the first ROSpec is canceled
				rfcMessageHandler.cancelExecution(roSpec1.getRoSpecID());
				times = 1;
				// the execution of the second ROSpec is started
				ROSpec rs;
				rfcMessageHandler.requestExecution(rs = withCapture());
				times = 2;
				assertEquals(rs.getRoSpecID(), roSpec2.getRoSpecID());
			}
		};
		// a "preempt" ROSpec event is sent
		event = events.get(0);
		assertEquals(event.getEventType(), ROSpecEventType.PREEMPTION_OF_ROSPEC);
		assertEquals(event.getRoSpecID(), roSpec1.getRoSpecID());
		assertEquals(event.getPreemptingROSpecID(), roSpec2.getRoSpecID());
		// a "start" ROSpec event is sent for the second ROSpec
		event = events.get(1);
		assertEquals(event.getEventType(), ROSpecEventType.START_OF_ROSPEC);
		assertEquals(event.getRoSpecID(), roSpec2.getRoSpecID());
		assertEquals(event.getPreemptingROSpecID(), 0);
		events.clear();

		// remove the first ROSpec (inactive), set the same priority as of
		// second ROSpec and add it again
		rm.remove(roSpec1.getRoSpecID());
		new Verifications() {
			{
				// the ROReportSpec is removed from ROReportSpecsManager
				roReportSpecsManager.remove(roSpec1.getRoSpecID());
				times = 1;
			}
		};
		roSpec1.setPriority(roSpec2.getPriority());
		rm.add(roSpec1);
		// activate the first ROSpec
		rm.setState(roSpec1.getRoSpecID(), ROSpecCurrentState.ACTIVE);
		// remove the second ROSpec (still active)
		rm.remove(roSpec2.getRoSpecID());
		// finish the execution of the second ROSpec with an execution response
		rm.executionResponseReceived(roSpec2.getRoSpecID(), new ArrayList<TagData>(),
				true /* isLastResponse */);
		new Verifications() {
			{
				// the execution of the second ROSpec is canceled
				rfcMessageHandler.cancelExecution(roSpec2.getRoSpecID());
				times = 1;
				// the execution of the first ROSpec is started
				ROSpec rs;
				rfcMessageHandler.requestExecution(rs = withCapture());
				times = 3;
				assertEquals(rs.getRoSpecID(), roSpec1.getRoSpecID());
			}
		};
		// an "end" ROSpec event is sent for the second ROSpec
		event = events.get(0);
		assertEquals(event.getEventType(), ROSpecEventType.END_OF_ROSPEC);
		assertEquals(event.getRoSpecID(), roSpec2.getRoSpecID());
		assertEquals(event.getPreemptingROSpecID(), 0);
		// a "start" ROSpec event is sent for the first ROSpec
		event = events.get(1);
		assertEquals(event.getEventType(), ROSpecEventType.START_OF_ROSPEC);
		assertEquals(event.getRoSpecID(), roSpec1.getRoSpecID());
		assertEquals(event.getPreemptingROSpecID(), 0);
		events.clear();

		// remove the first ROSpec (active)
		rm.remove(roSpec1.getRoSpecID());
		// finish the execution of the first ROSpec with an execution response
		rm.executionResponseReceived(roSpec1.getRoSpecID(), new ArrayList<TagData>(),
				true /* isLastResponse */);
		new Verifications() {
			{
				// the ROReportSpec is removed from ROReportSpecsManager
				roReportSpecsManager.remove(roSpec1.getRoSpecID());
				times = 2;
				// the execution of the ROSpec is canceled
				rfcMessageHandler.cancelExecution(roSpec1.getRoSpecID());
				times = 2;
			}
		};
		// an "end" ROSpec event is sent for the ROSpec
		event = events.get(0);
		assertEquals(event.getEventType(), ROSpecEventType.END_OF_ROSPEC);
		assertEquals(event.getRoSpecID(), roSpec1.getRoSpecID());
		assertEquals(event.getPreemptingROSpecID(), 0);
		events.clear();
		assertEquals(rm.getROSpecs().size(), 0);

		// add two disabled ROSpecs
		ROBoundarySpec roBoundarySpec3 = new ROBoundarySpec(
				new TLVParameterHeader((byte) 0 /* reserverd */),
				new ROSpecStartTrigger(new TLVParameterHeader((byte) 0 /* reserved */),
						ROSpecStartTriggerType.NULL_NO_START_TRIGGER),
				new ROSpecStopTrigger(new TLVParameterHeader((byte) 0 /* reserved */),
						ROSpecStopTriggerType.NULL, 0 /* durationTriggerValue */));
		ROSpec roSpec3 = new ROSpec(new TLVParameterHeader((byte) 0), 2 /* roSpecID */,
				(short) 4 /* priority */, ROSpecCurrentState.DISABLED, roBoundarySpec3,
				null/* specList */);
		rm.add(roSpec1);
		rm.add(roSpec3);
		assertEquals(rm.getROSpecs().size(), 2);
		// activate both ROSpecs
		rm.setState(0, ROSpecCurrentState.INACTIVE);
		for (ROSpec roSpec : rm.getROSpecs()) {
			assertEquals(roSpec.getCurrentState(), ROSpecCurrentState.INACTIVE);
		}
		// try to start the ROSpecs with identifier 0
		try {
			rm.setState(0, ROSpecCurrentState.ACTIVE);
			fail();
		} catch (InvalidIdentifierException e) {
			assertTrue(e.getMessage().contains("cannot be started or stopped with identifier 0"));
		}
		// activate one ROSpec
		rm.setState(roSpec1.getRoSpecID(), ROSpecCurrentState.ACTIVE);
		// try to stop the ROSpecs with identifier 0
		try {
			rm.setState(0, ROSpecCurrentState.INACTIVE);
			fail();
		} catch (InvalidIdentifierException e) {
			assertTrue(e.getMessage().contains("cannot be started or stopped with identifier 0"));
		}

		// try to change state with invalid identifier
		try {
			rm.setState(10, ROSpecCurrentState.INACTIVE);
			fail();
		} catch (InvalidIdentifierException e) {
			assertTrue(e.getMessage().contains("Missing ROSpec"));
		}
	}

	@Test
	public void executionResponseReceived(@Mocked final RFCMessageHandler rfcMessageHandler)
			throws Exception {
		final List<ROSpecEvent> events = new ArrayList<>();
		ROBoundarySpec roBoundarySpec1 = new ROBoundarySpec(
				new TLVParameterHeader((byte) 0 /* reserverd */),
				new ROSpecStartTrigger(new TLVParameterHeader((byte) 0 /* reserved */),
						ROSpecStartTriggerType.NULL_NO_START_TRIGGER),
				new ROSpecStopTrigger(new TLVParameterHeader((byte) 0 /* reserved */),
						ROSpecStopTriggerType.NULL, 0 /* durationTriggerValue */));
		final ROSpec roSpec1 = new ROSpec(new TLVParameterHeader((byte) 0), 1 /* roSpecID */,
				(short) 5 /* priority */, ROSpecCurrentState.DISABLED, roBoundarySpec1,
				null/* specList */);
		ROBoundarySpec roBoundarySpec2 = new ROBoundarySpec(
				new TLVParameterHeader((byte) 0 /* reserverd */),
				new ROSpecStartTrigger(new TLVParameterHeader((byte) 0 /* reserved */),
						ROSpecStartTriggerType.NULL_NO_START_TRIGGER),
				new ROSpecStopTrigger(new TLVParameterHeader((byte) 0 /* reserved */),
						ROSpecStopTriggerType.NULL, 0 /* durationTriggerValue */));
		final ROSpec roSpec2 = new ROSpec(new TLVParameterHeader((byte) 0), 2 /* roSpecID */,
				(short) 4 /* priority */, ROSpecCurrentState.DISABLED, roBoundarySpec2,
				null/* specList */);

		ROSpecsManager rm = new ROSpecsManager(rfcMessageHandler, false /* hasUTCClock */);
		rm.addListener(new ROSpecsManagerListener() {

			@Override
			public void executionChanged(ROSpecEvent event) {
				events.add(event);
			}
		});
		// add a disabled ROSpec
		rm.add(roSpec1);
		// activate the ROSpec
		rm.setState(roSpec1.getRoSpecID(), ROSpecCurrentState.ACTIVE);
		new Verifications() {
			{
				// the execution is started
				rfcMessageHandler.requestExecution(withInstanceOf(ROSpec.class));
				times = 1;
			}
		};
		// a "start" ROSpec event is sent
		ROSpecEvent event = events.get(0);
		assertEquals(event.getEventType(), ROSpecEventType.START_OF_ROSPEC);
		assertEquals(event.getRoSpecID(), roSpec1.getRoSpecID());
		assertEquals(event.getPreemptingROSpecID(), 0);
		events.clear();

		// add a second disabled ROSpec with a higher priority
		rm.add(roSpec2);
		// activate the second ROSpec
		rm.setState(roSpec2.getRoSpecID(), ROSpecCurrentState.ACTIVE);
		new Verifications() {
			{
				// the execution of the first ROSpec is canceled
				rfcMessageHandler.cancelExecution(roSpec1.getRoSpecID());
				times = 1;
				// the execution of the second ROSpec is NOT started yet
				rfcMessageHandler.requestExecution(withInstanceOf(ROSpec.class));
				times = 1;
			}
		};
		// no ROSpec events are sent
		assertTrue(events.isEmpty());
		assertEquals(rm.getPendingROSpecIds().size(), 1);
		assertEquals(rm.getPendingROSpecIds().get(0).longValue(), roSpec1.getRoSpecID());
		// finish the execution of the first ROSpec with an execution response
		rm.executionResponseReceived(roSpec1.getRoSpecID(), new ArrayList<TagData>(),
				true /* isLastResponse */);
		new Verifications() {
			{
				// the execution of the second ROSpec is started
				rfcMessageHandler.requestExecution(withInstanceOf(ROSpec.class));
				times = 2;
			}
		};
		assertTrue(rm.getPendingROSpecIds().isEmpty());
		// a "preempt" ROSpec event is sent for the first ROSpec
		event = events.get(0);
		assertEquals(event.getEventType(), ROSpecEventType.PREEMPTION_OF_ROSPEC);
		assertEquals(event.getRoSpecID(), roSpec1.getRoSpecID());
		assertEquals(event.getPreemptingROSpecID(), roSpec2.getRoSpecID());
		// a "start" ROSpec event is sent for the second ROSpec
		event = events.get(1);
		assertEquals(event.getEventType(), ROSpecEventType.START_OF_ROSPEC);
		assertEquals(event.getRoSpecID(), roSpec2.getRoSpecID());
		assertEquals(event.getPreemptingROSpecID(), 0);
		events.clear();

		// finish the execution of the first ROSpec with an execution response
		// BEFORE the execution is cancelled
		rm.executionResponseReceived(roSpec2.getRoSpecID(), new ArrayList<TagData>(),
				true /* isLastResponse */);
		// remove the second ROSpec (active)
		rm.remove(roSpec2.getRoSpecID());
		new Verifications() {
			{
				// the execution of the second ROSpec needs NOT to be canceled
				rfcMessageHandler.cancelExecution(roSpec2.getRoSpecID());
				times = 0;
			}
		};
		assertEquals(rm.getPendingROSpecIds().size(), 0);
		// an "end" ROSpec event is sent for the second ROSpec
		event = events.get(0);
		assertEquals(event.getEventType(), ROSpecEventType.END_OF_ROSPEC);
		assertEquals(event.getRoSpecID(), roSpec2.getRoSpecID());
		assertEquals(event.getPreemptingROSpecID(), 0);
		events.clear();
		// the second ROSpec was removed
		assertEquals(rm.getROSpecs().size(), 1);
		assertEquals(rm.getROSpecs().get(0).getRoSpecID(), roSpec1.getRoSpecID());
	}

	// @Mocked
	// RFCMessageHandler rfcMessageHandler;

	public void gpiTriggers(//
			@Mocked final RFCMessageHandler rfcMessageHandler//
	) throws Exception {
		// add a disabled ROSpec with a GPI start and stop trigger
		ROSpecStartTrigger startTrigger = new ROSpecStartTrigger(
				new TLVParameterHeader((byte) 0 /* reserved */), ROSpecStartTriggerType.GPI);
		startTrigger.setGpiTV(new GPITriggerValue(new TLVParameterHeader((byte) 0), 1 /* port */,
				true, 0 /* timeOut */));
		ROSpecStopTrigger stopTrigger = new ROSpecStopTrigger(
				new TLVParameterHeader((byte) 0 /* reserved */),
				ROSpecStopTriggerType.GPI_WITH_TIMEOUT_VALUE, 0 /* durationTriggerValue */);
		stopTrigger.setGpiTriggerValue(new GPITriggerValue(new TLVParameterHeader((byte) 0),
				1 /* port */, false /* state */, 3000 /* timeout */));
		ROBoundarySpec roBoundarySpec = new ROBoundarySpec(
				new TLVParameterHeader((byte) 0 /* reserverd */), startTrigger, stopTrigger);
		final ROSpec roSpec = new ROSpec(new TLVParameterHeader((byte) 0), 1 /* roSpecID */,
				(short) 5 /* priority */, ROSpecCurrentState.DISABLED, roBoundarySpec,
				null/* specList */);
		ROSpecsManager rm = new ROSpecsManager(rfcMessageHandler, false /* hasUTCClock */);
		rm.add(roSpec);

		// try to start disabled ROSpec
		rm.gpiEventReceived(new GPIEvent(new TLVParameterHeader((byte) 0), 1 /* port */, true));
		// enable ROSpec
		rm.setState(roSpec.getRoSpecID(), ROSpecCurrentState.INACTIVE);
		// try to start ROSpec with irrelevant events
		rm.gpiEventReceived(new GPIEvent(new TLVParameterHeader((byte) 0), 0 /* port */, true));
		rm.gpiEventReceived(new GPIEvent(new TLVParameterHeader((byte) 0), 1 /* port */, false));
		new Verifications() {
			{
				// the execution is NOT started
				rfcMessageHandler.requestExecution(withInstanceOf(ROSpec.class));
				times = 0;
				// the RFC message handler is informed about the GPI events
				rfcMessageHandler.gpiEventReceived(withInstanceOf(GPIEvent.class));
				times = 3;
			}
		};
		// start ROSpec
		rm.gpiEventReceived(new GPIEvent(new TLVParameterHeader((byte) 0), 1 /* port */, true));
		new Verifications() {
			{
				// the execution is started
				rfcMessageHandler.requestExecution(withInstanceOf(ROSpec.class));
				times = 1;
			}
		};

		// try to stop ROSpec with irrelevant events
		rm.gpiEventReceived(new GPIEvent(new TLVParameterHeader((byte) 0), 0 /* port */, false));
		rm.gpiEventReceived(new GPIEvent(new TLVParameterHeader((byte) 0), 1 /* port */, true));
		new Verifications() {
			{
				// the execution of the ROSpec is NOT canceled
				rfcMessageHandler.cancelExecution(roSpec.getRoSpecID());
				times = 0;
			}
		};
		// stop ROSpec
		rm.gpiEventReceived(new GPIEvent(new TLVParameterHeader((byte) 0), 1 /* port */, false));
		new Verifications() {
			{
				// the execution of the ROSpec is canceled
				rfcMessageHandler.cancelExecution(roSpec.getRoSpecID());
				times = 1;
			}
		};
		// the time out of the stop trigger is tested with stopTimerGpi()
	}

	@Test
	public void startTimerPeriodic(@Mocked final RFCMessageHandler rfcMessageHandler)
			throws Exception {
		// oldState\newState|disabled.........|inactive....................|active
		// disabled.........|-................|start_start_timer_"periodic"|start_start_timer_"periodic"+start_stop_timer_"duration"/"GPI_with_timeout"
		// inactive.........|stop_start_timers|-...........................|start_stop_timer_"duration"/"GPI_with_timeout"
		// active...........|stop_all_timers..|stop_stop_timers............|-

		// add a disabled ROSpec
		ROSpecStartTrigger startTrigger = new ROSpecStartTrigger(
				new TLVParameterHeader((byte) 0 /* reserved */), ROSpecStartTriggerType.PERIODIC);
		PeriodicTriggerValue periodicTV = new PeriodicTriggerValue(new TLVParameterHeader((byte) 0),
				2000 /* offSet */, 1000 /* period */);
		periodicTV.setUtc(new UTCTimestamp(new TLVParameterHeader((byte) 0),
				BigInteger.valueOf((new Date().getTime() + 1000) * 1000)/* microseconds */));
		startTrigger.setPeriodicTV(periodicTV);
		ROBoundarySpec roBoundarySpec = new ROBoundarySpec(
				new TLVParameterHeader((byte) 0 /* reserverd */), startTrigger,
				new ROSpecStopTrigger(new TLVParameterHeader((byte) 0 /* reserved */),
						ROSpecStopTriggerType.NULL, 0 /* durationTriggerValue */));
		final ROSpec roSpec = new ROSpec(new TLVParameterHeader((byte) 0), 1 /* roSpecID */,
				(short) 5 /* priority */, ROSpecCurrentState.DISABLED, roBoundarySpec,
				null/* specList */);
		ROSpecsManager rm = new ROSpecsManager(rfcMessageHandler, true /* hasUTCClock */);
		rm.add(roSpec);

		// disabled -> inactive (with time stamp + offset)
		rm.setState(roSpec.getRoSpecID(), ROSpecCurrentState.INACTIVE);
		Thread.sleep(1000);
		// inactive -> disabled (before ROSpec is started the first time)
		rm.setState(roSpec.getRoSpecID(), ROSpecCurrentState.DISABLED);
		new Verifications() {
			{
				// the execution has NOT been started
				rfcMessageHandler.requestExecution(withInstanceOf(ROSpec.class));
				times = 0;
				rfcMessageHandler.cancelExecution(roSpec.getRoSpecID());
				times = 0;
			}
		};
		// disable -> inactive (enables the trigger again)
		rm.setState(roSpec.getRoSpecID(), ROSpecCurrentState.INACTIVE);
		// active -> inactive (after the ROSpec has been started the first time)
		Thread.sleep(2500);
		rm.setState(roSpec.getRoSpecID(), ROSpecCurrentState.INACTIVE);
		// finish the execution with an execution response
		rm.executionResponseReceived(roSpec.getRoSpecID(), new ArrayList<TagData>(),
				true /* isLastResponse */);
		new Verifications() {
			{
				// the execution has been started and stopped
				rfcMessageHandler.requestExecution(withInstanceOf(ROSpec.class));
				times = 1;
				rfcMessageHandler.cancelExecution(roSpec.getRoSpecID());
				times = 1;
			}
		};
		// active -> disabled (after the ROSpec has been started the second
		// time)
		Thread.sleep(1000);
		rm.setState(roSpec.getRoSpecID(), ROSpecCurrentState.DISABLED);
		// finish the execution with an execution response
		rm.executionResponseReceived(roSpec.getRoSpecID(), new ArrayList<TagData>(),
				true /* isLastResponse */);
		new Verifications() {
			{
				// the execution has been started and stopped a second time
				rfcMessageHandler.requestExecution(withInstanceOf(ROSpec.class));
				times = 2;
				rfcMessageHandler.cancelExecution(roSpec.getRoSpecID());
				times = 2;
			}
		};

		// disabled -> active (without time stamp and offset)
		periodicTV.setUtc(null);
		periodicTV.setOffSet(0);
		rm.setState(roSpec.getRoSpecID(), ROSpecCurrentState.ACTIVE);
		// active -> disabled (the ROSpec has been started directly)
		Thread.sleep(500);
		rm.setState(roSpec.getRoSpecID(), ROSpecCurrentState.DISABLED);
		// finish the execution with an execution response
		rm.executionResponseReceived(roSpec.getRoSpecID(), new ArrayList<TagData>(),
				true /* isLastResponse */);
		new Verifications() {
			{
				// the execution has been started and stopped
				rfcMessageHandler.requestExecution(withInstanceOf(ROSpec.class));
				times = 3;
				rfcMessageHandler.cancelExecution(roSpec.getRoSpecID());
				times = 3;
			}
		};
	}

	@Test
	public void stopTimerDuration(@Mocked final RFCMessageHandler rfcMessageHandler)
			throws Exception {
		// oldState\newState|disabled.........|inactive....................|active
		// disabled.........|-................|start_start_timer_"periodic"|start_start_timer_"periodic"+start_stop_timer_"duration"/"GPI_with_timeout"
		// inactive.........|stop_start_timers|-...........................|start_stop_timer_"duration"/"GPI_with_timeout"
		// active...........|stop_all_timers..|stop_stop_timers............|-

		// add a disabled ROSpec
		ROSpecStopTrigger stopTrigger = new ROSpecStopTrigger(
				new TLVParameterHeader((byte) 0 /* reserved */), ROSpecStopTriggerType.DURATION,
				1000 /* durationTriggerValue */);
		ROBoundarySpec roBoundarySpec = new ROBoundarySpec(
				new TLVParameterHeader((byte) 0 /* reserverd */),
				new ROSpecStartTrigger(new TLVParameterHeader((byte) 0 /* reserved */),
						ROSpecStartTriggerType.NULL_NO_START_TRIGGER),
				stopTrigger);
		final ROSpec roSpec = new ROSpec(new TLVParameterHeader((byte) 0), 1 /* roSpecID */,
				(short) 5 /* priority */, ROSpecCurrentState.DISABLED, roBoundarySpec,
				null/* specList */);
		ROSpecsManager rm = new ROSpecsManager(rfcMessageHandler, false /* hasUTCClock */);
		rm.add(roSpec);

		// disabled -> active
		rm.setState(roSpec.getRoSpecID(), ROSpecCurrentState.ACTIVE);
		// active -> inactive (before the stop timer)
		Thread.sleep(500);
		rm.setState(roSpec.getRoSpecID(), ROSpecCurrentState.INACTIVE);
		// finish the execution with an execution response
		rm.executionResponseReceived(roSpec.getRoSpecID(), new ArrayList<TagData>(),
				true /* isLastResponse */);
		new Verifications() {
			{
				// the execution has been started and stopped
				rfcMessageHandler.requestExecution(withInstanceOf(ROSpec.class));
				times = 1;
				rfcMessageHandler.cancelExecution(roSpec.getRoSpecID());
				times = 1;
			}
		};
		// inactive -> active
		rm.setState(roSpec.getRoSpecID(), ROSpecCurrentState.ACTIVE);
		// active -> disabled (before the stop timer)
		Thread.sleep(500);
		rm.setState(roSpec.getRoSpecID(), ROSpecCurrentState.DISABLED);
		// finish the execution with an execution response
		rm.executionResponseReceived(roSpec.getRoSpecID(), new ArrayList<TagData>(),
				true /* isLastResponse */);
		new Verifications() {
			{
				// the execution has been started and stopped
				rfcMessageHandler.requestExecution(withInstanceOf(ROSpec.class));
				times = 2;
				rfcMessageHandler.cancelExecution(roSpec.getRoSpecID());
				times = 2;
			}
		};
		// disabled -> inactive
		rm.setState(roSpec.getRoSpecID(), ROSpecCurrentState.INACTIVE);
		// inactive -> active
		rm.setState(roSpec.getRoSpecID(), ROSpecCurrentState.ACTIVE);
		// active -> inactive (by stop timer)
		Thread.sleep(500);
		new Verifications() {
			{
				// the execution has been started
				rfcMessageHandler.requestExecution(withInstanceOf(ROSpec.class));
				times = 3;
			}
		};
		Thread.sleep(500);
		// finish the execution with an execution response
		rm.executionResponseReceived(roSpec.getRoSpecID(), new ArrayList<TagData>(),
				true /* isLastResponse */);
		new Verifications() {
			{
				// the execution has been stopped
				rfcMessageHandler.cancelExecution(roSpec.getRoSpecID());
				times = 3;
			}
		};
	}

	@Test
	public void stopTimerGpi(@Mocked final RFCMessageHandler rfcMessageHandler) throws Exception {
		// oldState\newState|disabled.........|inactive....................|active
		// disabled.........|-................|start_start_timer_"periodic"|start_start_timer_"periodic"+start_stop_timer_"duration"/"GPI_with_timeout"
		// inactive.........|stop_start_timers|-...........................|start_stop_timer_"duration"/"GPI_with_timeout"
		// active...........|stop_all_timers..|stop_stop_timers............|-

		// add a disabled ROSpec
		ROSpecStopTrigger stopTrigger = new ROSpecStopTrigger(
				new TLVParameterHeader((byte) 0 /* reserved */),
				ROSpecStopTriggerType.GPI_WITH_TIMEOUT_VALUE, 0 /* durationTriggerValue */);
		stopTrigger.setGpiTriggerValue(new GPITriggerValue(new TLVParameterHeader((byte) 0),
				1 /* port */, false /* state */, 1000 /* timeout */));
		ROBoundarySpec roBoundarySpec = new ROBoundarySpec(
				new TLVParameterHeader((byte) 0 /* reserverd */),
				new ROSpecStartTrigger(new TLVParameterHeader((byte) 0 /* reserved */),
						ROSpecStartTriggerType.NULL_NO_START_TRIGGER),
				stopTrigger);
		final ROSpec roSpec = new ROSpec(new TLVParameterHeader((byte) 0), 1 /* roSpecID */,
				(short) 5 /* priority */, ROSpecCurrentState.DISABLED, roBoundarySpec,
				null/* specList */);
		ROSpecsManager rm = new ROSpecsManager(rfcMessageHandler, false /* hasUTCClock */);
		rm.add(roSpec);

		// disabled -> active
		rm.setState(roSpec.getRoSpecID(), ROSpecCurrentState.ACTIVE);
		// active -> inactive (before the stop timer)
		Thread.sleep(500);
		rm.gpiEventReceived(new GPIEvent(new TLVParameterHeader((byte) 0), 1 /* port */, false));
		// finish the execution with an execution response
		rm.executionResponseReceived(roSpec.getRoSpecID(), new ArrayList<TagData>(),
				true /* isLastResponse */);
		new Verifications() {
			{
				// the execution has been started and stopped
				rfcMessageHandler.requestExecution(withInstanceOf(ROSpec.class));
				times = 1;
				rfcMessageHandler.cancelExecution(roSpec.getRoSpecID());
				times = 1;
			}
		};
		// inactive -> active
		rm.setState(roSpec.getRoSpecID(), ROSpecCurrentState.ACTIVE);
		// active -> disabled (before the stop timer)
		Thread.sleep(500);
		rm.setState(roSpec.getRoSpecID(), ROSpecCurrentState.DISABLED);
		// finish the execution with an execution response
		rm.executionResponseReceived(roSpec.getRoSpecID(), new ArrayList<TagData>(),
				true /* isLastResponse */);
		new Verifications() {
			{
				// the execution has been started and stopped
				rfcMessageHandler.requestExecution(withInstanceOf(ROSpec.class));
				times = 2;
				rfcMessageHandler.cancelExecution(roSpec.getRoSpecID());
				times = 2;
			}
		};
		// disabled -> inactive
		rm.setState(roSpec.getRoSpecID(), ROSpecCurrentState.INACTIVE);
		// inactive -> active
		rm.setState(roSpec.getRoSpecID(), ROSpecCurrentState.ACTIVE);
		// active -> inactive (by stop timer)
		Thread.sleep(500);
		new Verifications() {
			{
				// the execution has been started
				rfcMessageHandler.requestExecution(withInstanceOf(ROSpec.class));
				times = 3;
			}
		};
		Thread.sleep(1000);
		// finish the execution with an execution response
		rm.executionResponseReceived(roSpec.getRoSpecID(), new ArrayList<TagData>(),
				true /* isLastResponse */);
		new Verifications() {
			{
				// the execution has been stopped
				rfcMessageHandler.cancelExecution(roSpec.getRoSpecID());
				times = 3;
			}
		};
	}
}
