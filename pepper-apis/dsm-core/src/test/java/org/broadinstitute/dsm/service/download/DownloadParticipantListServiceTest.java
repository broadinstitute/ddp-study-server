package org.broadinstitute.dsm.service.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.elastic.export.tabular.ModuleExportConfig;
import org.broadinstitute.dsm.model.elastic.export.tabular.TabularParticipantExporter;
import org.broadinstitute.dsm.model.elastic.export.tabular.TabularParticipantParser;
import org.broadinstitute.dsm.model.elastic.search.UnparsedESParticipantDto;
import org.broadinstitute.dsm.model.filter.participant.ManualFilterParticipantList;
import org.broadinstitute.dsm.model.participant.DownloadParticipantListParams;
import org.broadinstitute.dsm.model.participant.DownloadParticipantListPayload;
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
    private static final String shortId = "PT_SHORT";
    private static final String FIRST_NAME = "testDataDownloadFromElastic";
    private static final String LAST_NAME = "lastName";
    private static final String AY_RACE_DOWNLOAD_FILE = "elastic/participantListAboutYouRaceDownload.json";
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private static String ddpParticipantId = "PT_DOWNLOAD_TEST";
    private static ParticipantDto participantDto = null;

    @BeforeClass
    public static void doFirst() {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/lmsMappings.json", null);
        ddpInstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(instanceName, esIndex);
        ddpParticipantId = TestParticipantUtil.genDDPParticipantId(ddpParticipantId);
        participantDto = TestParticipantUtil.createParticipant(ddpParticipantId, ddpInstanceDto.getDdpInstanceId());
        ElasticTestUtil.createParticipant(esIndex, participantDto);
        ElasticTestUtil.addParticipantProfileFromTemplate(esIndex, ddpParticipantId, shortId, FIRST_NAME,
                LAST_NAME, "email");
        ElasticTestUtil.addDsmEntityFromFile(esIndex, "elastic/dsmKitRequestShippingClinicalOrders.json", ddpParticipantId, "1990-10-10",
                null);
        ElasticTestUtil.addActivitiesFromFile(esIndex, "elastic/lmsAboutYouActivity.json", ddpParticipantId);
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
        List<Filter> columnNames = getColumnNames("elastic/filtersWithClinicalOrderColumns.json");
        Assert.assertNotNull(filterable);
        Assert.assertNotNull(columnNames);
        Assert.assertNotEquals(0, columnNames.size());
        QueryParamsMap queryParamsMap = buildMockQueryParams(true, true, "xlsx");
        List<ParticipantWrapperDto> downloadList = DownloadParticipantListService.fetchParticipantEsData(filterable, queryParamsMap);
        DownloadParticipantListParams downloadParticipantListParams = new DownloadParticipantListParams(queryParamsMap);

        TabularParticipantParser parser = new TabularParticipantParser(columnNames, ddpInstanceDto,
                downloadParticipantListParams.isHumanReadable(), downloadParticipantListParams.isOnlyMostRecent(), null);
        List<ModuleExportConfig> exportConfigs = parser.generateExportConfigs();
        List<Map<String, Object>> participantEsDataMaps = downloadList.stream().map(dto ->
                ((UnparsedESParticipantDto) dto.getEsData()).getDataAsMap()).toList();
        List<Map<String, String>> participantValueMaps = parser.parse(exportConfigs, participantEsDataMaps);
        TabularParticipantExporter participantExporter =
                TabularParticipantExporter.getExporter(exportConfigs, participantValueMaps, downloadParticipantListParams.getFileFormat());
        assertNotNull(participantExporter);
        assertNotNull(participantExporter.getExportFilename());
        assertParticipantExporterMap(participantExporter);

    }

    @Test
    public void testParticipantRaceDownloadFromElastic() {
        ManualFilterParticipantList filterable = getFilterFromFile(AY_RACE_DOWNLOAD_FILE);
        List<Filter> columnNames = getColumnNames(AY_RACE_DOWNLOAD_FILE);
        Assert.assertNotNull(filterable);
        Assert.assertNotNull(columnNames);
        Assert.assertNotEquals(0, columnNames.size());

        QueryParamsMap queryParamsMap = buildMockQueryParams(false, true, "tsv");
        List<ParticipantWrapperDto> downloadList = DownloadParticipantListService.fetchParticipantEsData(filterable, queryParamsMap);
        DownloadParticipantListParams downloadParticipantListParams = new DownloadParticipantListParams(queryParamsMap);

        //load activity definition
        String activityDefJson = null;
        try {
            activityDefJson = TestUtil.readFile("elastic/lmsAboutYouActivityDef.json");
        } catch (Exception e) {
            Assert.fail("Failed to read activity definition file " + e.getMessage());
        }
        Map<String, Object> activityDef = new Gson().fromJson(activityDefJson, Map.class);
        Map<String, Map<String, Object>> lmsActivityDefs = Map.of("LMS_ACTIVITY_DEFS", activityDef);

        TabularParticipantParser parser = new TabularParticipantParser(columnNames, ddpInstanceDto,
                downloadParticipantListParams.isHumanReadable(), downloadParticipantListParams.isOnlyMostRecent(), lmsActivityDefs);
        List<ModuleExportConfig> exportConfigs = parser.generateExportConfigs();
        List<Map<String, Object>> participantEsDataMaps = downloadList.stream().map(dto ->
                ((UnparsedESParticipantDto) dto.getEsData()).getDataAsMap()).toList();
        List<Map<String, String>> participantValueMaps = parser.parse(exportConfigs, participantEsDataMaps);
        TabularParticipantExporter participantExporter =
                TabularParticipantExporter.getExporter(exportConfigs, participantValueMaps, downloadParticipantListParams.getFileFormat());
        assertNotNull(participantExporter);
        assertNotNull(participantExporter.getExportFilename());
        assertParticipantRaceExporterMap(participantExporter);
    }

    private void assertParticipantRaceExporterMap(TabularParticipantExporter participantExporter) {
        assertEquals(1, participantExporter.participantValueMaps.size());
        Map<String, String> participantValues = participantExporter.participantValueMaps.get(0);
        assertNotNull(participantValues);
        assertEquals(shortId, participantValues.get("PROFILE.HRUID"));
        assertEquals(FIRST_NAME, participantValues.get("PROFILE.FIRSTNAME"));
        assertEquals(LAST_NAME, participantValues.get("PROFILE.LASTNAME"));
        assertEquals("Hispanic, Latino, or Spanish (For example: Colombian, Cuban, Dominican, Mexican or Mexican American, Puerto Rican, Salvadoran, etc.), None of these fully describe my child/me, HISPANIC_SPANISH", participantValues.get("ABOUT_YOU.RACE"));
        assertEquals("Other Ethnicity", participantValues.get("ABOUT_YOU.RACE_DETAIL"));
    }

    private void assertParticipantExporterMap(TabularParticipantExporter participantExporter) {
        // matching the values with the values in the file elastic/filtersWithClinicalOrderColumns.json

        assertEquals(1, participantExporter.participantValueMaps.size());
        Map<String, String> participantValues = participantExporter.participantValueMaps.get(0);
        assertNotNull(participantValues);
        assertNotNull(participantValues.get("DSM.CLINICALORDER.ORDERDATE"));
        assertEquals("03-27-2024 11:42:06", participantValues.get("DSM.CLINICALORDER.ORDERDATE"));
        assertNotNull(participantValues.get("DSM.CLINICALORDER.ORDERID"));
        assertEquals("SOME_ORDER_ID", participantValues.get("DSM.CLINICALORDER.ORDERID"));
        assertNotNull(participantValues.get("DSM.CLINICALORDER.MERCURYPDOID"));
        assertEquals("PDO-123456", participantValues.get("DSM.CLINICALORDER.MERCURYPDOID"));
        assertNotNull(participantValues.get("DSM.CLINICALORDER.ORDERSTATUS"));
        assertEquals("APPROVED", participantValues.get("DSM.CLINICALORDER.ORDERSTATUS"));
        assertNull(participantValues.get("DSM.CLINICALORDER.DSMKITREQUESTID"));
    }

    private void assertKitRequestShipping(List<ParticipantWrapperDto> downloadList) {

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

    private List<Filter> getColumnNames(String fileName) {
        try {
            String filterJson = TestUtil.readFile(fileName);
            DownloadParticipantListPayload downloadParticipantListPayload = new Gson().fromJson(filterJson,
                    DownloadParticipantListPayload.class);
            return downloadParticipantListPayload.getColumnNames();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Failed to read filter from file " + fileName);
        }
        return null;
    }

}
