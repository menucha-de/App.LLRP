package havis.llrpservice.server.rfc;

import havis.llrpservice.xml.configuration.AddressGroup;
import havis.llrpservice.xml.configuration.DefaultsType;
import havis.llrpservice.xml.configuration.InstanceInterfacesType;
import havis.llrpservice.xml.configuration.InstanceRFCType;
import havis.llrpservice.xml.configuration.InterfacesType;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;
import havis.llrpservice.xml.configuration.RFCType;
import havis.llrpservice.xml.configuration.RfcPortProperties;

import org.testng.Assert;
import org.testng.annotations.Test;

public class RFCConfigAnalyserTest {

	@Test
	public void getAddress() {
		// create a server config with an address
		LLRPServerConfigurationType serverConfig = new LLRPServerConfigurationType();
		DefaultsType defaults = new DefaultsType();
		InterfacesType interfaces = new InterfacesType();
		RFCType rfc = new RFCType();
		AddressGroup addressGroup = new AddressGroup();
		rfc.setAddressGroup(addressGroup);
		interfaces.setRFC(rfc);
		defaults.setInterfaces(interfaces);
		serverConfig.setDefaults(defaults);

		// create a server instance config without an address
		LLRPServerInstanceConfigurationType serverInstanceConfig = new LLRPServerInstanceConfigurationType();
		InstanceInterfacesType instanceInterfaces = new InstanceInterfacesType();
		InstanceRFCType instanceRfc = new InstanceRFCType();
		instanceInterfaces.setRFC(instanceRfc);
		serverInstanceConfig.setInterfaces(instanceInterfaces);

		// get the address
		// the address of the server config is returned
		RFCConfigAnalyser ca = new RFCConfigAnalyser(serverConfig);
		Assert.assertEquals(ca.getAddress(), addressGroup);
		ca.setServerInstanceConfig(serverInstanceConfig);
		Assert.assertEquals(ca.getAddress(), addressGroup);

		// add an address to the instance config
		AddressGroup instanceAddressGroup = new AddressGroup();
		instanceRfc.setAddressGroup(instanceAddressGroup);

		// get the address
		// the address of the instance config is returned
		Assert.assertEquals(ca.getAddress(), instanceAddressGroup);
	}

	@Test
	public void getRFCPortProperties() {
		// create a server config with an RFC openCloseTimeout
		LLRPServerConfigurationType serverConfig = new LLRPServerConfigurationType();
		DefaultsType defaults = new DefaultsType();
		InterfacesType interfaces = new InterfacesType();
		RFCType rfc = new RFCType();
		RfcPortProperties rfcPortProperties = new RfcPortProperties();
		rfc.setRfcPortProperties(rfcPortProperties);
		interfaces.setRFC(rfc);
		defaults.setInterfaces(interfaces);
		serverConfig.setDefaults(defaults);

		// create an instance config without RFC properties structure
		LLRPServerInstanceConfigurationType serverInstanceConfig = new LLRPServerInstanceConfigurationType();
		InstanceInterfacesType instanceInterfaces = new InstanceInterfacesType();
		InstanceRFCType instanceRfc = new InstanceRFCType();
		instanceInterfaces.setRFC(instanceRfc);
		serverInstanceConfig.setInterfaces(instanceInterfaces);

		// get the openCloseTimeout
		// the timeout of the server config is returned
		RFCConfigAnalyser ca = new RFCConfigAnalyser(serverConfig);
		Assert.assertEquals(ca.getRFCPortProperties(), rfcPortProperties);
		ca.setServerInstanceConfig(serverInstanceConfig);
		Assert.assertEquals(ca.getRFCPortProperties(), rfcPortProperties);

		// add a RFC properties structure to the instance config
		RfcPortProperties instanceRfcPortProperties = new RfcPortProperties();
		instanceRfc.setRfcPortProperties(instanceRfcPortProperties);
		instanceInterfaces.setRFC(instanceRfc);
		// get the openCloseTimeout
		// the timeout of the instance config is returned
		Assert.assertEquals(ca.getRFCPortProperties(),
				instanceRfcPortProperties);
	}
}
