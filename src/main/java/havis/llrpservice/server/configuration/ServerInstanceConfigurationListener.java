package havis.llrpservice.server.configuration;

public interface ServerInstanceConfigurationListener {
	public void updated(ServerInstanceConfiguration src,
			ServerConfiguration serverConf, Throwable exception);
}
