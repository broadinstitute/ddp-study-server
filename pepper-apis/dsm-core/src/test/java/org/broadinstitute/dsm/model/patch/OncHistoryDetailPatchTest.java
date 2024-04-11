package org.broadinstitute.dsm.model.patch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.service.elastic.ElasticSearchService;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.MedicalRecordTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.broadinstitute.dsm.util.TestUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class OncHistoryDetailPatchTest extends DbAndElasticBaseTest {
    private static final ElasticSearchService elasticSearchService = new ElasticSearchService();
    private static final DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private static final String instanceName = "ohdetailpatch";
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private static ParticipantDto testParticipant = null;
    private static MedicalRecordTestUtil medicalRecordTestUtil;

    @BeforeClass
    public static void setup() throws Exception {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/lmsMappings.json", null);
        ddpInstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(instanceName, esIndex, instanceName);
        medicalRecordTestUtil = new MedicalRecordTestUtil();
    }

    @AfterClass
    public static void tearDown() {
        try {
            Assert.assertNull(testParticipant);
            ddpInstanceDao.delete(ddpInstanceDto.getDdpInstanceId());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception: " + e);
        }
        ElasticTestUtil.deleteIndex(esIndex);
    }

    @After
    public void deleteParticipants() {
        int participantId = testParticipant.getRequiredParticipantId();
        MedicalRecordTestUtil.deleteOncHistoryDetailRecords(participantId);
        medicalRecordTestUtil.tearDown();
        if (testParticipant != null) {
            TestParticipantUtil.deleteParticipant(participantId);
            testParticipant = null;
        }
    }

    @Test
    public void doPatchTest() {
        String ddpParticipantId = TestParticipantUtil.genDDPParticipantId("onchistorypatch");
        try {
            testParticipant = TestParticipantUtil.createParticipant(ddpParticipantId, ddpInstanceDto.getDdpInstanceId());
            int participantId = testParticipant.getRequiredParticipantId();
            ElasticTestUtil.createParticipant(esIndex, testParticipant);
            medicalRecordTestUtil.createMedicalRecordBundle(testParticipant, ddpInstanceDto);

            Profile profile = ElasticTestUtil.addParticipantProfileFromFile(esIndex, "elastic/participantProfile.json",
                    ddpParticipantId);

            String patchJson = TestUtil.readFile("onchistory/patch.json");
            Gson gson = new Gson();
            Patch patch = gson.fromJson(patchJson, Patch.class);
            patch.setDdpParticipantId(ddpParticipantId);
            patch.setParentId(Integer.toString(participantId));
            patch.setRealm(instanceName);
            patch.setId(null);

            OncHistoryDetailPatch oncHistoryDetailPatch = new OncHistoryDetailPatch(patch);
            oncHistoryDetailPatch.setElasticSearchExportable(true);
            oncHistoryDetailPatch.doPatch();

            try {
                // TODO: ElasticDataExportAdapter should probably force a refresh, but for now...
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                Assert.fail("Interrupted while waiting for ES refresh");
            }

            List<MedicalRecord> medRecords = MedicalRecord.getMedicalRecordsForParticipant(participantId);
            Assert.assertEquals(2, medRecords.size());
            log.debug("Participant document with oncHistory patch: {}",
                    ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));

            ElasticSearchParticipantDto esParticipant =
                    elasticSearchService.getRequiredParticipantDocument(ddpParticipantId, esIndex);
            Profile esProfile = esParticipant.getProfile().orElseThrow();
            Assert.assertEquals(profile.getHruid(), esProfile.getHruid());

            // the patch medical record is not written to ES
            Dsm dsm = esParticipant.getDsm().orElseThrow();
            List<MedicalRecord> esMedRecords = dsm.getMedicalRecord();
            log.debug("ES med records {}", esMedRecords);
            Assert.assertEquals(1, esMedRecords.size());
            MedicalRecord esMedRecord = esMedRecords.get(0);

            List<MedicalRecord> recs = medRecords.stream().filter(mr ->
                    mr.getMedicalRecordId() == esMedRecord.getMedicalRecordId()).toList();
            Assert.assertEquals(1, recs.size());
            MedicalRecord specifiedMedRec = recs.get(0);
            Assert.assertEquals("PHYSICIAN", specifiedMedRec.getType());
            Assert.assertEquals("PHYSICIAN", esMedRecord.getType());

            List<OncHistoryDetail> oncHistoryDetailList = dsm.getOncHistoryDetail();
            Assert.assertEquals(1, oncHistoryDetailList.size());
            OncHistoryDetail oncHistoryDetail = oncHistoryDetailList.get(0);

            Map<String, String> patchMap = nameValuesToMap(patch.getNameValues());
            Assert.assertEquals(patchMap.get("oD.destructionPolicy"), oncHistoryDetail.getDestructionPolicy());
            Assert.assertEquals(patchMap.get("oD.facility"), oncHistoryDetail.getFacility());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception: " + e);
        }
    }

    private static Map<String, String> nameValuesToMap(List<NameValue> nameValues) {
        Map<String, String> nameToValue = new HashMap<>();
        for (var nameValue: nameValues) {
            nameToValue.put(nameValue.getName(), nameValue.getValue().toString());
        }
        return nameToValue;
    }
}
