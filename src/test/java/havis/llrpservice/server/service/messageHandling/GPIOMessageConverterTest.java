package havis.llrpservice.server.service.messageHandling;

import havis.device.io.Direction;
import havis.device.io.IOConfiguration;
import havis.device.io.State;
import havis.llrpservice.data.message.parameter.GPIPortCurrentState;
import havis.llrpservice.data.message.parameter.GPIPortCurrentStateGPIState;
import havis.llrpservice.data.message.parameter.GPOWriteData;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;

import org.testng.Assert;
import org.testng.annotations.Test;

public class GPIOMessageConverterTest {

	@Test
	public void convertGPIPortCurrentState() {
		GPIOMessageConverter converter = new GPIOMessageConverter();
		GPIPortCurrentState state = new GPIPortCurrentState(
				new TLVParameterHeader((byte) 0), 1 /* gpiPortNum */,
				false /* gpiConfig */, GPIPortCurrentStateGPIState.HIGH);
		IOConfiguration conf = converter.convert(state);
		Assert.assertEquals(conf.getId(), state.getGpiPortNum());
		Assert.assertFalse(conf.isEnable());
		Assert.assertEquals(conf.getDirection(), Direction.INPUT);
		Assert.assertEquals(conf.getState(), State.HIGH);

		state = new GPIPortCurrentState(new TLVParameterHeader((byte) 0),
				2 /* gpiPortNum */, true /* gpiConfig */,
				GPIPortCurrentStateGPIState.LOW);
		conf = converter.convert(state);
		Assert.assertEquals(conf.getId(), state.getGpiPortNum());
		Assert.assertTrue(conf.isEnable());
		Assert.assertEquals(conf.getDirection(), Direction.INPUT);
		Assert.assertEquals(conf.getState(), State.LOW);

		state = new GPIPortCurrentState(new TLVParameterHeader((byte) 0),
				3 /* gpiPortNum */, true /* gpiConfig */,
				GPIPortCurrentStateGPIState.UNKNOWN);
		conf = converter.convert(state);
		Assert.assertEquals(conf.getId(), state.getGpiPortNum());
		Assert.assertTrue(conf.isEnable());
		Assert.assertEquals(conf.getDirection(), Direction.INPUT);
		Assert.assertEquals(conf.getState(), State.UNKNOWN);
	}

	@Test
	public void convertGPOWriteData() {
		GPIOMessageConverter converter = new GPIOMessageConverter();
		GPOWriteData state = new GPOWriteData(new TLVParameterHeader((byte) 0),
				1 /* gpoPortNum */, false /* gpoState */);
		IOConfiguration conf = converter.convert(state);
		Assert.assertEquals(conf.getId(), state.getGpoPortNum());
		Assert.assertFalse(conf.isEnable());
		Assert.assertEquals(conf.getDirection(), Direction.OUTPUT);
		Assert.assertEquals(conf.getState(), State.LOW);

		state = new GPOWriteData(new TLVParameterHeader((byte) 0),
				2 /* gpoPortNum */, true /* gpoState */);
		conf = converter.convert(state);
		Assert.assertEquals(conf.getId(), state.getGpoPortNum());
		Assert.assertFalse(conf.isEnable());
		Assert.assertEquals(conf.getDirection(), Direction.OUTPUT);
		Assert.assertEquals(conf.getState(), State.HIGH);
	}
}
