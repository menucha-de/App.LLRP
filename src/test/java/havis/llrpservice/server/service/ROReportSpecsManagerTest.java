package havis.llrpservice.server.service;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.testng.annotations.Test;

import havis.device.rf.tag.TagData;
import havis.llrpservice.data.message.parameter.ROReportSpec;
import havis.llrpservice.data.message.parameter.ROReportTrigger;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.data.message.parameter.TagReportContentSelector;
import havis.llrpservice.server.service.ROReportSpecsManager.ROReportSpecsManagerListener;

public class ROReportSpecsManagerTest {

	@Test
	public void resetTriggers() throws Exception {
		final Map<Long, ROReportSpec> events = new HashMap<>();

		ROReportSpecsManager rm = new ROReportSpecsManager();
		// add ROReportSpec
		int roSpecIdTagCount = 1;
		rm.set(roSpecIdTagCount, createROReportSpec(
				ROReportTrigger.UPON_N_TAGREPORTDATA_PARAMETERS_OR_END_OF_ROSPEC, 2 /* n */));
		int roSpecIdTimer = 2;
		rm.set(roSpecIdTimer, createROReportSpec(
				ROReportTrigger.UPON_N_MILLISECONDS_OR_END_OF_ROSPEC, 1000 /* n */));
		// add a listener for report events
		ROReportSpecsManagerListener listener = new ROReportSpecsManagerListener() {

			@Override
			public void report(long roSpecId, ROReportSpec roReportSpec) {
				events.put(roSpecId, roReportSpec);
			}
		};
		rm.addListener(listener);
		// activate ROReportSpec
		rm.roSpecStarted(roSpecIdTagCount);
		rm.roSpecStarted(roSpecIdTimer);

		// inform about an execution response
		rm.executionResponseReceived(roSpecIdTagCount, Arrays.asList(new TagData()));
		// wait some time
		Thread.sleep(750);
		// no event received
		assertTrue(events.isEmpty());

		// reset triggers
		rm.resetTriggers();
		// inform about an execution response
		rm.executionResponseReceived(roSpecIdTagCount, Arrays.asList(new TagData()));
		// wait some time
		Thread.sleep(500);
		// no event received
		assertTrue(events.isEmpty());

		// wait some time
		Thread.sleep(750);
		// an event was received
		assertEquals(events.size(), 1);
		events.clear();
		// inform about a further execution response
		rm.executionResponseReceived(roSpecIdTagCount, Arrays.asList(new TagData()));
		// an event was received
		assertEquals(events.size(), 1);
		events.clear();

		// deactivate ROReportSpecs
		rm.roSpecStopped(roSpecIdTagCount);
		rm.roSpecStopped(roSpecIdTimer);
	}

	@Test
	public void triggersPeriodic() throws Exception {
		triggerPeriodic(ROReportTrigger.UPON_N_MILLISECONDS_OR_END_OF_AISPEC_OR_END_OF_RFSURVEYSPEC,
				false /* seconds */);
		triggerPeriodic(ROReportTrigger.UPON_N_MILLISECONDS_OR_END_OF_ROSPEC, false /* seconds */);
		triggerPeriodic(ROReportTrigger.UPON_N_SECONDS_OR_END_OF_AISPEC_OR_END_OF_RFSURVEYSPEC,
				true /* seconds */);
		triggerPeriodic(ROReportTrigger.UPON_N_SECONDS_OR_END_OF_ROSPEC, true /* seconds */);
	}

	private void triggerPeriodic(ROReportTrigger trigger, boolean seconds) throws Exception {
		final Map<Long, ROReportSpec> events = new HashMap<>();

		ROReportSpecsManager rm = new ROReportSpecsManager();
		// add ROReportSpec
		int roSpecId = 1;
		rm.set(roSpecId, createROReportSpec(trigger, seconds ? 1 : 1000 /* n */));
		// add a listener for report events
		ROReportSpecsManagerListener listener = new ROReportSpecsManagerListener() {

			@Override
			public void report(long roSpecId, ROReportSpec roReportSpec) {
				events.put(roSpecId, roReportSpec);
			}
		};
		rm.addListener(listener);
		// activate ROReportSpec
		rm.roSpecStarted(roSpecId);

		// wait a short time
		Thread.sleep(500);
		// no event received
		assertTrue(events.isEmpty());
		// wait until trigger is started
		Thread.sleep(750);
		// an event was received
		assertEquals(events.size(), 1);
		events.clear();

		// wait a short time
		Thread.sleep(500);
		// no event received
		assertTrue(events.isEmpty());
		// wait until trigger is started again
		Thread.sleep(500);
		// an event was received
		assertEquals(events.size(), 1);
		events.clear();

		// deactivate ROReportSpec
		rm.roSpecStopped(roSpecId);
		// stop event received
		assertEquals(events.size(), 1);
		events.clear();
		Thread.sleep(1000);
		// no further event received
		assertTrue(events.isEmpty());
	}

	@Test
	public void triggersNTagReportData() throws Exception {
		triggerNTagReportData(ROReportTrigger.UPON_N_TAGREPORTDATA_PARAMETERS_OR_END_OF_AISPEC);
		triggerNTagReportData(ROReportTrigger.UPON_N_TAGREPORTDATA_PARAMETERS_OR_END_OF_ROSPEC);
	}

	private void triggerNTagReportData(ROReportTrigger trigger) throws Exception {
		final Map<Long, ROReportSpec> events = new HashMap<>();

		ROReportSpecsManager rm = new ROReportSpecsManager();
		// add ROReportSpec
		int roSpecId = 1;
		rm.set(roSpecId, createROReportSpec(trigger, 2 /* n */));
		// add a listener for report events
		ROReportSpecsManagerListener listener = new ROReportSpecsManagerListener() {

			@Override
			public void report(long roSpecId, ROReportSpec roReportSpec) {
				events.put(roSpecId, roReportSpec);
			}
		};
		rm.addListener(listener);
		// activate ROReportSpec
		rm.roSpecStarted(roSpecId);

		// inform about an execution response
		rm.executionResponseReceived(roSpecId, Arrays.asList(new TagData()));
		// no event received because the tag count has not been reached
		assertTrue(events.isEmpty());
		// inform about further execution responses
		rm.executionResponseReceived(roSpecId,
				Arrays.asList(new TagData(), new TagData(), new TagData()));
		// an event was received
		assertEquals(events.size(), 1);
		events.clear();

		// inform about an execution response
		rm.executionResponseReceived(roSpecId, Arrays.asList(new TagData()));
		// no event received because the tag count has not been reached
		assertTrue(events.isEmpty());
		// inform about further execution response
		rm.executionResponseReceived(roSpecId, Arrays.asList(new TagData()));
		// an event was received
		assertEquals(events.size(), 1);
		events.clear();

		// deactivate ROReportSpec
		rm.roSpecStopped(roSpecId);
		// stop event received
		assertEquals(events.size(), 1);
		events.clear();
	}

	@Test
	public void getROReportSpec() throws Exception {
		ROReportSpecsManager rm = new ROReportSpecsManager();
		assertEquals(rm.getROReportSpec(1 /* roSpecId */).getRoReportTrigger(),
				ROReportTrigger.NONE);

		// set a default ROReportSpec
		rm.setDefaultROReportSpec(createROReportSpec(
				ROReportTrigger.UPON_N_TAGREPORTDATA_PARAMETERS_OR_END_OF_ROSPEC, 1 /* n */));
		// the default ROReportSpec is returned for any ROSpec
		int roSpecId = 1;
		assertEquals(rm.getROReportSpec(roSpecId).getRoReportTrigger(),
				ROReportTrigger.UPON_N_TAGREPORTDATA_PARAMETERS_OR_END_OF_ROSPEC);

		// add a specific ROReportSpec for an ROSpec
		rm.set(roSpecId,
				createROReportSpec(
						ROReportTrigger.UPON_N_MILLISECONDS_OR_END_OF_AISPEC_OR_END_OF_RFSURVEYSPEC,
						1 /* n */));
		// the specific ROReportSpec is returned
		assertEquals(rm.getROReportSpec(roSpecId).getRoReportTrigger(),
				ROReportTrigger.UPON_N_MILLISECONDS_OR_END_OF_AISPEC_OR_END_OF_RFSURVEYSPEC);

		// activate ROReportSpec
		rm.roSpecStarted(roSpecId);
		// change specific ROReportSpec
		rm.set(roSpecId, createROReportSpec(
				ROReportTrigger.UPON_N_SECONDS_OR_END_OF_AISPEC_OR_END_OF_RFSURVEYSPEC, 1 /* n */));
		// the original ROReportSpec is returned
		assertEquals(rm.getROReportSpec(roSpecId).getRoReportTrigger(),
				ROReportTrigger.UPON_N_MILLISECONDS_OR_END_OF_AISPEC_OR_END_OF_RFSURVEYSPEC);
		// deactivate ROReportSpec
		rm.roSpecStopped(roSpecId);
		// the new ROReportSpec is returned
		assertEquals(rm.getROReportSpec(roSpecId).getRoReportTrigger(),
				ROReportTrigger.UPON_N_SECONDS_OR_END_OF_AISPEC_OR_END_OF_RFSURVEYSPEC);

		// remove specific ROReportSpec
		rm.remove(roSpecId);
		// the default ROReportSpec is returned
		assertEquals(rm.getROReportSpec(roSpecId).getRoReportTrigger(),
				ROReportTrigger.UPON_N_TAGREPORTDATA_PARAMETERS_OR_END_OF_ROSPEC);
	}

	private ROReportSpec createROReportSpec(ROReportTrigger trigger, int n) {
		return new ROReportSpec(new TLVParameterHeader((byte) 0x00), trigger, n,
				new TagReportContentSelector(new TLVParameterHeader((byte) 0x00),
						false /* enableROSpecID */, false /* enableSpecIndex */,
						false /* enableInventoryParameterSpecID */, false /* enableAntennaID */,
						false /* enableChannelIndex */, false /* enablePeakRSSI */,
						false /* enableFirstSeenTimestamp */, false /* enableLastSeenTimestamp */,
						false /* enableTagSeenCount */, false /* enableAccessSpecID */));
	}
}
