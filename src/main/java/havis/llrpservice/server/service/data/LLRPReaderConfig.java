package havis.llrpservice.server.service.data;

import java.util.Arrays;

import havis.llrpservice.data.message.SetReaderConfig;
import havis.llrpservice.data.message.parameter.AccessReportSpec;
import havis.llrpservice.data.message.parameter.AccessReportTrigger;
import havis.llrpservice.data.message.parameter.C1G2EPCMemorySelector;
import havis.llrpservice.data.message.parameter.EventNotificationState;
import havis.llrpservice.data.message.parameter.EventNotificationStateEventType;
import havis.llrpservice.data.message.parameter.EventsAndReports;
import havis.llrpservice.data.message.parameter.KeepaliveSpec;
import havis.llrpservice.data.message.parameter.KeepaliveSpecTriggerType;
import havis.llrpservice.data.message.parameter.ROReportSpec;
import havis.llrpservice.data.message.parameter.ROReportTrigger;
import havis.llrpservice.data.message.parameter.ReaderEventNotificationSpec;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.data.message.parameter.TagReportContentSelector;

/**
 * Provides the reader configuration which cannot be determined via the RF/GPIO
 * controllers.
 */
public class LLRPReaderConfig {
	private AccessReportSpec accessReportSpec;
	private EventsAndReports eventAndReports;
	private KeepaliveSpec keepaliveSpec;
	private ReaderEventNotificationSpec readerEventNotificationSpec;
	private ROReportSpec roReportSpec;

	public LLRPReaderConfig() {
		reset();
	}

	public void reset() {
		accessReportSpec = new AccessReportSpec(new TLVParameterHeader((byte) 0x00),
				AccessReportTrigger.WHENEVER_ROREPORT_IS_GENERATED);
		eventAndReports = new EventsAndReports(new TLVParameterHeader((byte) 0x00),
				false /* hold */);
		keepaliveSpec = new KeepaliveSpec(new TLVParameterHeader((byte) 0x00),
				KeepaliveSpecTriggerType.NULL, 0 /* timerInterval */);
		readerEventNotificationSpec = new ReaderEventNotificationSpec(
				new TLVParameterHeader((byte) 0x00),
				Arrays.asList(
						new EventNotificationState(new TLVParameterHeader((byte) 0x00),
								EventNotificationStateEventType.ROSPEC_EVENT, false),
						new EventNotificationState(new TLVParameterHeader((byte) 0x00),
								EventNotificationStateEventType.GPI_EVENT, false),
						new EventNotificationState(new TLVParameterHeader((byte) 0x00),
								EventNotificationStateEventType.READER_EXCEPTION_EVENT, false)));
		TagReportContentSelector contentSelector = new TagReportContentSelector(
				new TLVParameterHeader((byte) 0x00), true /* enableROSpecID */,
				true /* enableSpecIndex */, true /* enableInventoryParameterSpecID */,
				true /* enableAntennaID */, true /* enableChannelIndex */,
				true /* enablePeakRSSI */, false /* enableFirstSeenTimestamp */,
				false /* enableLastSeenTimestamp */, false /* enableTagSeenCount */,
				true /* enableAccessSpecID */);
		contentSelector.setC1g2EPCMemorySelectorList(
				Arrays.asList(new C1G2EPCMemorySelector(new TLVParameterHeader((byte) 0),
						true /* enableCRC */, true /* enablePCBits */, false /* enableXPCBits */)));
		roReportSpec = new ROReportSpec(new TLVParameterHeader((byte) 0x00),
				ROReportTrigger.UPON_N_TAGREPORTDATA_PARAMETERS_OR_END_OF_ROSPEC, 1 /* n */,
				contentSelector);
	}

	public void set(SetReaderConfig llrpMessage) {
		if (llrpMessage.getAccessReportSpec() != null) {
			accessReportSpec = llrpMessage.getAccessReportSpec();
		}
		if (llrpMessage.getEventAndReports() != null) {
			eventAndReports = llrpMessage.getEventAndReports();
		}
		if (llrpMessage.getKeepaliveSpec() != null) {
			keepaliveSpec = llrpMessage.getKeepaliveSpec();
		}
		if (llrpMessage.getReaderEventNotificationSpec() != null) {
			readerEventNotificationSpec = llrpMessage.getReaderEventNotificationSpec();
		}
		if (llrpMessage.getRoReportSpec() != null) {
			roReportSpec = llrpMessage.getRoReportSpec();
		}
	}

	public AccessReportSpec getAccessReportSpec() {
		return accessReportSpec;
	}

	public EventsAndReports getEventAndReports() {
		return eventAndReports;
	}

	public KeepaliveSpec getKeepaliveSpec() {
		return keepaliveSpec;
	}

	public ReaderEventNotificationSpec getReaderEventNotificationSpec() {
		return readerEventNotificationSpec;
	}

	public ROReportSpec getRoReportSpec() {
		return roReportSpec;
	}

	@Override
	public String toString() {
		return "LLRPReaderConfig [accessReportSpec=" + accessReportSpec + ", eventAndReports="
				+ eventAndReports + ", keepaliveSpec=" + keepaliveSpec
				+ ", readerEventNotificationSpec=" + readerEventNotificationSpec + ", roReportSpec="
				+ roReportSpec + "]";
	}
}
