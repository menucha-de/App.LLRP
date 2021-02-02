package havis.llrpservice.server.service.data;

import havis.llrpservice.data.message.parameter.C1G2LLRPCapabilities;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.data.message.parameter.UHFC1G2RFModeTableEntry;
import havis.llrpservice.data.message.parameter.UHFC1G2RFModeTableEntryDivideRatio;
import havis.llrpservice.data.message.parameter.UHFC1G2RFModeTableEntryForwardLinkModulation;
import havis.llrpservice.data.message.parameter.UHFC1G2RFModeTableEntryModulation;
import havis.llrpservice.data.message.parameter.UHFC1G2RFModeTableEntrySpectralMaskIndicator;

/**
 * Provides the reader capabilities which cannot be determined via the RF/GPIO
 * controllers.
 */
public class LLRPReaderCapabilities {

	// GeneralDeviceCapabilities
	private final boolean canSetAntennaProperties = true;

	// RegulatoryCapabilities
	private final UHFC1G2RFModeTableEntry uhfC1G2RFModeTableEntry = new UHFC1G2RFModeTableEntry(
			new TLVParameterHeader((byte) 0x00), 0 /* modeIdentifier */,
			UHFC1G2RFModeTableEntryDivideRatio._64DIV3 /* drValue */, false /* epcHAGConformance */,
			UHFC1G2RFModeTableEntryModulation._2 /* mValue */,
			UHFC1G2RFModeTableEntryForwardLinkModulation.DSB_ASK /* forwardLinkModukation */,
			UHFC1G2RFModeTableEntrySpectralMaskIndicator.DENSE_INTERROGATOR_MODE_MASK /* spectralMaskIndicator */,
			0 /* bdrValue */, 0 /* pieValue */, 0 /* minTariValue */, 0 /* maxTariValue */,
			0 /* stepTariValue */);

	// C1G2LLRPCapabilities
	private final C1G2LLRPCapabilities c1g2llrpCapabilities = new C1G2LLRPCapabilities(
			new TLVParameterHeader((byte) 0x00), false /* canSupportBlockErase */,
			false /* canSupportBlockWrite */, false /* canSupportBlockPermalock */,
			false /* canSupportTagRecommissioning */, false /* canSupportUMIMethod2 */,
			false /* canSupportXPC */, 0 /* maxNumSelectFiltersPerQuery */);

	/* GeneralDeviceCapabilities */
	public boolean isCanSetAntennaProperties() {
		return canSetAntennaProperties;
	}

	/* RegulatoryCapabilities */

	public UHFC1G2RFModeTableEntry getUHFC1G2RFModeTableEntry() {
		return uhfC1G2RFModeTableEntry;
	}

	/* C1G2LLRPCapabilties */

	public C1G2LLRPCapabilities getC1G2LLRPCapabilities() {
		return c1g2llrpCapabilities;
	}
}
