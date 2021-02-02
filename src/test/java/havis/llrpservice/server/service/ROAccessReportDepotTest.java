package havis.llrpservice.server.service;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import havis.llrpservice.common.entityManager.Entity;
import havis.llrpservice.common.io.PathHandler;
import havis.llrpservice.common.io.XMLFile;
import havis.llrpservice.data.message.MessageHeader;
import havis.llrpservice.data.message.ProtocolVersion;
import havis.llrpservice.data.message.ROAccessReport;
import havis.llrpservice.data.message.parameter.AccessSpecId;
import havis.llrpservice.data.message.parameter.AntennaId;
import havis.llrpservice.data.message.parameter.ChannelIndex;
import havis.llrpservice.data.message.parameter.Custom;
import havis.llrpservice.data.message.parameter.EPC96;
import havis.llrpservice.data.message.parameter.FirstSeenTimestampUTC;
import havis.llrpservice.data.message.parameter.FirstSeenTimestampUptime;
import havis.llrpservice.data.message.parameter.FrequencyRSSILevelEntry;
import havis.llrpservice.data.message.parameter.InventoryParameterSpecID;
import havis.llrpservice.data.message.parameter.LastSeenTimestampUTC;
import havis.llrpservice.data.message.parameter.LastSeenTimestampUptime;
import havis.llrpservice.data.message.parameter.Parameter;
import havis.llrpservice.data.message.parameter.PeakRSSI;
import havis.llrpservice.data.message.parameter.RFSurveyReportData;
import havis.llrpservice.data.message.parameter.ROSpecID;
import havis.llrpservice.data.message.parameter.SpecIndex;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.data.message.parameter.TVParameterHeader;
import havis.llrpservice.data.message.parameter.TagReportData;
import havis.llrpservice.data.message.parameter.TagSeenCount;
import havis.llrpservice.data.message.parameter.UTCTimestamp;
import havis.llrpservice.server.configuration.ServerConfiguration;
import havis.llrpservice.server.configuration.ServerInstanceConfiguration;
import havis.llrpservice.server.persistence.ObservablePersistence;
import havis.llrpservice.server.persistence._FileHelperTest;
import havis.llrpservice.server.service.ROAccessReportDepot.ROAccessReportDepotListener;
import havis.llrpservice.server.service.data.ROAccessReportEntity;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;
import mockit.Mocked;
import mockit.Verifications;

public class ROAccessReportDepotTest {

	private static final Path SERVER_INIT_PATH = new PathHandler()
			.toAbsolutePath("havis/llrpservice/server/service/LLRPServerConfiguration.xml");
	private static final Path INIT_PATH = new PathHandler()
			.toAbsolutePath("havis/llrpservice/server/service/LLRPServerInstanceConfiguration.xml");

	private static final Path BASE_PATH = new PathHandler()
			.toAbsolutePath("havis/llrpservice/server/service");
	private static final Path BASE_OUTPUT_PATH = BASE_PATH.resolve("../../../../../output");
	private static final Path SERVER_LATEST_PATH = BASE_OUTPUT_PATH.resolve("config/newConfig.xml");
	private static final Path LATEST_PATH = BASE_OUTPUT_PATH
			.resolve("config/newInstanceConfig.xml");

	@AfterClass
	public static void cleanUp() {
		// Remove output directory
		try {
			_FileHelperTest.deleteFiles(BASE_OUTPUT_PATH.toString());
			BASE_OUTPUT_PATH.toFile().delete();
		} catch (Exception e) {
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void handling(@Mocked final ROAccessReportDepotListener listener) throws Throwable {
		// Create ServerConfigFile
		XMLFile<LLRPServerConfigurationType> serverConfigFile = new XMLFile<>(
				LLRPServerConfigurationType.class, SERVER_INIT_PATH, SERVER_LATEST_PATH);

		// Create ServerConfiguration
		ServerConfiguration serverConf = new ServerConfiguration(serverConfigFile);
		serverConf.open();

		// Create ServerInstanceConfiguration file
		XMLFile<LLRPServerInstanceConfigurationType> instanceConfigFile = new XMLFile<>(
				LLRPServerInstanceConfigurationType.class, INIT_PATH, LATEST_PATH);

		// create ServerInstanceConfiguration
		ServerInstanceConfiguration instanceConf = new ServerInstanceConfiguration(serverConf,
				instanceConfigFile);
		instanceConf.open();

		// Get persistence of depot
		ObservablePersistence persistence = instanceConf.getPersistence();

		// New depot
		ROAccessReportDepot depot = new ROAccessReportDepot();

		// Open the depot
		depot.open(persistence);

		// Create a ROAccessReport
		long roSpecId = 555;
		ROAccessReport report = new ROAccessReport(
				new MessageHeader((byte) 0x00, ProtocolVersion.LLRP_V1_0_1, 0));

		TLVParameterHeader tlvHeader = new TLVParameterHeader((byte) 0x00);
		TVParameterHeader tvHeader = new TVParameterHeader();

		Custom custom = new Custom(tlvHeader, 1, 11, new byte[] { 0x01, 0x02 });
		List<Custom> customs = new ArrayList<Custom>(Arrays.asList(custom));

		// Custom (Filled all fields)
		report.setCusList(customs);

		// rfSurveyReportData (Filled all fields)
		UTCTimestamp utc = new UTCTimestamp(tlvHeader, new BigInteger("111111113"));
		FrequencyRSSILevelEntry fle = new FrequencyRSSILevelEntry(new TLVParameterHeader((byte) 0),
				22, 33, (byte) -5, (byte) 20, utc);
		RFSurveyReportData rfSurveyReportData = new RFSurveyReportData(tlvHeader,
				new ArrayList<FrequencyRSSILevelEntry>(Arrays.asList(fle)));
		rfSurveyReportData.setCusList(customs);
		rfSurveyReportData.setRoSpecID(new ROSpecID(tvHeader, 44));
		rfSurveyReportData.setSpecIndex(new SpecIndex(tvHeader, 55));
		report.setRfSurveyReportDataList(Arrays.asList(rfSurveyReportData));

		// tagReport (Filled all fields)
		TagReportData tagReportData = new TagReportData(tlvHeader,
				new EPC96(tvHeader, new byte[] { (byte) 0xAF, (byte) 0xFE }));
		tagReportData.setAccessSpecID(new AccessSpecId(tvHeader, 66));
		tagReportData.setAntID(new AntennaId(tvHeader, 77));
		List<Parameter> c1g2Paras = new ArrayList<Parameter>();
		c1g2Paras.add(new EPC96(tvHeader, new byte[] { (byte) 0xAF, (byte) 0xFE }));
		c1g2Paras.add(new AntennaId(tvHeader, 199));
		tagReportData.setC1g2TagDataList(c1g2Paras);
		tagReportData.setChannelInd(new ChannelIndex(tvHeader, 88));
		tagReportData.setCusList(customs);
		tagReportData
				.setFirstSTUptime(new FirstSeenTimestampUptime(tvHeader, new BigInteger("99")));
		tagReportData.setFirstSTUTC(new FirstSeenTimestampUTC(tvHeader, new BigInteger("111")));
		tagReportData.setInvParaSpecID(new InventoryParameterSpecID(tvHeader, 222));
		tagReportData.setLastSTUptime(new LastSeenTimestampUptime(tvHeader, new BigInteger("333")));
		tagReportData.setLastSTUTC(new LastSeenTimestampUTC(tvHeader, new BigInteger("444")));
		tagReportData.setOpSpecResultList(c1g2Paras);
		tagReportData.setPeakRSSI(new PeakRSSI(tvHeader, (byte) 0x12));
		tagReportData.setRoSpecID(new ROSpecID(tvHeader, roSpecId));
		tagReportData.setSpecIndex(new SpecIndex(tvHeader, 666));
		tagReportData.setTagSC(new TagSeenCount(tvHeader, 777));
		report.setTagReportDataList(Arrays.asList(tagReportData));

		// Add Report to depot
		ROAccessReportEntity reportEntity = new ROAccessReportEntity();
		reportEntity.setRoSpecId(roSpecId);
		reportEntity.setReport(report);
		List<String> reportIds = depot.add(Arrays.asList(reportEntity));

		// Write report to storage
		depot.flush();

		assertEquals(persistence.getGroups(ROAccessReportEntity.class).size(), 1);

		// Re-instantiate ServerInstanceConfiguration to re-instantiate the
		// persistence (no object should handled there)
		instanceConf = new ServerInstanceConfiguration(serverConf, instanceConfigFile);
		instanceConf.open();
		persistence = instanceConf.getPersistence();

		depot.close();

		depot = new ROAccessReportDepot();
		depot.open(persistence);

		// Sleep for over 1 second to flush in a new group
		Thread.sleep(1100);
		depot.flush();

		assertEquals(persistence.getGroups(ROAccessReportEntity.class).size(), 2);

		// Same procedure as above
		instanceConf = new ServerInstanceConfiguration(serverConf, instanceConfigFile);
		instanceConf.open();
		persistence = instanceConf.getPersistence();

		// Re-Initialize the depot
		depot.close();

		depot = new ROAccessReportDepot();
		depot.open(persistence);

		// Sleep for over 1 second to flush in a new group
		Thread.sleep(1100);
		depot.flush();

		assertEquals(persistence.getGroups(ROAccessReportEntity.class).size(), 3);

		List<Entity<Object>> reportEntities = depot.acquire(reportIds);
		// Serialized object must be equal to object to be handled by depot
		ROAccessReportEntity current = (ROAccessReportEntity) reportEntities.get(0).getObject();
		assertEquals(current.getReport().toString(), report.toString());

		// reportsIds should be equal to the reportIds in the depot
		assertEquals(reportIds, depot.getEntityIds());

		// Remove all reports from depot
		depot.remove(reportIds);

		// reportsIds should not be equal to the reportIds in the depot
		assertNotEquals(reportIds, depot.getEntityIds());

		// Add a listener
		depot.addListener(listener);

		// Add the report again (listener add should be called)
		reportIds = depot.add(Arrays.asList(reportEntity));

		reportEntities = depot.acquire(reportIds);
		// Release writing (listener updated should be called)
		depot.release(reportEntities, /* writing */true);

		reportEntities = depot.acquire(reportIds);
		// Release reading (listener updated should not be called)
		depot.release(reportEntities, /* writing */false);

		// Add the report again (listener remove should be called)
		depot.remove(reportIds);

		// Remove Listener from depot
		depot.removeListener(listener);

		// Add the report again (listener add should not be called)
		reportIds = depot.add(Arrays.asList(reportEntity));

		depot.close();

		new Verifications() {
			{
				listener.added(withInstanceOf(ROAccessReportDepot.class),
						withInstanceOf(ArrayList.class));
				times = 1;
				listener.removed(withInstanceOf(ROAccessReportDepot.class),
						withInstanceOf(ArrayList.class));
				times = 1;
				listener.updated(withInstanceOf(ROAccessReportDepot.class),
						withInstanceOf(ArrayList.class));
				times = 1;
			}
		};

	}
}
