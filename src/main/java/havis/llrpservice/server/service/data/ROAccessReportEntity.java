package havis.llrpservice.server.service.data;

import java.io.Serializable;
import havis.llrpservice.data.message.ROAccessReport;
import havis.llrpservice.server.service.ROAccessReportDepot;

/**
 * Provides an entity for an {@link ROAccessReportDepot} with a ROAccessReport
 * and the relating ROSpecId. A report may not contain the ROSpecId due to the
 * content selector.
 */
public class ROAccessReportEntity implements Serializable {

	private static final long serialVersionUID = 5309836167751836418L;

	private long roSpecId;
	private ROAccessReport report;

	public long getRoSpecId() {
		return roSpecId;
	}

	public void setRoSpecId(long roSpecId) {
		this.roSpecId = roSpecId;
	}

	public ROAccessReport getReport() {
		return report;
	}

	public void setReport(ROAccessReport report) {
		this.report = report;
	}

	@Override
	public String toString() {
		return "ROAccessReportEntity [roSpecId=" + roSpecId + ", report=" + report + "]";
	}
}
