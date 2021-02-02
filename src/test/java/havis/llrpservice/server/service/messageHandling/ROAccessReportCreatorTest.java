package havis.llrpservice.server.service.messageHandling;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.Test;

import havis.device.rf.tag.TagData;
import havis.device.rf.tag.result.ReadResult;
import havis.device.rf.tag.result.ReadResult.Result;
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
import havis.llrpservice.data.message.parameter.ProtocolId;
import havis.llrpservice.data.message.parameter.ROSpecID;
import havis.llrpservice.data.message.parameter.SpecIndex;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.data.message.parameter.TVParameterHeader;
import havis.llrpservice.data.message.parameter.TagReportContentSelector;
import havis.llrpservice.data.message.parameter.TagReportData;
import havis.llrpservice.data.message.parameter.TagSeenCount;
import havis.llrpservice.sbc.rfc.message.ExecuteResponse;
import havis.llrpservice.server.platform.TimeStamp;
import havis.llrpservice.server.rfc.messageData.ExecuteResponseData;
import havis.llrpservice.server.service.data.ROAccessReportEntity;
import havis.util.platform.Platform;
import mockit.Expectations;
import mockit.Mocked;

public class ROAccessReportCreatorTest {

	@Mocked
	private Platform platform;

	@Test
	public void create() throws Exception {
		new Expectations() {
			{
				platform.hasUTCClock();
				result = true;
			}
		};
		ROAccessReportCreator creator = new ROAccessReportCreator();

		// create an empty report
		List<TagData> tagDataList = new ArrayList<>();
		ExecuteResponse executeResponse = new ExecuteResponse(
				new havis.llrpservice.sbc.rfc.message.MessageHeader(3), tagDataList,
				new TimeStamp(platform));
		Map<Long, Long> tagDataAccessSpecIds = new HashMap<Long, Long>();
		tagDataAccessSpecIds.put(30L, 40L);
		ExecuteResponseData executeResponseData = new ExecuteResponseData(1 /* roSpecId */,
				2 /* specIndex */, 3 /* inventoryParameterSpecId */, 4 /* antennaId */,
				ProtocolId.EPC_GLOBAL_C1G2, tagDataAccessSpecIds, true /* isLastResponse */);
		TagReportContentSelector contentSelector = new TagReportContentSelector(
				new TLVParameterHeader((byte) 0 /* reserved */), false /* enableROSpecID */,
				false /* enableSpecIndex */, false /* enableInventoryParameterSpecID */,
				false /* enableAntennaID */, false /* enableChannelIndex */,
				false /* enablePeakRSSI */, false /* enableFirstSeenTimestamp */,
				false /* enableLastSeenTimestamp */, false /* enableTagSeenCount */,
				false /* enableAccessSpecID */);
		List<C1G2EPCMemorySelector> c1g2epcMemorySelectorList = new ArrayList<>();
		c1g2epcMemorySelectorList.add(new C1G2EPCMemorySelector(new TLVParameterHeader((byte) 0),
				false /* enableCRC */, false /* enablePCBits */, false /* enableXPCBits */));
		contentSelector.setC1g2EPCMemorySelectorList(c1g2epcMemorySelectorList);
		ROAccessReport report = creator.create(ProtocolVersion.LLRP_V1_1, executeResponse,
				executeResponseData, contentSelector);
		Assert.assertEquals(report.getMessageHeader().getVersion(), ProtocolVersion.LLRP_V1_1);
		Assert.assertTrue(report.getTagReportDataList().isEmpty());

		// add tag data without access results and create a report
		TagData tagData = new TagData();
		tagData.setTagDataId(30L);
		tagData.setChannel((short) 10);
		tagData.setAntennaID((short) executeResponseData.getAntennaId());
		tagData.setCrc((short) 0x8000); // ushort value
		tagData.setPc((short) 0x8001); // ushort value
		tagData.setEpc(new byte[] { 14, 15 });
		int xpcW1 = 0x8765;
		int xpcW2 = 0x4321;
		tagData.setXpc((xpcW1 << 16) | xpcW2); // uint value
		tagData.setRssi(17);
		tagDataList.add(tagData);
		report = creator.create(ProtocolVersion.LLRP_V1_1, executeResponse, executeResponseData,
				contentSelector);
		// check disabled fields
		Assert.assertEquals(report.getTagReportDataList().size(), 1);
		TagReportData data = report.getTagReportDataList().get(0);
		Assert.assertNull(data.getRoSpecID());
		Assert.assertNull(data.getSpecIndex());
		Assert.assertNull(data.getInvParaSpecID());
		Assert.assertNull(data.getAntID());
		Assert.assertNull(data.getChannelInd());
		Assert.assertNull(data.getPeakRSSI());
		Assert.assertNull(data.getFirstSTUTC());
		Assert.assertNull(data.getFirstSTUptime());
		Assert.assertNull(data.getLastSTUTC());
		Assert.assertNull(data.getLastSTUptime());
		Assert.assertNull(data.getTagSC());
		Assert.assertTrue(data.getC1g2TagDataList().isEmpty());
		Assert.assertNull(data.getAccessSpecID());
		Assert.assertTrue(data.getOpSpecResultList().isEmpty());

		// enable fields and create report again
		contentSelector = new TagReportContentSelector(
				new TLVParameterHeader((byte) 0 /* reserved */), true /* enableROSpecID */,
				true /* enableSpecIndex */, true /* enableInventoryParameterSpecID */,
				true /* enableAntennaID */, true /* enableChannelIndex */,
				true /* enablePeakRSSI */, true /* enableFirstSeenTimestamp */,
				true /* enableLastSeenTimestamp */, true /* enableTagSeenCount */,
				true /* enableAccessSpecID */);
		c1g2epcMemorySelectorList = new ArrayList<>();
		c1g2epcMemorySelectorList.add(new C1G2EPCMemorySelector(new TLVParameterHeader((byte) 0),
				true /* enableCRC */, true /* enablePCBits */, true /* enableXPCBits */));
		contentSelector.setC1g2EPCMemorySelectorList(c1g2epcMemorySelectorList);
		long startTime = System.currentTimeMillis();
		report = creator.create(ProtocolVersion.LLRP_V1_1, executeResponse, executeResponseData,
				contentSelector);
		Assert.assertEquals(report.getTagReportDataList().size(), 1);
		data = report.getTagReportDataList().get(0);
		Assert.assertEquals(data.getRoSpecID().getRoSpecID(), executeResponseData.getRoSpecId());
		Assert.assertEquals(data.getSpecIndex().getSpecIndex(), executeResponseData.getSpecIndex());
		Assert.assertEquals(data.getInvParaSpecID().getInventoryParameterSpecID(),
				executeResponseData.getInventoryParameterSpecId());
		Assert.assertEquals(data.getAntID().getAntennaId(), executeResponseData.getAntennaId());
		Assert.assertEquals(data.getChannelInd().getChannelIndex(), tagData.getChannel());
		Assert.assertEquals(data.getPeakRSSI().getPeakRSSI(), tagData.getRssi());
		Assert.assertTrue(data.getFirstSTUTC().getMicroseconds().longValue() >= startTime);
		Assert.assertNull(data.getFirstSTUptime());
		Assert.assertEquals(data.getLastSTUTC().getMicroseconds().longValue(),
				data.getFirstSTUTC().getMicroseconds().longValue());
		Assert.assertNull(data.getLastSTUptime());
		Assert.assertEquals(data.getTagSC().getTagCount(), 1);
		Assert.assertEquals(data.getC1g2TagDataList().size(), 4);
		Assert.assertEquals(((C1G2CRC) data.getC1g2TagDataList().get(0)).getCrc(),
				DataTypeConverter.ushort(tagData.getCrc()));
		Assert.assertEquals(((C1G2PC) data.getC1g2TagDataList().get(1)).getPcBits(),
				DataTypeConverter.ushort(tagData.getPc()));
		Assert.assertEquals(((C1G2XPCW1) data.getC1g2TagDataList().get(2)).getXpcW1(), xpcW1);
		Assert.assertEquals(((C1G2XPCW2) data.getC1g2TagDataList().get(3)).getXpcW2(), xpcW2);

		Assert.assertNull(data.getAccessSpecID());
		Assert.assertTrue(data.getOpSpecResultList().isEmpty());

		// add access results to tag data and create report again
		byte[] readData = new byte[] { 60, 70 };
		tagData.getResultList().add(new ReadResult("123" /* opSpecId */, readData, Result.SUCCESS));
		report = creator.create(ProtocolVersion.LLRP_V1_1, executeResponse, executeResponseData,
				contentSelector);
		data = report.getTagReportDataList().get(0);
		Assert.assertEquals(data.getAccessSpecID().getAccessSpecId(), 40L);
		Assert.assertEquals(data.getOpSpecResultList().size(), 1);
		C1G2ReadOpSpecResult readResult = (C1G2ReadOpSpecResult) data.getOpSpecResultList().get(0);
		Assert.assertEquals(readResult.getOpSpecID(), 123);
		Assert.assertEquals(readResult.getReadData(), readData);
		Assert.assertEquals(readResult.getResult(), C1G2ReadOpSpecResultValues.SUCCESS);
	}

	@Test
	public void accumulate() throws Exception {
		ROAccessReportCreator creator = new ROAccessReportCreator();

		List<ROAccessReportEntity> reportEntities = new ArrayList<ROAccessReportEntity>();

		// empty list
		ROAccessReport report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getMessageHeader().getVersion(), ProtocolVersion.LLRP_V1_1);
		Assert.assertTrue(report.getTagReportDataList().isEmpty());

		// 2 reports with same EPC96 and disabled fields
		TagReportContentSelector contentSelector1 = new TagReportContentSelector(
				new TLVParameterHeader((byte) 0 /* reserved */), false /* enableROSpecID */,
				false /* enableSpecIndex */, false /* enableInventoryParameterSpecID */,
				false /* enableAntennaID */, false /* enableChannelIndex */,
				false /* enablePeakRSSI */, false /* enableFirstSeenTimestamp */,
				false /* enableLastSeenTimestamp */, false /* enableTagSeenCount */,
				false /* enableAccessSpecID */);
		List<C1G2EPCMemorySelector> c1g2epcMemorySelectorList1 = new ArrayList<>();
		C1G2EPCMemorySelector c1g2epcMemorySelector = new C1G2EPCMemorySelector(
				new TLVParameterHeader((byte) 0), false /* enableCRC */, false /* enablePCBits */,
				false /* enableXPCBits */);
		c1g2epcMemorySelectorList1.add(c1g2epcMemorySelector);
		contentSelector1.setC1g2EPCMemorySelectorList(c1g2epcMemorySelectorList1);
		reportEntities
				.add(createTagReportEntity(contentSelector1, false /* epcData */, true /* utc */));
		reportEntities
				.add(createTagReportEntity(contentSelector1, false /* epcData */, true /* utc */));
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 1);
		TagReportData tagReport = report.getTagReportDataList().get(0);
		Assert.assertTrue(Arrays.equals(tagReport.getEpc96().getEpc(), new byte[] { 3, 4 }));

		// 2 reports with same/different field content:
		reportEntities.clear();
		TagReportContentSelector contentSelector2 = new TagReportContentSelector(
				new TLVParameterHeader((byte) 0 /* reserved */), true /* enableROSpecID */,
				true /* enableSpecIndex */, true /* enableInventoryParameterSpecID */,
				true /* enableAntennaID */, true /* enableChannelIndex */,
				true /* enablePeakRSSI */, true /* enableFirstSeenTimestamp */,
				true /* enableLastSeenTimestamp */, true /* enableTagSeenCount */,
				true /* enableAccessSpecID */);
		List<C1G2EPCMemorySelector> c1g2epcMemorySelectorList2 = new ArrayList<>();
		C1G2EPCMemorySelector c1g2epcMemorySelector2 = new C1G2EPCMemorySelector(
				new TLVParameterHeader((byte) 0), true /* enableCRC */, true /* enablePCBits */,
				true /* enableXPCBits */);
		c1g2epcMemorySelectorList2.add(c1g2epcMemorySelector2);
		contentSelector2.setC1g2EPCMemorySelectorList(c1g2epcMemorySelectorList2);

		// EPC96
		reportEntities.clear();
		reportEntities
				.add(createTagReportEntity(contentSelector2, false /* epcData */, false /* utc */));
		reportEntities
				.add(createTagReportEntity(contentSelector2, false /* epcData */, false /* utc */));

		EPC96 epc96 = reportEntities.get(0).getReport().getTagReportDataList().get(0).getEpc96();
		byte[] origEpc96 = epc96.getEpc();
		epc96.setEpc(new byte[] { 123 });
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 2);
		epc96.setEpc(origEpc96);
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 1);

		// EPCData
		reportEntities.clear();
		reportEntities
				.add(createTagReportEntity(contentSelector2, true /* epcData */, true /* utc */));
		reportEntities
				.add(createTagReportEntity(contentSelector2, true /* epcData */, true /* utc */));

		EPCData epcData = reportEntities.get(0).getReport().getTagReportDataList().get(0)
				.getEpcData();
		BitSet origEpcDataEpc = epcData.getEpc();
		BitSet epcDataEpc = new BitSet();
		epcDataEpc.set(1, 1);
		epcData.setEpc(epcDataEpc);
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 2);
		epcData.setEpc(origEpcDataEpc);
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 1);

		// OpSpecResultList
		// - different sizes
		List<Parameter> opSpecResults = reportEntities.get(0).getReport().getTagReportDataList()
				.get(0).getOpSpecResultList();
		Parameter removedOpSpecResult = opSpecResults.remove(opSpecResults.size() - 1);
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 2);
		// - different types
		opSpecResults.add(new C1G2KillOpSpecResult(new TLVParameterHeader((byte) 0),
				C1G2KillOpSpecResultValues.INCORRECT_PASSWORD_ERROR, 3 /* opSpecId */));
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 2);
		opSpecResults.remove(opSpecResults.size() - 1);
		opSpecResults.add(removedOpSpecResult);
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 1);

		C1G2KillOpSpecResult killResult = (C1G2KillOpSpecResult) opSpecResults.get(0);
		C1G2KillOpSpecResultValues origKillResult = killResult.getResult();
		killResult.setResult(C1G2KillOpSpecResultValues.NON_SPECIFIC_READER_ERROR);
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 2);
		killResult.setResult(origKillResult);
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 1);

		C1G2LockOpSpecResult lockResult = (C1G2LockOpSpecResult) opSpecResults.get(1);
		C1G2LockOpSpecResultValues origLockResult = lockResult.getResult();
		lockResult.setResult(C1G2LockOpSpecResultValues.NON_SPECIFIC_READER_ERROR);
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 2);
		lockResult.setResult(origLockResult);
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 1);

		C1G2ReadOpSpecResult readResult = (C1G2ReadOpSpecResult) opSpecResults.get(2);
		byte[] origReadResult = readResult.getReadData();
		readResult.setReadData(new byte[] { 123 });
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 2);
		readResult.setReadData(origReadResult);
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 1);

		C1G2WriteOpSpecResult writeResult = (C1G2WriteOpSpecResult) opSpecResults.get(3);
		int origWriteResult = writeResult.getNumWordsWritten();
		writeResult.setNumWordsWritten(123);
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 2);
		writeResult.setNumWordsWritten(origWriteResult);
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 1);

		// ROSpecId
		ROSpecID origRoSpecId = reportEntities.get(0).getReport().getTagReportDataList().get(0)
				.getRoSpecID();
		reportEntities.get(0).setRoSpecId(123);
		reportEntities.get(0).getReport().getTagReportDataList().get(0).setRoSpecID(
				new ROSpecID(new TVParameterHeader(), reportEntities.get(0).getRoSpecId()));
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 2);
		reportEntities.get(0).setRoSpecId(origRoSpecId.getRoSpecID());
		reportEntities.get(0).getReport().getTagReportDataList().get(0).setRoSpecID(origRoSpecId);
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 1);

		// SpecIndex
		SpecIndex origSpecIndex = reportEntities.get(0).getReport().getTagReportDataList().get(0)
				.getSpecIndex();
		reportEntities.get(0).getReport().getTagReportDataList().get(0)
				.setSpecIndex(new SpecIndex(new TVParameterHeader(), 123));
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 2);
		reportEntities.get(0).getReport().getTagReportDataList().get(0).setSpecIndex(origSpecIndex);
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 1);

		// InventoryParameterSpecID
		InventoryParameterSpecID origInvParamSpecId = reportEntities.get(0).getReport()
				.getTagReportDataList().get(0).getInvParaSpecID();
		reportEntities.get(0).getReport().getTagReportDataList().get(0)
				.setInvParaSpecID(new InventoryParameterSpecID(new TVParameterHeader(), 123));
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 2);
		reportEntities.get(0).getReport().getTagReportDataList().get(0)
				.setInvParaSpecID(origInvParamSpecId);
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 1);

		// AntennaId
		AntennaId origAntennaId = reportEntities.get(0).getReport().getTagReportDataList().get(0)
				.getAntID();
		reportEntities.get(0).getReport().getTagReportDataList().get(0)
				.setAntID(new AntennaId(new TVParameterHeader(), 123));
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 2);
		reportEntities.get(0).getReport().getTagReportDataList().get(0).setAntID(origAntennaId);
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 1);

		// AirProtocolTagData
		// different sizes
		List<Parameter> c1g2TagDataList = reportEntities.get(0).getReport().getTagReportDataList()
				.get(0).getC1g2TagDataList();
		Parameter removedC1g2TagData = c1g2TagDataList.remove(c1g2TagDataList.size() - 1);
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 2);
		// different types
		c1g2TagDataList.add(new C1G2CRC(new TVParameterHeader(), 3));
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 2);
		c1g2TagDataList.remove(c1g2TagDataList.size() - 1);
		c1g2TagDataList.add(removedC1g2TagData);
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 1);

		C1G2CRC crc = (C1G2CRC) reportEntities.get(0).getReport().getTagReportDataList().get(0)
				.getC1g2TagDataList().get(0);
		int origCrc = crc.getCrc();
		crc.setCrc(123);
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 2);
		crc.setCrc(origCrc);
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 1);

		C1G2PC pc = (C1G2PC) reportEntities.get(0).getReport().getTagReportDataList().get(0)
				.getC1g2TagDataList().get(1);
		int origPc = pc.getPcBits();
		pc.setPcBits(123);
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 2);
		pc.setPcBits(origPc);
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 1);

		C1G2XPCW1 xpcW1 = (C1G2XPCW1) reportEntities.get(0).getReport().getTagReportDataList()
				.get(0).getC1g2TagDataList().get(2);
		int origXpcW1 = xpcW1.getXpcW1();
		xpcW1.setXpcW1(123);
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 2);
		xpcW1.setXpcW1(origXpcW1);
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 1);

		C1G2XPCW2 xpcW2 = (C1G2XPCW2) reportEntities.get(0).getReport().getTagReportDataList()
				.get(0).getC1g2TagDataList().get(3);
		int origXpcW2 = xpcW2.getXpcW2();
		xpcW2.setXpcW2(123);
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 2);
		xpcW2.setXpcW2(origXpcW2);
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 1);

		// AccessSpecId
		AccessSpecId origAccessSpecId = reportEntities.get(0).getReport().getTagReportDataList()
				.get(0).getAccessSpecID();
		reportEntities.get(0).getReport().getTagReportDataList().get(0)
				.setAccessSpecID(new AccessSpecId(new TVParameterHeader(), 123));
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 2);
		reportEntities.get(0).getReport().getTagReportDataList().get(0)
				.setAccessSpecID(origAccessSpecId);
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 1);

		// FirstSeenTimestampUTC
		FirstSeenTimestampUTC origFirstSeenUtc = reportEntities.get(0).getReport()
				.getTagReportDataList().get(0).getFirstSTUTC();
		reportEntities.get(1).getReport().getTagReportDataList().get(0).setFirstSTUTC(
				new FirstSeenTimestampUTC(new TVParameterHeader(), new BigInteger("4")));
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 1);
		Assert.assertEquals(
				report.getTagReportDataList().get(0).getFirstSTUTC().getMicroseconds().longValue(),
				4);
		reportEntities.get(0).getReport().getTagReportDataList().get(0)
				.setFirstSTUTC(origFirstSeenUtc);

		// LastSeenTimestampUTC
		LastSeenTimestampUTC origLastSeenUtc = reportEntities.get(0).getReport()
				.getTagReportDataList().get(0).getLastSTUTC();
		reportEntities.get(1).getReport().getTagReportDataList().get(0).setLastSTUTC(
				new LastSeenTimestampUTC(new TVParameterHeader(), new BigInteger("400")));
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 1);
		Assert.assertEquals(
				report.getTagReportDataList().get(0).getLastSTUTC().getMicroseconds().longValue(),
				400);
		reportEntities.get(0).getReport().getTagReportDataList().get(0)
				.setLastSTUTC(origLastSeenUtc);

		// PeakRSSI
		report.getTagReportDataList().get(0).setTagSC(new TagSeenCount(new TVParameterHeader(), 1));

		PeakRSSI origPeakRssi = reportEntities.get(0).getReport().getTagReportDataList().get(0)
				.getPeakRSSI();
		reportEntities.get(1).getReport().getTagReportDataList().get(0)
				.setPeakRSSI(new PeakRSSI(new TVParameterHeader(), (byte) 100));
		report = creator.accumulate(ProtocolVersion.LLRP_V1_1, reportEntities);
		Assert.assertEquals(report.getTagReportDataList().size(), 1);
		Assert.assertEquals(report.getTagReportDataList().get(0).getPeakRSSI().getPeakRSSI(), 100);
		reportEntities.get(0).getReport().getTagReportDataList().get(0).setPeakRSSI(origPeakRssi);

		// TagSeenCount
		Assert.assertEquals(report.getTagReportDataList().get(0).getTagSC().getTagCount(), 2);
	}

	private ROAccessReportEntity createTagReportEntity(TagReportContentSelector contentSelector,
			boolean epcData, boolean utc) {
		ROAccessReport report = new ROAccessReport(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_1, 3 /* id */));
		List<TagReportData> tagReportDataList = new ArrayList<>();

		TagReportData tagReport;
		if (epcData) {
			BitSet epc = new BitSet();
			epc.set(0, 1);
			tagReport = new TagReportData(new TLVParameterHeader((byte) 0),
					new EPCData(new TLVParameterHeader((byte) 0), epc));
		} else {
			tagReport = new TagReportData(new TLVParameterHeader((byte) 0),
					new EPC96(new TVParameterHeader(), new byte[] { 3, 4 }));
		}
		List<Parameter> opSpecResultList = new ArrayList<>();
		opSpecResultList.add(new C1G2KillOpSpecResult(new TLVParameterHeader((byte) 0),
				C1G2KillOpSpecResultValues.SUCCESS, 20 /* opSpecId */));
		opSpecResultList.add(new C1G2LockOpSpecResult(new TLVParameterHeader((byte) 0),
				C1G2LockOpSpecResultValues.SUCCESS, 30 /* opSpecId */));
		opSpecResultList.add(new C1G2ReadOpSpecResult(new TLVParameterHeader((byte) 0),
				C1G2ReadOpSpecResultValues.NON_SPECIFIC_READER_ERROR, 40 /* opSpecId */,
				new byte[] { 7, 8 } /* readData */));
		opSpecResultList.add(new C1G2WriteOpSpecResult(new TLVParameterHeader((byte) 0),
				C1G2WriteOpSpecResultValues.INSUFFICIENT_POWER_TO_PERFORM_MEMORY_WRITE_OPERATION,
				50 /* opSpecId */, 7 /* numWordsWritten */));
		tagReport.setOpSpecResultList(opSpecResultList);
		long roSpecId = 4;
		if (contentSelector.isEnableROSpecID()) {
			tagReport.setRoSpecID(new ROSpecID(new TVParameterHeader(), roSpecId));
		}
		if (contentSelector.isEnableSpecIndex()) {
			tagReport.setSpecIndex(new SpecIndex(new TVParameterHeader(), 5));
		}
		if (contentSelector.isEnableInventoryParameterSpecID()) {
			tagReport.setInvParaSpecID(new InventoryParameterSpecID(new TVParameterHeader(), 6));
		}
		if (contentSelector.isEnableAntennaID()) {
			tagReport.setAntID(new AntennaId(new TVParameterHeader(), 7));
		}

		List<C1G2EPCMemorySelector> contentSelectorC1G2List = contentSelector
				.getC1g2EPCMemorySelectorList();
		if (contentSelectorC1G2List != null && !contentSelectorC1G2List.isEmpty()) {
			List<Parameter> c1g2TagDataList = new ArrayList<>();
			C1G2EPCMemorySelector contentSelectorC1G2 = contentSelectorC1G2List.get(0);
			if (contentSelectorC1G2.isEnableCRC()) {
				c1g2TagDataList.add(new C1G2CRC(new TVParameterHeader(), 8));
			}
			if (contentSelectorC1G2.isEnablePCBits()) {
				c1g2TagDataList.add(new C1G2PC(new TVParameterHeader(), 9));
			}
			if (contentSelectorC1G2.isEnableXPCBits()) {
				c1g2TagDataList.add(new C1G2XPCW1(new TVParameterHeader(), 10));
				c1g2TagDataList.add(new C1G2XPCW2(new TVParameterHeader(), 11));
			}
			tagReport.setC1g2TagDataList(c1g2TagDataList);
		}
		if (contentSelector.isEnableAccessSpecID()) {
			tagReport.setAccessSpecID(new AccessSpecId(new TVParameterHeader(), 13));
		}
		if (contentSelector.isEnableFirstSeenTimestamp()) {
			if (utc) {
				tagReport.setFirstSTUTC(
						new FirstSeenTimestampUTC(new TVParameterHeader(), new BigInteger("100")));
			} else {
				tagReport.setFirstSTUptime(new FirstSeenTimestampUptime(new TVParameterHeader(),
						new BigInteger("101")));
			}
		}
		if (contentSelector.isEnableLastSeenTimestamp()) {
			if (utc) {
				tagReport.setLastSTUTC(
						new LastSeenTimestampUTC(new TVParameterHeader(), new BigInteger("100")));
			} else {
				tagReport.setLastSTUptime(new LastSeenTimestampUptime(new TVParameterHeader(),
						new BigInteger("101")));
			}
		}
		if (contentSelector.isEnablePeakRSSI()) {
			tagReport.setPeakRSSI(new PeakRSSI(new TVParameterHeader(), (byte) 15));
		}
		if (contentSelector.isEnableChannelIndex()) {
			tagReport.setChannelInd(new ChannelIndex(new TVParameterHeader(), 16));
		}
		if (contentSelector.isEnableTagSeenCount()) {
			tagReport.setTagSC(new TagSeenCount(new TVParameterHeader(), 17));
		}
		tagReportDataList.add(tagReport);
		report.setTagReportDataList(tagReportDataList);
		ROAccessReportEntity entity = new ROAccessReportEntity();
		entity.setReport(report);
		entity.setRoSpecId(roSpecId);
		return entity;
	}
}
