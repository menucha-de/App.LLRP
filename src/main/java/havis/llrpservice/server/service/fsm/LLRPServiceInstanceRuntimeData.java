package havis.llrpservice.server.service.fsm;

import havis.util.platform.Platform;

public class LLRPServiceInstanceRuntimeData {
	private final int unexpectedTimeout;
	private final Platform platform;

	/**
	 * @param platform
	 * @param roSpecsManager
	 * @param unexpectedTimeout
	 *            time out in seconds o
	 */
	public LLRPServiceInstanceRuntimeData(Platform platform, int unexpectedTimeout) {
		this.platform = platform;
		this.unexpectedTimeout = unexpectedTimeout;
	}

	public Platform getPlatform() {
		return platform;
	}

	public int getUnexpectedTimeout() {
		return unexpectedTimeout;
	}

	@Override
	public String toString() {
		return "LLRPServiceInstanceRuntimeData [unexpectedTimeout=" + unexpectedTimeout
				+ ", platform=" + platform + "]";
	}
}
