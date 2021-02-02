package havis.llrpservice.server.service.messageHandling;

import havis.device.io.Direction;
import havis.device.io.IOConfiguration;
import havis.device.io.State;
import havis.device.io.StateEvent;
import havis.device.rf.capabilities.AntennaReceiveSensitivityRangeTableEntry;
import havis.device.rf.capabilities.DeviceCapabilities;
import havis.device.rf.capabilities.FreqHopTableEntry;
import havis.device.rf.capabilities.ReceiveSensitivityTableEntry;
import havis.device.rf.capabilities.TransmitPowerTableEntry;
import havis.llrpservice.data.message.parameter.AntennaConfiguration;
import havis.llrpservice.data.message.parameter.AntennaProperties;
import havis.llrpservice.data.message.parameter.CommunicationsStandard;
import havis.llrpservice.data.message.parameter.CountryCodes;
import havis.llrpservice.data.message.parameter.FixedFrequencyTable;
import havis.llrpservice.data.message.parameter.FrequencyHopTable;
import havis.llrpservice.data.message.parameter.FrequencyInformation;
import havis.llrpservice.data.message.parameter.GPIEvent;
import havis.llrpservice.data.message.parameter.GPIOCapabilities;
import havis.llrpservice.data.message.parameter.GPIPortCurrentState;
import havis.llrpservice.data.message.parameter.GPIPortCurrentStateGPIState;
import havis.llrpservice.data.message.parameter.GPOWriteData;
import havis.llrpservice.data.message.parameter.GeneralDeviceCapabilities;
import havis.llrpservice.data.message.parameter.LLRPCapabilities;
import havis.llrpservice.data.message.parameter.PerAntennaAirProtocol;
import havis.llrpservice.data.message.parameter.PerAntennaReceiveSensitivityRange;
import havis.llrpservice.data.message.parameter.ProtocolId;
import havis.llrpservice.data.message.parameter.RFReceiver;
import havis.llrpservice.data.message.parameter.RFTransmitter;
import havis.llrpservice.data.message.parameter.ReceiveSensitivityTabelEntry;
import havis.llrpservice.data.message.parameter.RegulatoryCapabilities;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.data.message.parameter.TransmitPowerLevelTableEntry;
import havis.llrpservice.data.message.parameter.UHFBandCapabilities;
import havis.llrpservice.data.message.parameter.UHFC1G2RFModeTable;
import havis.llrpservice.data.message.parameter.UHFC1G2RFModeTableEntry;
import havis.llrpservice.sbc.gpio.message.StateChanged;
import havis.llrpservice.server.service.data.LLRPReaderCapabilities;
import havis.llrpservice.xml.properties.LLRPCapabilitiesType;
import havis.util.platform.PlatformException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Converts data structures from the RF controller interface and the LLRP
 * configuration to data structures for LLRP messages.
 */
public class LLRPMessageConverter {

	public GeneralDeviceCapabilities convert(DeviceCapabilities rfcDeviceCapabilities,
			boolean canSetAntennaProperties, ProtocolId airProtocolId, int gpiPortCount,
			int gpoPortCount, boolean hasUTCClock) throws PlatformException {
		if (rfcDeviceCapabilities == null) {
			return null;
		}

		List<ReceiveSensitivityTabelEntry> receiveSensitivityTable = new ArrayList<>();
		if (rfcDeviceCapabilities.getReceiveSensitivityTable() != null) {
			for (ReceiveSensitivityTableEntry receiveSensitivityTableEntry : rfcDeviceCapabilities
					.getReceiveSensitivityTable().getEntryList()) {
				receiveSensitivityTable.add(new ReceiveSensitivityTabelEntry(
						new TLVParameterHeader((byte) 0), receiveSensitivityTableEntry.getIndex(),
						receiveSensitivityTableEntry.getReceiveSensitivity()));
			}
		}

		List<PerAntennaAirProtocol> airProtocolSupportedPerAntenna = new ArrayList<>();
		for (int i = 1; i <= rfcDeviceCapabilities.getNumberOfAntennas(); i++) {
			PerAntennaAirProtocol airProtocol = new PerAntennaAirProtocol(
					new TLVParameterHeader((byte) 0), i, Arrays.asList(airProtocolId));
			airProtocolSupportedPerAntenna.add(airProtocol);
		}

		GPIOCapabilities gpioCapabilities = new GPIOCapabilities(new TLVParameterHeader((byte) 0),
				gpiPortCount, gpoPortCount);

		GeneralDeviceCapabilities generalDeviceCapabilities = new GeneralDeviceCapabilities(
				new TLVParameterHeader((byte) 0x00), rfcDeviceCapabilities.getManufacturer(),
				rfcDeviceCapabilities.getModel(), rfcDeviceCapabilities.getFirmware(),
				airProtocolSupportedPerAntenna.size(), canSetAntennaProperties,
				receiveSensitivityTable, airProtocolSupportedPerAntenna, gpioCapabilities,
				hasUTCClock);

		List<PerAntennaReceiveSensitivityRange> perAntennaReceiveSensitivityRange = new ArrayList<>();
		if (rfcDeviceCapabilities.getAntennaReceiveSensitivityRangeTable() != null) {
			for (AntennaReceiveSensitivityRangeTableEntry entry : rfcDeviceCapabilities
					.getAntennaReceiveSensitivityRangeTable().getEntryList()) {
				PerAntennaReceiveSensitivityRange range = new PerAntennaReceiveSensitivityRange(
						new TLVParameterHeader((byte) 0), entry.getIndex(), entry.getMin(),
						entry.getMax());
				perAntennaReceiveSensitivityRange.add(range);
			}
		}
		generalDeviceCapabilities
				.setPerAntennaReceiveSensitivityRange(perAntennaReceiveSensitivityRange);

		return generalDeviceCapabilities;
	}

	public RegulatoryCapabilities convert(
			havis.device.rf.capabilities.RegulatoryCapabilities rfcRegulatoryCapabilities,
			LLRPReaderCapabilities readerCapabilities) {
		if (rfcRegulatoryCapabilities == null) {
			return null;
		}
		CountryCodes.CountryCode countryCode = CountryCodes
				.get(rfcRegulatoryCapabilities.getCountryCode());
		CommunicationsStandard communicationsStandard = CommunicationsStandard
				.get(rfcRegulatoryCapabilities.getCommunicationStandard());

		RegulatoryCapabilities regulatoryCap = new RegulatoryCapabilities(
				new TLVParameterHeader((byte) 0x00), countryCode, communicationsStandard);

		List<TransmitPowerLevelTableEntry> transmitPowerTable = new ArrayList<>();
		if (rfcRegulatoryCapabilities.getTransmitPowerTable() != null) {
			for (TransmitPowerTableEntry entry : rfcRegulatoryCapabilities.getTransmitPowerTable()
					.getEntryList()) {
				transmitPowerTable
						.add(new TransmitPowerLevelTableEntry(new TLVParameterHeader((byte) 0x00),
								entry.getIndex(), entry.getTransmitPower()));
			}
		}

		FrequencyInformation frequencyInformation;
		if (rfcRegulatoryCapabilities.isHopping()) {
			List<FrequencyHopTable> freqHopTables = new ArrayList<>();
			if (rfcRegulatoryCapabilities.getFreqHopTable() != null) {
				for (FreqHopTableEntry entry : rfcRegulatoryCapabilities.getFreqHopTable()
						.getEntryList()) {
					List<Long> freqList = new ArrayList<>();
					for (Integer freq : entry.getFreqList()) {
						freqList.add(freq.longValue());
					}
					freqHopTables.add(new FrequencyHopTable(new TLVParameterHeader((byte) 0x00),
							entry.getIndex(), freqList));
				}
			}
			frequencyInformation = new FrequencyInformation(new TLVParameterHeader((byte) 0x00),
					freqHopTables);
		} else {
			List<Long> freqList = new ArrayList<>();
			if (rfcRegulatoryCapabilities.getFixedFreqTable() != null) {
				for (Integer freq : rfcRegulatoryCapabilities.getFixedFreqTable().getFreqList()) {
					freqList.add(freq.longValue());
				}
			}
			frequencyInformation = new FrequencyInformation(new TLVParameterHeader((byte) 0x00),
					new FixedFrequencyTable(new TLVParameterHeader((byte) 0x00), freqList));
		}

		List<UHFC1G2RFModeTableEntry> entries = new ArrayList<>();
		entries.add(readerCapabilities.getUHFC1G2RFModeTableEntry());
		UHFC1G2RFModeTable table = new UHFC1G2RFModeTable(new TLVParameterHeader((byte) 0x00),
				entries);
		List<UHFC1G2RFModeTable> uhfC1G2RFModeTable = new ArrayList<>();
		uhfC1G2RFModeTable.add(table);

		if (transmitPowerTable.size() > 0 && uhfC1G2RFModeTable.size() > 0) {
			regulatoryCap.setUhfBandCapabilities(
					new UHFBandCapabilities(new TLVParameterHeader((byte) 0x00), transmitPowerTable,
							frequencyInformation, uhfC1G2RFModeTable));
		}

		return regulatoryCap;
	}

	public AntennaConfiguration convert(
			havis.device.rf.configuration.AntennaConfiguration antennaConfiguration) {
		if (antennaConfiguration == null) {
			return null;
		}
		AntennaConfiguration llrpAntennaConfiguration = new AntennaConfiguration(
				new TLVParameterHeader((byte) 0x00), antennaConfiguration.getId());
		if (antennaConfiguration.getReceiveSensitivity() != null) {
			RFReceiver rfRece = new RFReceiver(new TLVParameterHeader((byte) 0x00),
					antennaConfiguration.getReceiveSensitivity());
			llrpAntennaConfiguration.setRfReceiver(rfRece);
		}
		if (antennaConfiguration.getHopTableID() != null
				&& antennaConfiguration.getChannelIndex() != null
				&& antennaConfiguration.getTransmitPower() != null) {
			RFTransmitter rfTran = new RFTransmitter(new TLVParameterHeader((byte) 0x00),
					antennaConfiguration.getHopTableID(), antennaConfiguration.getChannelIndex(),
					antennaConfiguration.getTransmitPower());
			llrpAntennaConfiguration.setRfTransmitter(rfTran);
		}
		return llrpAntennaConfiguration;
	}

	public AntennaProperties convert(
			havis.device.rf.configuration.AntennaProperties antennaProperties) {
		if (antennaProperties == null) {
			return null;
		}
		AntennaProperties llrpAntennaProperties = new AntennaProperties(
				new TLVParameterHeader((byte) 0x00), antennaProperties.isConnected(),
				antennaProperties.getId(), antennaProperties.getGain());
		return llrpAntennaProperties;
	}

	/**
	 * Converts a IO configuration from GPIO interface to LLRP GPI data. If it
	 * is a GPO then <code>null</code> is returned.
	 * 
	 * @param ioConfiguration
	 * @return The GPI data
	 */
	public GPIPortCurrentState convertGPIConfig(IOConfiguration ioConfiguration) {
		if (ioConfiguration == null || ioConfiguration.getDirection() != Direction.INPUT) {
			return null;
		}
		GPIPortCurrentStateGPIState state = GPIPortCurrentStateGPIState.UNKNOWN;
		if (ioConfiguration.getState() != null) {
			switch (ioConfiguration.getState()) {
			case HIGH:
				state = GPIPortCurrentStateGPIState.HIGH;
				break;
			case LOW:
				state = GPIPortCurrentStateGPIState.LOW;
				break;
			case UNKNOWN:
			}
		}
		return new GPIPortCurrentState(new TLVParameterHeader((byte) 0), ioConfiguration.getId(),
				ioConfiguration.isEnable(), state);
	}

	/**
	 * Converts a IO configuration from GPIO interface to LLRP GPO data. If it
	 * is a GPI then <code>null</code> is returned. If the GPIO module returns a
	 * state {@link State#UNKNOWN} then it is assumed that the port is low.
	 * 
	 * @param IOConfiguration
	 * @return The GPO data
	 */
	public GPOWriteData convertGPOConfig(IOConfiguration IOConfiguration) {
		if (IOConfiguration == null || IOConfiguration.getDirection() != Direction.OUTPUT) {
			return null;
		}
		return new GPOWriteData(new TLVParameterHeader((byte) 0), IOConfiguration.getId(),
				IOConfiguration.getState() == State.HIGH);
	}

	/**
	 * Converts a state change event from GPIO interface to LLRP GPI event. If
	 * the GPIO module returns a state {@link State#UNKNOWN} then it is assumed
	 * that the port is low.
	 * 
	 * @param gpiEvent
	 * @return The GPIEvent
	 */
	public GPIEvent convert(StateChanged gpiEvent) {
		if (gpiEvent == null) {
			return null;
		}
		StateEvent state = gpiEvent.getStateEvent();
		return new GPIEvent(new TLVParameterHeader((byte) 0), state.getId(),
				state.getState() == State.HIGH);
	}

	public LLRPCapabilities convert(LLRPCapabilitiesType llrpCapabilities) {
		return new LLRPCapabilities(new TLVParameterHeader((byte) 0),
				llrpCapabilities.isCanDoRFSurvey(), llrpCapabilities.isCanReportBufferFillWarning(),
				llrpCapabilities.isSupportsClientRequestOpSpec(),
				llrpCapabilities.isCanDoTagInventoryStateAwareSingulation(),
				llrpCapabilities.isSupportsEventAndReportHolding(),
				llrpCapabilities.getMaxPriorityLevelSupported(),
				llrpCapabilities.getClientRequestOpSpecTimeout(),
				llrpCapabilities.getMaxNumROSpecs(), llrpCapabilities.getMaxNumSpecsPerROSpec(),
				llrpCapabilities.getMaxNumInventoryParameterSpecsPerAISpec(),
				llrpCapabilities.getMaxNumAccessSpecs(),
				llrpCapabilities.getMaxNumOpSpecsPerAccessSpec());
	}
}
