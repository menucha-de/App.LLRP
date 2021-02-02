package havis.llrpservice.server.event;

public interface EventQueueListener {
	public void added(EventQueue src, Event event);

	public void removed(EventQueue src, Event event);
}
