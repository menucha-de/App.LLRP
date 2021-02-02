package havis.llrpservice.server.stub;

import havis.device.rf.RFConsumer;
import havis.device.rf.capabilities.AntennaReceiveSensitivityRangeTable;
import havis.device.rf.capabilities.AntennaReceiveSensitivityRangeTableEntry;
import havis.device.rf.capabilities.Capabilities;
import havis.device.rf.capabilities.CapabilityType;
import havis.device.rf.capabilities.DeviceCapabilities;
import havis.device.rf.capabilities.FixedFreqTable;
import havis.device.rf.capabilities.FreqHopTable;
import havis.device.rf.capabilities.ReceiveSensitivityTable;
import havis.device.rf.capabilities.ReceiveSensitivityTableEntry;
import havis.device.rf.capabilities.RegulatoryCapabilities;
import havis.device.rf.capabilities.TransmitPowerTable;
import havis.device.rf.configuration.Configuration;
import havis.device.rf.configuration.ConfigurationType;
import havis.device.rf.exception.ConnectionException;
import havis.device.rf.exception.ImplementationException;
import havis.device.rf.exception.ParameterException;
import havis.device.rf.tag.Filter;
import havis.device.rf.tag.TagData;
import havis.device.rf.tag.operation.ReadOperation;
import havis.device.rf.tag.operation.RequestOperation;
import havis.device.rf.tag.operation.TagOperation;
import havis.device.rf.tag.result.OperationResult;
import havis.device.rf.tag.result.ReadResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RFDeviceStub implements havis.device.rf.RFDevice {

	private Logger log = Logger.getLogger(RFDeviceStub.class.getName());

	private final long GET_CAPABILITIES_DELAY = 20; // ms
	private final long GET_CONFIGURATION_DELAY = 15; // ms
	private final long EXECUTE_DELAY = 2; // ms

	private RFConsumer consumer;

	@Override
	public void openConnection(RFConsumer consumer, int timeout)
			throws ConnectionException, ImplementationException {
		this.consumer = consumer;
	}

	@Override
	public void closeConnection() {
	}

	@Override
	public List<Capabilities> getCapabilities(CapabilityType type) {
		try {
			Thread.sleep(GET_CAPABILITIES_DELAY);
		} catch (InterruptedException e) {
			log.log(Level.SEVERE, "Sleeping in 'getCapabilities' was interrupted", e);
		}

		DeviceCapabilities devCaps = new DeviceCapabilities();

		ReceiveSensitivityTable receiveSensitivityTable = new ReceiveSensitivityTable();
		ReceiveSensitivityTableEntry receiveSensitivityTableEntry = new ReceiveSensitivityTableEntry();
		receiveSensitivityTableEntry.setIndex((short) 1);
		receiveSensitivityTableEntry.setReceiveSensitivity((short) 125);
		receiveSensitivityTable.getEntryList().add(receiveSensitivityTableEntry);
		devCaps.setReceiveSensitivityTable(receiveSensitivityTable);

		AntennaReceiveSensitivityRangeTable antennaReceiveSensitivityRangeTable = new AntennaReceiveSensitivityRangeTable();
		AntennaReceiveSensitivityRangeTableEntry antennaReceiveSensitivityRangeTableEntry = new AntennaReceiveSensitivityRangeTableEntry();
		antennaReceiveSensitivityRangeTable.getEntryList()
				.add(antennaReceiveSensitivityRangeTableEntry);
		devCaps.setAntennaReceiveSensitivityRangeTable(antennaReceiveSensitivityRangeTable);

		devCaps.setFirmware("0.0.9");
		devCaps.setManufacturer((short) 1);
		devCaps.setModel((short) 2);
		devCaps.setMaxReceiveSensitivity((short) 1);
		devCaps.setNumberOfAntennas((short) 1);

		RegulatoryCapabilities regCaps = new RegulatoryCapabilities();
		regCaps.setCommunicationStandard((short) 2);
		regCaps.setCountryCode((short) 276);
		regCaps.setHopping(false);
		regCaps.setFixedFreqTable(new FixedFreqTable());
		regCaps.setFreqHopTable(new FreqHopTable());
		regCaps.setTransmitPowerTable(new TransmitPowerTable());

		return new ArrayList<Capabilities>(Arrays.asList(devCaps, regCaps));
	}

	@Override
	public List<Configuration> getConfiguration(ConfigurationType type, short antennaID,
			short gpiPort, short gpoPort) {
		try {
			Thread.sleep(GET_CONFIGURATION_DELAY);
		} catch (InterruptedException e) {
			log.log(Level.SEVERE, "Sleeping in 'getConfiguration' was interrupted", e);
		}
		return new ArrayList<Configuration>();
	}

	@Override
	public void setConfiguration(List<Configuration> configuration) throws ImplementationException {
	}

	@Override
	public String getRegion() throws ConnectionException {
		return null;
	}

	@Override
	public void resetConfiguration() throws ImplementationException {
	}

	@Override
	public List<TagData> execute(List<Short> antennas, List<Filter> filter,
			List<TagOperation> operations) throws ParameterException, ImplementationException {
		try {
			Thread.sleep(EXECUTE_DELAY);
		} catch (InterruptedException e) {
			throw new ImplementationException("Sleeping in 'execute' was interrupted", e);
		}
		List<TagData> result = new ArrayList<>();
		long id = 25;
		// short[] epc = {(short)0xAFFE, (short)0xAFFE, 0x1234, 0x5678,
		// (short)0xAFFE, (short)0xAFFE};
		byte[] epc = { (byte) 0xAF, (byte) 0xFE, (byte) 0xAF, (byte) 0xFE, (byte) 0x12, (byte) 0x34,
				(byte) 0x56, (byte) 0x78, (byte) 0xAF, (byte) 0xFE, (byte) 0xAF, (byte) 0xFE };
		short pc = 0x3000;
		int xpc = 0;
		short antennaID = 1;
		byte rssi = (byte) (-128 + Math.random() * 256);
		short channel = 1;
		List<OperationResult> results = new ArrayList<>();

		TagData tagData = new TagData();
		tagData.setTagDataId(id);
		tagData.setEpc(epc);
		tagData.setPc(pc);
		tagData.setXpc(xpc);
		tagData.setAntennaID(antennaID);
		tagData.setRssi(rssi);
		tagData.setChannel(channel);
		tagData.setResultList(results);
		tagData.setCrc((short) 0x1234);
		for (TagOperation op : operations) {
			if (op instanceof ReadOperation) {
				// short[] readData = {(short)0xAFFE, (short)0xAFFE,
				// (short)0xAFFE, (short)0xAFFE, 0x1234, 0x5678, (short)0xAFFE,
				// (short)0xAFFE};
				byte[] readData = { (byte) 0xAF, (byte) 0xFE, (byte) 0xAF, (byte) 0xFE, (byte) 0xAF,
						(byte) 0xFE, (byte) 0xAF, (byte) 0xFE, (byte) 0x12, (byte) 0x34,
						(byte) 0x56, (byte) 0x78, (byte) 0xAF, (byte) 0xFE, (byte) 0xAF,
						(byte) 0xFE };

				ReadResult readResult = new ReadResult();
				readResult.setOperationId(op.getOperationId());
				readResult.setReadData(readData);
				readResult.setResult(ReadResult.Result.SUCCESS);
				results.add(readResult);
			} else if (op instanceof RequestOperation) {
				List<TagOperation> operations2 = consumer.getOperations(tagData);
				for (TagOperation op2 : operations2) {
					byte[] readData = { 0x12, 0x34, 0x56, 0x78 };
					ReadResult readResult = new ReadResult();
					readResult.setOperationId(op2.getOperationId());
					readResult.setReadData(readData);
					readResult.setResult(ReadResult.Result.SUCCESS);
					results.add(readResult);
				}
			}
		}
		result.add(tagData);

		return result;
	}

	@Override
	public List<String> getSupportedRegions() {
		return null;
	}

	@Override
	public void setRegion(String id) throws ParameterException, ImplementationException {

	}

	@Override
	public void installFirmware() throws ImplementationException {

	}
}
