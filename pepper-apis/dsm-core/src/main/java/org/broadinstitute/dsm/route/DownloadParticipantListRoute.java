package org.broadinstitute.dsm.route;

import com.google.common.net.MediaType;
import org.broadinstitute.dsm.analytics.GoogleAnalyticsMetrics;
import org.broadinstitute.dsm.analytics.GoogleAnalyticsMetricsTracker;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.model.elastic.export.tabular.DataDictionaryExporter;
import org.broadinstitute.dsm.model.elastic.export.tabular.ModuleExportConfig;
import org.broadinstitute.dsm.model.elastic.export.tabular.TabularParticipantExporter;
import org.broadinstitute.dsm.model.elastic.export.tabular.TabularParticipantParser;
import org.broadinstitute.dsm.model.elastic.search.UnparsedDeserializer;
import org.broadinstitute.dsm.model.elastic.search.UnparsedESParticipantDto;
import org.broadinstitute.dsm.model.filter.FilterFactory;
import org.broadinstitute.dsm.model.filter.Filterable;
import org.broadinstitute.dsm.model.participant.DownloadParticipantListParams;
import org.broadinstitute.dsm.model.participant.DownloadParticipantListPayload;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperDto;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperResult;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.broadinstitute.dsm.util.ElasticSearchUtil.DEFAULT_FROM;
import static org.broadinstitute.dsm.util.ElasticSearchUtil.MAX_RESULT_SIZE;

public class DownloadParticipantListRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(DownloadParticipantListRoute.class);

    /**
     * Generates a file for download.  When removing the feature-flag-export-new role, processRequestNew should
     * be renamed to processRequest, and the old 'processRequest' method should be deleted
     */
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        DownloadParticipantListPayload payload =
                ObjectMapperSingleton.instance().readValue(request.body(), DownloadParticipantListPayload.class);
        DownloadParticipantListParams params = new DownloadParticipantListParams(request.queryMap());

        String realm = RoutePath.getRealm(request);
        String userIdReq = UserUtil.getUserId(request);
        if (!UserUtil.checkUserAccess(realm, userId, "pt_list_view", userIdReq)) {
            response.status(403);
            return UserErrorMessages.NO_RIGHTS;
        }
        Instant startInstant = Instant.now();
        DDPInstance instance = DDPInstance.getDDPInstance(realm);

        TabularParticipantParser parser = new TabularParticipantParser(payload.getColumnNames(), instance,
                params.isHumanReadable(), params.isOnlyMostRecent(), null);
        setResponseHeaders(response, realm + "_export.zip");

        Filterable filterable = FilterFactory.of(request);
        List<ParticipantWrapperDto> participants = fetchParticipantEsData(filterable, request.queryMap());
        logger.info("Beginning parse of " + participants.size() + " participants");
        List<ModuleExportConfig> exportConfigs = parser.generateExportConfigs();
        List<Map<String, Object>> participantEsDataMaps = participants.stream().map(dto ->
                        ((UnparsedESParticipantDto) dto.getEsData()).getDataAsMap()).collect(Collectors.toList());
        List<Map<String, String>> participantValueMaps = parser.parse(exportConfigs, participantEsDataMaps);

        ZipOutputStream zos = new ZipOutputStream(response.raw().getOutputStream());
        TabularParticipantExporter participantExporter = TabularParticipantExporter.getExporter(exportConfigs,
                participantValueMaps, params.getFileFormat());
        ZipEntry participantFile = new ZipEntry(participantExporter.getExportFilename());
        zos.putNextEntry(participantFile);
        participantExporter.export(zos);

        DataDictionaryExporter dictionaryExporter = new DataDictionaryExporter(exportConfigs);
        ZipEntry dictionaryFile = new ZipEntry(dictionaryExporter.getExportFilename());
        zos.putNextEntry(dictionaryFile);

        dictionaryExporter.export(zos);
        zos.closeEntry();
        zos.finish();
        Instant endInstant = Instant.now();
        long perfTime = Duration.between(startInstant, endInstant).toMillis();
        GoogleAnalyticsMetricsTracker.getInstance()
                .sendAnalyticsMetrics(realm, GoogleAnalyticsMetrics.EVENT_CATEGORY_PARTICIPANT_LIST_EXPORT,
                        GoogleAnalyticsMetrics.EVENT_PARTICIPANT_LIST_EXPORT_LOAD_TIME,
                        GoogleAnalyticsMetrics.EVENT_PARTICIPANT_LIST_EXPORT_LOAD_TIME, (int) perfTime);
        return response.raw();
    }

    protected void setResponseHeaders(Response response, String filename) {
        response.type(MediaType.OCTET_STREAM.toString());
        response.header("Access-Control-Expose-Headers", "Content-Disposition");
        response.header("Content-Disposition", "attachment;filename=" + filename);
    }

    /**
     * Fetches participant information from ElasticSearch in batches of MAX_RESULT_SIZE
     */
    private List<ParticipantWrapperDto> fetchParticipantEsData(Filterable filter, QueryParamsMap queryParamsMap) {
        List<ParticipantWrapperDto> allResults = new ArrayList<ParticipantWrapperDto>();
        int currentFrom = DEFAULT_FROM;
        int currentTo = MAX_RESULT_SIZE;
        while (true) {
            // For each batch of results, add the DTOs to the allResults list
            filter.setFrom(currentFrom);
            filter.setTo(currentTo);
            ParticipantWrapperResult filteredSubset = (ParticipantWrapperResult) filter.filter(queryParamsMap, new UnparsedDeserializer());
            allResults.addAll(filteredSubset.getParticipants());
            // if the total count is less than the range we are currently on, stop fetching
            if (filteredSubset.getTotalCount() < currentTo) {
                break;
            }
            currentFrom = currentTo;
            currentTo += MAX_RESULT_SIZE;
        }

        return allResults;
    }

}
