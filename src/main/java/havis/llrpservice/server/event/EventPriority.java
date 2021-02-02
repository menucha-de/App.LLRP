package havis.llrpservice.server.event;

public class EventPriority {
	// the default priority used for all events without explicit priority
	public final static int DEFAULT = 0;
	// received LLRP messages and events from the LLRPMessageHandler (opened,
	// closed)
	public final static int LLRP = 10;
	// requests for canceling service instances
	public final static int SERVICE_INSTANCE = 20;
}
