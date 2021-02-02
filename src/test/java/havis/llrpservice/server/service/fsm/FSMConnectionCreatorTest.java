package havis.llrpservice.server.service.fsm;

import org.testng.Assert;
import org.testng.annotations.Test;

import havis.llrpservice.common.fsm.FSM;
import havis.llrpservice.common.fsm.FSMActionException;
import havis.llrpservice.data.message.CloseConnection;
import havis.llrpservice.data.message.CloseConnectionResponse;
import havis.llrpservice.data.message.MessageHeader;
import havis.llrpservice.data.message.ProtocolVersion;
import havis.llrpservice.data.message.parameter.LLRPStatusCode;
import havis.llrpservice.server.gpio.GPIOMessageHandler;
import havis.llrpservice.server.llrp.LLRPMessageHandler;
import havis.llrpservice.server.rfc.RFCMessageHandler;
import havis.llrpservice.server.service.ROAccessReportDepot;
import havis.llrpservice.server.service.ROSpecsManager;
import havis.llrpservice.server.service.fsm.gpio.GPIORuntimeData;
import havis.llrpservice.server.service.fsm.lllrp.LLRPRuntimeData;
import havis.llrpservice.server.service.fsm.rfc.RFCRuntimeData;
import havis.llrpservice.xml.properties.LLRPCapabilitiesType;
import havis.util.platform.Platform;
import mockit.Mocked;
import mockit.Verifications;

public class FSMConnectionCreatorTest {

	@Test
	public void createLLRPCloseConnection(@Mocked final Platform platform,
			@Mocked final LLRPMessageHandler llrpMessageHandler,
			@Mocked final RFCMessageHandler rfcMessageHandler) throws Exception {
		// create FSM
		FSMEvents fsmEvents = createFSMEvents(platform, null /* roSpecsManager */,
				llrpMessageHandler, rfcMessageHandler, null /* gpioMessageHandler */,
				null /* reportDepot */);
		FSM<FSMEvent> fsm = new FSMCreator().create(fsmEvents);

		// send CloseConnection message
		fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData()
				.setNegotiatedProtocolVersion(ProtocolVersion.LLRP_V1_1);
		fsmEvents.LLRP_MESSAGE_RECEIVED.setMessage(new CloseConnection(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_1, 1 /* id */)));
		fsm.fire(fsmEvents.LLRP_MESSAGE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("2 "));
		fsm.fire(fsmEvents.LLRP_CLOSE_CONNECTION_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));
		new Verifications() {
			{
				// the LLRP response has been sent
				llrpMessageHandler
						.requestSendingData(withInstanceOf(CloseConnectionResponse.class));
				times = 1;
			}
		};

		// all processed messages must have been removed from runtime data
		Assert.assertNull(fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData().getCurrentMessage());
		// the flag for restarting the LLRP server has been set
		Assert.assertTrue(fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData().isRestartServer());

		// send CloseConnection message with invalid protocol version
		LLRPRuntimeData llrpRuntimeData = fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData();
		llrpRuntimeData.setRestartServer(false);
		llrpRuntimeData.setNegotiatedProtocolVersion(ProtocolVersion.LLRP_V1_0_1);
		fsmEvents.LLRP_MESSAGE_RECEIVED.setMessage(new CloseConnection(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_1, 1 /* id */)));
		fsm.fire(fsmEvents.LLRP_MESSAGE_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("2 "));
		fsm.fire(fsmEvents.LLRP_CLOSE_CONNECTION_RECEIVED);
		Assert.assertTrue(fsm.getCurrentState().getName().startsWith("1 "));

		new Verifications() {
			{
				// the LLRP response has been sent
				CloseConnectionResponse response;
				llrpMessageHandler.requestSendingData(response = withCapture());
				times = 2;

				Assert.assertEquals(response.getStatus().getStatusCode(),
						LLRPStatusCode.M_UNSUPPORTED_VERSION);
			}
		};
		Assert.assertFalse(fsmEvents.LLRP_MESSAGE_RECEIVED.getRuntimeData().isRestartServer());
	}

	private FSMEvents createFSMEvents(Platform platform, ROSpecsManager roSpecsManager,
			LLRPMessageHandler llrpMessageHandler, RFCMessageHandler rfcMessageHandler,
			GPIOMessageHandler gpioMessageHandler, ROAccessReportDepot reportDepot)
			throws FSMActionException {
		LLRPCapabilitiesType llrpCapabilities = new LLRPCapabilitiesType();
		llrpCapabilities.setCanDoRFSurvey(false);
		llrpCapabilities.setCanReportBufferFillWarning(false);
		llrpCapabilities.setSupportsClientRequestOpSpec(false);
		llrpCapabilities.setCanDoTagInventoryStateAwareSingulation(false);
		llrpCapabilities.setSupportsEventAndReportHolding(true);
		llrpCapabilities.setMaxPriorityLevelSupported((byte) 6);
		llrpCapabilities.setClientRequestOpSpecTimeout(7000);
		llrpCapabilities.setMaxNumROSpecs(1000);
		llrpCapabilities.setMaxNumSpecsPerROSpec(1000);
		llrpCapabilities.setMaxNumInventoryParameterSpecsPerAISpec(1000);
		llrpCapabilities.setMaxNumAccessSpecs(1000);
		llrpCapabilities.setMaxNumOpSpecsPerAccessSpec(1000);
		LLRPServiceInstanceRuntimeData serviceInstanceRuntimeData = new LLRPServiceInstanceRuntimeData(
				platform, 5 /* unexpectedTimeout */);
		LLRPRuntimeData llrpRuntimeData = new LLRPRuntimeData(null /* identificationSource */,
				llrpCapabilities, llrpMessageHandler, roSpecsManager, reportDepot);
		RFCRuntimeData rfcRuntimeData = new RFCRuntimeData(rfcMessageHandler);
		GPIORuntimeData gpioRuntimeData = null;
		if (gpioMessageHandler != null) {
			gpioRuntimeData = new GPIORuntimeData(gpioMessageHandler);
		}
		return new FSMEvents(serviceInstanceRuntimeData, llrpRuntimeData, rfcRuntimeData,
				gpioRuntimeData);
	}
}
