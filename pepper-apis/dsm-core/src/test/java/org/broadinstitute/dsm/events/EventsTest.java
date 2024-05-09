package org.broadinstitute.dsm.events;

import java.io.IOException;

import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.db.dao.queue.EventDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.juniperkits.TestKitUtil;
import org.broadinstitute.dsm.util.DDPRequestUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.KitShippingTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class EventsTest extends DbAndElasticBaseTest {
    private static final String TEST_USER = "EVENT_USER";
    private static final DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private static String instanceName = "event_instance";
    private static String instanceGuid = "event_instance";
    private static String esIndex;
    private static KitShippingTestUtil kitShippingTestUtil = new KitShippingTestUtil(TEST_USER, "EVENT");
    private static DDPInstanceDto ddpInstanceDto;
    private static TestKitUtil testKitUtil;
    private static ParticipantDto participantDto;
    private static String ddpParticipantId;
    private static final ParticipantDao participantDao = new ParticipantDao();

    @BeforeClass
    public static void doFirst() {
        esIndex = ElasticTestUtil.createIndex("event_instance", "elastic/lmsMappings.json", null);
        testKitUtil = new TestKitUtil(instanceName, instanceGuid, instanceName, "event_test_prefix",
                "event-group", null);
        testKitUtil.setupInstanceAndSettings();
        testKitUtil.setEsIndex(esIndex);
        ddpInstanceDto = ddpInstanceDao.getDDPInstanceByInstanceId(testKitUtil.getDdpInstanceId()).orElseThrow();
        ddpParticipantId = TestParticipantUtil.genDDPParticipantId("EVENT_PARTICIPANT");
        participantDto = TestParticipantUtil.createParticipant(ddpParticipantId, ddpInstanceDto.getDdpInstanceId());
        testKitUtil.createEventsForDDPInstance("SENT", "SENT", "event_test for sent kit");

    }

    @AfterClass
    public static void doLast() {
        kitShippingTestUtil.tearDown();
        participantDao.delete(participantDto.getParticipantId().get());
        testKitUtil.deleteGeneratedData();
        ElasticTestUtil.deleteIndex(esIndex);
    }

    @Test
    public void testKitSentEvent() {
        try (MockedStatic<DDPRequestUtil> utilities = Mockito.mockStatic(DDPRequestUtil.class)) {
            utilities.when(() -> DDPRequestUtil.postRequest(Mockito.anyString(), Mockito.any(), Mockito.anyString(), Mockito.anyBoolean()))
                    .thenReturn(200);

            //assert that mocking is working correctly
            assert DDPRequestUtil.postRequest("", "", "", true) == 200;

            int kitRequestId = kitShippingTestUtil.createTestKitShipping(participantDto, ddpInstanceDto, "SALIVA",
                    testKitUtil.getKitTypeId());
            KitRequestShipping kitRequestShipping = KitDao.getKitRequest(kitRequestId).orElseThrow();
            String kitLabel = "EVENT_KIT_LABEL";
            testKitUtil.changeKitRequestShippingToSent(kitRequestShipping, kitLabel);

            Assert.assertTrue(new EventDao().hasTriggeredEventByEventTypeAndDsmKitId("SENT",
                    kitRequestShipping.getDsmKitRequestId()).orElse(false));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

}
