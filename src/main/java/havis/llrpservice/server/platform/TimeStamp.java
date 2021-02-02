package havis.llrpservice.server.platform;

import java.lang.management.ManagementFactory;

import havis.util.platform.Platform;
import havis.util.platform.PlatformException;

public class TimeStamp {
	private boolean isUtc;
	private long timestamp;

	public TimeStamp(Platform platform) throws PlatformException {
		// if hardware clock is available
		if (platform.hasUTCClock()) {
			isUtc = true;
			timestamp = System.currentTimeMillis();
		} else {
			isUtc = false;
			timestamp = platform.getUptime();
			// if hardware clock and system up time is not available
			if (timestamp < 0) {
				// use up time of JVM
				timestamp = ManagementFactory.getRuntimeMXBean().getUptime();
			}
		}
	}

	/**
	 * Whether UTC time stamp or up time.
	 */
	public boolean isUtc() {
		return isUtc;
	}

	/**
	 * Gets time stamp in milliseconds.
	 */
	public long getTimestamp() {
		return timestamp;
	}
}
