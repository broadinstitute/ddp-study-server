package org.broadinstitute.dsm.route;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import lombok.NonNull;
import org.broadinstitute.dsm.exception.AuthorizationException;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
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
            throw new DsmInternalError("PatchUtil.ColumnNameMap is null");
        }

        String userIdRequest = UserUtil.getUserId(request);
        String requestBody = request.body();
        try {
            Patch patch = GSON.fromJson(requestBody, Patch.class);
            String realm = RouteUtil.requireRealm(patch.getRealm());
            logger.info("Got patch request made by {} for realm {} and table alias {}", userId, realm, patch.getTableAlias());

            if ((UserUtil.checkUserAccessForPatch(realm, userId, DBConstants.MR_VIEW, userIdRequest, patch)
                    || UserUtil.checkUserAccessForPatch(realm, userId, DBConstants.MR_ABSTRACTER, userIdRequest, patch)
                    || UserUtil.checkUserAccessForPatch(realm, userId, DBConstants.PT_LIST_VIEW, userIdRequest, patch))
                    || UserUtil.checkKitShippingAccessForPatch(realm, userId, userIdRequest, patch)) {

                BasePatch patcher = PatchFactory.makePatch(patch, notificationUtil);
                return patcher.doPatch();

            } else {
                throw new AuthorizationException("User is not authorized to patch participant data");
            }
        } catch (JsonSyntaxException e) {
            throw new DSMBadRequestException("Invalid request payload format for patch");
        } catch (DuplicateException e) {
            // TODO: fix this by inspecting the DuplicateException throwers to see if cases are due to
            // bad request data (400)
            throw new DsmInternalError("Duplicate value", e);
        }
    }
}
