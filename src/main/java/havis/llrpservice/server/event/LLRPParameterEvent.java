package havis.llrpservice.server.event;

import havis.llrpservice.data.message.parameter.Parameter;

public class LLRPParameterEvent implements Event {

	private final Parameter parameter;
	private Object data;

	public LLRPParameterEvent(Parameter event) {
		this(event, null /* data */);
	}

	public LLRPParameterEvent(Parameter event, Object data) {
		this.parameter = event;
		this.data = data;
	}

	public Object getData() {
		return data;
	}

	@Override
	public EventType getEventType() {
		return EventType.LLRP_PARAMETER;
	}

	public Parameter getParameter() {
		return parameter;
	}

	@Override
	public String toString() {
		return "LLRPParameterEvent [parameter=" + parameter + "]";
	}
}
