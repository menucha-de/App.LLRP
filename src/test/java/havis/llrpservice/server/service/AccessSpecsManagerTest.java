package havis.llrpservice.server.service;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.List;

import org.testng.annotations.Test;

import havis.llrpservice.data.message.parameter.AccessSpec;
import havis.llrpservice.data.message.parameter.ProtocolId;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.server.rfc.RFCMessageHandler;
import mockit.Expectations;
import mockit.Mocked;
import mockit.Verifications;

public class AccessSpecsManagerTest {

	@Test
	public void addRemove(@Mocked final RFCMessageHandler rfcMessageHandler) throws Exception {
		// add a disabled AccessSpec
		final AccessSpec accessSpec1 = new AccessSpec(new TLVParameterHeader((byte) 0),
				567L /* accessSpecID */, 1 /* antennaID */, ProtocolId.EPC_GLOBAL_C1G2,
				false /* currentState */, 2L /* roSpecID */, null /* accessSpecStopTrigger */,
				null /* AccessCommand */);
		AccessSpecsManager am = new AccessSpecsManager(rfcMessageHandler);
		am.add(accessSpec1);
		// "getAccessSpecs" returns the added AccessSpec
		assertEquals(am.getAccessSpecs().size(), 1);

		// try to add the same AccessSpec again
		try {
			am.add(accessSpec1);
			fail();
		} catch (InvalidIdentifierException e) {
			assertTrue(e.getMessage().contains("already exists"));
		}

		// remove the AccessSpec
		List<AccessSpec> ac = am.remove(accessSpec1.getAccessSpecId());
		assertEquals(ac.get(0), accessSpec1);
		// the AccessSpec list is empty
		assertEquals(am.getAccessSpecs().size(), 0);

		// add an enabled AccessSpec
		accessSpec1.setCurrentState(true);
		am.add(accessSpec1);
		// "getAccessSpecs" returns the added AccessSpec
		assertEquals(am.getAccessSpecs().size(), 1);
		assertEquals(am.getAccessSpecs().get(0), accessSpec1);
		new Verifications() {
			{
				// a copy of the enabled AccessSpec has been added to the RFC
				// message handler
				rfcMessageHandler.add(accessSpec1);
				times = 0;
				AccessSpec as;
				rfcMessageHandler.add(as = withCapture());
				times = 1;
				assertEquals(as.getAccessSpecId(), accessSpec1.getAccessSpecId());
				assertTrue(as.isCurrentState());
			}
		};

		new Expectations() {
			{
				rfcMessageHandler.remove(anyLong);
				result = accessSpec1;
			}
		};

		// remove the enabled AccessSpec
		ac = am.remove(accessSpec1.getAccessSpecId());
		assertEquals(ac.get(0), accessSpec1);
		// the AccessSpec has been disabled
		assertFalse(ac.get(0).isCurrentState());
		// the AccessSpec list is empty
		assertEquals(am.getAccessSpecs().size(), 0);
		new Verifications() {
			{
				// the enabled AccessSpec has been removed from the RFC message
				// handler
				rfcMessageHandler.remove(accessSpec1.getAccessSpecId());
				times = 1;
			}
		};

		// add two access specs
		am.add(accessSpec1);
		AccessSpec accessSpec2 = new AccessSpec(new TLVParameterHeader((byte) 0),
				890L /* accessSpecID */, 1 /* antennaID */, ProtocolId.EPC_GLOBAL_C1G2,
				false /* currentState */, 2L /* roSpecID */, null /* accessSpecStopTrigger */,
				null /* AccessCommand */);
		am.add(accessSpec2);
		// remove both access specs with identifier 0
		am.remove(0);
		// the AccessSpec list is empty
		assertEquals(am.getAccessSpecs().size(), 0);
	}

	@Test
	public void setState(@Mocked final RFCMessageHandler rfcMessageHandler) throws Exception {
		// add a disabled AccessSpec
		final AccessSpec accessSpec1 = new AccessSpec(new TLVParameterHeader((byte) 0),
				567L /* accessSpecID */, 1 /* antennaID */, ProtocolId.EPC_GLOBAL_C1G2,
				false /* currentState */, 2L /* roSpecID */, null /* accessSpecStopTrigger */,
				null /* AccessCommand */);
		AccessSpecsManager am = new AccessSpecsManager(rfcMessageHandler);
		am.add(accessSpec1);
		// enable the AccessSpec
		am.setState(accessSpec1.getAccessSpecId(), true/* enabled */);
		assertTrue(am.getAccessSpecs().get(0).isCurrentState());
		new Verifications() {
			{
				// a copy of the enabled AccessSpec has been added to the RFC
				// message handler
				rfcMessageHandler.add(accessSpec1);
				times = 0;
				AccessSpec as;
				rfcMessageHandler.add(as = withCapture());
				times = 1;
				assertEquals(as.getAccessSpecId(), accessSpec1.getAccessSpecId());
				assertTrue(as.isCurrentState());
			}
		};

		new Expectations() {
			{
				rfcMessageHandler.remove(anyLong);
				result = accessSpec1;
			}
		};

		// disable the AccessSpec
		am.setState(accessSpec1.getAccessSpecId(), false/* enabled */);
		assertFalse(am.getAccessSpecs().get(0).isCurrentState());
		new Verifications() {
			{
				// the enabled AccessSpec has been removed from the RFC message
				// handler
				rfcMessageHandler.remove(accessSpec1.getAccessSpecId());
				times = 1;
			}
		};

		// add a further disabled access specs
		AccessSpec accessSpec2 = new AccessSpec(new TLVParameterHeader((byte) 0),
				890L /* accessSpecID */, 1 /* antennaID */, ProtocolId.EPC_GLOBAL_C1G2,
				false /* currentState */, 2L /* roSpecID */, null /* accessSpecStopTrigger */,
				null /* AccessCommand */);
		am.add(accessSpec2);
		// activate both access specs with identifier 0
		am.setState(0, true);
		for (AccessSpec accessSpec : am.getAccessSpecs()) {
			assertTrue(accessSpec.isCurrentState());
		}
		// deactivate both access specs with identifier 0
		am.setState(0, false);
		for (AccessSpec accessSpec : am.getAccessSpecs()) {
			assertFalse(accessSpec.isCurrentState());
		}
	}

	@Test
	public void getAccessSpecs(@Mocked final RFCMessageHandler rfcMessageHandler) throws Exception {
		// add AccessSpecs
		final AccessSpec accessSpec1 = new AccessSpec(new TLVParameterHeader((byte) 0),
				567L /* accessSpecID */, 1 /* antennaID */, ProtocolId.EPC_GLOBAL_C1G2,
				false /* currentState */, 2L /* roSpecID */, null /* accessSpecStopTrigger */,
				null /* AccessCommand */);
		final AccessSpec accessSpec2 = new AccessSpec(new TLVParameterHeader((byte) 0),
				568L /* accessSpecID */, 1 /* antennaID */, ProtocolId.EPC_GLOBAL_C1G2,
				false /* currentState */, 2L /* roSpecID */, null /* accessSpecStopTrigger */,
				null /* AccessCommand */);
		final AccessSpec accessSpec3 = new AccessSpec(new TLVParameterHeader((byte) 0),
				569L /* accessSpecID */, 1 /* antennaID */, ProtocolId.EPC_GLOBAL_C1G2,
				false /* currentState */, 2L /* roSpecID */, null /* accessSpecStopTrigger */,
				null /* AccessCommand */);
		AccessSpecsManager am = new AccessSpecsManager(rfcMessageHandler);
		am.add(accessSpec1);
		am.add(accessSpec2);
		am.add(accessSpec3);
		// the AccessSpecs must be returned in the same order as they have been
		// added
		List<AccessSpec> accessSpecs = am.getAccessSpecs();
		assertEquals(accessSpecs.size(), 3);
		assertEquals(accessSpecs.get(0).getAccessSpecId(), 567);
		assertEquals(accessSpecs.get(1).getAccessSpecId(), 568);
		assertEquals(accessSpecs.get(2).getAccessSpecId(), 569);
	}
}
