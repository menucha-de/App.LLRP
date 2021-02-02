package havis.llrpservice.sbc.rfc.message;

import java.util.List;
import havis.device.rf.tag.TagData;
import havis.llrpservice.server.platform.TimeStamp;

public class ExecuteResponse extends Response {

	private final List<TagData> tagData;
	private TimeStamp timeStamp;

	public ExecuteResponse(MessageHeader messageHeader, List<TagData> tagData,
			TimeStamp timeStamp) {
		super(messageHeader, MessageType.EXECUTE_RESPONSE);
		this.tagData = tagData;
		this.timeStamp = timeStamp;
	}

	public List<TagData> getTagData() {
		return tagData;
	}

	public TimeStamp getTimeStamp() {
		return timeStamp;
	}

	@Override
	public String toString() {
		return "ExecuteResponse [tagData=" + tagData + ", timeStamp=" + timeStamp + ", super="
				+ super.toString() + "]";
	}
}
