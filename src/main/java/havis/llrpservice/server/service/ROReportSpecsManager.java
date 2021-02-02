package havis.llrpservice.server.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import havis.device.rf.tag.TagData;
import havis.llrpservice.data.message.parameter.ROReportSpec;
import havis.llrpservice.data.message.parameter.ROReportTrigger;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.data.message.parameter.TagReportContentSelector;

public class ROReportSpecsManager {
	private Logger log = Logger.getLogger(ROReportSpecsManager.class.getName());

	public interface ROReportSpecsManagerListener {
		void report(long roSpecId, ROReportSpec roReportSpec);
	}

	private List<ROReportSpecsManagerListener> listeners = new ArrayList<>();
	private ROReportSpec dfltRoReportSpec = new ROReportSpec(new TLVParameterHeader((byte) 0x00),
			ROReportTrigger.NONE, 0 /* n */,
			new TagReportContentSelector(new TLVParameterHeader((byte) 0x00),
					false /* enableROSpecID */, false /* enableSpecIndex */,
					false /* enableInventoryParameterSpecID */, false /* enableAntennaID */,
					false /* enableChannelIndex */, false /* enablePeakRSSI */,
					false /* enableFirstSeenTimestamp */, false /* enableLastSeenTimestamp */,
					false /* enableTagSeenCount */, false /* enableAccessSpecID */));
	// roSpecId -> ROReportSpec
	private Map<Long, ROReportSpec> roReportSpecs = new HashMap<>();
	private Map<Long, ROReportSpec> activeROReportSpecs = new HashMap<>();
	// roSpecId -> tag count
	private Map<Long, Integer> tagCounts = new HashMap<>();
	private Timer timer = null;
	// roSpecId -> timer task
	private Map<Long, TimerTask> tasks = new HashMap<>();

	ROReportSpecsManager() {
	}

	public synchronized void addListener(ROReportSpecsManagerListener listener) {
		listeners.add(listener);
	}

	public synchronized void removeListener(ROReportSpecsManagerListener listener) {
		listeners.remove(listener);
	}

	/**
	 * Sets a default ROReportSpec.
	 * 
	 * @param roReportSpec
	 */
	public synchronized void setDefaultROReportSpec(ROReportSpec roReportSpec) {
		this.dfltRoReportSpec = roReportSpec;
	}

	/**
	 * Returns the ROReportSpec for a ROSpec.
	 * 
	 * @param roSpecId
	 * @return
	 */
	public synchronized ROReportSpec getROReportSpec(long roSpecId) {
		ROReportSpec roReportSpec = activeROReportSpecs.get(roSpecId);
		if (roReportSpec != null) {
			return roReportSpec;
		}
		roReportSpec = roReportSpecs.get(roSpecId);
		if (roReportSpec == null) {
			roReportSpec = dfltRoReportSpec;
		}
		return roReportSpec;
	}

	public synchronized void resetTriggers() {
		// for each tag counter
		for (Long roSpecId : tagCounts.keySet()) {
			// reset tag count
			tagCounts.put(roSpecId, 0);
		}
		// for each timer task
		for (Entry<Long, TimerTask> taskEntry : tasks.entrySet()) {
			long roSpecId = taskEntry.getKey();
			TimerTask timerTask = taskEntry.getValue();
			// cancel the current timer task
			timerTask.cancel();
			if (log.isLoggable(Level.FINE)) {
				log.log(Level.FINE,
						"Canceled timer for ROReportSpec of ROSpec " + roSpecId + " due to reset");
			}
			// add a new timer task for the same ROReportSpec
			addTrigger(roSpecId);
		}
	}

	/**
	 * Sets an ROReportSpec.
	 * 
	 * @param roSpecId
	 * @param roReportSpec
	 */
	synchronized void set(long roSpecId, ROReportSpec roReportSpec) {
		roReportSpecs.put(roSpecId, roReportSpec);
	}

	/**
	 * Removes an ROReportSpec.
	 * 
	 * @param roSpecId
	 */
	synchronized void remove(long roSpecId) {
		roReportSpecs.remove(roSpecId);
	}

	/**
	 * Informs the ROReportSpecsManager about the starting of an ROSpec
	 * execution. The relating ROReportSpec is activated.
	 * 
	 * @param roSpecId
	 */
	synchronized void roSpecStarted(final long roSpecId) {
		addTrigger(roSpecId);
	}

	/**
	 * Informs the ROReportSpecsManager about the end of an ROSpec execution.
	 * The relating ROReportSpec is deactivated.
	 * 
	 * @param roSpecId
	 */
	synchronized void roSpecStopped(long roSpecId) {
		// remove trigger
		ROReportSpec roReportSpec = removeTrigger(roSpecId);
		switch (roReportSpec.getRoReportTrigger()) {
		// AISpec always stops with ROSpec because only the
		// AISpecStopTrigger "0: Null - Stop when ROSpec is done" is supported
		case UPON_N_TAGREPORTDATA_PARAMETERS_OR_END_OF_AISPEC:
		case UPON_N_TAGREPORTDATA_PARAMETERS_OR_END_OF_ROSPEC:
		case UPON_N_SECONDS_OR_END_OF_AISPEC_OR_END_OF_RFSURVEYSPEC:
		case UPON_N_SECONDS_OR_END_OF_ROSPEC:
		case UPON_N_MILLISECONDS_OR_END_OF_AISPEC_OR_END_OF_RFSURVEYSPEC:
		case UPON_N_MILLISECONDS_OR_END_OF_ROSPEC:
			// fire report event
			fireReport(roSpecId, roReportSpec);
			break;
		default:
			break;
		}
	}

	/**
	 * Informs the ROReportSpecsManager about the receipt of a new report.
	 * <p>
	 * The call of this method is only allowed when the relating ROReportSpec is
	 * active (see {@link #roSpecStarted(long)}, {@link #roSpecStopped(long)}).
	 * </p>
	 * 
	 * @param roSpecId
	 * @param tagData
	 */
	synchronized void executionResponseReceived(long roSpecId, List<TagData> tagData) {
		ROReportSpec roReportSpec = activeROReportSpecs.get(roSpecId);
		switch (roReportSpec.getRoReportTrigger()) {
		case UPON_N_TAGREPORTDATA_PARAMETERS_OR_END_OF_AISPEC:
		case UPON_N_TAGREPORTDATA_PARAMETERS_OR_END_OF_ROSPEC:
			// if unlimited tag count
			if (0 == roReportSpec.getN()) {
				break;
			}
			int newTagCount = tagCounts.get(roSpecId) + tagData.size();
			// if tag count has been reached
			if (newTagCount >= roReportSpec.getN()) {
				// fire report event
				fireReport(roSpecId, roReportSpec);
				// reset tag count
				newTagCount = 0;
			}
			// save new tag count
			tagCounts.put(roSpecId, newTagCount);
			break;
		default:
			break;
		}
	}

	private ROReportSpec addTrigger(long roSpecId) {
		ROReportSpec roReportSpec = getROReportSpec(roSpecId);
		activeROReportSpecs.put(roSpecId, roReportSpec);
		int period = 0;
		switch (roReportSpec.getRoReportTrigger()) {
		case UPON_N_TAGREPORTDATA_PARAMETERS_OR_END_OF_AISPEC:
		case UPON_N_TAGREPORTDATA_PARAMETERS_OR_END_OF_ROSPEC:
			tagCounts.put(roSpecId, 0);
			break;
		case UPON_N_SECONDS_OR_END_OF_AISPEC_OR_END_OF_RFSURVEYSPEC:
		case UPON_N_SECONDS_OR_END_OF_ROSPEC:
			period = roReportSpec.getN() * 1000;
			break;
		case UPON_N_MILLISECONDS_OR_END_OF_AISPEC_OR_END_OF_RFSURVEYSPEC:
		case UPON_N_MILLISECONDS_OR_END_OF_ROSPEC:
			period = roReportSpec.getN();
			break;
		default:
			break;
		}
		if (period > 0) {
			addTimerTask(roSpecId, period, roReportSpec);
		}
		return roReportSpec;
	}

	private ROReportSpec removeTrigger(long roSpecId) {
		removeTimerTask(roSpecId);
		tagCounts.remove(roSpecId);
		return activeROReportSpecs.remove(roSpecId);
	}

	private void addTimerTask(final long roSpecId, long period, final ROReportSpec roReportSpec) {
		if (timer == null) {
			timer = new Timer();
		}
		TimerTask task = new TimerTask() {

			@Override
			public void run() {
				fireReport(roSpecId, roReportSpec);
			}
		};
		tasks.put(roSpecId, task);
		timer.scheduleAtFixedRate(task, period /* offset */, period);
		if (log.isLoggable(Level.FINE)) {
			log.log(Level.FINE, "Scheduled timer for ROReportSpec of ROSpec " + roSpecId
					+ ": offset=" + period + ",period=" + period);
		}
	}

	private void removeTimerTask(long roSpecId) {
		if (timer == null) {
			return;
		}
		TimerTask task = tasks.remove(roSpecId);
		if (task != null) {
			task.cancel();
			if (log.isLoggable(Level.FINE)) {
				log.log(Level.FINE, "Canceled timer for ROReportSpec of ROSpec " + roSpecId);
			}
		}
		if (tasks.isEmpty()) {
			timer.cancel();
			timer = null;
		}
	}

	private void fireReport(long roSpecId, ROReportSpec roReportSpec) {
		for (ROReportSpecsManagerListener listener : listeners) {
			listener.report(roSpecId, roReportSpec);
		}
	}
}
