package org.broadinstitute.dsm.service.adminoperation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.pubsub.WorkflowStatusUpdate;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.FieldSettingsTestUtil;
import org.broadinstitute.dsm.util.KitShippingTestUtil;
import org.broadinstitute.dsm.util.MedicalRecordTestUtil;
import org.broadinstitute.dsm.util.ParticipantDataTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class ElasticExportServiceTest extends DbAndElasticBaseTest {
    private static final String TEST_USER = "TEST_USER";
    private static final String instanceName = "elasticexport";
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private static final List<ParticipantDto> participants = new ArrayList<>();
    private static int participantCounter = 0;
    private static MedicalRecordTestUtil medicalRecordTestUtil;
    private static KitShippingTestUtil kitShippingTestUtil;
    private static final Map<String, Map<String, Integer>> participantDataInfo = new HashMap<>();

    private static final List<Integer> fieldSettingsIds = new ArrayList<>();

    @BeforeClass
    public static void setup() throws Exception {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/atcpMappings.json",
                "elastic/atcpSettings.json");
        ddpInstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(instanceName, esIndex);
        medicalRecordTestUtil = new MedicalRecordTestUtil();
        kitShippingTestUtil = new KitShippingTestUtil(TEST_USER, instanceName);
    }

    @AfterClass
    public static void tearDown() {
        DdpInstanceGroupTestUtil.deleteInstance(ddpInstanceDto);
        ElasticTestUtil.deleteIndex(esIndex);
    }

    @After
    public void deleteParticipantData() {
        kitShippingTestUtil.tearDown();
        medicalRecordTestUtil.tearDown();

        ParticipantDataDao participantDataDao = new ParticipantDataDao();
        List<ParticipantData> participantDataList =
                participantDataDao.getParticipantDataByInstanceId(ddpInstanceDto.getDdpInstanceId());
        participantDataList.forEach(participantData ->
                participantDataDao.delete(participantData.getParticipantDataId()));

        participants.forEach(participantDto ->
                TestParticipantUtil.deleteParticipant(participantDto.getParticipantIdOrThrow()));
        participants.clear();
        //!! TEMP: do we have any?
        fieldSettingsIds.forEach(FieldSettingsTestUtil::deleteFieldSettings);
        fieldSettingsIds.clear();
    }

    @Test
    public void testExportParticipants() {
        ParticipantDto participant = createParticipant();
        String ddpParticipantId = participant.getRequiredDdpParticipantId();

        // give this ptp a bunch of data
        createParticipantData(ddpParticipantId);
        createMedicalRecordAndOncHistory(participant);
        kitShippingTestUtil.createTestKitShipping(participant, ddpInstanceDto);

        // verify the data is actually in ES
        verifyParticipant(ddpParticipantId);
        verifyMedicalRecordAndOncHistory(participant);
        verifyKitShipping(ddpParticipantId);

        // replace ES dsm with minimal data so we can verify the export
        updateEsDsm(ddpParticipantId);

        List<ExportLog> exportLogs = new ArrayList<>();
        ElasticExportService.exportParticipants(List.of(ddpParticipantId), ddpInstanceDto.getInstanceName(),
                exportLogs);

        // verify the data was exported properly
        verifyParticipant(ddpParticipantId);
        verifyMedicalRecordAndOncHistory(participant);
        verifyKitShipping(ddpParticipantId);
    }

    private ParticipantDto createParticipant() {
        String baseName = String.format("%s_%d", instanceName, participantCounter++);
        ParticipantDto participant =
                TestParticipantUtil.createParticipantWithEsProfile(baseName, ddpInstanceDto, esIndex);
        participants.add(participant);
        return participant;
    }

    private void createParticipantData(String ddpParticipantId) {
        int instanceId = ddpInstanceDto.getDdpInstanceId();

        // create random participant data
        Map<String, String> dataMap = new HashMap<>();
        dataMap.put("REGISTRATION_TYPE", "Self");
        ParticipantDataTestUtil.createParticipantData(ddpParticipantId,
                dataMap, "AT_GROUP_MISCELLANEOUS", instanceId, TEST_USER);

        Map<String, Integer> ptpDataInfo = new HashMap<>();
        ptpDataInfo.put("AT_GROUP_MISCELLANEOUS", 1);

        dataMap.clear();
        dataMap.put("ELIGIBILITY", "1");
        ParticipantDataTestUtil.createParticipantData(ddpParticipantId,
                dataMap, "AT_GROUP_ELIGIBILITY", instanceId, TEST_USER);

        ptpDataInfo.put("AT_GROUP_ELIGIBILITY", 1);
        participantDataInfo.put(ddpParticipantId, ptpDataInfo);

        WorkflowStatusUpdate.updateEsParticipantData(ddpParticipantId,
                DDPInstance.getDDPInstance(ddpInstanceDto.getInstanceName()));
    }

    private void createMedicalRecordAndOncHistory(ParticipantDto participantDto) {
        int medicalRecordId = medicalRecordTestUtil.createMedicalRecord(participantDto, ddpInstanceDto);

        OncHistoryDetail.Builder builder = new OncHistoryDetail.Builder()
                .withDdpInstanceId(ddpInstanceDto.getDdpInstanceId())
                .withMedicalRecordId(medicalRecordId)
                .withFacility("Office")
                .withDestructionPolicy("12")
                .withChangedBy(TEST_USER);

        medicalRecordTestUtil.createOncHistoryDetail(participantDto, builder.build(), esIndex);
        medicalRecordTestUtil.createOncHistory(participantDto, TEST_USER, esIndex);
    }

    private void updateEsDsm(String ddpParticipantId) {
        Dsm dsm = new Dsm();
        Participant participant = new Participant();
        participant.setDdpParticipantId(ddpParticipantId);
        dsm.setParticipant(participant);
        ElasticTestUtil.addParticipantDsm(esIndex, dsm, ddpParticipantId);
    }

    private void verifyMedicalRecordAndOncHistory(ParticipantDto participant) {
        String ddpParticipantId = participant.getRequiredDdpParticipantId();
        ElasticSearchParticipantDto esParticipant =
                ElasticSearchUtil.getParticipantESDataByParticipantId(esIndex, ddpParticipantId);
        log.debug("Verifying ES participant record for {}: {}", ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));
        Dsm dsm = esParticipant.getDsm().orElseThrow();

        List<OncHistoryDetail> oncHistoryDetailList = dsm.getOncHistoryDetail();
        Assert.assertEquals(1, oncHistoryDetailList.size());

        List<MedicalRecord> medicalRecords = dsm.getMedicalRecord();
        Assert.assertEquals(1, medicalRecords.size());

        int participantId = participant.getRequiredParticipantId();
        int medicalRecordId = medicalRecordTestUtil.getParticipantMedicalIds(participantId).get(0);

        OncHistoryDetail oncHistoryDetail = oncHistoryDetailList.stream()
                .filter(rec -> rec.getMedicalRecordId() == medicalRecordId)
                .findFirst().orElseThrow();
        Assert.assertEquals(oncHistoryDetail.getOncHistoryDetailId(),
                medicalRecordTestUtil.getParticipantOncHistoryDetailIds(participantId).get(0).intValue());
        Assert.assertTrue(medicalRecords.stream()
                .anyMatch(rec -> rec.getMedicalRecordId() == medicalRecordId));
    }

    private void verifyKitShipping(String ddpParticipantId) {
        ElasticSearchParticipantDto esParticipant =
                ElasticSearchUtil.getParticipantESDataByParticipantId(esIndex, ddpParticipantId);
        log.debug("Verifying ES participant record for {}: {}", ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));
        Dsm dsm = esParticipant.getDsm().orElseThrow();
        List<KitRequestShipping> kitRequests = dsm.getKitRequestShipping();
        log.debug("Found {} kit requests for ptp {}", kitRequests.size(), ddpParticipantId);
        kitShippingTestUtil.getParticipantKitRequestIds(ddpParticipantId).forEach(kitRequestId -> {
            Assert.assertTrue(kitRequests.stream()
                    .anyMatch(kitRequest -> kitRequest.getDsmKitId().intValue() == kitRequestId));
        });
    }

    private void verifyParticipant(String ddpParticipantId) {
        ElasticSearchParticipantDto esParticipant =
                ElasticSearchUtil.getParticipantESDataByParticipantId(esIndex, ddpParticipantId);
        log.debug("Verifying ES participant record for {}: {}", ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));
        Dsm dsm = esParticipant.getDsm().orElseThrow();
        List<ParticipantData> participantData = dsm.getParticipantData();

        Map<String, Integer> ptpDataInfo = participantDataInfo.get(ddpParticipantId);
        Assert.assertEquals(ptpDataInfo.size(), participantData.size());

        participantData.forEach(ptpData -> {
            Integer dataMapSize = ptpDataInfo.get(ptpData.getRequiredFieldTypeId());
            Assert.assertNotNull(dataMapSize);
            Assert.assertEquals(dataMapSize.intValue(), ptpData.getDataMap().size());
        });

        Participant participant = dsm.getParticipant().orElseThrow();
        Assert.assertEquals(ddpParticipantId, participant.getDdpParticipantId());
    }
}
