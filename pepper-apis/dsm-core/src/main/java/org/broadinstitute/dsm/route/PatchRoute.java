package org.broadinstitute.dsm.route;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.NonNull;
import org.broadinstitute.dsm.exception.DuplicateException;
import org.broadinstitute.dsm.model.patch.BasePatch;
import org.broadinstitute.dsm.model.patch.Patch;
import org.broadinstitute.dsm.model.patch.PatchFactory;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.broadinstitute.dsm.util.PatchUtil;
import org.broadinstitute.dsm.util.UserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

/**
 * DSM UI's `patch` requests are handled by this endpoint.
 * The users who have mr_view and pt_list_view and mr_abstracter have access to make changes in the PatchRoute.
 * Users with kit_shipping access (GP users) can only change kit values
 * */
public class PatchRoute extends RequestHandler {
    private static final Logger logger = LoggerFactory.getLogger(RequestHandler.class);

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private NotificationUtil notificationUtil;
    private PatchUtil patchUtil;

    public PatchRoute(@NonNull NotificationUtil notificationUtil, @NonNull PatchUtil patchUtil) {
        this.notificationUtil = notificationUtil;
        this.patchUtil = patchUtil;
    }

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        if (PatchUtil.getColumnNameMap() == null) {
            response.status(500);
            throw new RuntimeException("ColumnNameMap is null!");
        }
        String userIdRequest = UserUtil.getUserId(request);
        String requestBody = request.body();
        Patch patch = GSON.fromJson(requestBody, Patch.class);
        String realm = patch.getRealm();
        logger.info("Received a patch request, made by " + userId + " in realm "+realm +" for table alias " + patch.getTableAlias());
        if ((UserUtil.checkUserAccessForPatch(realm, userId, DBConstants.MR_VIEW, userIdRequest, patch)
                || UserUtil.checkUserAccessForPatch(realm, userId, DBConstants.MR_ABSTRACTER, userIdRequest, patch)
                || UserUtil.checkUserAccessForPatch(realm, userId, DBConstants.PT_LIST_VIEW, userIdRequest, patch))
                || ( UserUtil.checkUserAccessForPatch(realm, userId, DBConstants.KIT_SHIPPING, userIdRequest, patch)
                && DBConstants.DDP_KIT_ALIAS.equals(patch.getTableAlias()))) {
            try {
                BasePatch patcher = PatchFactory.makePatch(patch, notificationUtil);
                return patcher.doPatch();
            } catch (DuplicateException e) {
                response.status(500);
                throw new RuntimeException("Duplicate value", e);
            } catch (Exception e) {
                response.status(500);
                throw new RuntimeException("An error occurred while attempting to patch ", e);
            }
        } else {
            response.status(403);
            logger.warn("User with id {} does not have needed privileges", userId);
            return UserErrorMessages.NO_RIGHTS;
        }
    }
}
