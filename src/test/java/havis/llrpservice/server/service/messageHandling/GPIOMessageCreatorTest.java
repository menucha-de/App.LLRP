package havis.llrpservice.server.service.messageHandling;

import havis.device.io.Direction;
import havis.device.io.IOConfiguration;
import havis.device.io.Type;
import havis.llrpservice.data.message.GetReaderCapabilities;
import havis.llrpservice.data.message.GetReaderCapabilitiesRequestedData;
import havis.llrpservice.data.message.GetReaderConfig;
import havis.llrpservice.data.message.GetReaderConfigRequestedData;
import havis.llrpservice.data.message.SetReaderConfig;
import havis.llrpservice.data.message.parameter.GPIPortCurrentState;
import havis.llrpservice.data.message.parameter.GPIPortCurrentStateGPIState;
import havis.llrpservice.data.message.parameter.GPOWriteData;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.server.service.messageHandling.GPIOMessageCreator.GPIOGetReaderConfigRequest;
import havis.llrpservice.server.service.messageHandling.GPIOMessageCreator.GPIOSetReaderConfigRequest;

import java.util.ArrayList;
import java.util.List;

import mockit.Delegate;
import mockit.Mocked;
import mockit.NonStrictExpectations;

import org.testng.Assert;
import org.testng.annotations.Test;

public class GPIOMessageCreatorTest {

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
		GPIOMessageCreator creator = new GPIOMessageCreator();

		for (GetReaderCapabilitiesRequestedData requestedData : GetReaderCapabilitiesRequestedData
				.values()) {
			data.requestedData = requestedData;
			List<Type> request = creator.createRequest(llrpMessage);
			switch (requestedData) {
			case ALL:
			case GENERAL_DEVICE_CAPABILITIES:
				Assert.assertEquals(request.size(), 1);
				Assert.assertEquals(request.get(0), Type.IO);
				break;
			case C1G2_LLRP_CAPABILITIES:
			case LLRP_CAPABILITIES:
			case REGULATORY_CAPABILITIES:
				Assert.assertEquals(request.size(), 0);
			}
		}
	}

	@Test
	public void createRequestGetReaderConfig(
			@Mocked final GetReaderConfig llrpMessage) {
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

				llrpMessage.getGpiPortNum();
				result = 3;

				llrpMessage.getGpoPortNum();
				result = 4;
			}
		};
		GPIOMessageCreator creator = new GPIOMessageCreator();

		for (GetReaderConfigRequestedData requestedData : GetReaderConfigRequestedData
				.values()) {
			data.requestedData = requestedData;
			GPIOGetReaderConfigRequest request = creator
					.createRequest(llrpMessage);
			switch (requestedData) {
			case ALL:
			case GPI_CURRENT_STATE:
			case GPO_WRITE_DATA:
				Assert.assertEquals(request.getTypes().size(), 1);
				Assert.assertEquals(request.getTypes().get(0), Type.IO);
				Assert.assertEquals(request.getGpiPortNum(), 3);
				Assert.assertEquals(request.getGpoPortNum(), 4);
				break;
			case ACCESS_REPORT_SPEC:
			case ANTENNA_CONFIGURATION:
			case ANTENNA_PROPERTIES:
			case EVENTS_AND_REPORTS:
			case IDENTIFICATION:
			case KEEPALIVE_SPEC:
			case LLRP_CONFIGURATION_STATE_VALUE:
			case READER_EVENT_NOTIFICATION:
			case RO_REPORT_SPEC:
				Assert.assertEquals(request.getTypes().size(), 0);
			}
		}
	}

	@Test
	public void createRequestSetReaderConfig(
			@Mocked final SetReaderConfig llrpMessage) {
		class Data {
			List<GPIPortCurrentState> gpiPortCurrentStateList;
			List<GPOWriteData> gpoWriteDataList;
		}
		final Data data = new Data();
		new NonStrictExpectations() {
			{
				llrpMessage.getGpiPortCurrentStateList();
				result = new Delegate<SetReaderConfig>() {
					@SuppressWarnings("unused")
					public List<GPIPortCurrentState> getGpiPortCurrentStateList() {
						return data.gpiPortCurrentStateList;
					}
				};

				llrpMessage.getGpoWriteDataList();
				result = new Delegate<SetReaderConfig>() {
					@SuppressWarnings("unused")
					public List<GPOWriteData> getGpoWriteDataList() {
						return data.gpoWriteDataList;
					}
				};

				llrpMessage.isResetToFactoryDefaults();
				result = true;
			}
		};
		GPIOMessageCreator creator = new GPIOMessageCreator();
		data.gpiPortCurrentStateList = new ArrayList<>();
		data.gpiPortCurrentStateList.add(new GPIPortCurrentState(
				new TLVParameterHeader((byte) 0), 1 /* gpiPortNum */,
				false /* gpiConfig */, GPIPortCurrentStateGPIState.HIGH));
		GPIOSetReaderConfigRequest conf = creator.createRequest(llrpMessage);
		Assert.assertEquals(conf.getConfigurations().size(), 1);
		Assert.assertTrue(conf.getConfigurations().get(0) instanceof IOConfiguration);
		Assert.assertTrue(conf.isReset());

		data.gpoWriteDataList = new ArrayList<>();
		data.gpoWriteDataList.add(new GPOWriteData(new TLVParameterHeader(
				(byte) 0), 2 /* gpiPortNum */, true /* gpoState */));
		conf = creator.createRequest(llrpMessage);
		Assert.assertEquals(conf.getConfigurations().size(), 2);
		IOConfiguration ioConf = (IOConfiguration) conf.getConfigurations()
				.get(0);
		Assert.assertEquals(ioConf.getDirection(), Direction.INPUT);
		ioConf = (IOConfiguration) conf.getConfigurations().get(1);
		Assert.assertEquals(ioConf.getDirection(), Direction.OUTPUT);
		Assert.assertTrue(conf.isReset());

		data.gpiPortCurrentStateList = null;
		conf = creator.createRequest(llrpMessage);
		Assert.assertEquals(conf.getConfigurations().size(), 1);
		Assert.assertTrue(conf.getConfigurations().get(0) instanceof IOConfiguration);
		Assert.assertTrue(conf.isReset());
	}
}
