package havis.llrpservice.server.llrp;

import havis.llrpservice.xml.configuration.AddressGroup;
import havis.llrpservice.xml.configuration.DefaultsType;
import havis.llrpservice.xml.configuration.InstanceInterfacesType;
import havis.llrpservice.xml.configuration.InstanceLLRPType;
import havis.llrpservice.xml.configuration.InstanceLLRPType.LlrpPortProperties;
import havis.llrpservice.xml.configuration.InterfacesType;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;
import havis.llrpservice.xml.configuration.LLRPType;

import org.testng.Assert;
import org.testng.annotations.Test;

public class LLRPConfigAnalyserTest {

	@Test
	public void getAddress() {
		// create server configuration with LLRP address
		LLRPServerConfigurationType serverConfig = new LLRPServerConfigurationType();
		DefaultsType defaults = new DefaultsType();
		InterfacesType interfaces = new InterfacesType();
		LLRPType llrp = new LLRPType();
		AddressGroup addressGroup = new AddressGroup();
		llrp.setAddressGroup(addressGroup);
		interfaces.setLLRP(llrp);
		defaults.setInterfaces(interfaces);
		serverConfig.setDefaults(defaults);

		// get address via analyser
		// the LLRP address from the server config is returned
		LLRPConfigAnalyser ca = new LLRPConfigAnalyser(serverConfig);
		ca.setServerInstanceConfig(null);
		Assert.assertEquals(ca.getAddress(), addressGroup);

		// set instance config without LLRP part
		LLRPServerInstanceConfigurationType serverInstanceConfig = new LLRPServerInstanceConfigurationType();
		InstanceInterfacesType instanceInterfaces = new InstanceInterfacesType();
		serverInstanceConfig.setInterfaces(instanceInterfaces);

		// get address via analyser
		// the LLRP address from the server config is returned
		ca.setServerInstanceConfig(serverInstanceConfig);
		Assert.assertEquals(ca.getAddress(), addressGroup);

		// add a LLRP address to the instance config
		InstanceLLRPType instanceLLRP = new InstanceLLRPType();
		AddressGroup instanceAddressGroup = new AddressGroup();
		instanceLLRP.setAddressGroup(instanceAddressGroup);
		instanceInterfaces.setLLRP(instanceLLRP);

		// get address via analyser
		// the LLRP address from the instance config is returned
		ca.setServerInstanceConfig(serverInstanceConfig);
		Assert.assertEquals(ca.getAddress(), instanceAddressGroup); }

	@Test
	public void getOpenCloseTimeout() {
		// create server configuration with openCloseTimeout
		LLRPServerConfigurationType serverConfig = new LLRPServerConfigurationType();
		DefaultsType defaults = new DefaultsType();
		InterfacesType interfaces = new InterfacesType();
		LLRPType llrp = new LLRPType();
		llrp.setOpenCloseTimeout(5);
		interfaces.setLLRP(llrp);
		defaults.setInterfaces(interfaces);
		serverConfig.setDefaults(defaults);

		// get timeout via analyser
		// the timeout from the server config is returned
		LLRPConfigAnalyser ca = new LLRPConfigAnalyser(serverConfig);
		Assert.assertEquals(ca.getOpenCloseTimeout(), 5);

		// set instance config without LLRP part
		LLRPServerInstanceConfigurationType serverInstanceConfig = new LLRPServerInstanceConfigurationType();
		InstanceInterfacesType instanceInterfaces = new InstanceInterfacesType();
		serverInstanceConfig.setInterfaces(instanceInterfaces);

		// get timeout via analyser
		// the timeout from the server config is returned
		ca.setServerInstanceConfig(serverInstanceConfig);
		Assert.assertEquals(ca.getOpenCloseTimeout(), 5);

		// add LLRP part without a timeout
		InstanceLLRPType instanceLLRP = new InstanceLLRPType();
		instanceInterfaces.setLLRP(instanceLLRP);

		// get timeout via analyser
		// the timeout from the server config is returned
		ca.setServerInstanceConfig(serverInstanceConfig);
		Assert.assertEquals(ca.getOpenCloseTimeout(), 5);

		// add a timeout to the LLRP part of the instance config
		LlrpPortProperties llrpPortProperties = new LlrpPortProperties();
		llrpPortProperties.setOpenCloseTimeout(6);
		instanceLLRP.setLlrpPortProperties(llrpPortProperties);

		// get timeout via analyser
		// the timeout from the instance config is returned
		ca.setServerInstanceConfig(serverInstanceConfig);
		Assert.assertEquals(ca.getOpenCloseTimeout(), 6);
	}
}
