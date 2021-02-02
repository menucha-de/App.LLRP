package havis.llrpservice.server.service.messageHandling;

import havis.llrpservice.data.message.Message;
import havis.llrpservice.data.message.parameter.Parameter;

public class _LLRPWrapperTest {
	private Parameter llrpParameter = null;
	private Message llrpMessage = null;

	public Parameter getParameter() {
		return llrpParameter;
	}

	public void setParameter(Parameter parameter) {
		this.llrpParameter = parameter;
	}

	public Message getMessage() {
		return llrpMessage;
	}

	public void setMessage(Message message) {
		this.llrpMessage = message;
	}

}
