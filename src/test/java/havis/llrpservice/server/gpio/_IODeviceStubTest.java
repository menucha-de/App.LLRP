package havis.llrpservice.server.gpio;

import java.util.List;

import havis.device.io.Configuration;
import havis.device.io.IOConsumer;
import havis.device.io.IODevice;
import havis.device.io.Type;
import havis.device.io.exception.ConnectionException;
import havis.device.io.exception.ImplementationException;
import havis.device.io.exception.ParameterException;

public class _IODeviceStubTest implements IODevice {

	@Override
	public void openConnection(IOConsumer consumer, int timeout)
			throws ConnectionException, ImplementationException {
	}

	@Override
	public void closeConnection() throws ConnectionException,
			ImplementationException {
	}

	@Override
	public List<Configuration> getConfiguration(Type type, short pin)
			throws ConnectionException, ParameterException,
			ImplementationException {
		return null;
	}

	@Override
	public void setConfiguration(List<Configuration> configuration)
			throws ConnectionException, ParameterException,
			ImplementationException {
	}

	@Override
	public void resetConfiguration() throws ImplementationException,
			ConnectionException, ImplementationException {
	}
}
