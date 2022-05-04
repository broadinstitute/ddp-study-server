package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.util.ElasticsearchServiceUtil.getIndexForStudy;

import java.io.IOException;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.elastic.ElasticSearchIndexType;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.dsm.ParticipantStatusES;
import org.broadinstitute.ddp.model.dsm.ParticipantStatusTrackingInfo;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.util.GsonUtil;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import spark.Request;
import spark.Response;
import spark.Route;

/**
 * This route calls DSM and returns the DTO after enriching it with certain information
 */
@Slf4j
public class GetDsmParticipantStatusRoute implements Route {
    private final RestHighLevelClient esClient;
    private final Gson gson;

    public GetDsmParticipantStatusRoute(RestHighLevelClient esClient) {
        this.esClient = esClient;
        this.gson = GsonUtil.standardBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").create();
    }

    /**
     * Halts with
     * 1) 404 if the participant doesn't exist in Pepper/DSM
     * 2) 400 if the studyGuid / userGuid is malformed
     * 3) 500 (general) in case of other unexpected errors
     */
    @Override
    public ParticipantStatusTrackingInfo handle(Request request, Response response) {
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        String userGuid = request.params(RouteConstants.PathParam.USER_GUID);

        log.info("Attempting to fetch DSM participant status for {} in study {}", userGuid, studyGuid);

        response.type(ContentType.APPLICATION_JSON.getMimeType());
        return process(studyGuid, userGuid);
    }

    ParticipantStatusTrackingInfo process(String studyGuid, String userGuid) {
        // User guid or study guid might not exist, or user might not be in study.
        // In all these cases, there won't be an enrollment status, so we return 404.
        EnrollmentStatusType status = TransactionWrapper.withTxn(handle -> handle
                .attach(JdbiUserStudyEnrollment.class)
                .getEnrollmentStatusByUserAndStudyGuids(userGuid, studyGuid)
                .orElseThrow(() -> {
                    String errMsg = "Participant " + userGuid + " or study " + studyGuid + " not found";
                    log.warn(errMsg);
                    throw ResponseUtil.haltError(404, new ApiError(ErrorCodes.NOT_FOUND, errMsg));
                }));
        try {
            var info = getDataFromEs(userGuid, studyGuid, status);
            if (info == null) {
                // Somehow it's not found in ES. For backwards-compatibility we return 404.
                String errMsg = "Participant " + userGuid + " or study " + studyGuid + " not found";
                log.warn(errMsg);
                throw ResponseUtil.haltError(404, new ApiError(ErrorCodes.NOT_FOUND, errMsg));
            }
            return info;
        } catch (IOException e) {
            String errMsg = "Something went wrong during fetching the workflow statuses from ES for study "
                    + studyGuid + " and participant " + userGuid + ".";
            log.error(errMsg, e);
            throw ResponseUtil.haltError(500, new ApiError(ErrorCodes.SERVER_ERROR, errMsg));
        }
    }

    private ParticipantStatusTrackingInfo getDataFromEs(String userGuid, String studyGuid,
                                                        EnrollmentStatusType status) throws IOException {
        String esIndex = TransactionWrapper.withTxn(handle -> {
            StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
            return getIndexForStudy(handle, studyDto, ElasticSearchIndexType.PARTICIPANTS_STRUCTURED);
        });

        // Limit the data returned from ES so we just grab what we need.
        String[] includes = {"dsm", "samples", "workflows"};
        GetRequest getRequest = new GetRequest(esIndex, "_doc", userGuid);
        getRequest.fetchSourceContext(new FetchSourceContext(true, includes, null));
        GetResponse esResponse = esClient.get(getRequest, RequestOptions.DEFAULT);
        String source = esResponse.getSourceAsString();
        if (source == null || source.isBlank()) {
            return null;
        }

        ParticipantStatusES participantStatus = gson.fromJson(source, ParticipantStatusES.class);
        return new ParticipantStatusTrackingInfo(participantStatus, status, userGuid);
    }
}
