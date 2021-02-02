package havis.llrpservice.server.service.messageHandling;

import havis.device.rf.capabilities.CapabilityType;
import havis.device.rf.configuration.ConfigurationType;
import havis.llrpservice.data.message.GetReaderCapabilities;
import havis.llrpservice.data.message.GetReaderCapabilitiesRequestedData;
import havis.llrpservice.data.message.GetReaderConfig;
import havis.llrpservice.data.message.GetReaderConfigRequestedData;
import havis.llrpservice.data.message.SetReaderConfig;
import havis.llrpservice.data.message.parameter.AntennaConfiguration;
import havis.llrpservice.data.message.parameter.AntennaProperties;
import havis.llrpservice.data.message.parameter.RFReceiver;
import havis.llrpservice.data.message.parameter.RFTransmitter;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.server.service.messageHandling.RFCMessageCreator.RFCGetReaderConfigRequest;
import havis.llrpservice.server.service.messageHandling.RFCMessageCreator.RFCSetReaderConfigRequest;

import java.util.ArrayList;
import java.util.List;

import mockit.Delegate;
import mockit.Mocked;
import mockit.NonStrictExpectations;

import org.testng.Assert;
import org.testng.annotations.Test;

public class RFCMessageCreatorTest {

	@Test
	public void createRequestGetReaderCapabilities(
			@Mocked final GetReaderCapabilities llrpMessage) {
		class Data {
			GetReaderCapabilitiesRequestedData requestedData;
		}
		final Data data = new Data();
		new NonStrictExpectations() {
			{
				llrpMessage.getRequestedData();
				result = new Delegate<GetReaderCapabilities>() {
					@SuppressWarnings("unused")
					public GetReaderCapabilitiesRequestedData getRequestedData() {
						return data.requestedData;
					}
				};
			}
		};
		RFCMessageCreator creator = new RFCMessageCreator();
		for (GetReaderCapabilitiesRequestedData requestedData : GetReaderCapabilitiesRequestedData
				.values()) {
			data.requestedData = requestedData;
			List<CapabilityType> conf = creator.createRequest(llrpMessage);
			switch (requestedData) {
			case GENERAL_DEVICE_CAPABILITIES:
				Assert.assertEquals(conf.size(), 1);
				Assert.assertEquals(conf.get(0), CapabilityType.DEVICE_CAPABILITIES);
				break;
			case REGULATORY_CAPABILITIES:
				Assert.assertEquals(conf.size(), 1);
				Assert.assertEquals(conf.get(0), CapabilityType.REGULATORY_CAPABILITIES);
				break;
			case ALL:
				Assert.assertEquals(conf.size(), 2);
				Assert.assertTrue(conf.contains(CapabilityType.DEVICE_CAPABILITIES));
				Assert.assertTrue(conf.contains(CapabilityType.REGULATORY_CAPABILITIES));
				break;
			case C1G2_LLRP_CAPABILITIES:
			case LLRP_CAPABILITIES:
				Assert.assertEquals(conf.size(), 0);
			}
		}
	}

	@Test
	public void createRequestGetReaderConfig(@Mocked final GetReaderConfig llrpMessage) {
		class Data {
			GetReaderConfigRequestedData requestedData;
		}
		final Data data = new Data();
		new NonStrictExpectations() {
			{
				llrpMessage.getRequestedData();
				result = new Delegate<GetReaderConfig>() {
					@SuppressWarnings("unused")
					public GetReaderConfigRequestedData getRequestedData() {
						return data.requestedData;
					}
				};

				llrpMessage.getAntennaID();
				result = 1;
			}
		};
		RFCMessageCreator creator = new RFCMessageCreator();
		for (GetReaderConfigRequestedData requestedData : GetReaderConfigRequestedData.values()) {
			data.requestedData = requestedData;
			RFCGetReaderConfigRequest conf = creator.createRequest(llrpMessage);
			switch (requestedData) {
			case ANTENNA_CONFIGURATION:
				Assert.assertEquals(conf.getTypes().size(), 1);
				Assert.assertEquals(conf.getTypes().get(0),
						ConfigurationType.ANTENNA_CONFIGURATION);
				break;
			case ANTENNA_PROPERTIES:
				Assert.assertEquals(conf.getTypes().size(), 1);
				Assert.assertEquals(conf.getTypes().get(0), ConfigurationType.ANTENNA_PROPERTIES);
				break;
			case ALL:
				Assert.assertEquals(conf.getTypes().size(), 2);
				Assert.assertTrue(
						conf.getTypes().contains(ConfigurationType.ANTENNA_CONFIGURATION));
				Assert.assertTrue(conf.getTypes().contains(ConfigurationType.ANTENNA_PROPERTIES));
				break;
			case ACCESS_REPORT_SPEC:
			case EVENTS_AND_REPORTS:
			case GPI_CURRENT_STATE:
			case GPO_WRITE_DATA:
			case IDENTIFICATION:
			case KEEPALIVE_SPEC:
			case LLRP_CONFIGURATION_STATE_VALUE:
			case READER_EVENT_NOTIFICATION:
			case RO_REPORT_SPEC:
			}
			Assert.assertEquals(conf.getAntennaId(), 1);
		}
	}

	@Test
	public void createRequestSetReaderConfig(@Mocked final SetReaderConfig llrpMessage) {
		class Data {
			List<AntennaConfiguration> antennaConfigurationList;
			List<AntennaProperties> antennaPropertiesList;
		}
		final Data data = new Data();
		new NonStrictExpectations() {
			{
				llrpMessage.getAntennaConfigurationList();
				result = new Delegate<SetReaderConfig>() {
					@SuppressWarnings("unused")
					public List<AntennaConfiguration> getAntennaConfigurationList() {
						return data.antennaConfigurationList;
					}
				};

				llrpMessage.getAntennaPropertiesList();
				result = new Delegate<SetReaderConfig>() {
					@SuppressWarnings("unused")
					public List<AntennaProperties> getAntennaPropertiesList() {
						return data.antennaPropertiesList;
					}
				};

				llrpMessage.isResetToFactoryDefaults();
				result = true;
			}
		};
		RFCMessageCreator creator = new RFCMessageCreator();
		// create request from empty antenna configuration
		// an empty request is returned
		data.antennaConfigurationList = new ArrayList<>();
		AntennaConfiguration antennaConfiguration = new AntennaConfiguration(
				new TLVParameterHeader((byte) 0), 10 /* antennaID */);
		data.antennaConfigurationList.add(antennaConfiguration);
		RFCSetReaderConfigRequest conf = creator.createRequest(llrpMessage);
		Assert.assertEquals(conf.getConfigurations().size(), 0);

		// create request from full antenna data
		antennaConfiguration.setRfReceiver(
				new RFReceiver(new TLVParameterHeader((byte) 0), 1 /* receiverSensitivity */));
		antennaConfiguration.setRfTransmitter(new RFTransmitter(new TLVParameterHeader((byte) 0),
				2 /* hopTableID */, 3 /* channelIndex */, 4 /* transmitPower */));
		data.antennaPropertiesList = new ArrayList<>();
		AntennaProperties antennaProperties = new AntennaProperties(
				new TLVParameterHeader((byte) 0), true /* connected */, 7 /* antennaID */,
				(short) 8 /* antennaGain */);
		data.antennaPropertiesList.add(antennaProperties);
		conf = creator.createRequest(llrpMessage);
		Assert.assertEquals(conf.getConfigurations().size(), 2);
		havis.device.rf.configuration.AntennaProperties antennaProps = (havis.device.rf.configuration.AntennaProperties) conf
				.getConfigurations().get(0);
		Assert.assertEquals(antennaProps.getId(), antennaProperties.getAntennaID());
		havis.device.rf.configuration.AntennaConfiguration antennaConf = (havis.device.rf.configuration.AntennaConfiguration) conf
				.getConfigurations().get(1);
		Assert.assertEquals(antennaConf.getId(), antennaConfiguration.getAntennaID());
		Assert.assertTrue(conf.isReset());
	}
}
