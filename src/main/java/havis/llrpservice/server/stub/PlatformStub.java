package havis.llrpservice.server.stub;

import havis.util.platform.Platform;
import havis.util.platform.PlatformException;

public class PlatformStub implements Platform {

	@Override
	public void open() throws PlatformException {
	}

	@Override
	public void close() throws PlatformException {
	}

	/**
	 * @return <code>-1</code> (no uptime is available)
	 */
	@Override
	public long getUptime() throws PlatformException {
		return -1;
	}

	/**
	 * @return <code>false</code>
	 */
	@Override
	public boolean hasUTCClock() throws PlatformException {
		return false;
	}

}
