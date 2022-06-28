package org.broadinstitute.dsm.route;

import static org.broadinstitute.dsm.util.ElasticSearchUtil.DEFAULT_FROM;
import static org.broadinstitute.dsm.util.ElasticSearchUtil.MAX_RESULT_SIZE;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.net.MediaType;
import lombok.Getter;
import lombok.Setter;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.elastic.export.tabular.*;
import org.broadinstitute.dsm.model.filter.FilterFactory;
import org.broadinstitute.dsm.model.filter.Filterable;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperDto;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperResult;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

public class DownloadParticipantListRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(DownloadParticipantListRoute.class);

    private static final String FILE_DATE_FORMAT = "yyyy-MM-dd";

    public DownloadParticipantListRoute() {
    }

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        DownloadParticipantListPayload payload =
                ObjectMapperSingleton.instance().readValue(request.body(), DownloadParticipantListPayload.class);

        String realm = RoutePath.getRealm(request);
        DDPInstance instance = DDPInstance.getDDPInstanceWithRole(realm, DBConstants.MEDICAL_RECORD_ACTIVATED);

        TabularParticipantParser parser = new TabularParticipantParser(payload.getColumnNames(), instance);

        Filterable filterable = FilterFactory.of(request);
        List<ParticipantWrapperDto> participants = fetchParticipantEsData(filterable, request.queryMap());
        List<ModuleExportConfig> exportConfigs = parser.generateExportConfigs();
        List<Map<String, String>> participantValueMaps = parser.parse(exportConfigs, participants);

        response.type(MediaType.TSV_UTF_8.toString());
        response.header("Access-Control-Expose-Headers", "Content-Disposition");
        response.header("Content-Disposition", "attachment;filename=" + getExportFilename());

        TabularParticipantExporter exporter = new TabularParticipantExporter(exportConfigs, participantValueMaps);
        exporter.writeTable(response.raw().getWriter());
        return response.raw();
    }

    /** Fetches participant information from ElasticSearch in batches of MAX_RESULT_SIZE  */
    private static List<ParticipantWrapperDto> fetchParticipantEsData(Filterable filter, QueryParamsMap queryParamsMap) {
        List<ParticipantWrapperDto> allResults = new ArrayList<ParticipantWrapperDto>();
        int currentFrom = DEFAULT_FROM;
        int currentTo = MAX_RESULT_SIZE;

        while (true) {
            // For each batch of results, add the DTOs to the allResults list
            filter.setFrom(currentFrom);
            filter.setTo(currentTo);
            ParticipantWrapperResult filteredSubset = (ParticipantWrapperResult) filter.filter(queryParamsMap);
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

    private static String getExportFilename() {
        LocalDate date = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(FILE_DATE_FORMAT);
        String exportFileName = String.format("Participant-%s.tsv", date.format(formatter));
        return exportFileName;
    }

    @Getter
    @Setter
    private static class DownloadParticipantListPayload {
        private List<Filter> columnNames;
    }
}



