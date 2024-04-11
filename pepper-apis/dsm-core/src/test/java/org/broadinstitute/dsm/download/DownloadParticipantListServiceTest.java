package org.broadinstitute.dsm.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.model.filter.participant.ManualFilterParticipantList;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperDto;
import org.broadinstitute.dsm.service.download.DownloadParticipantListService;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.broadinstitute.dsm.util.TestUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import spark.QueryParamsMap;

@Slf4j
public class DownloadParticipantListServiceTest extends DbAndElasticBaseTest {

    private static final String instanceName = "download_test_instance";
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private static final String guid = "PT_SAMPLE_QUEUE_TEST";
    private static ParticipantDto participantDto = null;
    private static final String shortId = "PT_SHORT";

    @BeforeClass
    public static void doFirst() {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/lmsMappings.json", null);
        ddpInstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(instanceName, esIndex);
        String ddpParticipantId = TestParticipantUtil.genDDPParticipantId(guid);
        participantDto = TestParticipantUtil.createParticipant(ddpParticipantId, ddpInstanceDto.getDdpInstanceId());
        ElasticTestUtil.createParticipant(esIndex, participantDto);
        ElasticTestUtil.addParticipantProfileFromTemplate(esIndex,  ddpParticipantId, shortId, "testDataDownloadFromElastic",
                "lastName", "email");
        ElasticTestUtil.addDsmEntityFromFile(esIndex, "elastic/dsmKitRequestShipping.json", ddpParticipantId, "1990-10-10", null);
        log.debug("ES participant record with DSM for {}: {}", ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));
    }

    @AfterClass
    public static void cleanUp() {
        TestParticipantUtil.deleteParticipant(participantDto.getParticipantId().orElseThrow());
        DdpInstanceGroupTestUtil.deleteInstance(ddpInstanceDto);
        ElasticTestUtil.deleteIndex(esIndex);
    }

    @Test
    public void testDataDownloadFromElastic() {
        ManualFilterParticipantList filterable = getFilterFromFile("elastic/filterWithSampleQueueColumn.json");
        Assert.assertNotNull(filterable);
        QueryParamsMap queryParamsMap = buildMockQueryParams(true, true, "xlsx");
        List<ParticipantWrapperDto> downloadList = DownloadParticipantListService.fetchParticipantEsData(filterable, queryParamsMap);
        assertDownloadList(downloadList);

        queryParamsMap = buildMockQueryParams(false, true, "xlsx");
        downloadList = DownloadParticipantListService.fetchParticipantEsData(filterable, queryParamsMap);
        assertDownloadList(downloadList);

        queryParamsMap = buildMockQueryParams(true, false, "xlsx");
        downloadList = DownloadParticipantListService.fetchParticipantEsData(filterable, queryParamsMap);
        assertDownloadList(downloadList);

        queryParamsMap = buildMockQueryParams(false, false, "xlsx");
        downloadList = DownloadParticipantListService.fetchParticipantEsData(filterable, queryParamsMap);
        assertDownloadList(downloadList);

        queryParamsMap = buildMockQueryParams(true, true, "tsv");
        downloadList = DownloadParticipantListService.fetchParticipantEsData(filterable, queryParamsMap);
        assertDownloadList(downloadList);

        queryParamsMap = buildMockQueryParams(false, true, "tsv");
        downloadList = DownloadParticipantListService.fetchParticipantEsData(filterable, queryParamsMap);
        assertDownloadList(downloadList);

        queryParamsMap = buildMockQueryParams(true, false, "tsv");
        downloadList = DownloadParticipantListService.fetchParticipantEsData(filterable, queryParamsMap);
        assertDownloadList(downloadList);

        queryParamsMap = buildMockQueryParams(false, false, "tsv");
        downloadList = DownloadParticipantListService.fetchParticipantEsData(filterable, queryParamsMap);
        assertDownloadList(downloadList);
    }

    private void assertDownloadList(List<ParticipantWrapperDto> downloadList) {
        assertNotNull(downloadList);
        assertEquals(1, downloadList.size());
        List<Object> kitRequestShippings = ((List<Object>) ((Map<String, Object>)
                ((Map<String, Object>) downloadList.get(0).getEsDataAsMap().get("dataAsMap")).get("dsm")).get("kitRequestShipping"));
        assertNotNull(kitRequestShippings);
        assertEquals(5, kitRequestShippings.size());
        assertSampleQueueValue(kitRequestShippings);
    }

    private void assertSampleQueueValue(List<Object> kitRequestShippings) {
        kitRequestShippings.forEach(kitRequestShipping -> {
            Map<String, Object> kitRequestShippingMap = (Map<String, Object>) kitRequestShipping;
            assertNotNull(kitRequestShippingMap.get("sampleQueue"));
            assertNotEquals("", kitRequestShippingMap.get("sampleQueue"));
            if (kitRequestShippingMap.get("ddpLabel").equals("deactivatedKit")) {
                assertEquals("Deactivated", kitRequestShippingMap.get("sampleQueue"));
            } else if (kitRequestShippingMap.get("ddpLabel").equals("receivedKit")) {
                assertEquals("Received", kitRequestShippingMap.get("sampleQueue"));
            } else if (kitRequestShippingMap.get("ddpLabel").equals("errorKit")) {
                assertEquals("GP manual Label", kitRequestShippingMap.get("sampleQueue"));
            } else if (kitRequestShippingMap.get("ddpLabel").equals("newKit")) {
                assertEquals("Waiting on GP", kitRequestShippingMap.get("sampleQueue"));
            } else if (kitRequestShippingMap.get("ddpLabel").equals("shippedKit")) {
                assertEquals("Shipped", kitRequestShippingMap.get("sampleQueue"));
            } else {
                Assert.fail();
            }
        });
    }

    private QueryParamsMap buildMockQueryParams(boolean onlyMostRecent, boolean humanReadable, String fileFormat) {
        QueryParamsMap mainQueryParamsMap = mock(QueryParamsMap.class);

        // Mocking the nested QueryParamsMap
        QueryParamsMap nestedParentQueryParamsMap = mock(QueryParamsMap.class);
        when(mainQueryParamsMap.get("parent")).thenReturn(nestedParentQueryParamsMap);
        when(nestedParentQueryParamsMap.value()).thenReturn("participantList");

        QueryParamsMap nestedOnlyMostRecentQueryParamsMap = mock(QueryParamsMap.class);
        when(mainQueryParamsMap.get("onlyMostRecent")).thenReturn(nestedOnlyMostRecentQueryParamsMap);
        when(nestedOnlyMostRecentQueryParamsMap.value()).thenReturn(String.valueOf(onlyMostRecent));

        QueryParamsMap nestedRealmQueryParamsMap = mock(QueryParamsMap.class);
        when(mainQueryParamsMap.get("realm")).thenReturn(nestedRealmQueryParamsMap);
        when(nestedRealmQueryParamsMap.value()).thenReturn(ddpInstanceDto.getInstanceName());

        QueryParamsMap nestedHumanReadableQueryParamsMap = mock(QueryParamsMap.class);
        when(mainQueryParamsMap.get("humanReadable")).thenReturn(nestedHumanReadableQueryParamsMap);
        when(nestedHumanReadableQueryParamsMap.value()).thenReturn(String.valueOf(humanReadable));

        QueryParamsMap nestedUserIdQueryParamsMap = mock(QueryParamsMap.class);
        when(mainQueryParamsMap.get("userId")).thenReturn(nestedUserIdQueryParamsMap);
        when(nestedUserIdQueryParamsMap.value()).thenReturn("6");

        QueryParamsMap nestedFileFormatQueryParamsMap = mock(QueryParamsMap.class);
        when(mainQueryParamsMap.get("fileFormat")).thenReturn(nestedFileFormatQueryParamsMap);
        when(nestedFileFormatQueryParamsMap.value()).thenReturn(fileFormat);

        QueryParamsMap nestedFiltersQueryParamsMap = mock(QueryParamsMap.class);
        when(mainQueryParamsMap.get("filters")).thenReturn(nestedFiltersQueryParamsMap);
        when(nestedFiltersQueryParamsMap.value()).thenReturn("[]");

        return mainQueryParamsMap;
    }

    private ManualFilterParticipantList getFilterFromFile(String fileName){
        try {
            String filterJson = TestUtil.readFile(fileName);
            filterJson = filterJson.replace(":shortId", shortId);
            ManualFilterParticipantList filterable = new Gson().fromJson(filterJson, ManualFilterParticipantList.class);
            return filterable;
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Failed to read filter from file " + fileName);
        }
        return null;
    }
}
