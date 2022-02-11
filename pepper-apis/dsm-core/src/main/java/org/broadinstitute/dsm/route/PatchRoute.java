package org.broadinstitute.dsm.route;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.NonNull;
import org.broadinstitute.dsm.exception.DuplicateException;
import org.broadinstitute.dsm.model.patch.Patch;
import org.broadinstitute.dsm.model.patch.BasePatch;
import org.broadinstitute.dsm.model.patch.PatchFactory;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

//Class needs to be refactored as soon as possible!!!
public class PatchRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(PatchRoute.class);
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
        if (UserUtil.checkUserAccess(null, userId, DBConstants.MR_VIEW, userIdRequest) || UserUtil.checkUserAccess(null, userId, DBConstants.MR_ABSTRACTER, userIdRequest)
                || UserUtil.checkUserAccess(null, userId, DBConstants.MR_VIEW, userIdRequest) || UserUtil.checkUserAccess(null, userId, DBConstants.PT_LIST_VIEW, userIdRequest)) {
            try {
                String requestBody = request.body();
                Patch patch = GSON.fromJson(requestBody, Patch.class);
                BasePatch patcher = PatchFactory.makePatch(patch, notificationUtil);
                return patcher.doPatch();
            }
            catch (DuplicateException e) {
                response.status(500);
                throw new RuntimeException("Duplicate value", e);
            }
            catch (Exception e) {
                response.status(500);
                throw new RuntimeException("An error occurred while attempting to patch ", e);
            }
        }
        else {
            response.status(403);
            return UserErrorMessages.NO_RIGHTS;
        }
    }
}
