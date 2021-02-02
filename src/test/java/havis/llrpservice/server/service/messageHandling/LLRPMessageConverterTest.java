package havis.llrpservice.server.service.messageHandling;

import havis.device.io.Direction;
import havis.device.io.IOConfiguration;
import havis.device.io.State;
import havis.device.io.StateEvent;
import havis.device.rf.capabilities.Capabilities;
import havis.device.rf.capabilities.DeviceCapabilities;
import havis.device.rf.capabilities.RegulatoryCapabilities;
import havis.device.rf.configuration.AntennaConfiguration;
import havis.device.rf.configuration.AntennaProperties;
import havis.device.rf.configuration.Configuration;
import havis.llrpservice.common.serializer.JsonSerializer;
import havis.llrpservice.csc.llrp.json.LLRPJacksonMixIns;
import havis.llrpservice.data.message.parameter.GPIEvent;
import havis.llrpservice.data.message.parameter.GPIPortCurrentState;
import havis.llrpservice.data.message.parameter.GPIPortCurrentStateGPIState;
import havis.llrpservice.data.message.parameter.GPOWriteData;
import havis.llrpservice.data.message.parameter.GeneralDeviceCapabilities;
import havis.llrpservice.data.message.parameter.Parameter;
import havis.llrpservice.data.message.parameter.ProtocolId;
import havis.llrpservice.sbc.gpio.message.MessageHeader;
import havis.llrpservice.sbc.gpio.message.StateChanged;
import havis.llrpservice.server.service.data.LLRPReaderCapabilities;
import havis.llrpservice.xml.properties.LLRPCapabilitiesType;

import java.net.URI;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class LLRPMessageConverterTest {

	private String testResourcePath = "havis/llrpservice/server/service/messageHandling/parameter";
	private String rfcDeviceCaps = "rfcDeviceCaps.json";
	private String rfcRegCaps = "rfcRegCaps.json";
	private String llrpGeneralDeviceCaps = "llrpGeneralDeviceCaps.json";
	private List<String> llrpRegCaps = new ArrayList<>();
	private String rfcAntennaConfiguration = "rfcAntennaConfiguration.json";
	private String llrpAntennaConfiguration = "llrpAntennaConfiguration.json";
	private String rfcAntennaProperties = "rfcAntennaProperties.json";
	private String llrpAntennaProperties = "llrpAntennaProperties.json";
	private String llrpCaps = "llrpCaps.json";

	private List<String> llrpGPIState = new ArrayList<>();
	private List<String> llrpGPOState = new ArrayList<>();

	private LLRPJacksonMixIns mixIns = new LLRPJacksonMixIns();
	private JsonSerializer rfcSerializer = new JsonSerializer(
			_RFCWrapperTest.class);
	private JsonSerializer llrpSerializer = new JsonSerializer(
			_LLRPWrapperTest.class);

	@BeforeClass
	public void init() throws Exception {
		ClassLoader loader = getClass().getClassLoader();
		URL resource = loader.getResource(testResourcePath);
		testResourcePath = Paths.get(new URI(resource.toString())).toString()
				+ "/";
		rfcSerializer.setPrettyPrint(true);
		llrpSerializer.setPrettyPrint(true);
		llrpSerializer.addSerializerMixIns(mixIns);
		llrpSerializer.addDeserializerMixIns(mixIns);

		llrpRegCaps.add("llrpRegCaps_1.json");
		llrpRegCaps.add("llrpRegCaps_2.json");
		llrpGPIState.add("llrpGPIState_1.json");
		llrpGPIState.add("llrpGPIState_2.json");
		llrpGPIState.add("llrpGPIState_3.json");
		llrpGPOState.add("llrpGPOState_1.json");
		llrpGPOState.add("llrpGPOState_2.json");

	}

	@Test
	public void convertGeneralDeviceCapabilties() throws Exception {
		// General device capabilities Tests
		LLRPMessageConverter converter = new LLRPMessageConverter();
		Assert.assertNull(converter.convert((DeviceCapabilities) null,
				true /* canSetAntennaProperties */, ProtocolId.EPC_GLOBAL_C1G2,
				0 /* gpiPortCount */, 0 /* gpoPortCount */, false /* hasUTCClock */));

		// Device Caps from file
		DeviceCapabilities devCaps = (DeviceCapabilities) getRFCCaps(rfcDeviceCaps);
		GeneralDeviceCapabilities compare = converter.convert(devCaps,
				true /* canSetAntennaProperties */, ProtocolId.EPC_GLOBAL_C1G2,
				1 /* gpiPortCount */, 2 /* gpoPortCount */, false /* hasUTCClock */);

		// LLRP GeneralDeviceCapabiltities
		GeneralDeviceCapabilities expected = (GeneralDeviceCapabilities) getLLRPParameter(llrpGeneralDeviceCaps);
		Assert.assertEquals(compare.toString(), expected.toString());
	}

	@Test
	public void convertRegulatoryCapabilities() throws Exception {
		LLRPMessageConverter converter = new LLRPMessageConverter();
		Assert.assertNull(converter.convert(
				(havis.device.rf.capabilities.RegulatoryCapabilities) null,
				(LLRPReaderCapabilities) null));
		LLRPReaderCapabilities data = new LLRPReaderCapabilities();

		// RFC Regulatory Capabilities
		RegulatoryCapabilities regCaps = (RegulatoryCapabilities) getRFCCaps(rfcRegCaps);

		// expected LLRP data structure
		havis.llrpservice.data.message.parameter.RegulatoryCapabilities expected = (havis.llrpservice.data.message.parameter.RegulatoryCapabilities) getLLRPParameter(llrpRegCaps
				.get(0));

		// Convert RFC caps to LLRP caps
		havis.llrpservice.data.message.parameter.RegulatoryCapabilities compare = converter
				.convert(regCaps, data);

		Assert.assertEquals(compare.toString(), expected.toString());

		// Hopping true
		expected = (havis.llrpservice.data.message.parameter.RegulatoryCapabilities) getLLRPParameter(llrpRegCaps
				.get(1));

		regCaps.setHopping(true);
		// Convert RFC caps to LLRP caps
		compare = converter.convert(regCaps, data);
		Assert.assertEquals(compare.toString(), expected.toString());
	}

	@Test
	public void convertAntennaConfiguration() throws Exception {
		LLRPMessageConverter converter = new LLRPMessageConverter();
		Assert.assertNull(converter
				.convert((havis.device.rf.configuration.AntennaConfiguration) null));

		AntennaConfiguration antennaConfiguration = (AntennaConfiguration) getRFCConf(rfcAntennaConfiguration);
		havis.llrpservice.data.message.parameter.AntennaConfiguration compare = converter
				.convert(antennaConfiguration);
		Assert.assertEquals(compare.toString(),
				getLLRPParameter(llrpAntennaConfiguration).toString());
	}

	@Test
	public void convertAntennaProperties() throws Exception {
		LLRPMessageConverter converter = new LLRPMessageConverter();
		Assert.assertNull(converter
				.convert((havis.device.rf.configuration.AntennaProperties) null));

		AntennaProperties antennaProperties = (AntennaProperties) getRFCConf(rfcAntennaProperties);
		havis.llrpservice.data.message.parameter.AntennaProperties compare = converter
				.convert(antennaProperties);

		Assert.assertEquals(compare.toString(),
				getLLRPParameter(llrpAntennaProperties).toString());
	}

	@Test
	public void convertLLRPCapabilitiesData() throws Exception {
		LLRPMessageConverter converter = new LLRPMessageConverter();
		LLRPCapabilitiesType llrpCapabilitiesType = new LLRPCapabilitiesType();

		Parameter compare = converter.convert(llrpCapabilitiesType);

		Assert.assertEquals(compare.toString(), getLLRPParameter(llrpCaps)
				.toString());

	}

	@Test
	public void convertGPIConfig() throws Exception {
		LLRPMessageConverter converter = new LLRPMessageConverter();

		Assert.assertNull(converter.convertGPIConfig(null));

		IOConfiguration conf = new IOConfiguration((short) 0 /* id */,
				Direction.OUTPUT, State.UNKNOWN, false/* gpiEventsEnabled */);
		Assert.assertNull(converter.convertGPIConfig(conf));

		conf = new IOConfiguration((short) 1 /* id */, Direction.INPUT,
				State.HIGH, true /* gpiEventsEnabled */);
		GPIPortCurrentState state = converter.convertGPIConfig(conf);
		Assert.assertEquals(state.getGpiPortNum(), conf.getId());
		Assert.assertEquals(state.getState(), GPIPortCurrentStateGPIState.HIGH);
		Assert.assertTrue(state.getGpiConfig());

		conf = new IOConfiguration((short) 2 /* id */, Direction.INPUT,
				State.LOW, false/* gpiEventsEnabled */);
		state = converter.convertGPIConfig(conf);
		Assert.assertEquals(state.getGpiPortNum(), conf.getId());
		Assert.assertEquals(state.getState(), GPIPortCurrentStateGPIState.LOW);
		Assert.assertFalse(state.getGpiConfig());

		conf = new IOConfiguration((short) 3 /* id */, Direction.INPUT,
				State.UNKNOWN, false/* gpiEventsEnabled */);
		state = converter.convertGPIConfig(conf);
		Assert.assertEquals(state.getGpiPortNum(), conf.getId());
		Assert.assertEquals(state.getState(),
				GPIPortCurrentStateGPIState.UNKNOWN);
		Assert.assertFalse(state.getGpiConfig());
	}

	@Test
	public void convertGPOConfig() throws Exception {
		LLRPMessageConverter converter = new LLRPMessageConverter();

		Assert.assertNull(converter.convertGPOConfig(null));

		IOConfiguration conf = new IOConfiguration((short) 0 /* id */,
				Direction.INPUT, State.UNKNOWN, false/* gpiEventsEnabled */);
		Assert.assertNull(converter.convertGPOConfig(conf));

		conf = new IOConfiguration((short) 1 /* id */, Direction.OUTPUT,
				State.HIGH, true /* gpiEventsEnabled */);
		GPOWriteData state = converter.convertGPOConfig(conf);
		Assert.assertEquals(state.getGpoPortNum(), conf.getId());
		Assert.assertTrue(state.getGpoState());

		conf = new IOConfiguration((short) 2 /* id */, Direction.OUTPUT,
				State.LOW, false /* gpiEventsEnabled */);
		state = converter.convertGPOConfig(conf);
		Assert.assertEquals(state.getGpoPortNum(), conf.getId());
		Assert.assertFalse(state.getGpoState());

		// state UNKNOWN is converted to LOW
		conf = new IOConfiguration((short) 3 /* id */, Direction.OUTPUT,
				State.UNKNOWN, false /* gpiEventsEnabled */);
		state = converter.convertGPOConfig(conf);
		Assert.assertEquals(state.getGpoPortNum(), conf.getId());
		Assert.assertFalse(state.getGpoState());
	}

	@Test
	public void convertStateChanged() throws Exception {
		LLRPMessageConverter converter = new LLRPMessageConverter();

		Assert.assertNull(converter.convert((StateChanged) null));

		StateEvent gpiEvent = new StateEvent((short) 1 /* id */, State.HIGH);
		GPIEvent llrpEvent = converter.convert(new StateChanged(
				new MessageHeader(3 /* id */), gpiEvent));
		Assert.assertEquals(llrpEvent.getGpiPortNumber(), gpiEvent.getId());
		Assert.assertTrue(llrpEvent.isState());

		gpiEvent = new StateEvent((short) 2 /* id */, State.LOW);
		llrpEvent = converter.convert(new StateChanged(
				new MessageHeader(3 /* id */), gpiEvent));
		Assert.assertEquals(llrpEvent.getGpiPortNumber(), gpiEvent.getId());
		Assert.assertFalse(llrpEvent.isState());

		// state UNKNOWN is converted to LOW
		gpiEvent = new StateEvent((short) 3 /* id */, State.UNKNOWN);
		llrpEvent = converter.convert(new StateChanged(
				new MessageHeader(3 /* id */), gpiEvent));
		Assert.assertEquals(llrpEvent.getGpiPortNumber(), gpiEvent.getId());
		Assert.assertFalse(llrpEvent.isState());
	}

	private Parameter getLLRPParameter(String object) throws Exception {
		_LLRPWrapperTest wrapper = llrpSerializer.deserialize(_FileHelperTest
				.readFile(testResourcePath + object));
		return wrapper.getParameter();
	}

	private Capabilities getRFCCaps(String object) throws Exception {
		_RFCWrapperTest wrapper = rfcSerializer.deserialize(_FileHelperTest
				.readFile(testResourcePath + object));
		return wrapper.getRfcCaps();
	}

	private Configuration getRFCConf(String object) throws Exception {
		_RFCWrapperTest wrapper = rfcSerializer.deserialize(_FileHelperTest
				.readFile(testResourcePath + object));
		return wrapper.getRfcConf();
	}

}
