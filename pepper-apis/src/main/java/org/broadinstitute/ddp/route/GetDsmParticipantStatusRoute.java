package org.broadinstitute.ddp.route;

import org.apache.http.entity.ContentType;
import org.broadinstitute.ddp.client.DsmClient;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.elastic.ElasticSearchIndexType;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.dsm.ParticipantStatusTrackingInfo;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.broadinstitute.ddp.util.ElasticsearchServiceUtil.getIndexForStudy;

/**
 * This route calls DSM and returns the DTO after enriching it with certain information
 */
public class GetDsmParticipantStatusRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(GetDsmParticipantStatusRoute.class);

    private final DsmClient dsm;

    private final RestHighLevelClient esClient;

    public GetDsmParticipantStatusRoute(DsmClient dsmClient, RestHighLevelClient esClient) {
        this.dsm = dsmClient;
        this.esClient = esClient;
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
        String token = RouteUtil.getDDPAuth(request).getToken();

        LOG.info("Attempting to fetch DSM participant status for {} in study {}", userGuid, studyGuid);

        response.type(ContentType.APPLICATION_JSON.getMimeType());
        return process(studyGuid, userGuid, token);
    }

    ParticipantStatusTrackingInfo process(String studyGuid, String userGuid, String token) {
        // User guid or study guid might not exist, or user might not be in study.
        // In all these cases, there won't be an enrollment status, so we return 404.
        EnrollmentStatusType status = TransactionWrapper.withTxn(handle -> handle
                .attach(JdbiUserStudyEnrollment.class)
                .getEnrollmentStatusByUserAndStudyGuids(userGuid, studyGuid)
                .orElseThrow(() -> {
                    String errMsg = "Participant " + userGuid + " or study " + studyGuid + " not found";
                    LOG.warn(errMsg);
                    throw ResponseUtil.haltError(404, new ApiError(ErrorCodes.NOT_FOUND, errMsg));
                }));

        var result = dsm.getParticipantStatus(studyGuid, userGuid, token);
        result.runIfThrown(e -> LOG.error("Failed to fetch participant status from DSM", e));
        LOG.info("DSM call completed, study={} and participant={}, status={}", studyGuid, userGuid, result.getStatusCode());

        if (result.getStatusCode() == 200) {
            List<ParticipantStatusTrackingInfo.Workflow> workflows;
            try {
                workflows = getWorkflowsFromEs(userGuid, studyGuid);
            } catch (IOException e) {
                String errMsg = "Something went wrong during fetching the workflow statuses from ES for study "
                        + studyGuid + " and participant " + userGuid + ".";
                LOG.error(errMsg, e);
                throw ResponseUtil.haltError(500, new ApiError(ErrorCodes.SERVER_ERROR, errMsg));
            }
            return new ParticipantStatusTrackingInfo(result.getBody(), workflows, status, userGuid);
        } else if (result.getStatusCode() == 404) {
            String errMsg = "Participant " + userGuid + " or study " + studyGuid + " not found";
            LOG.warn(errMsg);
            throw ResponseUtil.haltError(404, new ApiError(ErrorCodes.NOT_FOUND, errMsg));
        } else {
            // The user doesn't need to know the details, just general information, so we convert
            // all unrecognized statuses caused by DSM interaction into HTTP 500
            String errMsg = "Something went wrong with DSM interaction while trying to fetch the status "
                    + " for the study " + studyGuid + " and participant " + userGuid
                    + ". The returned HTTP status is " + result.getStatusCode();
            LOG.error(errMsg);
            throw ResponseUtil.haltError(500, new ApiError(ErrorCodes.SERVER_ERROR, errMsg));
        }
    }

    private List<ParticipantStatusTrackingInfo.Workflow> getWorkflowsFromEs(String userGuid, String studyGuid) throws IOException {
        List<ParticipantStatusTrackingInfo.Workflow> result = new ArrayList<>();
        if (esClient != null) {
            String esIndex = TransactionWrapper.withTxn(handle -> {
                StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
                return getIndexForStudy(handle, studyDto, ElasticSearchIndexType.PARTICIPANTS_STRUCTURED);
            });
            GetRequest getRequest = new GetRequest(esIndex, "_doc", userGuid);
            GetResponse esResponse = esClient.get(getRequest, RequestOptions.DEFAULT);
            Map<String, Object> source = esResponse.getSource();
            if (source != null) {
                List<?> workflows = (List<?>) source.get("workflows");
                if (workflows != null) {
                    for (var wfItem : workflows) {
                        Map<?, ?> workflow = (Map<?, ?>) wfItem;
                        result.add(new ParticipantStatusTrackingInfo.Workflow(
                                (String) workflow.get("workflow"),
                                (String) workflow.get("status"))
                        );
                    }
                }
            }
        }
        return result;
    }
}
