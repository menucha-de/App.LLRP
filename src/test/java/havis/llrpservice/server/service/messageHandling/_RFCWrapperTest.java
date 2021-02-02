package havis.llrpservice.server.service.messageHandling;

import havis.device.rf.capabilities.Capabilities;
import havis.device.rf.configuration.Configuration;
import havis.llrpservice.sbc.rfc.message.Message;

public class _RFCWrapperTest {
	private Capabilities rfcCaps = null;
	private Configuration rfcConf = null;
	private Message rfcMessage = null;

	public Message getRfcMessage() {
		return rfcMessage;
	}

	public void setRfcMessage(Message rfcMessage) {
		this.rfcMessage = rfcMessage;
	}

	public havis.device.rf.capabilities.Capabilities getRfcCaps() {
		return rfcCaps;
	}

	public void setRfcCaps(
			havis.device.rf.capabilities.Capabilities rfcCaps) {
		this.rfcCaps = rfcCaps;
	}

	public havis.device.rf.configuration.Configuration getRfcConf() {
		return rfcConf;
	}

	public void setRfcConf(
			havis.device.rf.configuration.Configuration rfcConf) {
		this.rfcConf = rfcConf;
	}
}
