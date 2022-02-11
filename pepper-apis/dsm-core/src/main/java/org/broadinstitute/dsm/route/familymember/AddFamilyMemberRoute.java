package org.broadinstitute.dsm.route.familymember;

import java.util.NoSuchElementException;
import java.util.Optional;

import com.google.gson.Gson;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.model.Study;
import org.broadinstitute.dsm.model.familymember.AddFamilyMember;
import org.broadinstitute.dsm.model.participant.data.AddFamilyMemberPayload;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberDetails;
import org.broadinstitute.dsm.model.participant.data.ParticipantData;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class AddFamilyMemberRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(AddFamilyMemberRoute.class);

    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {
        Gson gson = new Gson();
        AddFamilyMemberPayload addFamilyMemberPayload = gson.fromJson(request.body(), AddFamilyMemberPayload.class);

        String participantId = addFamilyMemberPayload.getParticipantId()
                .orElseThrow(() -> new NoSuchElementException("Participant Guid is not provided"));

        String realm =
                addFamilyMemberPayload.getRealm().orElseThrow(() -> new NoSuchElementException("Realm is not provided"));
        boolean isRealmAbleOfAddFamilyMember = DDPInstanceDao.getRole(realm, DBConstants.ADD_FAMILY_MEMBER);
        if (!isRealmAbleOfAddFamilyMember) {
            response.status(400);
            logger.warn("Study : " + realm + " is not setup to add family member");
            return new Result(400, "Study is not setup to add family member");
        }

        Optional<FamilyMemberDetails> maybeFamilyMemberData = addFamilyMemberPayload.getData();
        if (maybeFamilyMemberData.isEmpty() || maybeFamilyMemberData.orElseGet(FamilyMemberDetails::new).isFamilyMemberFieldsEmpty()) {
            response.status(400);
            logger.warn("Family member information for participant : " + participantId + " is not provided");
            return new Result(400, "Family member information is not provided");
        }

        Integer uId =
                addFamilyMemberPayload.getUserId().orElseThrow(() -> new NoSuchElementException("User id is not provided"));
        if (Integer.parseInt(userId) != uId) {
            throw new RuntimeException("User id was not equal. User id in token " + userId + " user id in request " + uId);
        }

        ParticipantDataDao participantDataDao = new ParticipantDataDao();
        AddFamilyMember addFamilyMember = AddFamilyMember.instance(Study.valueOf(realm.toUpperCase()),addFamilyMemberPayload);
        long createdFamilyMemberId = addFamilyMember.add();
        return ParticipantData.parseDto(participantDataDao.get(createdFamilyMemberId).orElseThrow());
    }


}
