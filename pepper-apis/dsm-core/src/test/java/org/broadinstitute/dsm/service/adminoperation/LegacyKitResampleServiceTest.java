package org.broadinstitute.dsm.service.adminoperation;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.kitrequest.KitRequestDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.juniperkits.TestKitUtil;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.MedicalRecordTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class LegacyKitResampleServiceTest  extends DbAndElasticBaseTest {
    private static final String instanceName = "kitResampleService";
    private static final String shortId = "PT_SHORT";
    private static final String legacyShortId = "LEGACY_SHORT";
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private static String ddpParticipantId = "PT_SAMPLE_QUEUE_TEST";
    private static ParticipantDto participantDto = null;
    private static int participantCounter = 0;
    private static Pair<ParticipantDto, String> legacyParticipantPair;
    public static TestKitUtil testKitUtil;
    private static final String newCollaboratorSampleId = "NEW_SAMPLE_ID";
    private static final String newCollaboratorParticipantId = "NEW_PARTICIPANT_ID";

    private static final String oldCollaboratorSampleId = "OLD_SAMPLE_ID";
    private static final String oldCollaboratorParticipantId = "OLD_PARTICIPANT_ID";
    private static final String ddpLabel = "DDP_LABEL";
    private static final String ddpKitRequestId = "RESAMPLE_KIT_REQUEST_ID";

    @BeforeClass
    public static void doFirst() {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/lmsMappings.json", null);
        ddpInstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(instanceName, esIndex);
        DDPInstance ddpInstance = DDPInstance.getDDPInstance(instanceName);
        ddpParticipantId = TestParticipantUtil.genDDPParticipantId(ddpParticipantId);
        legacyParticipantPair = TestParticipantUtil.createLegacyParticipant(ddpParticipantId, participantCounter++, ddpInstanceDto);
        ElasticTestUtil.createParticipant(esIndex, participantDto);
        testKitUtil = new TestKitUtil(instanceName, instanceName, "RESAMPLE", instanceName, "SALIVA", null);
        testKitUtil.createKitRequestShipping(ddpParticipantId, oldCollaboratorSampleId, oldCollaboratorParticipantId,
                 ddpLabel,  ddpKitRequestId, "SALIVA", ddpInstance, "100");
    }

    @AfterClass
    public static void tearDown() {
        DdpInstanceGroupTestUtil.deleteInstance(ddpInstanceDto);
        ElasticTestUtil.deleteIndex(esIndex);
    }

    @After
    public void deleteParticipantData() {
        MedicalRecordTestUtil.deleteInstanceMedicalRecordBundles(ddpInstanceDto);
        TestParticipantUtil.deleteInstanceParticipants(ddpInstanceDto);
    }

    @Test
    public void testVerify() {
        LegacyKitResampleRequest legacyKitResampleRequest = new LegacyKitResampleRequest(oldCollaboratorSampleId, newCollaboratorSampleId,
                newCollaboratorParticipantId, shortId, legacyShortId);
        DDPInstance ddpInstance = DDPInstance.getDDPInstance(instanceName);
        try {
            legacyKitResampleRequest.verify(ddpInstance, new KitRequestDao());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Should not have thrown exception");
        }

    }

}
