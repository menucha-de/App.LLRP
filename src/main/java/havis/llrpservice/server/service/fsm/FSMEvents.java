package havis.llrpservice.server.service.fsm;

import havis.llrpservice.server.service.fsm.gpio.FSMGPIOGetConfigurationResponseEvent;
import havis.llrpservice.server.service.fsm.gpio.FSMGPIOMessageEvent;
import havis.llrpservice.server.service.fsm.gpio.FSMGPIOResetConfigurationResponseEvent;
import havis.llrpservice.server.service.fsm.gpio.FSMGPIOSetConfigurationResponseEvent;
import havis.llrpservice.server.service.fsm.gpio.FSMGPIOStateChangedEvent;
import havis.llrpservice.server.service.fsm.gpio.GPIORuntimeData;
import havis.llrpservice.server.service.fsm.lllrp.FSMLLRPCloseConnectionEvent;
import havis.llrpservice.server.service.fsm.lllrp.FSMLLRPGetReaderCapabilitiesEvent;
import havis.llrpservice.server.service.fsm.lllrp.FSMLLRPGetReaderConfigEvent;
import havis.llrpservice.server.service.fsm.lllrp.FSMLLRPMessageEvent;
import havis.llrpservice.server.service.fsm.lllrp.FSMLLRPSetReaderConfigEvent;
import havis.llrpservice.server.service.fsm.lllrp.LLRPRuntimeData;
import havis.llrpservice.server.service.fsm.rfc.FSMRFCExecuteResponseEvent;
import havis.llrpservice.server.service.fsm.rfc.FSMRFCGetCapabilitiesResponseEvent;
import havis.llrpservice.server.service.fsm.rfc.FSMRFCGetConfigurationResponseEvent;
import havis.llrpservice.server.service.fsm.rfc.FSMRFCMessageEvent;
import havis.llrpservice.server.service.fsm.rfc.FSMRFCResetConfigurationResponseEvent;
import havis.llrpservice.server.service.fsm.rfc.FSMRFCSetConfigurationResponseEvent;
import havis.llrpservice.server.service.fsm.rfc.RFCRuntimeData;

public class FSMEvents {
	// LLRP
	public final FSMLLRPMessageEvent LLRP_MESSAGE_RECEIVED = new FSMLLRPMessageEvent();
	public final FSMLLRPGetReaderCapabilitiesEvent LLRP_GET_READER_CAPABILITIES_RECEIVED = new FSMLLRPGetReaderCapabilitiesEvent();
	public final FSMLLRPGetReaderConfigEvent LLRP_GET_READER_CONFIG_RECEIVED = new FSMLLRPGetReaderConfigEvent();
	public final FSMLLRPSetReaderConfigEvent LLRP_SET_READER_CONFIG_RECEIVED = new FSMLLRPSetReaderConfigEvent();
	public final FSMLLRPCloseConnectionEvent LLRP_CLOSE_CONNECTION_RECEIVED = new FSMLLRPCloseConnectionEvent();

	// RFC
	public final FSMRFCMessageEvent RFC_MESSAGE_RECEIVED = new FSMRFCMessageEvent();
	public final FSMRFCGetCapabilitiesResponseEvent RFC_GET_CAPABILITIES_RESPONSE_RECEIVED = new FSMRFCGetCapabilitiesResponseEvent();
	public final FSMRFCGetConfigurationResponseEvent RFC_GET_CONFIGURATION_RESPONSE_RECEIVED = new FSMRFCGetConfigurationResponseEvent();
	public final FSMRFCSetConfigurationResponseEvent RFC_SET_CONFIGURATION_RESPONSE_RECEIVED = new FSMRFCSetConfigurationResponseEvent();
	public final FSMRFCResetConfigurationResponseEvent RFC_RESET_CONFIGURATION_RESPONSE_RECEIVED = new FSMRFCResetConfigurationResponseEvent();
	public final FSMRFCExecuteResponseEvent RFC_EXECUTE_RESPONSE_RECEIVED = new FSMRFCExecuteResponseEvent();

	// GPIO
	public final FSMGPIOMessageEvent GPIO_MESSAGE_RECEIVED = new FSMGPIOMessageEvent();
	public final FSMGPIOGetConfigurationResponseEvent GPIO_GET_CONFIGURATION_RESPONSE_RECEIVED = new FSMGPIOGetConfigurationResponseEvent();
	public final FSMGPIOSetConfigurationResponseEvent GPIO_SET_CONFIGURATION_RESPONSE_RECEIVED = new FSMGPIOSetConfigurationResponseEvent();
	public final FSMGPIOResetConfigurationResponseEvent GPIO_RESET_CONFIGURATION_RESPONSE_RECEIVED = new FSMGPIOResetConfigurationResponseEvent();
	public final FSMGPIOStateChangedEvent GPIO_STATE_CHANGED_RECEIVED = new FSMGPIOStateChangedEvent();

	public FSMEvents(LLRPServiceInstanceRuntimeData llrpServiceInstanceRuntimeData,
			LLRPRuntimeData llrpRuntimeData, RFCRuntimeData rfcRuntimeData,
			GPIORuntimeData gpioRuntimeData) {
		// LLRP
		LLRP_MESSAGE_RECEIVED.setRuntimeData(llrpRuntimeData);
		LLRP_GET_READER_CAPABILITIES_RECEIVED.setRuntimeData(llrpServiceInstanceRuntimeData,
				llrpRuntimeData, rfcRuntimeData, gpioRuntimeData);
		LLRP_GET_READER_CONFIG_RECEIVED.setRuntimeData(llrpRuntimeData, rfcRuntimeData,
				gpioRuntimeData);
		LLRP_SET_READER_CONFIG_RECEIVED.setRuntimeData(llrpServiceInstanceRuntimeData,
				llrpRuntimeData, rfcRuntimeData, gpioRuntimeData);
		LLRP_CLOSE_CONNECTION_RECEIVED.setRuntimeData(llrpRuntimeData);

		// RFC
		RFC_MESSAGE_RECEIVED.setRuntimeData(llrpRuntimeData, rfcRuntimeData);
		RFC_GET_CAPABILITIES_RESPONSE_RECEIVED.setRuntimeData(llrpServiceInstanceRuntimeData,
				llrpRuntimeData, rfcRuntimeData, gpioRuntimeData);
		RFC_GET_CONFIGURATION_RESPONSE_RECEIVED.setRuntimeData(llrpRuntimeData, rfcRuntimeData,
				gpioRuntimeData);
		RFC_SET_CONFIGURATION_RESPONSE_RECEIVED.setRuntimeData(llrpRuntimeData, rfcRuntimeData,
				gpioRuntimeData);
		RFC_RESET_CONFIGURATION_RESPONSE_RECEIVED.setRuntimeData(llrpRuntimeData, rfcRuntimeData);
		RFC_EXECUTE_RESPONSE_RECEIVED.setRuntimeData(llrpServiceInstanceRuntimeData,
				llrpRuntimeData, rfcRuntimeData);

		// GPIO
		GPIO_MESSAGE_RECEIVED.setRuntimeData(llrpRuntimeData, gpioRuntimeData);
		GPIO_GET_CONFIGURATION_RESPONSE_RECEIVED.setRuntimeData(llrpServiceInstanceRuntimeData,
				llrpRuntimeData, rfcRuntimeData, gpioRuntimeData);
		GPIO_SET_CONFIGURATION_RESPONSE_RECEIVED.setRuntimeData(llrpRuntimeData, rfcRuntimeData,
				gpioRuntimeData);
		GPIO_RESET_CONFIGURATION_RESPONSE_RECEIVED.setRuntimeData(llrpRuntimeData, gpioRuntimeData);
		GPIO_STATE_CHANGED_RECEIVED.setRuntimeData(llrpServiceInstanceRuntimeData, llrpRuntimeData,
				gpioRuntimeData);
	}
}
