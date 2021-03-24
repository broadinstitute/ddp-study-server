package org.broadinstitute.dsm.route.participant;

import java.util.NoSuchElementException;

import org.broadinstitute.dsm.db.dao.participant.data.ParticipantDataDao;
import org.broadinstitute.dsm.model.participant.data.NewParticipantData;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.broadinstitute.dsm.util.UserUtil;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

public class GetParticipantDataRoute extends RequestHandler {

    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {
        QueryParamsMap queryParamsMap = request.queryMap();

        if (!queryParamsMap.hasKey(UserUtil.USER_ID) && !queryParamsMap.get(UserUtil.USER_ID).hasValue()) {
            throw new NoSuchElementException("User id is not provided");
        }
        String uId = queryParamsMap.get(UserUtil.USER_ID).value();

        if (!uId.equals(userId)) {
            throw new RuntimeException("User id was not equal. User id in token " + userId + " user id in request " + uId);
        }

        if (!queryParamsMap.hasKey(ParticipantUtil.DDP_PARTICIPANT_ID) && !queryParamsMap.get(ParticipantUtil.DDP_PARTICIPANT_ID).hasValue()) {
            throw new NoSuchElementException("Participant Id is not provided");
        }
        String ddpParticipantId = queryParamsMap.get(ParticipantUtil.DDP_PARTICIPANT_ID).value();
        ParticipantDataDao participantDataDao = new ParticipantDataDao();

        return NewParticipantData.parseDtoList(participantDataDao.getParticipantDataByParticipantId(ddpParticipantId));
    }
}
