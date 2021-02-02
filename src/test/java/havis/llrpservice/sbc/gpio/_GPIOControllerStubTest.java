package havis.llrpservice.sbc.gpio;

import havis.device.io.IOConsumer;
import havis.device.io.IODevice;
import havis.device.io.Type;

import java.util.List;

public class _GPIOControllerStubTest implements IODevice {

	public void setPort(String addr, int port) {
	}

	@Override
	public void openConnection(IOConsumer consumer, int timeout)
			throws havis.device.io.exception.ConnectionException,
			havis.device.io.exception.ImplementationException {
	}

	@Override
	public void closeConnection()
			throws havis.device.io.exception.ConnectionException,
			havis.device.io.exception.ImplementationException {
	}

	@Override
	public List<havis.device.io.Configuration> getConfiguration(Type type,
			short pin) throws havis.device.io.exception.ConnectionException,
			havis.device.io.exception.ParameterException,
			havis.device.io.exception.ImplementationException {
		return null;
	}

	@Override
	public void setConfiguration(
			List<havis.device.io.Configuration> configuration)
			throws havis.device.io.exception.ConnectionException,
			havis.device.io.exception.ParameterException,
			havis.device.io.exception.ImplementationException {
	}

	@Override
	public void resetConfiguration()
			throws havis.device.io.exception.ImplementationException,
			havis.device.io.exception.ConnectionException,
			havis.device.io.exception.ImplementationException {
	}
}
