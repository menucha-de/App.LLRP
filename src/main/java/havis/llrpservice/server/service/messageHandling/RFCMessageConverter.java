package havis.llrpservice.server.service.messageHandling;

import havis.device.rf.configuration.Configuration;
import havis.llrpservice.data.message.parameter.AntennaConfiguration;
import havis.llrpservice.data.message.parameter.AntennaProperties;
import havis.llrpservice.data.message.parameter.RFReceiver;
import havis.llrpservice.data.message.parameter.RFTransmitter;

/**
 * Converts data structures from LLRP messages to data structures for the RF
 * controller interface.
 */
public class RFCMessageConverter {

	/**
	 * Converts a LLRP antenna configuration to an antenna configuration for the
	 * RFC interface.
	 * 
	 * @param antennaConfiguration
	 * @return The configuration
	 */
	public Configuration convert(AntennaConfiguration antennaConfiguration) {
		havis.device.rf.configuration.AntennaConfiguration rfcAntennaConfiguration = new havis.device.rf.configuration.AntennaConfiguration();
		rfcAntennaConfiguration.setId((short) antennaConfiguration.getAntennaID());
		RFReceiver rfReceiver = antennaConfiguration.getRfReceiver();
		if (rfReceiver != null) {
			rfcAntennaConfiguration
					.setReceiveSensitivity((short) rfReceiver.getReceiverSensitivity());
		}
		RFTransmitter rfTransmitter = antennaConfiguration.getRfTransmitter();
		if (rfTransmitter != null) {
			rfcAntennaConfiguration.setTransmitPower((short) rfTransmitter.getTransmitPower());
			rfcAntennaConfiguration.setHopTableID((short) rfTransmitter.getHopTableID());
			rfcAntennaConfiguration.setChannelIndex((short) rfTransmitter.getChannelIndex());
		}
		return rfcAntennaConfiguration;
	}

	/**
	 * Converts LLRP antenna properties to antenna properties for the RFC
	 * interface.
	 * 
	 * @param antennaProperty
	 * @return The configuration
	 */
	public Configuration convert(AntennaProperties antennaProperty) {
		havis.device.rf.configuration.AntennaProperties antennaProperties = new havis.device.rf.configuration.AntennaProperties();
		antennaProperties.setId((short) antennaProperty.getAntennaID());
		antennaProperties.setGain(antennaProperty.getAntennaGain());
		antennaProperties.setConnected(antennaProperty.isConnected());
		return antennaProperties;
	}
}
