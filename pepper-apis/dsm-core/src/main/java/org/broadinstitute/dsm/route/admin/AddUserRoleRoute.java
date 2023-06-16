package org.broadinstitute.dsm.route.admin;

import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.pubsub.EditParticipantMessagePublisher;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.lddp.handlers.util.Result;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

@Slf4j
public class AddUserRoleRoute extends RequestHandler {

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {

        QueryParamsMap queryParams = request.queryMap();

        String realm = null;
        if (queryParams.value(RoutePath.REALM) != null) {
            realm = queryParams.get(RoutePath.REALM).value();
        }
        String userIdRequest = UserUtil.getUserId(request);
        if (UserUtil.checkUserAccess(realm, userId, "participant_edit", userIdRequest)) {
            String messageData = request.body();

            if (StringUtils.isBlank(messageData)) {
                logger.error("Message data is blank");
            }

            JsonObject messageJsonObject = new Gson().fromJson(messageData, JsonObject.class);

            JsonObject dataFromJson = messageJsonObject.get("data").getAsJsonObject();

            String data = dataFromJson.toString();

            Map<String, String> attributeMap = getStringStringMap(userId, messageJsonObject);

            try {
                EditParticipantMessagePublisher.publishMessage(data, attributeMap, projectId, topicId);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return new Result(200);
        } else {
            response.status(500);
            return new Result(500, UserErrorMessages.NO_RIGHTS);
        }

    }
    /*
    SELECT @uemail := "<someone's email>";

SELECT @uid := user_id FROM access_user WHERE email COLLATE utf8mb4_general_ci  = @uemail;


SELECT @gid := group_id from ddp_group where name COLLATE utf8mb4_general_ci = "pecgs" ;

INSERT INTO `dev_dsm_db`.`access_user_role_group` (`user_id`, `role_id`, `group_id`)
 SELECT @uid, role_id, @gid FROM access_role WHERE name in ('onc_history_upload', 'upload_ror_file');
     */

}
