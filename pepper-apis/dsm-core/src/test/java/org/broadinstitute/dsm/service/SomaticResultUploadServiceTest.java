package org.broadinstitute.dsm.service;

import static org.junit.Assert.assertNotNull;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.SomaticResultUpload;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.model.somatic.result.SomaticResultMetaData;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;

public class SomaticResultUploadServiceTest extends DbAndElasticBaseTest {

    private static final DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private static final String instanceName = "somatic-upload-test";
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private static ParticipantDto testParticipant = null;

    @BeforeClass
    public static void setup() throws Exception {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/lmsMappings.json", null);
        ddpInstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(instanceName, esIndex, instanceName);
        Config loadedConfig = ConfigManager.getInstance().getConfig();
        somaticResultUploadSerivce = SomaticResultUploadService.fromConfig(loadedConfig);
    }

    @AfterClass
    public static void tearDown() {
        try {
            Assert.assertNotNull(testParticipant);
            TestParticipantUtil.deleteParticipant(testParticipant.getRequiredParticipantId());
            Assert.assertNotNull(ddpInstanceDto);
            ddpInstanceDao.delete(ddpInstanceDto.getDdpInstanceId());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception: " + e);
        }
        ElasticTestUtil.deleteIndex(esIndex);
    }

    static SomaticResultUploadService somaticResultUploadSerivce;

    @Test
    public void test_authorizeValidRequest() {
        assertNotNull(somaticResultUploadSerivce);
    }

    @Test
    public void testDelete() {
        String ddpParticipantId = TestParticipantUtil.genDDPParticipantId("somaticupload");
        testParticipant = TestParticipantUtil.createParticipant(ddpParticipantId, ddpInstanceDto.getDdpInstanceId());
        SomaticResultMetaData uploadData = new SomaticResultMetaData("testFile.pdf", "application/pdf", 100L);
        SomaticResultUploadService.AuthorizeResult uploadAuth = somaticResultUploadSerivce.authorizeUpload(
                instanceName, Integer.toString(testParticipant.getRequiredParticipantId()),
                testParticipant.getRequiredDdpParticipantId(), uploadData);
        SomaticResultUpload deleteResult = somaticResultUploadSerivce.deleteUpload(
                testParticipant.getRequiredParticipantId(), uploadAuth.getSomaticResultUpload().getSomaticDocumentId(),
                instanceName);
        long secondsSinceDeletion = Duration.between(Instant.ofEpochSecond(deleteResult.getDeletedAt()), Instant.now()).getSeconds();
        assertNotNull(deleteResult);
        Assert.assertTrue(secondsSinceDeletion < 30);
        Assert.assertEquals(testParticipant.getRequiredParticipantId(), deleteResult.getDeletedByUserId().intValue());
        TransactionWrapper.useTxn(handle -> {
            SomaticResultUpload.hardDeleteSomaticDocumentById(handle,
                    uploadAuth.getSomaticResultUpload().getSomaticDocumentId());
        });

    }
}
