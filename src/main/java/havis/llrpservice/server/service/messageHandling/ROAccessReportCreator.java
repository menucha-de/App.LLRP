package havis.llrpservice.server.service.messageHandling;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import havis.device.rf.tag.TagData;
import havis.device.rf.tag.result.KillResult;
import havis.device.rf.tag.result.LockResult;
import havis.device.rf.tag.result.OperationResult;
import havis.device.rf.tag.result.ReadResult;
import havis.device.rf.tag.result.WriteResult;
import havis.llrpservice.common.ids.IdGenerator;
import havis.llrpservice.data.DataTypeConverter;
import havis.llrpservice.data.message.MessageHeader;
import havis.llrpservice.data.message.ProtocolVersion;
import havis.llrpservice.data.message.ROAccessReport;
import havis.llrpservice.data.message.parameter.AccessSpecId;
import havis.llrpservice.data.message.parameter.AntennaId;
import havis.llrpservice.data.message.parameter.C1G2CRC;
import havis.llrpservice.data.message.parameter.C1G2EPCMemorySelector;
import havis.llrpservice.data.message.parameter.C1G2KillOpSpecResult;
import havis.llrpservice.data.message.parameter.C1G2KillOpSpecResultValues;
import havis.llrpservice.data.message.parameter.C1G2LockOpSpecResult;
import havis.llrpservice.data.message.parameter.C1G2LockOpSpecResultValues;
import havis.llrpservice.data.message.parameter.C1G2PC;
import havis.llrpservice.data.message.parameter.C1G2ReadOpSpecResult;
import havis.llrpservice.data.message.parameter.C1G2ReadOpSpecResultValues;
import havis.llrpservice.data.message.parameter.C1G2WriteOpSpecResult;
import havis.llrpservice.data.message.parameter.C1G2WriteOpSpecResultValues;
import havis.llrpservice.data.message.parameter.C1G2XPCW1;
import havis.llrpservice.data.message.parameter.C1G2XPCW2;
import havis.llrpservice.data.message.parameter.ChannelIndex;
import havis.llrpservice.data.message.parameter.EPC96;
import havis.llrpservice.data.message.parameter.EPCData;
import havis.llrpservice.data.message.parameter.FirstSeenTimestampUTC;
import havis.llrpservice.data.message.parameter.FirstSeenTimestampUptime;
import havis.llrpservice.data.message.parameter.InventoryParameterSpecID;
import havis.llrpservice.data.message.parameter.LastSeenTimestampUTC;
import havis.llrpservice.data.message.parameter.LastSeenTimestampUptime;
import havis.llrpservice.data.message.parameter.Parameter;
import havis.llrpservice.data.message.parameter.PeakRSSI;
import havis.llrpservice.data.message.parameter.ROSpecID;
import havis.llrpservice.data.message.parameter.SpecIndex;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.data.message.parameter.TVParameterHeader;
import havis.llrpservice.data.message.parameter.TagReportContentSelector;
import havis.llrpservice.data.message.parameter.TagReportData;
import havis.llrpservice.data.message.parameter.TagSeenCount;
import havis.llrpservice.sbc.rfc.message.ExecuteResponse;
import havis.llrpservice.server.rfc.messageData.ExecuteResponseData;
import havis.llrpservice.server.service.data.ROAccessReportEntity;

public class ROAccessReportCreator {

	public ROAccessReport create(ProtocolVersion protocolVersion, ExecuteResponse executeResponse,
			ExecuteResponseData executeResponseData, TagReportContentSelector contentSelector) {
		MessageHeader header = new MessageHeader((byte) 0x00, protocolVersion,
				IdGenerator.getNextLongId());
		ROAccessReport report = new ROAccessReport(header);
		// Is for every tag report the same
		ROSpecID roSpecID = new ROSpecID(new TVParameterHeader(),
				executeResponseData.getRoSpecId());
		// Is for every tag report the same
		SpecIndex specIndex = new SpecIndex(new TVParameterHeader(),
				executeResponseData.getSpecIndex());
		InventoryParameterSpecID invParamSpecId = new InventoryParameterSpecID(
				new TVParameterHeader(), executeResponseData.getInventoryParameterSpecId());

		List<TagReportData> tagReportDataList = new ArrayList<>();

		for (TagData tagData : executeResponse.getTagData()) {
			TagReportData tagReportData;
			if (tagData.getEpc().length == 12 /* 96-Bit */) {
				EPC96 epc96 = new EPC96(new TVParameterHeader(), tagData.getEpc());
				tagReportData = new TagReportData(new TLVParameterHeader((byte) 0x00), epc96);
			} else {
				EPCData epcData = new EPCData(new TLVParameterHeader((byte) 0x00),
						toBitSet(tagData.getEpc()));
				epcData.setEpcLengthBits(tagData.getEpc().length * 8);
				tagReportData = new TagReportData(new TLVParameterHeader((byte) 0x00), epcData);
			}
			if (contentSelector.isEnableROSpecID()) {
				tagReportData.setRoSpecID(roSpecID);
			}
			if (contentSelector.isEnableSpecIndex()) {
				tagReportData.setSpecIndex(specIndex);
			}
			if (contentSelector.isEnableInventoryParameterSpecID()) {
				tagReportData.setInvParaSpecID(invParamSpecId);
			}
			if (contentSelector.isEnableAntennaID()) {
				tagReportData
						.setAntID(new AntennaId(new TVParameterHeader(), tagData.getAntennaID()));
			}
			if (contentSelector.isEnableChannelIndex()) {
				tagReportData.setChannelInd(
						new ChannelIndex(new TVParameterHeader(), tagData.getChannel()));
			}
			if (contentSelector.isEnablePeakRSSI()) {
				tagReportData.setPeakRSSI(
						new PeakRSSI(new TVParameterHeader(), (byte) tagData.getRssi()));
			}
			if (contentSelector.isEnableFirstSeenTimestamp()) {
				BigInteger ts = new BigInteger(
						String.valueOf(executeResponse.getTimeStamp().getTimestamp() * 1000));
				if (executeResponse.getTimeStamp().isUtc()) {
					tagReportData
							.setFirstSTUTC(new FirstSeenTimestampUTC(new TVParameterHeader(), ts));
				} else {
					tagReportData.setFirstSTUptime(
							new FirstSeenTimestampUptime(new TVParameterHeader(), ts));
				}
			}
			if (contentSelector.isEnableLastSeenTimestamp()) {
				BigInteger ts = new BigInteger(
						String.valueOf(executeResponse.getTimeStamp().getTimestamp() * 1000));
				if (executeResponse.getTimeStamp().isUtc()) {
					tagReportData
							.setLastSTUTC(new LastSeenTimestampUTC(new TVParameterHeader(), ts));
				} else {
					tagReportData.setLastSTUptime(
							new LastSeenTimestampUptime(new TVParameterHeader(), ts));
				}
			}
			if (contentSelector.isEnableTagSeenCount()) {
				tagReportData.setTagSC(new TagSeenCount(new TVParameterHeader(), 1));
			}

			List<C1G2EPCMemorySelector> contentSelectorC1G2List = contentSelector
					.getC1g2EPCMemorySelectorList();
			if (contentSelectorC1G2List != null && !contentSelectorC1G2List.isEmpty()) {
				C1G2EPCMemorySelector contentSelectorC1G2 = contentSelectorC1G2List.get(0);
				List<Parameter> c1g2TagDataList = new ArrayList<>();
				if (contentSelectorC1G2.isEnableCRC()) {
					c1g2TagDataList.add(new C1G2CRC(new TVParameterHeader(),
							DataTypeConverter.ushort(tagData.getCrc())));
				}
				if (contentSelectorC1G2.isEnablePCBits()) {
					c1g2TagDataList.add(new C1G2PC(new TVParameterHeader(),
							DataTypeConverter.ushort(tagData.getPc())));
				}
				if (contentSelectorC1G2.isEnableXPCBits()) {
					int xpc = tagData.getXpc(); // uint value
					c1g2TagDataList
							.add(new C1G2XPCW1(new TVParameterHeader(), (xpc >> 16) & 0xFFFF));
					c1g2TagDataList.add(new C1G2XPCW2(new TVParameterHeader(), xpc & 0xFFFF));
				}
				tagReportData.setC1g2TagDataList(c1g2TagDataList);
			}

			List<Parameter> opSpecResultList = new ArrayList<>();
			if (tagData.getResultList() != null && !tagData.getResultList().isEmpty()) {
				if (contentSelector.isEnableAccessSpecID()) {
					long accessSpecId = executeResponseData.getTagDataAccessSpecIds()
							.get(tagData.getTagDataId());
					tagReportData.setAccessSpecID(
							new AccessSpecId(new TVParameterHeader(), accessSpecId));
				}

				List<OperationResult> results = tagData.getResultList();
				for (OperationResult opResult : results) {
					if (opResult instanceof KillResult) {
						KillResult killResult = (KillResult) opResult;
						C1G2KillOpSpecResultValues result = C1G2KillOpSpecResultValues.SUCCESS;
						switch (killResult.getResult()) {
						case INCORRECT_PASSWORD_ERROR:
							result = C1G2KillOpSpecResultValues.INCORRECT_PASSWORD_ERROR;
							break;
						case INSUFFICIENT_POWER:
							result = C1G2KillOpSpecResultValues.INSUFFICIENT_POWER_TO_PERFORM_KILL_OPERATION;
							break;
						case NO_RESPONSE_FROM_TAG:
							result = C1G2KillOpSpecResultValues.NO_RESPONSE_FROM_TAG;
							break;
						case NON_SPECIFIC_READER_ERROR:
							result = C1G2KillOpSpecResultValues.NON_SPECIFIC_READER_ERROR;
							break;
						case NON_SPECIFIC_TAG_ERROR:
							result = C1G2KillOpSpecResultValues.NON_SPECIFIC_TAG_ERROR;
							break;
						case SUCCESS:
							result = C1G2KillOpSpecResultValues.SUCCESS;
							break;
						case ZERO_KILL_PASSWORD_ERROR:
							result = C1G2KillOpSpecResultValues.ZERO_KILL_PASSWORD_ERROR;
							break;
						}

						opSpecResultList
								.add(new C1G2KillOpSpecResult(new TLVParameterHeader((byte) 0x00),
										result, Integer.valueOf(killResult.getOperationId())));
					} else if (opResult instanceof LockResult) {
						LockResult lockResult = (LockResult) opResult;
						C1G2LockOpSpecResultValues result = C1G2LockOpSpecResultValues.SUCCESS;
						switch (lockResult.getResult()) {
						case INCORRECT_PASSWORD_ERROR:
							result = C1G2LockOpSpecResultValues.INCORRECT_PASSWORD_ERROR;
							break;
						case INSUFFICIENT_POWER:
							result = C1G2LockOpSpecResultValues.INSUFFICIENT_POWER_TO_PERFORM_LOCK_OPERATION;
							break;
						case NO_RESPONSE_FROM_TAG:
							result = C1G2LockOpSpecResultValues.NO_RESPONSE_FROM_TAG;
							break;
						case NON_SPECIFIC_READER_ERROR:
							result = C1G2LockOpSpecResultValues.NON_SPECIFIC_READER_ERROR;
							break;
						case NON_SPECIFIC_TAG_ERROR:
							result = C1G2LockOpSpecResultValues.NON_SPECIFIC_TAG_ERROR;
							break;
						case SUCCESS:
							result = C1G2LockOpSpecResultValues.SUCCESS;
							break;
						case MEMORY_LOCKED_ERROR:
							result = C1G2LockOpSpecResultValues.TAG_MEMORY_LOCKED_ERROR;
							break;
						case MEMORY_OVERRUN_ERROR:
							result = C1G2LockOpSpecResultValues.TAG_MEMORY_OVERRUN_ERROR;
							break;
						}

						opSpecResultList
								.add(new C1G2LockOpSpecResult(new TLVParameterHeader((byte) 0x00),
										result, Integer.valueOf(lockResult.getOperationId())));

					} else if (opResult instanceof ReadResult) {
						ReadResult readResult = (ReadResult) opResult;
						C1G2ReadOpSpecResultValues result = C1G2ReadOpSpecResultValues.SUCCESS;

						switch (readResult.getResult()) {
						case INCORRECT_PASSWORD_ERROR:
							result = C1G2ReadOpSpecResultValues.INCORRECT_PASSWORD_ERROR;
							break;
						case MEMORY_LOCKED_ERROR:
							result = C1G2ReadOpSpecResultValues.MEMORY_LOCKED_ERROR;
							break;
						case MEMORY_OVERRUN_ERROR:
							result = C1G2ReadOpSpecResultValues.MEMORY_OVERRUN_ERROR;
							break;
						case NO_RESPONSE_FROM_TAG:
							result = C1G2ReadOpSpecResultValues.NO_RESPONSE_FROM_TAG;
							break;
						case NON_SPECIFIC_READER_ERROR:
							result = C1G2ReadOpSpecResultValues.NON_SPECIFIC_READER_ERROR;
							break;
						case NON_SPECIFIC_TAG_ERROR:
							result = C1G2ReadOpSpecResultValues.NON_SPECIFIC_TAG_ERROR;
							break;
						case SUCCESS:
							result = C1G2ReadOpSpecResultValues.SUCCESS;
							break;
						}

						opSpecResultList
								.add(new C1G2ReadOpSpecResult(new TLVParameterHeader((byte) 0x00),
										result, Integer.valueOf(readResult.getOperationId()),
										readResult.getReadData()));
					} else if (opResult instanceof WriteResult) {
						WriteResult writeResult = (WriteResult) opResult;
						C1G2WriteOpSpecResultValues result = C1G2WriteOpSpecResultValues.SUCCESS;
						switch (writeResult.getResult()) {
						case INCORRECT_PASSWORD_ERROR:
							result = C1G2WriteOpSpecResultValues.INCORRECT_PASSWORD_ERROR;
							break;
						case INSUFFICIENT_POWER:
							result = C1G2WriteOpSpecResultValues.INSUFFICIENT_POWER_TO_PERFORM_MEMORY_WRITE_OPERATION;
							break;
						case NO_RESPONSE_FROM_TAG:
							result = C1G2WriteOpSpecResultValues.NO_RESPONSE_FROM_TAG;
							break;
						case NON_SPECIFIC_READER_ERROR:
							result = C1G2WriteOpSpecResultValues.NON_SPECIFIC_READER_ERROR;
							break;
						case NON_SPECIFIC_TAG_ERROR:
							result = C1G2WriteOpSpecResultValues.NON_SPECIFIC_TAG_ERROR;
							break;
						case SUCCESS:
							result = C1G2WriteOpSpecResultValues.SUCCESS;
							break;
						case MEMORY_LOCKED_ERROR:
							result = C1G2WriteOpSpecResultValues.TAG_MEMORY_LOCKED_ERROR;
							break;
						case MEMORY_OVERRUN_ERROR:
							result = C1G2WriteOpSpecResultValues.TAG_MEMORY_OVERRUN_ERROR;
							break;
						}

						opSpecResultList
								.add(new C1G2WriteOpSpecResult(new TLVParameterHeader((byte) 0x00),
										result, Integer.valueOf(writeResult.getOperationId()),
										writeResult.getWordsWritten()));
					}

				}
			}
			tagReportData.setOpSpecResultList(opSpecResultList);
			tagReportDataList.add(tagReportData);
		}
		report.setTagReportDataList(tagReportDataList);
		return report;
	}

	public ROAccessReport accumulate(ProtocolVersion protocolVersion,
			List<ROAccessReportEntity> reportEntities) {
		MessageHeader header = new MessageHeader((byte) 0, protocolVersion,
				IdGenerator.getNextLongId());
		ROAccessReport ret = new ROAccessReport(header);
		List<TagReportData> accumulatedTagReports = new ArrayList<>();
		List<Long> accumulatedTagReportsRoSpecIds = new ArrayList<>();
		// for each report
		for (ROAccessReportEntity reportEntity : reportEntities) {
			long roSpecId = reportEntity.getRoSpecId();
			List<TagReportData> tagReports = reportEntity.getReport().getTagReportDataList();
			if (tagReports != null) {
				// for each tag report
				for (TagReportData tagReport : tagReports) {
					boolean isAccumulated = false;
					// for each accumulated tag report
					for (int i = 0; i < accumulatedTagReports.size() && !isAccumulated; i++) {
						TagReportData accumulatedTagReport = accumulatedTagReports.get(i);
						long accumulatedTagReportRoSpecId = accumulatedTagReportsRoSpecIds.get(i);
						// if tag reports can be accumulated
						if (match(roSpecId, tagReport, accumulatedTagReportRoSpecId,
								accumulatedTagReport)) {
							accumulate(tagReport, accumulatedTagReport);
							isAccumulated = true;
						}
					}
					if (!isAccumulated) {
						// add tag report to list of accumulated tag reports
						accumulatedTagReports.add(tagReport);
						accumulatedTagReportsRoSpecIds.add(roSpecId);
					}
				}
			}
		}
		ret.setTagReportDataList(accumulatedTagReports);
		return ret;
	}

	private boolean match(long roSpecId1, TagReportData tagReport1, long roSpecId2,
			TagReportData tagReport2) {
		// see 14.2.3.1

		// RoSpecId
		if (roSpecId1 != roSpecId2
				|| tagReport1.getRoSpecID() != null
						&& tagReport1.getRoSpecID().getRoSpecID() != roSpecId1
				|| tagReport2.getRoSpecID() != null
						&& tagReport2.getRoSpecID().getRoSpecID() != roSpecId2) {
			return false;
		}

		// EPC96
		EPC96 e1 = tagReport1.getEpc96();
		EPC96 e2 = tagReport2.getEpc96();
		if (!(not(e1, e2) || and(e1, e2) && Arrays.equals(e1.getEpc(), e2.getEpc()))) {
			return false;
		}

		// EPCData
		EPCData e3 = tagReport1.getEpcData();
		EPCData e4 = tagReport2.getEpcData();
		if (!(not(e3, e4) || and(e3, e4) && e3.getEpc().equals(e4.getEpc()))) {
			return false;
		}
		// OpSpecResultList
		List<Parameter> paramList1 = tagReport1.getOpSpecResultList() == null
				? new ArrayList<Parameter>() : tagReport1.getOpSpecResultList();
		List<Parameter> paramList2 = tagReport2.getOpSpecResultList() == null
				? new ArrayList<Parameter>() : tagReport2.getOpSpecResultList();
		if (paramList1.size() != paramList2.size()) {
			return false;
		}
		for (int i = 0; i < paramList1.size(); i++) {
			Parameter p1 = paramList1.get(i);
			Parameter p2 = paramList2.get(i);
			if (p1.getParameterHeader().getParameterType() != p2.getParameterHeader()
					.getParameterType()) {
				return false;
			}
			switch (p1.getParameterHeader().getParameterType()) {
			case C1G2_KILL_OP_SPEC_RESULT:
				C1G2KillOpSpecResult killResult1 = (C1G2KillOpSpecResult) p1;
				C1G2KillOpSpecResult killResult2 = (C1G2KillOpSpecResult) p2;
				if (killResult1.getOpSpecID() != killResult2.getOpSpecID()
						|| killResult1.getResult() != killResult2.getResult()) {
					return false;
				}
				break;
			case C1G2_LOCK_OP_SPEC_RESULT:
				C1G2LockOpSpecResult lockResult1 = (C1G2LockOpSpecResult) p1;
				C1G2LockOpSpecResult lockResult2 = (C1G2LockOpSpecResult) p2;
				if (lockResult1.getOpSpecID() != lockResult2.getOpSpecID()
						|| lockResult1.getResult() != lockResult2.getResult()) {
					return false;
				}
				break;
			case C1G2_READ_OP_SPEC_RESULT:
				C1G2ReadOpSpecResult readResult1 = (C1G2ReadOpSpecResult) p1;
				C1G2ReadOpSpecResult readResult2 = (C1G2ReadOpSpecResult) p2;
				if (readResult1.getOpSpecID() != readResult2.getOpSpecID()
						|| readResult1.getResult() != readResult2.getResult()
						|| readResult1.getReadDataWordCount() != readResult2.getReadDataWordCount()
						|| !Arrays.equals(readResult1.getReadData(), readResult2.getReadData())) {
					return false;
				}
				break;
			case C1G2_WRITE_OP_SPEC_RESULT:
				C1G2WriteOpSpecResult writeResult1 = (C1G2WriteOpSpecResult) p1;
				C1G2WriteOpSpecResult writeResult2 = (C1G2WriteOpSpecResult) p2;
				if (writeResult1.getOpSpecID() != writeResult2.getOpSpecID()
						|| writeResult1.getResult() != writeResult2.getResult()
						|| writeResult1.getNumWordsWritten() != writeResult2.getNumWordsWritten()) {
					return false;
				}
				break;
			default:
			}
		}

		// SpecIndex
		SpecIndex s1 = tagReport1.getSpecIndex();
		SpecIndex s2 = tagReport2.getSpecIndex();
		if (!(not(s1, s2) || and(s1, s2) && s1.getSpecIndex() == s2.getSpecIndex())) {
			return false;
		}
		// InventoryParameterSpecId
		InventoryParameterSpecID i1 = tagReport1.getInvParaSpecID();
		InventoryParameterSpecID i2 = tagReport2.getInvParaSpecID();
		if (!(not(i1, i2) || and(i1, i2)
				&& i1.getInventoryParameterSpecID() == i2.getInventoryParameterSpecID())) {
			return false;
		}
		// AntennaId
		AntennaId a1 = tagReport1.getAntID();
		AntennaId a2 = tagReport2.getAntID();
		if (!(not(a1, a2) || and(a1, a2) && a1.getAntennaId() == a2.getAntennaId())) {
			return false;
		}
		// AirProtocolTagData
		paramList1 = tagReport1.getC1g2TagDataList() == null ? new ArrayList<Parameter>()
				: tagReport1.getC1g2TagDataList();
		paramList2 = tagReport2.getC1g2TagDataList() == null ? new ArrayList<Parameter>()
				: tagReport2.getC1g2TagDataList();
		if (paramList1.size() != paramList2.size()) {
			return false;
		}
		for (int i = 0; i < paramList1.size(); i++) {
			Parameter p1 = paramList1.get(i);
			Parameter p2 = paramList2.get(i);
			if (p1.getParameterHeader().getParameterType() != p2.getParameterHeader()
					.getParameterType()) {
				return false;
			}
			switch (p1.getParameterHeader().getParameterType()) {
			case C1G2_CRC:
				if (((C1G2CRC) p1).getCrc() != ((C1G2CRC) p2).getCrc()) {
					return false;
				}
				break;
			case C1G2_PC:
				if (((C1G2PC) p1).getPcBits() != ((C1G2PC) p2).getPcBits()) {
					return false;
				}
				break;
			case C1G2_XPCW1:
				if (((C1G2XPCW1) p1).getXpcW1() != ((C1G2XPCW1) p2).getXpcW1()) {
					return false;
				}
				break;
			case C1G2_XPCW2:
				if (((C1G2XPCW2) p1).getXpcW2() != ((C1G2XPCW2) p2).getXpcW2()) {
					return false;
				}
				break;
			default:
			}
		}
		// AccessSpecId
		AccessSpecId a3 = tagReport1.getAccessSpecID();
		AccessSpecId a4 = tagReport2.getAccessSpecID();
		if (!(not(a3, a4) || and(a3, a4) && a3.getAccessSpecId() == a4.getAccessSpecId())) {
			return false;
		}
		return true;
	}

	private void accumulate(TagReportData tagReport, TagReportData accumulatedTagReport) {
		// see 14.2.3.1

		// FirstSeenTimestampUTC
		FirstSeenTimestampUTC f1 = tagReport.getFirstSTUTC();
		FirstSeenTimestampUTC f2 = accumulatedTagReport.getFirstSTUTC();
		if (and(f1, f2) && f1.getMicroseconds().longValue() < f2.getMicroseconds().longValue()) {
			f2.setMicroseconds(f1.getMicroseconds());
		}
		// FirstSeenTimestampUptime
		FirstSeenTimestampUptime f3 = tagReport.getFirstSTUptime();
		FirstSeenTimestampUptime f4 = accumulatedTagReport.getFirstSTUptime();
		if (and(f3, f4) && f3.getMicroseconds().longValue() < f4.getMicroseconds().longValue()) {
			f4.setMicroseconds(f3.getMicroseconds());
		}

		// LastSeenTimestampUTC
		LastSeenTimestampUTC f5 = tagReport.getLastSTUTC();
		LastSeenTimestampUTC f6 = accumulatedTagReport.getLastSTUTC();
		if (and(f5, f6) && f5.getMicroseconds().longValue() > f6.getMicroseconds().longValue()) {
			f6.setMicroseconds(f5.getMicroseconds());
		}
		// LastSeenTimestampUptime
		LastSeenTimestampUptime f7 = tagReport.getLastSTUptime();
		LastSeenTimestampUptime f8 = accumulatedTagReport.getLastSTUptime();
		if (and(f7, f8) && f7.getMicroseconds().longValue() > f8.getMicroseconds().longValue()) {
			f8.setMicroseconds(f7.getMicroseconds());
		}

		// max. PeakRSSI
		PeakRSSI p1 = tagReport.getPeakRSSI();
		PeakRSSI p2 = accumulatedTagReport.getPeakRSSI();
		if (and(p1, p2) && p1.getPeakRSSI() > p2.getPeakRSSI()) {
			p2.setPeakRSSI(p1.getPeakRSSI());
		}

		// ChannelIndex (omitted)

		// increase TagSeenCount
		if (accumulatedTagReport.getTagSC() != null) {
			accumulatedTagReport.setTagSC(new TagSeenCount(new TVParameterHeader(),
					accumulatedTagReport.getTagSC().getTagCount() + 1));
		}
	}

	private boolean not(Object o1, Object o2) {
		return o1 == null && o2 == null;
	}

	private boolean and(Object o1, Object o2) {
		return o1 != null && o2 != null;
	}

	private static BitSet toBitSet(byte[] bytes) {
		BitSet bits = new BitSet(bytes.length * 8);
		// for each bit
		for (int i = 0; i < bytes.length * 8; i++) {
			if ((bytes[i / 8] & (0x80 >> (i % 8))) > 0) {
				bits.set(i);
			}
		}
		return bits;
	}
}
