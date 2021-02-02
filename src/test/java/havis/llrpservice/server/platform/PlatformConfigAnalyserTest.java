package havis.llrpservice.server.platform;

import org.testng.Assert;
import org.testng.annotations.Test;

import havis.llrpservice.server.platform.PlatformConfigAnalyser;
import havis.llrpservice.xml.configuration.AddressGroup;
import havis.llrpservice.xml.configuration.DefaultsType;
import havis.llrpservice.xml.configuration.InstanceInterfacesType;
import havis.llrpservice.xml.configuration.InstanceSystemControllerType;
import havis.llrpservice.xml.configuration.InterfacesType;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;
import havis.llrpservice.xml.configuration.SystemControllerPortProperties;
import havis.llrpservice.xml.configuration.SystemControllerType;

public class PlatformConfigAnalyserTest {

	@Test
	public void getAddress() {
		// create a server config with an address
		LLRPServerConfigurationType serverConfig = new LLRPServerConfigurationType();
		DefaultsType defaults = new DefaultsType();
		InterfacesType interfaces = new InterfacesType();
		SystemControllerType sc = new SystemControllerType();
		AddressGroup addressGroup = new AddressGroup();
		sc.setAddressGroup(addressGroup);
		interfaces.setSystemController(sc);
		defaults.setInterfaces(interfaces);
		serverConfig.setDefaults(defaults);

		// create a server instance config without an address
		LLRPServerInstanceConfigurationType serverInstanceConfig = new LLRPServerInstanceConfigurationType();
		InstanceInterfacesType instanceInterfaces = new InstanceInterfacesType();
		InstanceSystemControllerType instanceSc = new InstanceSystemControllerType();
		instanceInterfaces.setSystemController(instanceSc);
		serverInstanceConfig.setInterfaces(instanceInterfaces);

		// get the address
		// the address of the server config is returned
		PlatformConfigAnalyser ca = new PlatformConfigAnalyser(serverConfig);
		Assert.assertEquals(ca.getAddress(), addressGroup);
		ca.setServerInstanceConfig(serverInstanceConfig);
		Assert.assertEquals(ca.getAddress(), addressGroup);

		// add an address to the instance config
		AddressGroup instanceAddressGroup = new AddressGroup();
		instanceSc.setAddressGroup(instanceAddressGroup);

		// get the address
		// the address of the instance config is returned
		Assert.assertEquals(ca.getAddress(), instanceAddressGroup);
	}

	@Test
	public void getSystemControllerPortProperties() {
		// create a server config with an RFC openCloseTimeout
		LLRPServerConfigurationType serverConfig = new LLRPServerConfigurationType();
		DefaultsType defaults = new DefaultsType();
		InterfacesType interfaces = new InterfacesType();
		SystemControllerType sc = new SystemControllerType();
		SystemControllerPortProperties scPortProperties = new SystemControllerPortProperties();
		sc.setSystemControllerPortProperties(scPortProperties);
		interfaces.setSystemController(sc);
		defaults.setInterfaces(interfaces);
		serverConfig.setDefaults(defaults);

		// create an instance config without RFC properties structure
		LLRPServerInstanceConfigurationType serverInstanceConfig = new LLRPServerInstanceConfigurationType();
		InstanceInterfacesType instanceInterfaces = new InstanceInterfacesType();
		InstanceSystemControllerType instanceSc = new InstanceSystemControllerType();
		instanceInterfaces.setSystemController(instanceSc);
		serverInstanceConfig.setInterfaces(instanceInterfaces);

		// get the openCloseTimeout
		// the timeout of the server config is returned
		PlatformConfigAnalyser ca = new PlatformConfigAnalyser(serverConfig);
		Assert.assertEquals(ca.getSystemControllerPortProperties(), scPortProperties);
		ca.setServerInstanceConfig(serverInstanceConfig);
		Assert.assertEquals(ca.getSystemControllerPortProperties(), scPortProperties);

		// add a RFC properties structure to the instance config
		SystemControllerPortProperties instanceScPortProperties = new SystemControllerPortProperties();
		instanceSc.setSystemControllerPortProperties(instanceScPortProperties);
		instanceInterfaces.setSystemController(instanceSc);
		// get the openCloseTimeout
		// the timeout of the instance config is returned
		Assert.assertEquals(ca.getSystemControllerPortProperties(), instanceScPortProperties);
	}
}
