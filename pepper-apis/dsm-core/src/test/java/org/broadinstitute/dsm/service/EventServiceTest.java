package org.broadinstitute.dsm.service;

import static org.broadinstitute.dsm.service.EventService.MAX_TRIES;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.io.IOException;
import java.util.Optional;

import org.broadinstitute.ddp.notficationevent.KitReasonType;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dao.kit.ClinicalKitDao;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.db.dao.queue.EventDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.kit.BSPKitDto;
import org.broadinstitute.dsm.db.dto.queue.EventDto;
import org.broadinstitute.dsm.kits.TestKitUtil;
import org.broadinstitute.dsm.model.gp.BSPKit;
import org.broadinstitute.dsm.model.gp.bsp.BSPKitStatus;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.DDPRequestUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.KitShippingTestUtil;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class EventServiceTest extends DbAndElasticBaseTest {
    private static final String TEST_USER = "EVENT_USER";
    private static final DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private static final String INSTANCE_NAME = "event_instance";
    private static final String INSTANCE_GUID = "event_instance";
    private static final String SALIVA = "SALIVA";
    private static final String EVENT_TYPE_SENT = "SALIVA_SENT";
    private static final String EVENT_TYPE_RECEIVED = "SALIVA_RECEIVED";
    private static String esIndex;
    private static KitShippingTestUtil kitShippingTestUtil;
    private static DDPInstanceDto ddpInstanceDto;
    private static DDPInstance ddpInstance;
    private static TestKitUtil testKitUtil;
    private static ParticipantDto participantDto;
    private static ParticipantDto unsuccessfulEventPtDto;
    private static String ddpParticipantId;
    private static String unsuccessfulEventPt;
    private static final ParticipantDao participantDao = new ParticipantDao();
    private final EventDao eventDao = new EventDao();
    private final ClinicalKitDao clinicalKitDao = new ClinicalKitDao();

    @BeforeClass
    public static void setUp() {
        esIndex = ElasticTestUtil.createIndex(INSTANCE_NAME, "elastic/lmsMappings.json", null);
        kitShippingTestUtil = new KitShippingTestUtil(TEST_USER, "EVENT");
        testKitUtil = new TestKitUtil(INSTANCE_NAME, INSTANCE_GUID, INSTANCE_NAME, "event_test_prefix", "event-group", null, esIndex);
        testKitUtil.setupInstanceAndSettings();
        ddpInstanceDto = ddpInstanceDao.getDDPInstanceByInstanceId(testKitUtil.getDdpInstanceId()).orElseThrow();
        ddpInstance = DDPInstance.from(ddpInstanceDto);
        ddpParticipantId = TestParticipantUtil.genDDPParticipantId("EVENT_PARTICIPANT");
        participantDto = TestParticipantUtil.createParticipant(ddpParticipantId, ddpInstanceDto.getDdpInstanceId());
        unsuccessfulEventPt = TestParticipantUtil.genDDPParticipantId("EVENT_PARTICIPANT_2");
        unsuccessfulEventPtDto = TestParticipantUtil.createParticipant(unsuccessfulEventPt,
                ddpInstanceDto.getDdpInstanceId());
        testKitUtil.createEventsForDDPInstance(EVENT_TYPE_SENT, "SENT", "test purpose: event for sent kit", false);
        testKitUtil.createEventsForDDPInstance(EVENT_TYPE_RECEIVED, "RECEIVED", "test purpose: event for received kit", false);
        testKitUtil.createEventsForDDPInstance(DBConstants.REQUIRED_SAMPLES_RECEIVED_EVENT, "RECEIVED",
                "test purpose: event for participant", true);
    }

    @AfterClass
    public static void tearDown() {
        kitShippingTestUtil.tearDown();
        participantDao.delete(participantDto.getParticipantId().get());
        participantDao.delete(unsuccessfulEventPtDto.getParticipantId().get());
        testKitUtil.deleteGeneratedData();
        ElasticTestUtil.deleteIndex(esIndex);
    }

    @Test
    public void testKitSentEvent() {
        try (MockedStatic<DDPRequestUtil> utilities = Mockito.mockStatic(DDPRequestUtil.class)) {
            utilities.when(() -> DDPRequestUtil.postRequest(anyString(), any(), anyString(), anyBoolean()))
                    .thenReturn(200);

            // Assert that mocking is working correctly
            Assert.assertEquals(200L, (long)DDPRequestUtil.postRequest("", "", "", true));

            int kitRequestId = kitShippingTestUtil.createTestKitShippingWithKitType(participantDto, ddpInstanceDto, SALIVA,
                    testKitUtil.getKitTypeId(), false);
            KitRequestShipping kitRequestShipping = KitDao.getKitRequest(kitRequestId).orElseThrow();
            String kitLabel = "EVENT_KIT_LABEL";
            testKitUtil.changeKitRequestShippingToSent(kitRequestShipping, kitLabel);
            EventDto eventDto = eventDao.getEventForKit(EVENT_TYPE_SENT, kitRequestShipping.getDsmKitRequestId()).orElseThrow();
            assertEvent(eventDto, true, kitRequestShipping.getDsmKitRequestId(), null, ddpInstanceDto.getDdpInstanceId(), EVENT_TYPE_SENT);
        } catch (IOException e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception");
        }
    }

    @Test
    public void testParticipantEvent_successful() {
        try (MockedStatic<DDPRequestUtil> utilities = Mockito.mockStatic(DDPRequestUtil.class)) {
            utilities.when(() -> DDPRequestUtil.postRequest(anyString(), any(), anyString(), anyBoolean())).thenReturn(200);
            clinicalKitDao.triggerParticipantEvent(DDPInstance.from(ddpInstanceDto), ddpParticipantId,
                    DBConstants.REQUIRED_SAMPLES_RECEIVED_EVENT);
            EventDto eventDto =
                    eventDao.getEventForParticipant(DBConstants.REQUIRED_SAMPLES_RECEIVED_EVENT, ddpParticipantId).orElseThrow();
            assertEvent(eventDto, true, 0, ddpParticipantId, ddpInstanceDto.getDdpInstanceId(),
                    DBConstants.REQUIRED_SAMPLES_RECEIVED_EVENT);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception");
        }

    }

    @Test
    public void testParticipantEvent_unsuccessful() {
        try (MockedStatic<EventService> eventServiceMockedStatic = Mockito.mockStatic(EventService.class, Mockito.CALLS_REAL_METHODS);
                MockedStatic<DDPRequestUtil> utilities = Mockito.mockStatic(DDPRequestUtil.class)) {
            //we can't use the same participant as before, because the event is already triggered and so dsm won't trigger it again
            utilities.when(() -> DDPRequestUtil.postRequest(anyString(), any(), anyString(), anyBoolean())).thenReturn(500);
            clinicalKitDao.triggerParticipantEvent(ddpInstance, unsuccessfulEventPt,
                    DBConstants.REQUIRED_SAMPLES_RECEIVED_EVENT);
            EventDto eventDto =
                    eventDao.getEventForParticipant(DBConstants.REQUIRED_SAMPLES_RECEIVED_EVENT, unsuccessfulEventPt).orElseThrow();
            assertEvent(eventDto, false, 0, unsuccessfulEventPt, ddpInstanceDto.getDdpInstanceId(),
                    DBConstants.REQUIRED_SAMPLES_RECEIVED_EVENT);
            eventServiceMockedStatic.verify(() -> EventService.logTriggerFailure(any(), anyString(), anyString(), anyString(), any()),
                    never());
            eventServiceMockedStatic.verify(() -> EventService.logTriggerExhausted(ddpInstance,
                    DBConstants.REQUIRED_SAMPLES_RECEIVED_EVENT, unsuccessfulEventPt, unsuccessfulEventPt), times(1));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception");
        }

    }

    @Test
    public void testUnsuccessfulEvent_BadResponse() {
        try (MockedStatic<EventService> eventServiceMockedStatic = Mockito.mockStatic(EventService.class, Mockito.CALLS_REAL_METHODS);
                MockedStatic<DDPRequestUtil> utilities = Mockito.mockStatic(DDPRequestUtil.class)) {

            utilities.when(() -> DDPRequestUtil.postRequest(anyString(), any(), anyString(), anyBoolean())).thenReturn(500);

            // Assert that mocking is working correctly
            Assert.assertEquals(500L, (long)DDPRequestUtil.postRequest("", "", "", true));

            int kitRequestId = kitShippingTestUtil.createTestKitShippingWithKitType(participantDto, ddpInstanceDto, SALIVA,
                    testKitUtil.getKitTypeId(), false);
            KitRequestShipping kitRequestShipping = KitDao.getKitRequest(kitRequestId).orElseThrow();
            String kitLabel = "EVENT_KIT_LABEL_2";
            testKitUtil.changeKitRequestShippingToSent(kitRequestShipping, kitLabel);
            EventDto eventDto = eventDao.getEventForKit(EVENT_TYPE_SENT, kitRequestShipping.getDsmKitRequestId()).orElseThrow();
            assertEvent(eventDto, false, kitRequestShipping.getDsmKitRequestId(), null, ddpInstanceDto.getDdpInstanceId(),
                    EVENT_TYPE_SENT);

            eventServiceMockedStatic.verify(() -> EventService.sendDDPEventRequest(EVENT_TYPE_SENT, ddpInstance, 0L,
                    ddpParticipantId, kitRequestShipping.getDdpKitRequestId(), KitReasonType.NORMAL), times(MAX_TRIES));
            eventServiceMockedStatic.verify(() -> EventService.logTriggerFailure(any(), anyString(), anyString(), anyString(),
                    any()), never());
            eventServiceMockedStatic.verify(() -> EventService.logTriggerExhausted(ddpInstance, EVENT_TYPE_SENT,
                    ddpParticipantId, kitRequestShipping.getDdpKitRequestId()), times(1));
        } catch (IOException e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception");
        }
    }

    @Test
    public void testUnsuccessfulEvent_Exception() {
        IOException exception = new IOException("Test IOException");
        try (MockedStatic<EventService> eventServiceMockedStatic = Mockito.mockStatic(EventService.class, Mockito.CALLS_REAL_METHODS)) {
            eventServiceMockedStatic.when(() -> EventService.sendDDPEventRequest(anyString(), any(), anyLong(), anyString(), anyString(),
                    any())).thenThrow(exception);

            int kitRequestId = kitShippingTestUtil.createTestKitShippingWithKitType(participantDto, ddpInstanceDto, SALIVA,
                    testKitUtil.getKitTypeId(), false);
            KitRequestShipping kitRequestShipping = KitDao.getKitRequest(kitRequestId).orElseThrow();
            String kitLabel = "EVENT_KIT_LABEL_3";
            testKitUtil.changeKitRequestShippingToSent(kitRequestShipping, kitLabel);
            EventDto eventDto = eventDao.getEventForKit(EVENT_TYPE_SENT, kitRequestShipping.getDsmKitRequestId()).orElseThrow();
            assertEvent(eventDto, false, kitRequestShipping.getDsmKitRequestId(), null,
                    ddpInstanceDto.getDdpInstanceId(), EVENT_TYPE_SENT);

            eventServiceMockedStatic.verify(() -> EventService.sendDDPEventRequest(EVENT_TYPE_SENT, ddpInstance, 0L,
                    ddpParticipantId, kitRequestShipping.getDdpKitRequestId(), KitReasonType.NORMAL), times(MAX_TRIES));
            eventServiceMockedStatic.verify(() -> EventService.logTriggerFailure(any(), anyString(), anyString(), anyString(),
                    any()), times(MAX_TRIES));
            eventServiceMockedStatic.verify(() -> EventService.logTriggerExhausted(ddpInstance, EVENT_TYPE_SENT,
                    ddpParticipantId, kitRequestShipping.getDdpKitRequestId()), times(1));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception");
        }
    }

    @Test
    public void testUnsuccessfulEvent_EventuallySuccessful() {
        try (MockedStatic<EventService> eventServiceMockedStatic = Mockito.mockStatic(EventService.class, Mockito.CALLS_REAL_METHODS)) {
            eventServiceMockedStatic.when(() -> EventService.sendDDPEventRequest(anyString(), any(), Mockito.anyLong(), anyString(),
                    anyString(), any())).thenAnswer(new Answer<Boolean>() {
                        private int count = 0;

                        @Override
                        public Boolean answer(InvocationOnMock invocation) {
                            if (count < 2) {
                                count++;
                                return false;
                            } else {
                                return true;
                            }
                        }
                    });

            int kitRequestId = kitShippingTestUtil.createTestKitShippingWithKitType(participantDto, ddpInstanceDto, SALIVA,
                    testKitUtil.getKitTypeId(), false);
            KitRequestShipping kitRequestShipping = KitDao.getKitRequest(kitRequestId).orElseThrow();
            String kitLabel = "EVENT_KIT_LABEL_4";
            testKitUtil.changeKitRequestShippingToSent(kitRequestShipping, kitLabel);
            EventDto eventDto = eventDao.getEventForKit(EVENT_TYPE_SENT, kitRequestShipping.getDsmKitRequestId()).orElseThrow();
            assertEvent(eventDto, true, kitRequestShipping.getDsmKitRequestId(), null,
                    ddpInstanceDto.getDdpInstanceId(), EVENT_TYPE_SENT);

            eventServiceMockedStatic.verify(() -> EventService.sendDDPEventRequest(EVENT_TYPE_SENT, ddpInstance, 0L,
                    ddpParticipantId, kitRequestShipping.getDdpKitRequestId(), KitReasonType.NORMAL), times(3));
            eventServiceMockedStatic.verify(() -> EventService.logTriggerFailure(any(), anyString(), anyString(), anyString(),
                    any()), never());
            eventServiceMockedStatic.verify(() -> EventService.logTriggerExhausted(ddpInstance, EVENT_TYPE_SENT,
                    ddpParticipantId, kitRequestShipping.getDdpKitRequestId()), never());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception");
        }
    }

    @Test
    public void testReceivedKitEvent() {
        try (MockedStatic<EventService> eventServiceMockedStatic = Mockito.mockStatic(EventService.class, Mockito.CALLS_REAL_METHODS);
                MockedStatic<DDPRequestUtil> utilities = Mockito.mockStatic(DDPRequestUtil.class)) {
            NotificationUtil mockedNotificationUtil = mock(NotificationUtil.class);

            utilities.when(() -> DDPRequestUtil.postRequest(anyString(), any(), anyString(), anyBoolean())).thenReturn(200);

            int kitRequestId = kitShippingTestUtil.createTestKitShippingWithKitType(participantDto, ddpInstanceDto, SALIVA,
                    testKitUtil.getKitTypeId(), false);
            KitRequestShipping kitRequestShipping = KitDao.getKitRequest(kitRequestId).orElseThrow();
            String kitLabel = "EVENT_KIT_LABEL_5";
            testKitUtil.changeKitRequestShippingToSent(kitRequestShipping, kitLabel);

            BSPKit bspKit = new BSPKit();
            Optional<BSPKitDto> optionalBSPKitDto = bspKit.canReceiveKit(kitLabel);
            if (optionalBSPKitDto.isEmpty()) {
                Assert.fail("Kit not found in ddp_kit table");
            }
            Optional<BSPKitStatus> result = bspKit.getKitStatus(optionalBSPKitDto.get(), mockedNotificationUtil);
            if (!result.isEmpty()) {
                Assert.fail("Kit should not be from a pt which is withdrawn");
            }
            bspKit.receiveKit(kitLabel, optionalBSPKitDto.get(), mockedNotificationUtil, "BSP").get();

            EventDto eventDto = eventDao.getEventForKit(EVENT_TYPE_RECEIVED, kitRequestShipping.getDsmKitRequestId()).orElseThrow();
            assertEvent(eventDto, true, kitRequestShipping.getDsmKitRequestId(), null, ddpInstanceDto.getDdpInstanceId(),
                    EVENT_TYPE_RECEIVED);

            eventServiceMockedStatic.verify(() -> EventService.sendDDPEventRequest(EVENT_TYPE_SENT, ddpInstance, 0L,
                    ddpParticipantId, kitRequestShipping.getDdpKitRequestId(), KitReasonType.NORMAL), times(1));
            eventServiceMockedStatic.verify(() -> EventService.logTriggerFailure(any(), anyString(), anyString(), anyString(), any()),
                    never());
            eventServiceMockedStatic.verify(() -> EventService.logTriggerExhausted(ddpInstance, EVENT_TYPE_SENT,
                    ddpParticipantId, kitRequestShipping.getDdpKitRequestId()), never());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception");
        }
    }

    private void assertEvent(EventDto eventDto, boolean expectedTrigger, Integer expectedDsmKitRequestId, String expectedDdpParticipantId,
                             Integer ddpInstanceId, String eventType) {
        Assert.assertEquals(expectedTrigger, eventDto.getEventTriggered());
        Assert.assertEquals(expectedDsmKitRequestId, eventDto.getDsmKitRequestId());
        Assert.assertEquals(expectedDdpParticipantId, eventDto.getDdpParticipantId());
        Assert.assertEquals((long) ddpInstanceId, eventDto.getDdpInstanceId());
        Assert.assertEquals(eventType, eventDto.getEventType());
    }
}
