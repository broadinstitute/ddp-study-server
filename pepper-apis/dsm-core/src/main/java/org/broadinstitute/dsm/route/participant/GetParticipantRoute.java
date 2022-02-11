package org.broadinstitute.dsm.route.participant;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.participant.ParticipantWrapper;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperPayload;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.broadinstitute.dsm.util.UserUtil;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

public class GetParticipantRoute extends RequestHandler {

    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {

        QueryParamsMap queryParamsMap = request.queryMap();

        String requestUserId = queryParamsMap.get(UserUtil.USER_ID).value();
        if (!userId.equals(requestUserId)) throw new IllegalAccessException("User id: " + userId  + "does not match to request user id: " + requestUserId);

        String realm = queryParamsMap.get(RoutePath.REALM).value();
        if (StringUtils.isBlank(realm)) throw new IllegalArgumentException("realm cannot be empty");

        if (!UserUtil.checkUserAccess(realm, userId, "mr_view", requestUserId) && !UserUtil.checkUserAccess(realm, userId, "pt_list_view", requestUserId)) {
            response.status(500);
            return UserErrorMessages.NO_RIGHTS;
        }
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(realm).orElseThrow();

        String ddpParticipantId = queryParamsMap.get(RoutePath.DDP_PARTICIPANT_ID).value();
        if (StringUtils.isBlank(ddpParticipantId)) throw new IllegalArgumentException("participant id cannot be empty");

        Map<String, String> queryConditions = Map.of(
                "p", " AND p.ddp_participant_id = '" + ddpParticipantId + "'",
                "ES", ParticipantUtil.isGuid(ddpParticipantId)
                        ? ElasticSearchUtil.BY_GUID + ddpParticipantId
                        : ElasticSearchUtil.BY_LEGACY_ALTPID + ddpParticipantId
        );
        ParticipantWrapperPayload participantWrapperPayload = new ParticipantWrapperPayload.Builder()
                .withFilter(queryConditions)
                .withDdpInstanceDto(ddpInstanceDto)
                .withFrom(0)
                .withTo(10)
                .build();
        ElasticSearch elasticSearch = new ElasticSearch();

        return new ParticipantWrapper(participantWrapperPayload, elasticSearch).getFilteredList().getParticipants();
    }
}
