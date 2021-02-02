package havis.llrpservice.sbc.rfc.message;

import java.util.List;
import havis.device.rf.tag.operation.TagOperation;

public class GetOperationsResponse extends Response {

	private final List<TagOperation> operations;

	public GetOperationsResponse(MessageHeader messageHeader,
			List<TagOperation> operations) {
		super(messageHeader, MessageType.GET_OPERATIONS_RESPONSE);
		this.operations = operations;
	}

	public List<TagOperation> getOperations() {
		return operations;
	}

	@Override
	public String toString() {
		return "GetOperationsResponse [operations=" + operations + ", "
				+ super.toString() + "]";
	}
}
