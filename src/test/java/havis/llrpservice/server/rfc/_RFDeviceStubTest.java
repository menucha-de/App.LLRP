package havis.llrpservice.server.rfc;

import havis.device.rf.RFConsumer;
import havis.device.rf.capabilities.Capabilities;
import havis.device.rf.capabilities.CapabilityType;
import havis.device.rf.configuration.Configuration;
import havis.device.rf.configuration.ConfigurationType;
import havis.device.rf.exception.ConnectionException;
import havis.device.rf.exception.ImplementationException;
import havis.device.rf.exception.ParameterException;
import havis.device.rf.tag.Filter;
import havis.device.rf.tag.TagData;
import havis.device.rf.tag.operation.TagOperation;

import java.util.List;

public class _RFDeviceStubTest implements havis.device.rf.RFDevice {

	@Override
	public void openConnection(RFConsumer consumer, int timeout)
			throws ConnectionException, ImplementationException {
		
	}

	@Override
	public void closeConnection() {
		
	}

	@Override
	public List<Capabilities> getCapabilities(CapabilityType type) {
		return null;
	}

	@Override
	public List<Configuration> getConfiguration(ConfigurationType type,
			short antennaID, short gpiPort, short gpoPort) {
		return null;
	}

	@Override
	public void setConfiguration(List<Configuration> configuration)
			throws ImplementationException {
		
	}

	@Override
	public void resetConfiguration() throws ImplementationException {
		
	}

	@Override
	public List<TagData> execute(List<Short> antennas, List<Filter> filter,
			List<TagOperation> operations) throws ParameterException,
			ImplementationException {
		return null;
	}

	@Override
	public List<String> getSupportedRegions() {
		return null;
	}

	@Override
	public void setRegion(String id) throws ParameterException,
			ImplementationException {
		
	}
	@Override
	public String getRegion() throws ConnectionException {
		return null;
	}

	@Override
	public void installFirmware() throws ImplementationException,
			ConnectionException {
	}
}