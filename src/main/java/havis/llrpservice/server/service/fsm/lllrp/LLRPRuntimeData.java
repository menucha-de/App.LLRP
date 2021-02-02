package havis.llrpservice.server.service.fsm.lllrp;

import java.util.ArrayList;
import java.util.List;

import havis.llrpservice.data.message.Message;
import havis.llrpservice.data.message.ProtocolVersion;
import havis.llrpservice.data.message.parameter.Identification;
import havis.llrpservice.data.message.parameter.LLRPConfigurationStateValue;
import havis.llrpservice.data.message.parameter.LLRPStatus;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.server.llrp.LLRPMessageHandler;
import havis.llrpservice.server.service.ROAccessReportDepot;
import havis.llrpservice.server.service.ROSpecsManager;
import havis.llrpservice.server.service.data.LLRPReaderConfig;
import havis.llrpservice.server.service.messageHandling.LLRPMessageCreator;
import havis.llrpservice.server.service.messageHandling.LLRPMessageValidator;
import havis.llrpservice.server.service.messageHandling.ROAccessReportCreator;
import havis.llrpservice.xml.properties.IdentificationSourceType;
import havis.llrpservice.xml.properties.LLRPCapabilitiesType;

public class LLRPRuntimeData {

	public final ProtocolVersion INITIAL_PROTOCOL_VERSION = ProtocolVersion.LLRP_V1_0_1;
	public final ProtocolVersion SUPPORTED_PROTOCOL_VERSION = ProtocolVersion.LLRP_V1_1;

	private final LLRPMessageValidator messageValidator = new LLRPMessageValidator();
	private final LLRPMessageCreator messageCreator = new LLRPMessageCreator();
	private final LLRPMessageHandler messageHandler;
	private final ROSpecsManager roSpecsManager;
	private final ROAccessReportCreator reportCreator = new ROAccessReportCreator();
	private final ROAccessReportDepot reportDepot;

	/**
	 * The currently processed messages (while a LLRP request is processed LLRP
	 * messages like KEEPALIVE_ACK can be received).
	 */
	private final List<CurrentMessage> currentMessages = new ArrayList<>();
	private ProtocolVersion negotiatedProtocolVersion;
	/**
	 * The configured identification source
	 * (LLRPServerProperties.xml/LLRPServerInstanceProperties.xml).
	 */
	private IdentificationSourceType identificationSource;
	/**
	 * The configured LLRP capabilities
	 * (LLRPServerProperties.xml/LLRPServerInstanceProperties.xml).
	 */
	private final LLRPCapabilitiesType llrpCapabilities;
	/**
	 * The reader configuration. It can be modified by the LLRP client with the
	 * SetReaderConfig message.
	 */
	private final LLRPReaderConfig readerConfig = new LLRPReaderConfig();
	private Identification identification = null;
	private final LLRPConfigurationStateValue llrpConfigStateValue = new LLRPConfigurationStateValue(
			new TLVParameterHeader((byte) 0x00), 0);
	private boolean restartServer;

	public class CurrentMessage {
		private final Message message;
		private LLRPStatus status;

		private CurrentMessage(Message message, LLRPStatus status) {
			this.message = message;
			this.status = status;
		}

		public Message getMessage() {
			return message;
		}

		public void setStatus(LLRPStatus status) {
			this.status = status;
		}

		public LLRPStatus getStatus() {
			return status;
		}

		@Override
		public String toString() {
			return "CurrentMessage [message=" + message + ", status=" + status + "]";
		}
	}

	public LLRPRuntimeData(IdentificationSourceType identificationSource,
			LLRPCapabilitiesType llrpCapabilities, LLRPMessageHandler messageHandler,
			ROSpecsManager roSpecsManager, ROAccessReportDepot reportDepot) {
		this.identificationSource = identificationSource;
		this.llrpCapabilities = llrpCapabilities;
		this.messageHandler = messageHandler;
		this.roSpecsManager = roSpecsManager;
		this.reportDepot = reportDepot;
	}

	public IdentificationSourceType getIdentificationSource() {
		return identificationSource;
	}

	public LLRPCapabilitiesType getLLRPCapabilities() {
		return llrpCapabilities;
	}

	public LLRPMessageHandler getMessageHandler() {
		return messageHandler;
	}

	public ROSpecsManager getRoSpecsManager() {
		return roSpecsManager;
	}

	public ROAccessReportCreator getROAccessReportCreator() {
		return reportCreator;
	}

	public ROAccessReportDepot getROAccessReportDepot() {
		return reportDepot;
	}

	public LLRPMessageValidator getMessageValidator() {
		return messageValidator;
	}

	public LLRPMessageCreator getMessageCreator() {
		return messageCreator;
	}

	public List<CurrentMessage> getCurrentMessages() {
		return currentMessages;
	}

	public CurrentMessage getCurrentMessage() {
		int size = currentMessages.size();
		return size == 0 ? null : currentMessages.get(size - 1);
	}

	public CurrentMessage getCurrentMessage(long messageId) {
		for (CurrentMessage currentMessage : currentMessages) {
			if (currentMessage.message.getMessageHeader().getId() == messageId) {
				return currentMessage;
			}
		}
		return null;
	}

	public void addCurrentMessage(Message message, LLRPStatus status) {
		currentMessages.add(new CurrentMessage(message, status));
	}

	public void removeCurrentMessage(long messageId) {
		CurrentMessage remove = null;
		for (CurrentMessage currentMessage : currentMessages) {
			if (currentMessage.message.getMessageHeader().getId() == messageId) {
				remove = currentMessage;
				break;
			}
		}
		if (remove != null) {
			currentMessages.remove(remove);
		}
	}

	public boolean isProtocolVersionNegotiated() {
		return negotiatedProtocolVersion != null;
	}

	public void setNegotiatedProtocolVersion(ProtocolVersion protocolVersion) {
		this.negotiatedProtocolVersion = protocolVersion;
	}

	public ProtocolVersion getProtocolVersion() {
		return negotiatedProtocolVersion == null ? INITIAL_PROTOCOL_VERSION
				: negotiatedProtocolVersion;
	}

	public LLRPReaderConfig getReaderConfig() {
		return readerConfig;
	}

	public Identification getIdentification() {
		return identification;
	}

	public void setIdentification(Identification id) {
		identification = id;
	}

	public LLRPConfigurationStateValue getLLRPConfigStateValue() {
		return llrpConfigStateValue;
	}

	public boolean isRestartServer() {
		return restartServer;
	}

	public void setRestartServer(boolean restartServer) {
		this.restartServer = restartServer;
	}

	@Override
	public String toString() {
		return "LLRPRuntimeData [INITIAL_PROTOCOL_VERSION=" + INITIAL_PROTOCOL_VERSION
				+ ", SUPPORTED_PROTOCOL_VERSION=" + SUPPORTED_PROTOCOL_VERSION
				+ ", messageValidator=" + messageValidator + ", messageCreator=" + messageCreator
				+ ", messageHandler=" + messageHandler + ", roSpecsManager=" + roSpecsManager
				+ ", currentMessages=" + currentMessages + ", negotiatedProtocolVersion="
				+ negotiatedProtocolVersion + ", identificationSource=" + identificationSource
				+ ", llrpCapabilities=" + llrpCapabilities + ", readerConfig=" + readerConfig
				+ ", identification=" + identification + ", llrpConfigStateValue="
				+ llrpConfigStateValue + ", restartServer=" + restartServer + "]";
	}
}