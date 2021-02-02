package havis.llrpservice.server.service.messageHandling;

import havis.llrpservice.data.message.AddAccessSpec;
import havis.llrpservice.data.message.AddROSpec;
import havis.llrpservice.data.message.Message;
import havis.llrpservice.data.message.ProtocolVersion;
import havis.llrpservice.data.message.parameter.Custom;
import havis.llrpservice.data.message.parameter.LLRPStatus;
import havis.llrpservice.data.message.parameter.LLRPStatusCode;
import havis.llrpservice.data.message.parameter.ROSpecCurrentState;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;

import java.util.List;

/**
 * Validates the content of LLRP messages and parameters.
 */
public class LLRPMessageValidator {

	/**
	 * Checks if a message header contains a specific protocol version.
	 * 
	 * @param message
	 * @param protocolVersion
	 * @return The LLRPStatus
	 */
	public LLRPStatus validateProtocolVersion(Message message,
			ProtocolVersion protocolVersion) {
		ProtocolVersion requestVersion = message.getMessageHeader()
				.getVersion();
		LLRPStatusCode status = LLRPStatusCode.M_SUCCESS;
		String errorDescription = "";

		if (requestVersion.getValue() != protocolVersion.getValue()) {
			status = LLRPStatusCode.M_UNSUPPORTED_VERSION;
			errorDescription = "Invalid protocol version " + requestVersion
					+ " (expected " + protocolVersion + ")";
		}

		return new LLRPStatus(new TLVParameterHeader((byte) 0), status,
				errorDescription);
	}

	/**
	 * Checks if a message header contains a supported protocol version.
	 * 
	 * @param message
	 * @param supportedVersion
	 * @return The LLRPStatus
	 */
	public LLRPStatus validateSupportedVersion(Message message,
			ProtocolVersion supportedVersion) {
		ProtocolVersion requestVersion = message.getMessageHeader()
				.getVersion();
		LLRPStatusCode status = LLRPStatusCode.M_SUCCESS;
		String errorDescription = "";

		if (requestVersion.getValue() > supportedVersion.getValue()) {
			status = LLRPStatusCode.M_UNSUPPORTED_VERSION;
			errorDescription = "The protocol version " + requestVersion
					+ " from message header is not supported (up to "
					+ supportedVersion + ")";
		}

		return new LLRPStatus(new TLVParameterHeader((byte) 0), status,
				errorDescription);
	}

	/**
	 * Checks for an unsupported custom LLRP parameter.
	 * 
	 * @param customParameter
	 * @return The LLRPStatus
	 */
	public LLRPStatus validateCustomExtension(List<Custom> customParameter) {
		LLRPStatusCode status = LLRPStatusCode.M_SUCCESS;
		String errorDescription = "";
		if (customParameter != null && customParameter.size() > 0) {
			status = LLRPStatusCode.P_UNEXPECTED_PARAMETER;
			errorDescription = "Custom parameters are not supported";
		}
		return new LLRPStatus(new TLVParameterHeader((byte) 0), status,
				errorDescription);
	}

	/**
	 * Checks if a ROSpec of an AddROSpec message has a specific state.
	 * 
	 * @param spec
	 * @param state
	 * @return The LLRPStatus
	 */
	public LLRPStatus validateSpecState(AddROSpec spec, ROSpecCurrentState state) {
		LLRPStatusCode status = LLRPStatusCode.M_SUCCESS;
		String errorDescription = "";

		if (spec.getRoSpec().getCurrentState() != state) {
			status = LLRPStatusCode.M_FIELD_ERROR;
			errorDescription = "Invalid ROSpec state "
					+ spec.getRoSpec().getCurrentState() + " (expected "
					+ state + ")";
		}

		return new LLRPStatus(new TLVParameterHeader((byte) 0x00), status,
				errorDescription);
	}

	/**
	 * Checks if an AccessSpec of an AddAccessSpec message has a specific state.
	 * 
	 * @param spec
	 * @param state
	 * @return The LLRPStatus
	 */
	public LLRPStatus validateSpecState(AddAccessSpec spec, boolean state) {
		LLRPStatusCode status = LLRPStatusCode.M_SUCCESS;
		String errorDescription = "";

		AddAccessSpec addAccessSpec = (AddAccessSpec) spec;
		if (addAccessSpec.getAccessSpec().isCurrentState() != state) {
			status = LLRPStatusCode.M_FIELD_ERROR;
			errorDescription = "Invalid AccessSpec state '"
					+ addAccessSpec.getAccessSpec().isCurrentState()
					+ "' (expected '" + state + "')";
		}

		return new LLRPStatus(new TLVParameterHeader((byte) 0x00), status,
				errorDescription);
	}
}
