package havis.llrpservice.sbc.rfc.message;

import havis.device.rf.tag.TagData;

public class GetOperations extends Request {

	private final TagData tag;

	public GetOperations(MessageHeader messageHeader, TagData tag) {
		super(messageHeader, MessageType.GET_OPERATIONS);
		this.tag = tag;
	}

	public TagData getTag() {
		return tag;
	}

	@Override
	public String toString() {
		return "GetOperations [tag=" + tag + ", " + super.toString() + "]";
	}
}
