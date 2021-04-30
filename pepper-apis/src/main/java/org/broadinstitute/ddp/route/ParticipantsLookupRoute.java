package org.broadinstitute.ddp.route;

import static java.util.Collections.emptyList;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.entity.ContentType;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.json.admin.participantslookup.ParticipantsLookupPayload;
import org.broadinstitute.ddp.json.admin.participantslookup.ParticipantsLookupResponse;
import org.broadinstitute.ddp.service.participantslookup.ParticipantsLookupService;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;


/**
 * Search for participants by a specified query.
 */
public class ParticipantsLookupRoute extends ValidatedJsonInputRoute<ParticipantsLookupPayload> {

    private static final Logger LOG = LoggerFactory.getLogger(ParticipantsLookupRoute.class);

    private final ParticipantsLookupService participantsLookupService;
    private final int resultsMaxCount;

    public ParticipantsLookupRoute(ParticipantsLookupService participantsLookupService, int resultsMaxCount) {
        this.participantsLookupService = participantsLookupService;
        this.resultsMaxCount = resultsMaxCount;
    }

    @Override
    public Object handle(Request request, Response response, ParticipantsLookupPayload payload) throws Exception {
        var studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        var query = payload.getQuery();

        response.type(ContentType.APPLICATION_JSON.getMimeType());

        LOG.info("Started participants lookup (query=\"{}\")", query);

        var result = new ParticipantsLookupResponse(0, emptyList());

        if (StringUtils.isNotBlank(query)) {
            var lookupResult = participantsLookupService.lookupParticipants(studyGuid, query, resultsMaxCount);
            result = new ParticipantsLookupResponse(
                    Long.valueOf(lookupResult.getTotalCount()).intValue(),
                    lookupResult.getResultRows()
            );
        }

        LOG.info("Finished participants lookup (query=\"{}\"), found {} rows, fetched {} rows",
                query, result.getTotalCount(), result.getParticipants().size());

        return result;
    }
}
