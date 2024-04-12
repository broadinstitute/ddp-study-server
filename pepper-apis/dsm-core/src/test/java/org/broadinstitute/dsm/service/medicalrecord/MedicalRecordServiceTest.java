package org.broadinstitute.dsm.service.medicalrecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistory;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.onchistory.OncHistoryDto;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.service.elastic.ElasticSearchService;
import org.broadinstitute.dsm.service.participant.OsteoParticipantService;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.MedicalRecordTestUtil;
import org.broadinstitute.dsm.util.SystemUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.broadinstitute.lddp.handlers.util.Institution;
import org.broadinstitute.lddp.handlers.util.InstitutionRequest;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class MedicalRecordServiceTest extends DbAndElasticBaseTest {
    private static final ElasticSearchService elasticSearchService = new ElasticSearchService();
    public static final String osteo1InstanceName = "osteo1test";
    public static final String osteo2InstanceName = "osteo2test";
    private static DDPInstanceDto osteo1InstanceDto;
    private static DDPInstanceDto osteo2InstanceDto;
    private static final List<ParticipantDto> participants = new ArrayList<>();
    private static int participantCounter = 0;

    @BeforeClass
    public static void setup() throws Exception {
        String osteo1EsIndex = ElasticTestUtil.createIndex(osteo1InstanceName, "elastic/osteo1Mappings.json",
                "elastic/osteo1Settings.json");
        osteo1InstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(osteo1InstanceName, osteo1EsIndex);
        String osteo2EsIndex = ElasticTestUtil.createIndex(osteo2InstanceName, "elastic/osteo1Mappings.json",
                "elastic/osteo1Settings.json");
        osteo2InstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(osteo2InstanceName, osteo2EsIndex);
    }

    @AfterClass
    public static void tearDown() {
        DdpInstanceGroupTestUtil.deleteInstance(osteo1InstanceDto);
        ElasticTestUtil.deleteIndex(osteo1InstanceDto.getEsParticipantIndex());
        DdpInstanceGroupTestUtil.deleteInstance(osteo2InstanceDto);
        ElasticTestUtil.deleteIndex(osteo2InstanceDto.getEsParticipantIndex());
    }

    @After
    public void deleteParticipantData() {
        MedicalRecordTestUtil.deleteInstanceMedicalRecordBundles(osteo1InstanceDto);
        MedicalRecordTestUtil.deleteInstanceMedicalRecordBundles(osteo2InstanceDto);

        participants.forEach(participantDto ->
                TestParticipantUtil.deleteParticipant(participantDto.getRequiredParticipantId()));
        participants.clear();
    }

    @Test
    public void testProcessInstitutionRequest() {
        // create an osteo1 and osteo2 participant
        ParticipantDto osteo1Ptp = createParticipant(osteo1InstanceDto);
        String osteo1ParticpantId = osteo1Ptp.getRequiredDdpParticipantId();

        ParticipantDto osteo2Ptp = createParticipant(osteo2InstanceDto);
        String osteo2ParticpantId = osteo2Ptp.getRequiredDdpParticipantId();

        Institution institution1 = MedicalRecordTestUtil.createInstitution(osteo1ParticpantId, 1);
        Institution institution2 = MedicalRecordTestUtil.createInstitution(osteo1ParticpantId, 2);
        Institution institution3 = MedicalRecordTestUtil.createInstitution(osteo2ParticpantId, 1);
        Institution institution4 = MedicalRecordTestUtil.createInstitution(osteo2ParticpantId, 2);

        String lastUpdated = SystemUtil.getDateFormatted(System.currentTimeMillis());
        InstitutionRequest institutionRequest =
                new InstitutionRequest(2, osteo1ParticpantId, List.of(institution1), lastUpdated);
        InstitutionRequest institutionRequest2 =
                new InstitutionRequest(3, osteo1ParticpantId, List.of(institution2), lastUpdated);
        InstitutionRequest institutionRequest3 =
                new InstitutionRequest(1, osteo2ParticpantId, List.of(institution3, institution4), lastUpdated);

        InstitutionRequest[] institutionRequests =
                new InstitutionRequest[] {institutionRequest, institutionRequest2, institutionRequest3};

        OsteoParticipantService osteoParticipantService =
                new OsteoParticipantService(osteo1InstanceName, osteo2InstanceName);
        MedicalRecordService medicalRecordService =
                new MedicalRecordService(new TestMedicalRecordInstanceProvider(osteoParticipantService, 0));

        DDPInstance ddpInstance = DDPInstance.getDDPInstance(osteo2InstanceName);
        try {
            medicalRecordService.processInstitutionRequest(institutionRequests, ddpInstance, 0);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Failed to process institution request: " + e.getMessage());
        }

        verifyMedicalRecordAndOncHistory(osteo1Ptp, osteo1InstanceDto, 2);
        verifyMedicalRecordAndOncHistory(osteo2Ptp, osteo2InstanceDto, 2);
    }

    private ParticipantDto createParticipant(DDPInstanceDto ddpInstanceDto) {
        String baseName = String.format("%s_%d", ddpInstanceDto.getInstanceName(), participantCounter++);
        ParticipantDto participant =
                TestParticipantUtil.createParticipantWithEsProfile(baseName, ddpInstanceDto);
        participants.add(participant);
        return participant;
    }

    private void verifyMedicalRecordAndOncHistory(ParticipantDto participant, DDPInstanceDto ddpInstanceDto,
                                                  int medicalRecordCount) {
        String ddpParticipantId = participant.getRequiredDdpParticipantId();
        String esIndex = ddpInstanceDto.getEsParticipantIndex();

        ElasticSearchParticipantDto esParticipant =
                elasticSearchService.getRequiredParticipantDocument(ddpParticipantId, esIndex);;
        log.debug("Verifying ES participant record for {}: {}", ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));
        Dsm dsm = esParticipant.getDsm().orElseThrow();

        List<MedicalRecord> esMedicalRecords = dsm.getMedicalRecord();
        Assert.assertEquals(medicalRecordCount, esMedicalRecords.size());

        int participantId = participant.getRequiredParticipantId();
        List<MedicalRecord> medicalRecords = MedicalRecord.getMedicalRecordsForParticipant(participantId);
        Assert.assertEquals(medicalRecordCount, medicalRecords.size());

        Assert.assertEquals(medicalRecords.stream().map(MedicalRecord::getMedicalRecordId).collect(Collectors.toSet()),
                esMedicalRecords.stream().map(MedicalRecord::getMedicalRecordId).collect(Collectors.toSet()));

        Optional<OncHistory> oh = dsm.getOncHistory();
        // see comment in DDPMedicalRecordDataRequest.writeInstitutionInfo
        Assert.assertFalse(oh.isPresent());

        Optional<OncHistoryDto> oncHistory = OncHistoryDao.getByParticipantId(participantId);
        Assert.assertTrue(oncHistory.isPresent());
        Assert.assertEquals(oncHistory.get().getParticipantId(), participant.getRequiredParticipantId());
    }
}
