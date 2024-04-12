package org.broadinstitute.dsm.service.adminoperation;

import static org.broadinstitute.dsm.util.MedicalRecordUtil.NOT_SPECIFIED;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistory;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDao;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDto;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.onchistory.OncHistoryDto;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.migration.VerificationLog;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.service.elastic.ElasticSearchService;
import org.broadinstitute.dsm.service.participantdata.ATParticipantDataTestUtil;
import org.broadinstitute.dsm.service.participantdata.ParticipantDataService;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.CohortTagTestUtil;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.KitShippingTestUtil;
import org.broadinstitute.dsm.util.MedicalRecordTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class ElasticExportServiceTest extends DbAndElasticBaseTest {
    private static final ElasticSearchService elasticSearchService = new ElasticSearchService();
    private static final String TEST_USER = "TEST_USER";
    private static final String OS1_TAG = "OS1";
    private static final String instanceName = "elasticexport";
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private static final List<ParticipantDto> participants = new ArrayList<>();
    private static int participantCounter = 0;
    private static MedicalRecordTestUtil medicalRecordTestUtil;
    private static KitShippingTestUtil kitShippingTestUtil;
    private static ATParticipantDataTestUtil atParticipantDataUtil;
    private static CohortTagTestUtil cohortTagTestUtil;
    private static final Map<String, Map<String, Integer>> participantDataInfo = new HashMap<>();

    @BeforeClass
    public static void setup() throws Exception {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/osteo1Mappings.json",
                "elastic/osteo1Settings.json");
        ddpInstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(instanceName, esIndex);
        medicalRecordTestUtil = new MedicalRecordTestUtil();
        kitShippingTestUtil = new KitShippingTestUtil(TEST_USER, instanceName);
        atParticipantDataUtil = new ATParticipantDataTestUtil(ddpInstanceDto.getDdpInstanceId());
        cohortTagTestUtil = new CohortTagTestUtil();
    }

    @AfterClass
    public static void tearDown() {
        DdpInstanceGroupTestUtil.deleteInstance(ddpInstanceDto);
        ElasticTestUtil.deleteIndex(esIndex);
    }

    @After
    public void deleteParticipantData() {
        cohortTagTestUtil.tearDown();
        kitShippingTestUtil.tearDown();
        medicalRecordTestUtil.tearDown();

        ParticipantDataDao participantDataDao = new ParticipantDataDao();
        List<ParticipantData> participantDataList =
                participantDataDao.getParticipantDataByInstanceId(ddpInstanceDto.getDdpInstanceId());
        participantDataList.forEach(participantData ->
                participantDataDao.delete(participantData.getParticipantDataId()));

        participants.forEach(participantDto ->
                TestParticipantUtil.deleteParticipant(participantDto.getRequiredParticipantId()));
        participants.clear();
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
        setMinimalEsDsm(ddpParticipantId);

        List<ExportLog> exportLogs = new ArrayList<>();
        ElasticExportService.exportParticipants(List.of(ddpParticipantId), ddpInstanceDto.getInstanceName(),
                exportLogs);

        try {
            // TODO: need to wait for the export to complete. Fussing with the refresh policy did not resolve this.
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            Assert.fail("Interrupted while waiting for ES refresh");
        }

        // verify the data was exported properly
        verifyParticipant(ddpParticipantId);
        verifyMedicalRecordAndOncHistory(participant);
        verifyKitShipping(ddpParticipantId);

        // special case: oncHistory created date is null
        medicalRecordTestUtil.updateOncHistory(participant.getRequiredParticipantId(), null, TEST_USER);

        exportLogs.clear();
        ElasticExportService.exportParticipants(List.of(ddpParticipantId), ddpInstanceDto.getInstanceName(),
                exportLogs);
        try {
            // TODO: need to wait for the export to complete. Fussing with the refresh policy did not resolve this.
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            log.error("Interrupted while waiting for ES refresh", e);
        }

        // verify the data was exported properly
        verifyParticipant(ddpParticipantId);
        verifyMedicalRecordAndOncHistory(participant);
        verifyKitShipping(ddpParticipantId);
    }

    @Test
    public void testExportNotSpecifiedMedicalRecord() {
        ParticipantDto participant = createParticipant();
        String ddpParticipantId = participant.getRequiredDdpParticipantId();

        // create a standard medical record with trimmings
        createMedicalRecordAndOncHistory(participant);
        // create a medical record with institution type NOT_SPECIFIED
        medicalRecordTestUtil.createMedicalRecord(participant, ddpInstanceDto);

        // verify the data is actually in ES
        verifyMedicalRecordNotSpecified(participant);

        // replace ES dsm with minimal data so we can verify the export
        setMinimalEsDsm(ddpParticipantId);

        List<ExportLog> exportLogs = new ArrayList<>();
        ElasticExportService.exportParticipants(List.of(ddpParticipantId), ddpInstanceDto.getInstanceName(),
                exportLogs);

        try {
            // TODO: need to wait for the export to complete. Fussing with the refresh policy did not resolve this.
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            Assert.fail("Interrupted while waiting for ES refresh");
        }

        verifyMedicalRecordNotSpecified(participant);
        verifyExportLogs(exportLogs);
    }

    @Test
    public void testVerifyElasticData() {
        ParticipantDto participant = createParticipant();
        String ddpParticipantId = participant.getRequiredDdpParticipantId();

        // give this ptp a bunch of data
        createParticipantData(ddpParticipantId);
        createMedicalRecordAndOncHistory(participant);
        createMedicalRecordAndOncHistory(participant);
        kitShippingTestUtil.createTestKitShipping(participant, ddpInstanceDto);

        List<VerificationLog> verificationLogs = ElasticExportService.verifyElasticData(List.of(ddpParticipantId),
                DDPInstance.getDDPInstance(instanceName), false);
        log.debug("Verification logs: (not verifyFields) {} ", verificationLogs);
        Assert.assertTrue(verificationLogs.stream()
                .filter(log -> !log.getStatus().equals(VerificationLog.VerificationStatus.VERIFIED))
                .findAny().isEmpty());

        // add DB data that is not in ES
        cohortTagTestUtil.createTag(OS1_TAG, ddpParticipantId, ddpInstanceDto.getDdpInstanceId());

        // remove ES data that is in DB
        ElasticTestUtil.removeDsmField(esIndex, ddpParticipantId, ESObjectConstants.PARTICIPANT);

        // add ES data that is not in DB
        OncHistoryDetail rec = new OncHistoryDetail.Builder()
                .withDdpInstanceId(ddpInstanceDto.getDdpInstanceId())
                .withMedicalRecordId(1)
                .withFacility("Office")
                .withDestructionPolicy("12")
                .withChangedBy(TEST_USER)
                .build();
        ElasticTestUtil.createOncHistoryDetail(esIndex, rec, participant.getRequiredDdpParticipantId());

        verificationLogs = ElasticExportService.verifyElasticData(List.of(ddpParticipantId),
                DDPInstance.getDDPInstance(instanceName), false);
        log.debug("Verification logs (not verifyFields): {}", verificationLogs);

        verificationLogs.forEach(log -> {
            VerificationLog.VerificationStatus status = log.getStatus();
            String entity = log.getEntity();
            if (entity.equals(ESObjectConstants.PARTICIPANT)) {
                Assert.assertEquals(VerificationLog.VerificationStatus.ES_ENTITY_MISMATCH, status);
                Assert.assertEquals(1, log.getDbOnlyEntityIds().size());
                Assert.assertTrue(log.getEsOnlyEntityIds().isEmpty());
            } else if (entity.equals(ESObjectConstants.COHORT_TAG)) {
                Assert.assertEquals(VerificationLog.VerificationStatus.ES_ENTITY_MISMATCH, status);
                Assert.assertEquals(1, log.getDbOnlyEntityIds().size());
                Assert.assertTrue(log.getEsOnlyEntityIds().isEmpty());
            } else if (entity.equals(ESObjectConstants.ONC_HISTORY_DETAIL)) {
                Assert.assertEquals(VerificationLog.VerificationStatus.ES_ENTITY_MISMATCH, status);
                Assert.assertEquals(1, log.getEsOnlyEntityIds().size());
                Assert.assertTrue(log.getDbOnlyEntityIds().isEmpty());
            } else {
                Assert.assertEquals(VerificationLog.VerificationStatus.VERIFIED, status);
            }
        });
    }

    @Test
    public void testVerifyElasticDataNoESDocument() {
        ParticipantDto participant = createParticipant();
        String ddpParticipantId = participant.getRequiredDdpParticipantId();

        createParticipantData(ddpParticipantId);
        createMedicalRecordAndOncHistory(participant);

        // DB has data but no ES document
        ElasticSearchService.deleteParticipantDocument(ddpParticipantId, ddpInstanceDto.getEsParticipantIndex());

        List<VerificationLog> verificationLogs = ElasticExportService.verifyElasticData(List.of(ddpParticipantId),
                DDPInstance.getDDPInstance(instanceName), false);
        log.debug("Verification logs: (not verifyFields) {} ", verificationLogs);
        verificationLogs.forEach(vLog ->
                Assert.assertEquals(VerificationLog.VerificationStatus.NO_ES_DOCUMENT, vLog.getStatus()));
    }

    @Test
    public void testVerifyElasticDataVerifyFields() {
        ParticipantDto participant = createParticipant();
        String ddpParticipantId = participant.getRequiredDdpParticipantId();

        // give this ptp a bunch of data
        createParticipantData(ddpParticipantId);
        createMedicalRecordAndOncHistory(participant);
        createMedicalRecordAndOncHistory(participant);
        kitShippingTestUtil.createTestKitShipping(participant, ddpInstanceDto);

        List<VerificationLog> verificationLogs = ElasticExportService.verifyElasticData(List.of(ddpParticipantId),
                DDPInstance.getDDPInstance(instanceName), true);
        log.debug("Verification logs (verifyFields): {}", verificationLogs);
        Assert.assertEquals(9, verificationLogs.size());
        // TODO: verifyFields mode is not ready for prime tie without a bit more work, so asserting characteristics of the
        // output is not very useful at this time. Follow-up work: PEPPER-1369
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

        Map<String, Integer> ptpDataInfo = new HashMap<>();
        ptpDataInfo.put("AT_GROUP_MISCELLANEOUS", 1);
        ptpDataInfo.put("AT_GROUP_ELIGIBILITY", 1);
        participantDataInfo.put(ddpParticipantId, ptpDataInfo);

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

    private void setMinimalEsDsm(String ddpParticipantId) {
        Dsm dsm = new Dsm();
        Participant participant = new Participant();
        participant.setDdpParticipantId(ddpParticipantId);
        dsm.setParticipant(participant);
        ElasticTestUtil.addParticipantDsm(esIndex, dsm, ddpParticipantId);
    }

    private void verifyMedicalRecordAndOncHistory(ParticipantDto participant) {
        String ddpParticipantId = participant.getRequiredDdpParticipantId();
        ElasticSearchParticipantDto esParticipant =
                elasticSearchService.getRequiredParticipantDocument(ddpParticipantId, esIndex);
        log.debug("Verifying ES participant record for {}: {}", ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));
        Dsm dsm = esParticipant.getDsm().orElseThrow();

        List<OncHistoryDetail> esOncHistoryDetailList = dsm.getOncHistoryDetail();
        Assert.assertEquals(1, esOncHistoryDetailList.size());

        List<MedicalRecord> esMedicalRecords = dsm.getMedicalRecord();
        Assert.assertEquals(1, esMedicalRecords.size());

        Optional<OncHistory> oh = dsm.getOncHistory();
        Assert.assertTrue(oh.isPresent());
        OncHistory esOncHistory = oh.get();

        int participantId = participant.getRequiredParticipantId();
        List<MedicalRecord> medicalRecords = MedicalRecord.getMedicalRecordsForParticipant(participantId);
        Assert.assertEquals(1, medicalRecords.size());

        int medicalRecordId = medicalRecords.get(0).getMedicalRecordId();
        Assert.assertEquals(medicalRecordId, esMedicalRecords.get(0).getMedicalRecordId());

        List<OncHistoryDetailDto> oncHistoryDetailList =
                OncHistoryDetail.getOncHistoryDetailByMedicalRecord(medicalRecordId);
        Assert.assertEquals(1, oncHistoryDetailList.size());

        int oncHistoryDetailId =
                (Integer) oncHistoryDetailList.get(0).getColumnValues().get(DBConstants.ONC_HISTORY_DETAIL_ID);
        int oncHistoryMedicalRecordId =
                (Integer) oncHistoryDetailList.get(0).getColumnValues().get(DBConstants.MEDICAL_RECORD_ID);
        Assert.assertEquals(oncHistoryDetailId, esOncHistoryDetailList.get(0).getOncHistoryDetailId());
        Assert.assertEquals(oncHistoryMedicalRecordId, esOncHistoryDetailList.get(0).getMedicalRecordId());

        Optional<OncHistoryDto> oncHistory = OncHistoryDao.getByParticipantId(participantId);
        Assert.assertTrue(oncHistory.isPresent());
        Assert.assertEquals(oncHistory.get().getCreated(), esOncHistory.getCreated());
        Assert.assertEquals(oncHistory.get().getReviewed(), esOncHistory.getReviewed());
    }

    private void verifyMedicalRecordNotSpecified(ParticipantDto participant) {
        String ddpParticipantId = participant.getRequiredDdpParticipantId();
        ElasticSearchParticipantDto esParticipant =
                elasticSearchService.getRequiredParticipantDocument(ddpParticipantId, esIndex);
        log.debug("Verifying ES participant record for {}: {}", ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));
        Dsm dsm = esParticipant.getDsm().orElseThrow();

        List<OncHistoryDetail> esOncHistoryDetailList = dsm.getOncHistoryDetail();
        Assert.assertEquals(1, esOncHistoryDetailList.size());
        OncHistoryDetail esOncHistoryDetail = esOncHistoryDetailList.get(0);

        // the medical record with institution type NOT_SPECIFIED for onc history is not exported
        List<MedicalRecord> esMedicalRecords = dsm.getMedicalRecord();
        Assert.assertEquals(1, esMedicalRecords.size());

        int participantId = participant.getRequiredParticipantId();
        List<MedicalRecord> medicalRecords = MedicalRecord.getMedicalRecordsForParticipant(participantId);
        Assert.assertEquals(2, medicalRecords.size());

        List<MedicalRecord> specifiedRecs = medicalRecords.stream().filter(medicalRecord ->
                !medicalRecord.getType().equals(NOT_SPECIFIED)).toList();
        Assert.assertEquals(1, specifiedRecs.size());
        int specifiedRecId = specifiedRecs.get(0).getMedicalRecordId();
        Assert.assertEquals(esOncHistoryDetail.getMedicalRecordId(), specifiedRecId);
    }

    private void verifyKitShipping(String ddpParticipantId) {
        ElasticSearchParticipantDto esParticipant =
                elasticSearchService.getRequiredParticipantDocument(ddpParticipantId, esIndex);
        log.debug("Verifying ES participant record for {}: {}", ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));
        Dsm dsm = esParticipant.getDsm().orElseThrow();
        List<KitRequestShipping> kitRequests = dsm.getKitRequestShipping();
        log.debug("Found {} kit requests for ptp {}", kitRequests.size(), ddpParticipantId);
        kitShippingTestUtil.getParticipantKitRequestIds(ddpParticipantId).forEach(kitRequestId ->
                Assert.assertTrue(kitRequests.stream().anyMatch(kitRequest ->
                    kitRequest.getDsmKitId().intValue() == kitRequestId)));
    }

    private void verifyParticipant(String ddpParticipantId) {
        ElasticSearchParticipantDto esParticipant =
                elasticSearchService.getRequiredParticipantDocument(ddpParticipantId, esIndex);
        log.debug("Verifying ES participant record for {}: {}", ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));
        Dsm dsm = esParticipant.getDsm().orElseThrow();
        List<ParticipantData> esParticipantData = dsm.getParticipantData();

        Map<String, Integer> ptpDataInfo = participantDataInfo.get(ddpParticipantId);
        Assert.assertEquals(ptpDataInfo.size(), esParticipantData.size());

        esParticipantData.forEach(ptpData -> {
            Integer dataMapSize = ptpDataInfo.get(ptpData.getRequiredFieldTypeId());
            Assert.assertNotNull(dataMapSize);
            Assert.assertEquals(dataMapSize.intValue(), ptpData.getDataMap().size());
        });

        Participant participant = dsm.getParticipant().orElseThrow();
        Assert.assertEquals(ddpParticipantId, participant.getDdpParticipantId());
    }

    private void verifyExportLogs(List<ExportLog> exportLogs) {
        Assert.assertFalse(exportLogs.stream().anyMatch(log -> {
            ExportLog.Status status = log.getStatus();
            return status.equals(ExportLog.Status.FAILURES) || status.equals(ExportLog.Status.ERROR);
        }));
    }
}
