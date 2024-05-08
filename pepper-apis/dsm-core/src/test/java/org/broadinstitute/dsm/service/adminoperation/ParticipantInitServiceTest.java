package org.broadinstitute.dsm.service.adminoperation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.service.elastic.ElasticSearchService;
import org.broadinstitute.dsm.service.participantdata.RgpParticipantDataService;
import org.broadinstitute.dsm.service.participantdata.RgpParticipantDataTestUtil;
import org.broadinstitute.dsm.service.participantdata.TestFamilyIdProvider;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class ParticipantInitServiceTest extends DbAndElasticBaseTest {
    private static final ElasticSearchService elasticSearchService = new ElasticSearchService();
    private static final String instanceName = "rgpinit";
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private static DDPInstance ddpInstance;
    private static final List<ParticipantDto> participants = new ArrayList<>();
    private static int participantCounter = 0;
    private static RgpParticipantDataTestUtil rgpParticipantDataTestUtil;

    @BeforeClass
    public static void setup() throws Exception {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/atcpMappings.json",
                "elastic/atcpSettings.json");
        ddpInstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(instanceName, esIndex);
        ddpInstance = DDPInstance.getDDPInstanceById(ddpInstanceDto.getDdpInstanceId());
        rgpParticipantDataTestUtil = new RgpParticipantDataTestUtil(esIndex);
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
        rgpParticipantDataTestUtil.tearDown();
    }

    @Test
    public void testInitParticipant() {
        // make ptp with some ES DSM data
        ParticipantDto participant = createParticipant();
        initParticipant(participant.getRequiredDdpParticipantId());
    }

    @Test
    public void testInitParticipantOnlyProfile() {
        // make ptp with ES profile and no participant ES DSM data
        initParticipant(TestParticipantUtil.createMinimalParticipant(ddpInstanceDto, participantCounter++));
    }

    @Test
    public void testInitParticipantWithFamilyIdAndRgpData() {
        // make ptp with family ID and RGP data
        ParticipantDto participant = createParticipant();
        String ddpParticipantId = participant.getRequiredDdpParticipantId();

        // this dictates which default values and workflows are set
        rgpParticipantDataTestUtil.loadFieldSettings(ddpInstanceDto.getDdpInstanceId());

        ElasticSearchParticipantDto esParticipantDto =
                elasticSearchService.getRequiredParticipantDocument(ddpParticipantId, esIndex);

        // create RGP participant data
        int familyId = 100;
        RgpParticipantDataService.createDefaultData(ddpParticipantId, esParticipantDto, ddpInstance,
                new TestFamilyIdProvider(familyId));

        esParticipantDto = elasticSearchService.getRequiredParticipantDocument(ddpParticipantId, esIndex);
        UpdateLog updateLog = ParticipantInitService.initParticipant(ddpParticipantId, esParticipantDto, ddpInstance,
                new TestFamilyIdProvider(100), false);
        Assert.assertEquals(UpdateLog.UpdateStatus.NOT_UPDATED, updateLog.getStatus());
    }

    @Test
    public void testInitParticipantWithFamilyId() {
        // make ptp with family ID and no RGP data
        ParticipantDto participant = createParticipant();
        String ddpParticipantId = participant.getRequiredDdpParticipantId();
        Dsm dsm = new Dsm();
        dsm.setFamilyId("100");
        ElasticTestUtil.addParticipantDsm(esIndex, dsm, ddpParticipantId);

        ElasticSearchParticipantDto esParticipantDto =
                elasticSearchService.getRequiredParticipantDocument(ddpParticipantId, esIndex);

        UpdateLog updateLog = ParticipantInitService.initParticipant(ddpParticipantId, esParticipantDto, ddpInstance,
                new TestFamilyIdProvider(100), false);

        Assert.assertEquals(UpdateLog.UpdateStatus.ERROR, updateLog.getStatus());
        Assert.assertTrue(updateLog.getMessage().contains("Participant has family ID in ES but no RGP data"));
    }

    @Test
    public void testInitParticipantWithRgpData() {
        // make ptp with RGP data but no family id in ES
        ParticipantDto participant = createParticipant();
        String ddpParticipantId = participant.getRequiredDdpParticipantId();

        // this dictates which default values and workflows are set
        rgpParticipantDataTestUtil.loadFieldSettings(ddpInstanceDto.getDdpInstanceId());

        ElasticSearchParticipantDto esParticipantDto =
                elasticSearchService.getRequiredParticipantDocument(ddpParticipantId, esIndex);

        // create RGP participant data
        int familyId = 1000;
        RgpParticipantDataService.createDefaultData(ddpParticipantId, esParticipantDto, ddpInstance,
                new TestFamilyIdProvider(familyId));
        removeEsFamilyId(esIndex, ddpParticipantId);

        esParticipantDto = elasticSearchService.getRequiredParticipantDocument(ddpParticipantId, esIndex);
        UpdateLog updateLog = ParticipantInitService.initParticipant(ddpParticipantId, esParticipantDto, ddpInstance,
                new TestFamilyIdProvider(familyId), false);
        Assert.assertEquals(UpdateLog.UpdateStatus.ES_UPDATED, updateLog.getStatus());
        rgpParticipantDataTestUtil.verifyDefaultElasticData(ddpParticipantId, familyId, Collections.emptyMap());
    }

    @Test
    public void testInitParticipantWithLegacyPid() {
        Pair<ParticipantDto, String> ptpToLegacyId =
                TestParticipantUtil.createLegacyParticipant(instanceName, participantCounter++, ddpInstanceDto, null, null, null);
        participants.add(ptpToLegacyId.getLeft());
        String ddpParticipantId = ptpToLegacyId.getLeft().getRequiredDdpParticipantId();
        String legacyPid = ptpToLegacyId.getRight();

        ElasticSearchParticipantDto esParticipantDto =
                elasticSearchService.getRequiredParticipantDocument(ddpParticipantId, esIndex);
        UpdateLog updateLog = ParticipantInitService.initParticipant(legacyPid, esParticipantDto, ddpInstance,
                new TestFamilyIdProvider(100), false);
        Assert.assertEquals(UpdateLog.UpdateStatus.NOT_UPDATED, updateLog.getStatus());
        Assert.assertTrue(updateLog.getMessage().contains("Legacy participant, skipping"));
    }

    private void initParticipant(String ddpParticipantId) {
        // this dictates which default values and workflows are set
        rgpParticipantDataTestUtil.loadFieldSettings(ddpInstanceDto.getDdpInstanceId());

        ElasticSearchParticipantDto esParticipantDto =
                elasticSearchService.getRequiredParticipantDocument(ddpParticipantId, esIndex);

        int familyId = 100;
        UpdateLog updateLog = ParticipantInitService.initParticipant(ddpParticipantId, esParticipantDto, ddpInstance,
                new TestFamilyIdProvider(familyId), false);
        Assert.assertEquals(UpdateLog.UpdateStatus.UPDATED, updateLog.getStatus());
        rgpParticipantDataTestUtil.verifyDefaultData(ddpParticipantId, familyId);
    }

    private ParticipantDto createParticipant() {
        String baseName = String.format("%s_%d", instanceName, participantCounter++);
        ParticipantDto participant =
                TestParticipantUtil.createParticipantWithEsProfile(baseName, ddpInstanceDto);
        participants.add(participant);
        return participant;
    }

    private static void removeEsFamilyId(String esIndex, String ddpParticipantId) {
        try {
            Map<String, Object> esMap =
                    ElasticSearchUtil.getObjectsMap(esIndex, ddpParticipantId, ESObjectConstants.DSM);
            Map<String, Object> dsmMap = (Map<String, Object>) esMap.get(ESObjectConstants.DSM);
            dsmMap.put(ESObjectConstants.FAMILY_ID, null);
            ElasticSearchUtil.updateRequest(ddpParticipantId, esIndex, esMap);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Error removing family ID from ES: " + e);
        }
    }
}
