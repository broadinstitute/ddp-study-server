package org.broadinstitute.dsm.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.service.elastic.ElasticSearchService;
import org.broadinstitute.dsm.service.participantdata.ATParticipantDataTestUtil;
import org.broadinstitute.dsm.service.participantdata.ParticipantDataService;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.MedicalRecordTestUtil;
import org.broadinstitute.dsm.util.ParticipantDataTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class ElasticSearchServiceTest extends DbAndElasticBaseTest {
    private static final String TEST_USER = "TEST_USER";
    private static final String instanceName = "esservice";
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private static final List<ParticipantDto> participants = new ArrayList<>();
    private static int participantCounter = 0;
    private static MedicalRecordTestUtil medicalRecordTestUtil;
    private static ATParticipantDataTestUtil atParticipantDataUtil;

    @BeforeClass
    public static void setup() throws Exception {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/atcpMappings.json",
                "elastic/atcpSettings.json");
        ddpInstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(instanceName, esIndex);
        medicalRecordTestUtil = new MedicalRecordTestUtil();
        atParticipantDataUtil = new ATParticipantDataTestUtil(ddpInstanceDto.getDdpInstanceId());
    }

    @AfterClass
    public static void tearDown() {
        DdpInstanceGroupTestUtil.deleteInstance(ddpInstanceDto);
        ElasticTestUtil.deleteIndex(esIndex);
    }

    @After
    public void deleteParticipantData() {
        medicalRecordTestUtil.tearDown();

        ParticipantDataDao participantDataDao = new ParticipantDataDao();
        List<ParticipantData> participantDataList =
                participantDataDao.getParticipantDataByInstanceId(ddpInstanceDto.getDdpInstanceId());
        participantDataList.forEach(participantData ->
                participantDataDao.delete(participantData.getParticipantDataId()));

        participants.forEach(participantDto ->
                TestParticipantUtil.deleteParticipant(participantDto.getParticipantId().orElseThrow()));
        participants.clear();
    }

    @Test
    public void testGetParticipantDocumentById() {
        // create a couple of participants with some DSM data
        ParticipantDto participant = createParticipant();
        String ddpParticipantId1 = participant.getRequiredDdpParticipantId();

        createParticipantData(ddpParticipantId1);
        createMedicalRecordAndOncHistory(participant);

        participant = createParticipant();
        String ddpParticipantId2 = participant.getRequiredDdpParticipantId();

        createParticipantData(ddpParticipantId2);

        ElasticSearchService elasticSearchService = new ElasticSearchService();

        Optional<ElasticSearchParticipantDto> esPtp =
                elasticSearchService.getParticipantDocumentById(ddpParticipantId1, esIndex);
        Assert.assertTrue(esPtp.isPresent());
        ElasticSearchParticipantDto esParticipant = esPtp.get();
        Assert.assertEquals(ddpParticipantId1, esParticipant.getProfile().get().getGuid());
        Assert.assertTrue(esParticipant.getDsm().isPresent());

        Assert.assertTrue(elasticSearchService.getParticipantDocumentById("bogus", esIndex).isEmpty());
    }

    @Test
    public void testGetAllDsmData() {
        // create a couple of participants with some DSM and activities data
        ParticipantDto participant = createParticipant();
        String ddpParticipantId1 = participant.getRequiredDdpParticipantId();

        createParticipantData(ddpParticipantId1);
        createMedicalRecordAndOncHistory(participant);

        participant = createParticipant();
        String ddpParticipantId2 = participant.getRequiredDdpParticipantId();

        createParticipantData(ddpParticipantId2);
        createMedicalRecordAndOncHistory(participant);

        // some non-DSM data
        List<Activities> activities = ParticipantDataTestUtil.getRgpActivities();
        ElasticTestUtil.addParticipantActivities(esIndex, activities, ddpParticipantId1);
        ElasticTestUtil.addParticipantActivities(esIndex, activities, ddpParticipantId2);

        ElasticSearchService elasticSearchService = new ElasticSearchService();
        Map<String, Map<String, Object>> esParticipants = elasticSearchService.getAllDsmData(esIndex);
        Assert.assertEquals(2, esParticipants.size());
        esParticipants.forEach((ddpParticipantId, dsmObjectMap) -> {
            Dsm dsm = elasticSearchService.deserializeDsmSourceMap(dsmObjectMap);
            List<OncHistoryDetail> esOncHistoryDetailList = dsm.getOncHistoryDetail();
            Assert.assertEquals(1, esOncHistoryDetailList.size());

            List<MedicalRecord> esMedicalRecords = dsm.getMedicalRecord();
            Assert.assertEquals(1, esMedicalRecords.size());
            MedicalRecord medicalRecord = esMedicalRecords.get(0);
            Assert.assertEquals(ddpParticipantId, medicalRecord.getDdpParticipantId());

            List<OncHistoryDetail> oncHistoryDetailList = dsm.getOncHistoryDetail();
            Assert.assertEquals(1, oncHistoryDetailList.size());
            OncHistoryDetail oncHistoryDetail = oncHistoryDetailList.get(0);
            Assert.assertEquals(medicalRecord.getMedicalRecordId(), oncHistoryDetail.getMedicalRecordId());
        });
    }

    @Test
    public void testGetParticipantDocumentAsString() {
        ParticipantDto participant = createParticipant();
        String ddpParticipantId = participant.getRequiredDdpParticipantId();

        Optional<String> ptpDoc = ElasticSearchService.getParticipantDocumentAsString(ddpParticipantId, esIndex);
        Assert.assertTrue(ptpDoc.isPresent());
        String ptpDocStr = ptpDoc.get();
        Assert.assertTrue(ptpDocStr.contains("\"profile\":{"));
        Assert.assertTrue(ptpDocStr.contains(ddpParticipantId));

        // bogus participant ID
        ElasticSearchService.getParticipantDocumentAsString(TestParticipantUtil.genDDPParticipantId("bogus"),
                esIndex).ifPresent(doc -> Assert.fail("Should not have found participant document"));
    }

    private ParticipantDto createParticipant() {
        String baseName = String.format("%s_%d", instanceName, participantCounter++);
        ParticipantDto participant =
                TestParticipantUtil.createParticipantWithEsProfile(baseName, ddpInstanceDto, esIndex);
        participants.add(participant);
        return participant;
    }

    private void createParticipantData(String ddpParticipantId) {
        // create random participant data
        atParticipantDataUtil.createMiscellaneousParticipantData(ddpParticipantId);
        atParticipantDataUtil.createEligibilityParticipantData(ddpParticipantId);

        ParticipantDataService.updateEsParticipantData(ddpParticipantId,
                DDPInstance.getDDPInstance(ddpInstanceDto.getInstanceName()));
    }

    private void createMedicalRecordAndOncHistory(ParticipantDto participantDto) {
        int medicalRecordId = medicalRecordTestUtil.createMedicalRecordBundle(participantDto, ddpInstanceDto);

        OncHistoryDetail.Builder builder = new OncHistoryDetail.Builder()
                .withDdpInstanceId(ddpInstanceDto.getDdpInstanceId())
                .withMedicalRecordId(medicalRecordId)
                .withFacility("Office")
                .withDestructionPolicy("12")
                .withChangedBy(TEST_USER);

        medicalRecordTestUtil.createOncHistoryDetail(participantDto, builder.build(), esIndex);
        medicalRecordTestUtil.createOrUpdateOncHistory(participantDto, TEST_USER, esIndex);
    }
}
