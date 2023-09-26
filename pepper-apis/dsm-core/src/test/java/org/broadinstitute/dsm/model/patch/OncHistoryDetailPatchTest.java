package org.broadinstitute.dsm.model.patch;

import java.util.List;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.institution.DDPInstitutionDao;
import org.broadinstitute.dsm.db.dao.ddp.medical.records.MedicalRecordDao;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDaoImpl;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDto;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.DBTestUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.broadinstitute.dsm.util.TestUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class OncHistoryDetailPatchTest extends DbAndElasticBaseTest {

    private static final DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private static final String instanceName = "onchistorydetailpatchtest";
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private static ParticipantDto testParticipant = null;

    @BeforeClass
    public static void setup() throws Exception {
        esIndex = ElasticTestUtil.createIndexWithMappings(instanceName, "elastic/lmsMappings.json");
        ddpInstanceDto = DBTestUtil.createTestDdpInstance(ddpInstanceDao, instanceName, esIndex);
    }

    @AfterClass
    public static void tearDown() {
        ddpInstanceDao.delete(ddpInstanceDto.getDdpInstanceId());
    }

    @After
    public void deleteParticipants() {
        if (testParticipant != null) {
            TestParticipantUtil.deleteParticipant(testParticipant.getParticipantId().orElseThrow());
            testParticipant = null;
        }
    }

    @Test
    public void doPatchTest() {
        String ddpParticipantId = TestParticipantUtil.genDDPParticipantId("OncHistoryDetailPatchTest");
        try {
            testParticipant = TestParticipantUtil.createParticipant(ddpParticipantId, ddpInstanceDto.getDdpInstanceId());
            //ElasticTestUtil.addProperty(ddpParticipantId, "dsm", esIndex);
            ElasticTestUtil.addDsmParticipant(testParticipant, ddpInstanceDto);
            log.info("TEMP: Participant document with ptp: {}", ElasticTestUtil.getParticipantDocument(esIndex, ddpParticipantId));

            ElasticTestUtil.addParticipantAndInstitution(testParticipant, ddpInstanceDto);

            Gson gson = new Gson();
            String profileJson = TestUtil.readFile("elastic/participantProfile.json");
            Profile profile = gson.fromJson(profileJson, Profile.class);
            profile.setGuid(ddpParticipantId);
            ElasticTestUtil.addParticipantProfile(profile, esIndex);
            log.info("TEMP: Participant document with profile and med record: {}",
                    ElasticTestUtil.getParticipantDocument(esIndex, ddpParticipantId));

            String activitiesJson = TestUtil.readFile("elastic/lmsActivities.json");
            ElasticTestUtil.addActivities(ddpParticipantId, activitiesJson, esIndex);

            String patchJson = TestUtil.readFile("onchistory/patch.json");

            Patch patch = gson.fromJson(patchJson, Patch.class);
            patch.setDdpParticipantId(ddpParticipantId);
            patch.setParentId(testParticipant.getParticipantId().toString());
            patch.setRealm(instanceName);

            OncHistoryDetailPatch oncHistoryDetailPatch = new OncHistoryDetailPatch(patch);
            oncHistoryDetailPatch.doPatch();
            log.info("TEMP: Participant document with oncHistory patch: {}",
                    ElasticTestUtil.getParticipantDocument(esIndex, ddpParticipantId));

            String shortId = profile.getHruid();
            ElasticSearchParticipantDto esParticipant =
                     ElasticSearchUtil.getParticipantESDataByParticipantId(esIndex, ddpParticipantId);
            log.info("TEMP: esParticipant {}", esParticipant);
            Profile esProfile = esParticipant.getProfile().orElseThrow();
            Assert.assertEquals(profile.getHruid(), esProfile.getHruid());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception: " + e);
        } finally {
            DDPInstitutionDao institutionDao = new DDPInstitutionDao();
            MedicalRecordDao medicalRecordDao = new MedicalRecordDao();
            List<MedicalRecord> medRecords =
                    MedicalRecord.getMedicalRecordsForParticipant(testParticipant.getParticipantId().orElseThrow());
            for (MedicalRecord medRecord: medRecords) {
                List<OncHistoryDetailDto> oncHistoryDetailList =
                        OncHistoryDetail.getOncHistoryDetailByMedicalRecord(medRecord.getMedicalRecordId());
                OncHistoryDetailDaoImpl oncHistoryDetailDao = new OncHistoryDetailDaoImpl();
                for (var ohd: oncHistoryDetailList) {
                    oncHistoryDetailDao.delete((Integer)ohd.getColumnValues().get(DBConstants.ONC_HISTORY_DETAIL_ID));
                }
                // the schema allows multiple medical records for an institution, so this will just
                // fail silently if the institution is already deleted
                medicalRecordDao.delete(medRecord.getMedicalRecordId());
                institutionDao.delete(medRecord.getInstitutionId());
            }
        }
    }
}
