package havis.llrpservice.server.service.messageHandling;

import havis.device.rf.configuration.Configuration;
import havis.llrpservice.data.message.parameter.AntennaConfiguration;
import havis.llrpservice.data.message.parameter.AntennaProperties;
import havis.llrpservice.data.message.parameter.RFReceiver;
import havis.llrpservice.data.message.parameter.RFTransmitter;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;

import org.testng.Assert;
import org.testng.annotations.Test;

public class RFCMessageConverterTest {

	@Test
	public void convertAntennaConfiguration() {
		RFCMessageConverter converter = new RFCMessageConverter();
		AntennaConfiguration antennaConfiguration = new AntennaConfiguration(
				new TLVParameterHeader((byte) 0), 1 /* antennaID */);
		RFReceiver rfReceiver = new RFReceiver(
				new TLVParameterHeader((byte) 0), 1 /* sensitivity */);
		antennaConfiguration.setRfReceiver(rfReceiver);
		RFTransmitter rfTransmitter = new RFTransmitter(new TLVParameterHeader(
				(byte) 0), 1 /* hopTableID */, 2 /* channelIndex */, 3 /* transmitPower */);
		antennaConfiguration.setRfTransmitter(rfTransmitter);
		Configuration conf = converter.convert(antennaConfiguration);
		havis.device.rf.configuration.AntennaConfiguration antennaConf = (havis.device.rf.configuration.AntennaConfiguration) conf;
		Assert.assertEquals(antennaConf.getId(),
				antennaConfiguration.getAntennaID());
		Assert.assertEquals((int) antennaConf.getReceiveSensitivity(),
				rfReceiver.getReceiverSensitivity());
		Assert.assertEquals((int) antennaConf.getChannelIndex(),
				rfTransmitter.getChannelIndex());
		Assert.assertEquals((int) antennaConf.getHopTableID(),
				rfTransmitter.getHopTableID());
		Assert.assertEquals((int) antennaConf.getTransmitPower(),
				rfTransmitter.getTransmitPower());
	}

	@Test
	public void convertAntennaProperties() {
		RFCMessageConverter converter = new RFCMessageConverter();
		AntennaProperties antennaProperties = new AntennaProperties();
		antennaProperties.setConnected(true);
		antennaProperties.setAntennaGain((short) 3);
		antennaProperties.setAntennaID((short) 4);
		Configuration conf = converter.convert(antennaProperties);
		havis.device.rf.configuration.AntennaProperties antennaConf = (havis.device.rf.configuration.AntennaProperties) conf;
		Assert.assertTrue(antennaConf.isConnected());
		Assert.assertEquals(antennaConf.getGain(),
				antennaProperties.getAntennaGain());
		Assert.assertEquals(antennaConf.getId(),
				antennaProperties.getAntennaID());
	}
}
