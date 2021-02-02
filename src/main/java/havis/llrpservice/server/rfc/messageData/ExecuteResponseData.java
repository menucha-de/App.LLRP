package havis.llrpservice.server.rfc.messageData;

import java.util.Map;

import havis.llrpservice.data.message.parameter.ProtocolId;

public class ExecuteResponseData {

	private long roSpecId;
	private int specIndex;
	private int antennaId;
	private ProtocolId protocolId;
	private int inventoryParameterSpecId;
	private boolean isLastResponse;

	private Map<Long, Long> tagDataAccessSpecIds;

	/**
	 * @param roSpecId
	 * @param specIndex
	 * @param antennaId
	 * @param protocolId
	 * @param tagDataAccessSpecIds
	 *            tagDataId -> accessSpecId
	 */
	public ExecuteResponseData(long roSpecId, int specIndex, int inventoryParameterSpecId,
			int antennaId, ProtocolId protocolId, Map<Long, Long> tagDataAccessSpecIds,
			boolean isLastResponse) {
		this.roSpecId = roSpecId;
		this.specIndex = specIndex;
		this.antennaId = antennaId;
		this.protocolId = protocolId;
		this.inventoryParameterSpecId = inventoryParameterSpecId;
		this.tagDataAccessSpecIds = tagDataAccessSpecIds;
		this.isLastResponse = isLastResponse;
	}

	public long getRoSpecId() {
		return roSpecId;
	}

	public int getSpecIndex() {
		return specIndex;
	}

	public int getInventoryParameterSpecId() {
		return inventoryParameterSpecId;
	}

	public int getAntennaId() {
		return antennaId;
	}

	public ProtocolId getProtocolId() {
		return protocolId;
	}

	/**
	 * Returns a map of TagDataIds to AccessSpecIds.
	 */
	public Map<Long, Long> getTagDataAccessSpecIds() {
		return tagDataAccessSpecIds;
	}

	public boolean isLastResponse() {
		return isLastResponse;
	}

	@Override
	public String toString() {
		return "ExecuteResponseData [roSpecId=" + roSpecId + ", specIndex=" + specIndex
				+ ", antennaId=" + antennaId + ", protocolId=" + protocolId
				+ ", inventoryParameterSpecId=" + inventoryParameterSpecId + ", isLastResponse="
				+ isLastResponse + ", tagDataAccessSpecIds=" + tagDataAccessSpecIds + "]";
	}
}
