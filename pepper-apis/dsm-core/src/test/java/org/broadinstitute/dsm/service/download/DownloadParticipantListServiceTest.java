package org.broadinstitute.dsm.service.download;

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
    private static String guid = "PT_SAMPLE_QUEUE_TEST";
    private static ParticipantDto participantDto = null;
    private static final String shortId = "PT_SHORT";

    @BeforeClass
    public static void doFirst() {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/lmsMappings.json", null);
        ddpInstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(instanceName, esIndex);
        guid = TestParticipantUtil.genDDPParticipantId(guid);
        participantDto = TestParticipantUtil.createParticipant(guid, ddpInstanceDto.getDdpInstanceId());
        ElasticTestUtil.createParticipant(esIndex, participantDto);
        ElasticTestUtil.addParticipantProfileFromTemplate(esIndex,  guid, shortId, "testDataDownloadFromElastic",
                "lastName", "email");
        ElasticTestUtil.addDsmEntityFromFile(esIndex, "elastic/dsmKitRequestShipping.json", guid, "1990-10-10", null);
        log.debug("ES participant record with DSM for {}: {}", guid,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, guid));
    }

    @AfterClass
    public static void cleanUp() {
        TestParticipantUtil.deleteParticipant(participantDto.getParticipantId().orElseThrow());
        DdpInstanceGroupTestUtil.deleteInstance(ddpInstanceDto);
        ElasticTestUtil.deleteIndex(esIndex);
    }

    @Test
    public void testKitRequestShippingDownloadFromElastic() {
        ManualFilterParticipantList filterable = getFilterFromFile("elastic/filterWithSampleQueueColumn.json");
        Assert.assertNotNull(filterable);
        QueryParamsMap queryParamsMap = buildMockQueryParams(true, true, "xlsx");
        List<ParticipantWrapperDto> downloadList = DownloadParticipantListService.fetchParticipantEsData(filterable, queryParamsMap);
        assertKitRequestShipping(downloadList);

        queryParamsMap = buildMockQueryParams(false, true, "xlsx");
        downloadList = DownloadParticipantListService.fetchParticipantEsData(filterable, queryParamsMap);
        assertKitRequestShipping(downloadList);

        queryParamsMap = buildMockQueryParams(true, false, "xlsx");
        downloadList = DownloadParticipantListService.fetchParticipantEsData(filterable, queryParamsMap);
        assertKitRequestShipping(downloadList);

        queryParamsMap = buildMockQueryParams(false, false, "xlsx");
        downloadList = DownloadParticipantListService.fetchParticipantEsData(filterable, queryParamsMap);
        assertKitRequestShipping(downloadList);

        queryParamsMap = buildMockQueryParams(true, true, "tsv");
        downloadList = DownloadParticipantListService.fetchParticipantEsData(filterable, queryParamsMap);
        assertKitRequestShipping(downloadList);

        queryParamsMap = buildMockQueryParams(false, true, "tsv");
        downloadList = DownloadParticipantListService.fetchParticipantEsData(filterable, queryParamsMap);
        assertKitRequestShipping(downloadList);

        queryParamsMap = buildMockQueryParams(true, false, "tsv");
        downloadList = DownloadParticipantListService.fetchParticipantEsData(filterable, queryParamsMap);
        assertKitRequestShipping(downloadList);

        queryParamsMap = buildMockQueryParams(false, false, "tsv");
        downloadList = DownloadParticipantListService.fetchParticipantEsData(filterable, queryParamsMap);
        assertKitRequestShipping(downloadList);
    }

    @Test
    public void testClinicalOrdersDownloadFromElastic() {
        ManualFilterParticipantList filterable = getFilterFromFile("elastic/filtersWithClinicalOrderColumns.json");
        Assert.assertNotNull(filterable);
        QueryParamsMap queryParamsMap = buildMockQueryParams(true, true, "xlsx");
        List<ParticipantWrapperDto> downloadList = DownloadParticipantListService.fetchParticipantEsData(filterable, queryParamsMap);
        assertClinicalOrders(downloadList);

        queryParamsMap = buildMockQueryParams(false, true, "xlsx");
        downloadList = DownloadParticipantListService.fetchParticipantEsData(filterable, queryParamsMap);
        assertClinicalOrders(downloadList);

        queryParamsMap = buildMockQueryParams(true, false, "xlsx");
        downloadList = DownloadParticipantListService.fetchParticipantEsData(filterable, queryParamsMap);
        assertClinicalOrders(downloadList);

        queryParamsMap = buildMockQueryParams(false, false, "xlsx");
        downloadList = DownloadParticipantListService.fetchParticipantEsData(filterable, queryParamsMap);
        assertClinicalOrders(downloadList);

        queryParamsMap = buildMockQueryParams(true, true, "tsv");
        downloadList = DownloadParticipantListService.fetchParticipantEsData(filterable, queryParamsMap);
        assertClinicalOrders(downloadList);

        queryParamsMap = buildMockQueryParams(false, true, "tsv");
        downloadList = DownloadParticipantListService.fetchParticipantEsData(filterable, queryParamsMap);
        assertClinicalOrders(downloadList);

        queryParamsMap = buildMockQueryParams(true, false, "tsv");
        downloadList = DownloadParticipantListService.fetchParticipantEsData(filterable, queryParamsMap);
        assertClinicalOrders(downloadList);

        queryParamsMap = buildMockQueryParams(false, false, "tsv");
        downloadList = DownloadParticipantListService.fetchParticipantEsData(filterable, queryParamsMap);
        assertClinicalOrders(downloadList);
    }

    private void assertKitRequestShipping(List<ParticipantWrapperDto> downloadList) {

        List<Object> kitRequestShippings = ((List<Object>) ((Map<String, Object>)
                ((Map<String, Object>) downloadList.get(0).getEsDataAsMap().get("dataAsMap")).get("dsm")).get("kitRequestShipping"));
        assertNotNull(kitRequestShippings);
        assertEquals(5, kitRequestShippings.size());
        assertSampleQueueValue(kitRequestShippings);
    }

    private void assertClinicalOrders(List<ParticipantWrapperDto> downloadList) {
        assertNotNull(downloadList);
        assertEquals(1, downloadList.size());
        List<Object> clinicalOrders = ((List<Object>) ((Map<String, Object>)
                ((Map<String, Object>) downloadList.get(0).getEsDataAsMap().get("dataAsMap")).get("dsm")).get("clinicalOrder"));
        assertNotNull(clinicalOrders);
        assertEquals(1, clinicalOrders.size());
        assertClinicalOrderValue(clinicalOrders);
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

    private void assertClinicalOrderValue(List<Object> clinicalOrders) {
        clinicalOrders.forEach(clinicalOrder -> {
            Map<String, Object> clinicalOrdersMap = (Map<String, Object>) clinicalOrder;
            assertNotNull(clinicalOrdersMap.get("orderId"));
            assertEquals("SOME_ORDER_ID", clinicalOrdersMap.get("orderId"));
            assertEquals("APPROVED", clinicalOrdersMap.get("orderStatus"));
            assertEquals(1711539726740L, clinicalOrdersMap.get("orderDate"));
            assertNotNull(clinicalOrdersMap.get("ddpParticipantId"));
            assertEquals(guid, clinicalOrdersMap.get("ddpParticipantId"));
            assertNotNull(clinicalOrdersMap.get("barcode"));
            assertEquals("kit-123456-7", clinicalOrdersMap.get("barcode"));
            assertEquals("PDO-123456", clinicalOrdersMap.get("mercuryPdoId"));
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

    private ManualFilterParticipantList getFilterFromFile(String fileName) {
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
