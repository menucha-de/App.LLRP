package havis.llrpservice.server.rfc.messageData;

public class ROReportSpecData {

	private long roSpecId;

	/**
	 * @param roSpecId
	 */
	public ROReportSpecData(long roSpecId) {
		this.roSpecId = roSpecId;
	}

	public long getRoSpecId() {
		return roSpecId;
	}

	@Override
	public String toString() {
		return "ROReportSpecData [roSpecId=" + roSpecId + "]";
	}

}
