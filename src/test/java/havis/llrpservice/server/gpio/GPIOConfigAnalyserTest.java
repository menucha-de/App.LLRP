package havis.llrpservice.server.gpio;

import havis.llrpservice.xml.configuration.AddressGroup;
import havis.llrpservice.xml.configuration.DefaultsType;
import havis.llrpservice.xml.configuration.GPIOType;
import havis.llrpservice.xml.configuration.GpioPortProperties;
import havis.llrpservice.xml.configuration.InstanceGPIOType;
import havis.llrpservice.xml.configuration.InstanceInterfacesType;
import havis.llrpservice.xml.configuration.InterfacesType;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;

import org.testng.Assert;
import org.testng.annotations.Test;

public class GPIOConfigAnalyserTest {

	@Test
	public void getAddress() {
		// create a server config with an address
		LLRPServerConfigurationType serverConfig = new LLRPServerConfigurationType();
		DefaultsType defaults = new DefaultsType();
		InterfacesType interfaces = new InterfacesType();
		GPIOType gpio = new GPIOType();
		AddressGroup addressGroup = new AddressGroup();
		gpio.setAddressGroup(addressGroup);
		interfaces.setGPIO(gpio);
		defaults.setInterfaces(interfaces);
		serverConfig.setDefaults(defaults);

		// create a server instance config without an address
		LLRPServerInstanceConfigurationType serverInstanceConfig = new LLRPServerInstanceConfigurationType();
		InstanceInterfacesType instanceInterfaces = new InstanceInterfacesType();
		InstanceGPIOType instanceGpio = new InstanceGPIOType();
		instanceInterfaces.setGPIO(instanceGpio);
		serverInstanceConfig.setInterfaces(instanceInterfaces);

		// get the address
		// the address of the server config is returned
		GPIOConfigAnalyser ca = new GPIOConfigAnalyser(serverConfig);
		Assert.assertEquals(ca.getAddress(), addressGroup);
		ca.setServerInstanceConfig(serverInstanceConfig);
		Assert.assertEquals(ca.getAddress(), addressGroup);

		// add an address to the instance config
		AddressGroup instanceAddressGroup = new AddressGroup();
		instanceGpio.setAddressGroup(instanceAddressGroup);

		// get the address
		// the address of the instance config is returned
		Assert.assertEquals(ca.getAddress(), instanceAddressGroup);
	}

	@Test
	public void getGPIOPortProperties() {
		// create a server config with an RFC openCloseTimeout
		LLRPServerConfigurationType serverConfig = new LLRPServerConfigurationType();
		DefaultsType defaults = new DefaultsType();
		InterfacesType interfaces = new InterfacesType();
		GPIOType rfc = new GPIOType();
		GpioPortProperties gpioPortProperties = new GpioPortProperties();
		rfc.setGpioPortProperties(gpioPortProperties);
		interfaces.setGPIO(rfc);
		defaults.setInterfaces(interfaces);
		serverConfig.setDefaults(defaults);

		// create an instance config without RFC properties structure
		LLRPServerInstanceConfigurationType serverInstanceConfig = new LLRPServerInstanceConfigurationType();
		InstanceInterfacesType instanceInterfaces = new InstanceInterfacesType();
		InstanceGPIOType instanceGpio = new InstanceGPIOType();
		instanceInterfaces.setGPIO(instanceGpio);
		serverInstanceConfig.setInterfaces(instanceInterfaces);

		// get the openCloseTimeout
		// the timeout of the server config is returned
		GPIOConfigAnalyser ca = new GPIOConfigAnalyser(serverConfig);
		Assert.assertEquals(ca.getGPIOPortProperties(), gpioPortProperties);
		ca.setServerInstanceConfig(serverInstanceConfig);
		Assert.assertEquals(ca.getGPIOPortProperties(), gpioPortProperties);

		// add a RFC properties structure to the instance config
		GpioPortProperties instanceRfcPortProperties = new GpioPortProperties();
		instanceGpio.setGpioPortProperties(instanceRfcPortProperties);
		instanceInterfaces.setGPIO(instanceGpio);
		// get the openCloseTimeout
		// the timeout of the instance config is returned
		Assert.assertEquals(ca.getGPIOPortProperties(),
				instanceRfcPortProperties);
	}
}
