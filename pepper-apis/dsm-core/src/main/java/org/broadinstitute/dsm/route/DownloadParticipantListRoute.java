package org.broadinstitute.dsm.route;

import com.google.common.net.MediaType;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.model.elastic.export.tabular.DataDictionaryExporter;
import org.broadinstitute.dsm.model.elastic.export.tabular.ModuleExportConfig;
import org.broadinstitute.dsm.model.elastic.export.tabular.TabularParticipantExporter;
import org.broadinstitute.dsm.model.elastic.export.tabular.TabularParticipantParser;
import org.broadinstitute.dsm.model.elastic.search.UnparsedESParticipantDto;
import org.broadinstitute.dsm.model.filter.FilterFactory;
import org.broadinstitute.dsm.model.filter.Filterable;
import org.broadinstitute.dsm.model.participant.DownloadParticipantListParams;
import org.broadinstitute.dsm.model.participant.DownloadParticipantListPayload;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperDto;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.service.download.DownloadParticipantListService;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DownloadParticipantListRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(DownloadParticipantListRoute.class);
    private DownloadParticipantListService downloadParticipantListService = new DownloadParticipantListService();

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
        DDPInstance instance = DDPInstance.getDDPInstance(realm);

        TabularParticipantParser parser = new TabularParticipantParser(payload.getColumnNames(), instance,
                params.isHumanReadable(), params.isOnlyMostRecent(), null);
        setResponseHeaders(response, realm + "_export.zip");

        Filterable filterable = FilterFactory.of(request);
        List<ParticipantWrapperDto> participants = downloadParticipantListService.fetchParticipantEsData(filterable, request.queryMap());
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
        return response.raw();
    }

    protected void setResponseHeaders(Response response, String filename) {
        response.type(MediaType.OCTET_STREAM.toString());
        response.header("Access-Control-Expose-Headers", "Content-Disposition");
        response.header("Content-Disposition", "attachment;filename=" + filename);
    }

}
