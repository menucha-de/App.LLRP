package havis.llrpservice.sbc.rfc.message;

import java.util.List;

import havis.device.rf.tag.Filter;
import havis.device.rf.tag.operation.TagOperation;

public class Execute extends Request {

	private final List<Short> antennas;
	private final List<Filter> filters;
	private final List<TagOperation> operations;

	public Execute(MessageHeader messageHeader, List<Short> antennas,
			List<Filter> filters, List<TagOperation> operations) {
		super(messageHeader, MessageType.EXECUTE);
		this.antennas = antennas;
		this.filters = filters;
		this.operations = operations;
	}

	public List<Short> getAntennas() {
		return antennas;
	}

	public List<Filter> getFilters() {
		return filters;
	}

	public List<TagOperation> getOperations() {
		return operations;
	}

	@Override
	public String toString() {
		return "Execute [antennas=" + antennas + ", filters=" + filters
				+ ", operations=" + operations + ", " + super.toString() + "]";
	}
}
