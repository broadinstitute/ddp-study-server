package org.broadinstitute.dsm.service.adminoperation;

import static org.broadinstitute.dsm.service.participantdata.ATParticipantDataService.AT_GROUP_GENOME_STUDY;
import static org.broadinstitute.dsm.service.participantdata.ATParticipantDataService.AT_PARTICIPANT_EXIT;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.service.elastic.ElasticSearchService;
import org.broadinstitute.dsm.service.participantdata.ATParticipantDataService;
import org.broadinstitute.dsm.service.participantdata.ATParticipantDataTestUtil;
import org.broadinstitute.dsm.service.participantdata.DuplicateParticipantData;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.FieldSettingsTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class ParticipantDataFixupServiceTest extends DbAndElasticBaseTest {
    private static final String TEST_USER = "TEST_USER";
    private static final String instanceName = "atdefault";
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private static DDPInstance ddpInstance;
    private static final ParticipantDataDao dataDao = new ParticipantDataDao();
    private static final List<ParticipantDto> participants = new ArrayList<>();
    private static int participantCounter = 0;
    private static final List<Integer> fieldSettingsIds = new ArrayList<>();
    private static final Gson gson = new Gson();
    private static ATParticipantDataTestUtil atParticipantDataUtil;

    @BeforeClass
    public static void setup() throws Exception {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/atcpMappings.json",
                "elastic/atcpSettings.json");
        ddpInstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(instanceName, esIndex);
        ddpInstance = DDPInstance.getDDPInstanceById(ddpInstanceDto.getDdpInstanceId());
        atParticipantDataUtil = new ATParticipantDataTestUtil(ddpInstanceDto.getDdpInstanceId());
    }

    @AfterClass
    public static void tearDown() {
        DdpInstanceGroupTestUtil.deleteInstance(ddpInstanceDto);
        ElasticTestUtil.deleteIndex(esIndex);
    }

    @After
    public void deleteParticipantData() {
        ParticipantDataDao participantDataDao = new ParticipantDataDao();
        List<ParticipantData> participantDataList =
                participantDataDao.getParticipantDataByInstanceId(ddpInstanceDto.getDdpInstanceId());
        participantDataList.forEach(participantData ->
                participantDataDao.delete(participantData.getParticipantDataId()));

        participants.forEach(participantDto ->
                TestParticipantUtil.deleteParticipant(participantDto.getParticipantId().orElseThrow()));
        participants.clear();
        fieldSettingsIds.forEach(FieldSettingsTestUtil::deleteFieldSettings);
        fieldSettingsIds.clear();
    }

    @Test
    public void testFixupGenomicId() {
        ParticipantDto participant = createParticipant();
        String ddpParticipantId = participant.getDdpParticipantIdOrThrow();
        int instanceId = ddpInstanceDto.getDdpInstanceId();

        int fieldSettingsId = FieldSettingsTestUtil.createExitStatusFieldSetting(ddpInstanceDto.getDdpInstanceId());
        fieldSettingsIds.add(fieldSettingsId);

        // create ptp data that is neither genomic id nor exit status
        atParticipantDataUtil.createMiscellaneousParticipantData(ddpParticipantId);
        atParticipantDataUtil.createEligibilityParticipantData(ddpParticipantId);

        List<ParticipantData> ptpData = dataDao.getParticipantData(ddpParticipantId);
        Assert.assertEquals(2, ptpData.size());
        ElasticSearchService.updateEsParticipantData(ddpParticipantId, ptpData, ddpInstance);

        ParticipantListRequest req = new ParticipantListRequest(List.of(ddpParticipantId));
        String reqJson = gson.toJson(req);

        Map<String, String> attributes = new HashMap<>();
        attributes.put("fixupType", "atcpGenomicId");
        attributes.put("dryRun", "false");

        ParticipantDataFixupService fixupService = new ParticipantDataFixupService();
        // there is nothing to fixup in this run
        fixupService.validRealms = List.of(instanceName);
        fixupService.initialize(TEST_USER, instanceName, attributes, reqJson);
        UpdateLog updateLog = fixupService.updateGenomicId(ddpParticipantId, ptpData);
        Assert.assertEquals(UpdateLog.UpdateStatus.NOT_UPDATED, updateLog.getStatus());

        ATParticipantDataService.insertGenomicIdForParticipant(ddpParticipantId, "GUID_1", instanceId);
        ATParticipantDataService.insertExitStatusForParticipant(ddpParticipantId, instanceId);

        // now have the correct number of genomic IDs and exit statuses
        updateLog = fixupService.updateGenomicId(ddpParticipantId,
                dataDao.getParticipantData(ddpParticipantId));
        Assert.assertEquals(UpdateLog.UpdateStatus.NOT_UPDATED, updateLog.getStatus());
        verifyParticipantData(ddpParticipantId);

        ATParticipantDataService.insertGenomicIdForParticipant(ddpParticipantId, "GUID_2", instanceId);
        ATParticipantDataService.insertExitStatusForParticipant(ddpParticipantId, instanceId);

        // now have extra genomic IDs and exit statuses
        updateLog = fixupService.updateGenomicId(ddpParticipantId,
                dataDao.getParticipantData(ddpParticipantId));
        Assert.assertEquals(UpdateLog.UpdateStatus.UPDATED, updateLog.getStatus());
        verifyParticipantData(ddpParticipantId);
    }

    @Test
    public void testFixupLegacyPidData() {
        // ptp with no data and no legacy pid (helps test the legacy ID lookup)
        createParticipant();

        // ptp with ptp data, legacy pid, and legacy ptp data (not overlapping with ptp data)
        Pair<ParticipantDto, String> ptpToLegacyId = createLegacyParticipant();
        String ddpParticipantId = ptpToLegacyId.getLeft().getRequiredDdpParticipantId();
        String legacyPid1 = ptpToLegacyId.getRight();
        atParticipantDataUtil.createEligibilityParticipantData(ddpParticipantId);
        atParticipantDataUtil.createExitStatusParticipantData(ddpParticipantId);

        Map<String, String> legcayGenomeStudyDataMap = new HashMap<>();
        legcayGenomeStudyDataMap.put("GENOME_STUDY_HAS_SIBLING", "0");
        legcayGenomeStudyDataMap.put("GENOME_STUDY_CPT_ID", "DDP_ATCP_678");
        atParticipantDataUtil.createGenomeStudyParticipantData(legacyPid1, legcayGenomeStudyDataMap);

        Map<String, String> legcayMiscStudyDataMap = new HashMap<>();
        legcayMiscStudyDataMap.put("REGISTRATION_STATUS", "SubmittedEnrollment");
        atParticipantDataUtil.createMiscellaneousParticipantData(legacyPid1, legcayMiscStudyDataMap);

        Map<String, List<ParticipantData>> ptpDataMap = new HashMap<>();
        List<ParticipantData> ptpDataList = dataDao.getParticipantData(ddpParticipantId);
        Assert.assertEquals(2, ptpDataList.size());
        ptpDataMap.put(ddpParticipantId, ptpDataList);

        List<ParticipantData> legacyPid1Data = dataDao.getParticipantData(legacyPid1);
        Assert.assertEquals(2, legacyPid1Data.size());
        ptpDataMap.put(legacyPid1, legacyPid1Data);

        ParticipantDataFixupService fixupService = new ParticipantDataFixupService();
        List<UpdateLog> updateLog = fixupService.fixupLegacyPidData(ptpDataMap, esIndex);

        Assert.assertEquals(2, updateLog.size());
        Set<String> ddpParticipantIds = updateLog.stream()
                .map(UpdateLog::getDdpParticipantId).collect(Collectors.toSet());
        Assert.assertEquals(Set.of(ddpParticipantId, legacyPid1), ddpParticipantIds);
        updateLog.stream().map(UpdateLog::getStatus).forEach(status ->
                Assert.assertEquals(UpdateLog.UpdateStatus.NOT_UPDATED, status));

        // ptp with ptp data and legacy pid and overlapping legacy ptp data
        Map<String, String> genomeStudyDataMap = new HashMap<>();
        genomeStudyDataMap.put("GENOME_STUDY_HAS_SIBLING", "0");
        genomeStudyDataMap.put("GENOME_STUDY_CPT_ID", "DDP_ATCP_1161");
        genomeStudyDataMap.put("GENOME_STUDY_STATUS", "2");
        genomeStudyDataMap.put("GENOME_STUDY_SPIT_KIT_BARCODE", "0");
        genomeStudyDataMap.put("GENOME_STUDY_DATE_SHIPPED", "2023-01-11");
        atParticipantDataUtil.createGenomeStudyParticipantData(ddpParticipantId, genomeStudyDataMap);

        Map<String, String> miscStudyDataMap = new HashMap<>();
        miscStudyDataMap.put("REGISTRATION_TYPE", "Dependent");
        miscStudyDataMap.put("REGISTRATION_STATUS", "ConsentedNeedsAssent");
        atParticipantDataUtil.createMiscellaneousParticipantData(ddpParticipantId, miscStudyDataMap);

        ptpDataList = dataDao.getParticipantData(ddpParticipantId);
        Assert.assertEquals(4, ptpDataList.size());
        ptpDataMap.put(ddpParticipantId, ptpDataList);

        // add another ptp with a legacy pid to test the legacy ID lookup
        createLegacyParticipant();

        updateLog = fixupService.fixupLegacyPidData(ptpDataMap, esIndex);
        Assert.assertEquals(2, updateLog.size());
        updateLog.forEach(log -> {
            if (log.getDdpParticipantId().equals(ddpParticipantId)) {
                Assert.assertEquals(UpdateLog.UpdateStatus.DUPLICATE_PARTICIPANT_DATA, log.getStatus());
                List<DuplicateParticipantData> duplicateData = log.getDuplicateParticipantData();
                Assert.assertEquals(2, duplicateData.size());

                duplicateData.forEach(data -> {
                    if (data.getFieldTypeId().equals(ATParticipantDataService.AT_GROUP_GENOME_STUDY)) {
                        Assert.assertEquals(Set.of("GENOME_STUDY_HAS_SIBLING", "GENOME_STUDY_CPT_ID"),
                                data.getCommonKeys());
                        Assert.assertEquals(Set.of("GENOME_STUDY_DATE_SHIPPED", "GENOME_STUDY_SPIT_KIT_BARCODE",
                                "GENOME_STUDY_STATUS"), data.getDuplicateOnlyKeys());
                        Assert.assertEquals(Map.of("GENOME_STUDY_CPT_ID", Pair.of("DDP_ATCP_678", "DDP_ATCP_1161")),
                                data.getDifferentValues());
                    } else {
                        Assert.assertEquals(Set.of("REGISTRATION_STATUS"), data.getCommonKeys());
                        Assert.assertEquals(Set.of("REGISTRATION_TYPE"), data.getDuplicateOnlyKeys());
                        Assert.assertEquals(Map.of("REGISTRATION_STATUS", Pair.of("SubmittedEnrollment",
                                "ConsentedNeedsAssent")), data.getDifferentValues());
                    }
                });
            } else {
                Assert.assertEquals(UpdateLog.UpdateStatus.NOT_UPDATED, log.getStatus());
            }
        });
    }

    private ParticipantDto createParticipant() {
        String baseName = String.format("%s_%d", instanceName, participantCounter++);
        ParticipantDto participant =
                TestParticipantUtil.createParticipantWithEsProfile(baseName, ddpInstanceDto);
        participants.add(participant);
        return participant;
    }

    private Pair<ParticipantDto, String> createLegacyParticipant() {
        Pair<ParticipantDto, String> pair =
                TestParticipantUtil.createLegacyParticipant(instanceName, participantCounter++, ddpInstanceDto, null, null, null);
        participants.add(pair.getLeft());
        return pair;
    }

    private void verifyParticipantData(String ddpParticipantId) {
        ParticipantDataDao dataDao = new ParticipantDataDao();
        List<ParticipantData> ptpDataList = dataDao.getParticipantData(ddpParticipantId);
        // 1 for exit status, 1 for genomic id, 2 for other data
        Assert.assertEquals(4, ptpDataList.size());

        Set<String> expectedFieldTypes =
                Set.of(AT_PARTICIPANT_EXIT, AT_GROUP_GENOME_STUDY, "AT_GROUP_MISCELLANEOUS", "AT_GROUP_ELIGIBILITY");
        Set<String> foundFieldTypes = ptpDataList.stream()
                .map(ParticipantData::getRequiredFieldTypeId).collect(Collectors.toSet());
        Assert.assertEquals(expectedFieldTypes, foundFieldTypes);
    }
}
