package org.broadinstitute.dsm.model.at;

import static org.broadinstitute.dsm.model.at.SearchKitRequest.SEARCH_MF_BAR;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.service.elastic.ElasticSearchService;
import org.broadinstitute.dsm.service.participantdata.ParticipantDataService;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.FieldSettingsTestUtil;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.broadinstitute.dsm.util.ParticipantDataTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

@Slf4j
public class ReceiveKitRequestTest extends DbAndElasticBaseTest {
    private static final ElasticSearchService elasticSearchService = new ElasticSearchService();
    private static final String TEST_USER = "TEST_USER";
    private static final String KIT_BARCODE = "SM-ABC123";
    private static final String instanceName = "atkit";
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private static final List<ParticipantDto> participants = new ArrayList<>();
    private static int participantCounter = 0;
    private static final List<Integer> fieldSettingsIds = new ArrayList<>();

    @BeforeClass
    public static void setup() throws Exception {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/atcpMappings.json",
                "elastic/atcpSettings.json");
        ddpInstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(instanceName, esIndex);
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
    public void testReceiveATKitRequest() {
        ParticipantDto participant = createParticipant();
        String ddpParticipantId = participant.getDdpParticipantIdOrThrow();
        createParticipantData(ddpParticipantId);

        NotificationUtil mockNotificationUtil = Mockito.mock(NotificationUtil.class);
        doNothing().when(mockNotificationUtil).sentNotification(anyString(), anyString(), anyString(), anyString());

        // no participant data for kits
        boolean updated = ReceiveKitRequest.receiveATKitRequest("bogus", ddpInstanceDto, mockNotificationUtil);
        Assert.assertFalse(updated);

        // add participant kit data and try again with bad mfBarcode
        createParticipantKitData(ddpParticipantId);
        updated = ReceiveKitRequest.receiveATKitRequest("bogus", ddpInstanceDto, mockNotificationUtil);
        Assert.assertFalse(updated);

        verifyParticipantData(ddpParticipantId, false);

        // test search, since that's the user journey
        DDPInstance ddpInstance = DDPInstance.getDDPInstance(ddpInstanceDto.getInstanceName());
        ElasticTestUtil.addActivitiesFromFile(esIndex, "elastic/atcpActivities.json", ddpParticipantId);

        List<KitRequestShipping> kitRequests =
                SearchKitRequest.findATKitRequest(SEARCH_MF_BAR,  KIT_BARCODE,  ddpInstance);
        Assert.assertEquals(1, kitRequests.size());
        verifyKitRequest(kitRequests.get(0), ddpParticipantId, false);

        // try again with good mfBarcode
        updated = ReceiveKitRequest.receiveATKitRequest(KIT_BARCODE, ddpInstanceDto, mockNotificationUtil);
        Assert.assertTrue(updated);

        verifyParticipantData(ddpParticipantId, true);
        verifyElasticData(ddpParticipantId);

        // search should now have a receive date
        kitRequests = SearchKitRequest.findATKitRequest(SEARCH_MF_BAR,  KIT_BARCODE,  ddpInstance);
        Assert.assertEquals(1, kitRequests.size());
        verifyKitRequest(kitRequests.get(0), ddpParticipantId, true);
    }

    private ParticipantDto createParticipant() {
        String baseName = String.format("%s_%d", instanceName, participantCounter++);
        String ddpParticipantId = TestParticipantUtil.genDDPParticipantId(baseName);
        ParticipantDto participant = TestParticipantUtil.createParticipant(ddpParticipantId, ddpInstanceDto.getDdpInstanceId());
        participants.add(participant);

        ElasticTestUtil.createParticipant(esIndex, participant);
        ElasticTestUtil.addParticipantProfileFromFile(esIndex, "elastic/participantProfile.json",
                ddpParticipantId);
        return participant;

    }

    private void createParticipantData(String ddpParticipantId) {
        int instanceId = ddpInstanceDto.getDdpInstanceId();

        // create random participant data
        Map<String, String> dataMap = new HashMap<>();
        dataMap.put("REGISTRATION_TYPE", "Self");
        ParticipantDataTestUtil.createParticipantData(ddpParticipantId,
                dataMap, "AT_GROUP_MISCELLANEOUS", instanceId, TEST_USER);

        dataMap.clear();
        dataMap.put("ELIGIBILITY", "1");
        ParticipantDataTestUtil.createParticipantData(ddpParticipantId,
                dataMap, "AT_GROUP_ELIGIBILITY", instanceId, TEST_USER);

        ParticipantDataService.updateEsParticipantData(ddpParticipantId,
                DDPInstance.getDDPInstance(ddpInstanceDto.getInstanceName()));
    }

    private void createParticipantKitData(String ddpParticipantId) {
        int instanceId = ddpInstanceDto.getDdpInstanceId();
        Map<String, String> dataMap = new HashMap<>();
        dataMap.put("GENOME_STUDY_CPT_ID", "DDP_ATCP_123");
        dataMap.put("GENOME_STUDY_CONSENT", "1");
        dataMap.put("GENOME_STUDY_DATE_CONSENTED", "2016-07-27T08:58:00");
        dataMap.put(ReceiveKitRequest.GENOME_STUDY_STATUS,
                Integer.toString(ReceiveKitRequest.ATKitShipStatus.SENT.getValue()));
        dataMap.put("GENOME_STUDY_SPIT_KIT_BARCODE", KIT_BARCODE);
        dataMap.put("GENOME_STUDY_KIT_TRACKING_NUMBER", "987654321");
        dataMap.put("GENOME_STUDY_DATE_SHIPPED", "2016-08-19T08:58:00");
        dataMap.put(ReceiveKitRequest.GENOME_STUDY_DATE_RECEIVED, null);

        ParticipantDataTestUtil.createParticipantData(ddpParticipantId,
                dataMap, "AT_GROUP_GENOME_STUDY", instanceId, TEST_USER);

        ParticipantDataService.updateEsParticipantData(ddpParticipantId,
                DDPInstance.getDDPInstance(ddpInstanceDto.getInstanceName()));
    }

    private void verifyParticipantData(String ddpParticipantId, boolean hasReceiveDate) {
        ParticipantDataDao dataDao = new ParticipantDataDao();
        List<ParticipantData> ptpDataList = dataDao.getParticipantData(ddpParticipantId);
        Assert.assertEquals(3, ptpDataList.size());

        Optional<ParticipantData> participantData = ptpDataList.stream()
                .filter(ptpData -> ptpData.getRequiredFieldTypeId().equals("AT_GROUP_GENOME_STUDY")).findAny();
        Assert.assertTrue(participantData.isPresent());
        Map<String, String> dataMap = participantData.get().getDataMap();

        Assert.assertEquals(KIT_BARCODE, dataMap.get("GENOME_STUDY_SPIT_KIT_BARCODE"));

        String receivedDate = dataMap.get(ReceiveKitRequest.GENOME_STUDY_DATE_RECEIVED);
        if (hasReceiveDate) {
            Assert.assertTrue(receivedDate != null && !receivedDate.isEmpty());
            Assert.assertEquals(Integer.toString(ReceiveKitRequest.ATKitShipStatus.RECEIVED.getValue()),
                    dataMap.get(ReceiveKitRequest.GENOME_STUDY_STATUS));
        } else {
            Assert.assertNull(receivedDate);
            Assert.assertEquals(Integer.toString(ReceiveKitRequest.ATKitShipStatus.SENT.getValue()),
                    dataMap.get(ReceiveKitRequest.GENOME_STUDY_STATUS));
        }
    }

    private void verifyElasticData(String ddpParticipantId) {
        ElasticSearchParticipantDto esParticipant =
                elasticSearchService.getRequiredParticipantDocumentById(ddpParticipantId, esIndex);
        log.debug("Verifying ES participant record for {}: {}", ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));
        Dsm dsm = esParticipant.getDsm().orElseThrow();

        List<ParticipantData> participantDataList = dsm.getParticipantData();
        Assert.assertEquals(3, participantDataList.size());

        Optional<ParticipantData> participantData = participantDataList.stream()
                .filter(ptpData -> ptpData.getRequiredFieldTypeId().equals("AT_GROUP_GENOME_STUDY")).findAny();
        Assert.assertTrue(participantData.isPresent());
        ParticipantData ptpData = participantData.get();
        Map<String, String> dataMap = ptpData.getDataMap();

        Assert.assertEquals(KIT_BARCODE, dataMap.get("GENOME_STUDY_SPIT_KIT_BARCODE"));
        Assert.assertEquals(Integer.toString(ReceiveKitRequest.ATKitShipStatus.RECEIVED.getValue()),
                dataMap.get(ReceiveKitRequest.GENOME_STUDY_STATUS));
        String receivedDate = dataMap.get(ReceiveKitRequest.GENOME_STUDY_DATE_RECEIVED);
        Assert.assertTrue(receivedDate != null && !receivedDate.isEmpty());
    }

    private static void verifyKitRequest(KitRequestShipping kitRequest, String ddpParticipantId,
                                         boolean hasReceiveDate) {
        Assert.assertEquals(ddpParticipantId, kitRequest.getParticipantId());
        Assert.assertEquals(KIT_BARCODE, kitRequest.getKitLabel());
        // these values come from the test profile and activities files loaded into the ptp ES document
        Assert.assertEquals("M", kitRequest.getGender());
        Assert.assertEquals("PLYENU", kitRequest.getHruid());
        String receiveDate = kitRequest.getReceiveDateString();
        if (hasReceiveDate) {
            Assert.assertTrue(receiveDate != null && !receiveDate.isEmpty());
        } else {
            Assert.assertNull(receiveDate);
        }
    }
}
