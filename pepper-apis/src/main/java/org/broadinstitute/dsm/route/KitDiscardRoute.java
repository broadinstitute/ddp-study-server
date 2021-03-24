package org.broadinstitute.dsm.route;

import com.google.gson.Gson;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.ddp.security.Auth0Util;
import org.broadinstitute.ddp.util.GoogleBucket;
import org.broadinstitute.dsm.db.KitDiscard;
import org.broadinstitute.dsm.db.User;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

public class KitDiscardRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(KitDiscardRoute.class);

    private static final String DIFFERENT_USER = "DIFFERENT_USER";
    private static final String USER_NO_RIGHT = "USER_NO_RIGHT";

    private final Auth0Util auth0Util;
    private final UserUtil userUtil;

    public KitDiscardRoute(@NonNull Auth0Util auth0Util, @NonNull UserUtil userUtil) {
        this.auth0Util = auth0Util;
        this.userUtil = userUtil;
    }

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        QueryParamsMap queryParams = request.queryMap();
        String realm;
        if (queryParams.value(RoutePath.REALM) != null) {
            realm = queryParams.get(RoutePath.REALM).value();
        }
        else {
            throw new RuntimeException("No realm query param was sent");
        }

        if (RoutePath.RequestMethod.GET.toString().equals(request.requestMethod())) {
            if (UserUtil.checkUserAccess(realm, userId, "discard_sample") || UserUtil.checkUserAccess(realm, userId, "participant_exit")) {
                return KitDiscard.getExitedKits(realm);
            }
            else {
                response.status(500);
                return new Result(500, UserErrorMessages.NO_RIGHTS);
            }
        }
        if (RoutePath.RequestMethod.PATCH.toString().equals(request.requestMethod())) {
            String requestBody = request.body();
            KitDiscard kitAction = new Gson().fromJson(requestBody, KitDiscard.class);
            if (request.url().contains(RoutePath.DISCARD_SHOW_UPLOAD)) {
                if (UserUtil.checkUserAccess(realm, userId, "discard_sample") || UserUtil.checkUserAccess(realm, userId, "participant_exit")) {
                    if (kitAction.getPath() != null) {
                        byte[] bytes = GoogleBucket.downloadFile(null,
                                TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GOOGLE_PROJECT_NAME),
                                TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GOOGLE_DISCARD_BUCKET), kitAction.getPath());
                        if (bytes != null) {
                            logger.info("Got file from bucket");
                            try {
                                HttpServletResponse rawResponse = response.raw();
                                rawResponse.getOutputStream().write(bytes);
                                rawResponse.setStatus(200);
                                rawResponse.getOutputStream().flush();
                                rawResponse.getOutputStream().close();
                                return new Result(200);
                            }
                            catch (IOException e) {
                                throw new RuntimeException("Couldn't send file ", e);
                            }
                        }
                    }
                }
                else {
                    response.status(500);
                    return new Result(500, UserErrorMessages.NO_RIGHTS);
                }
            }
            else {
                if (StringUtils.isNotBlank(kitAction.getKitDiscardId())) {
                    if (StringUtils.isNotBlank(kitAction.getAction())) {
                        if (UserUtil.checkUserAccess(realm, userId, "discard_sample") || UserUtil.checkUserAccess(realm, userId, "participant_exit")) {
                            kitAction.setAction(kitAction.getKitDiscardId(), kitAction.getAction());
                            return new Result(200);
                        }
                        else {
                            response.status(500);
                            return new Result(500, UserErrorMessages.NO_RIGHTS);
                        }
                    }
                    if (StringUtils.isNotBlank(kitAction.getDiscardDate())) {
                        if (UserUtil.checkUserAccess(realm, userId, "discard_sample")) {
                            String userIdRequest = UserUtil.getUserId(request);
                            kitAction.setKitDiscarded(kitAction.getKitDiscardId(), userIdRequest, kitAction.getDiscardDate());
                            return new Result(200);
                        }
                        else {
                            response.status(500);
                            return new Result(500, UserErrorMessages.NO_RIGHTS);
                        }
                    }
                }
            }
        }
        if (RoutePath.RequestMethod.POST.toString().equals(request.requestMethod())) {
            // confirm discard of sample
            if (request.url().contains(RoutePath.DISCARD_CONFIRM)) {
                String requestBody = request.body();
                KitDiscard kitAction = new Gson().fromJson(requestBody, KitDiscard.class);

                String token = kitAction.getToken();
                if (StringUtils.isNotBlank(token)) {
                    Auth0Util.Auth0UserInfo auth0UserInfo = auth0Util.getAuth0UserInfo(token);
                    if (auth0UserInfo != null) {
                        String email = auth0UserInfo.getEmail();
                        User user = User.getUser(email);
                        if (user != null && user.getId() != null) {
                            ArrayList<String> userSetting = userUtil.getUserAccessRoles(email);
                            if (userSetting.contains(DBConstants.KIT_SHIPPING) || userSetting.contains(DBConstants.DISCARD_SAMPLE)) {
                                KitDiscard kit = KitDiscard.getKitDiscard(kitAction.getKitDiscardId());
                                if (kit.getChangedById() != user.getUserId()) {
                                    if (KitDiscard.setConfirmed(kitAction.getKitDiscardId(), user.getUserId())) {
                                        return new Result(200, user.getName());
                                    }
                                    throw new RuntimeException("Failed to save confirm");
                                }
                                else {
                                    return new Result(500, DIFFERENT_USER);
                                }
                            }
                            else {
                                return new Result(500, USER_NO_RIGHT);
                            }
                        }
                        else {
                            throw new RuntimeException("User is not known in DSM");
                        }
                    }
                    else {
                        throw new RuntimeException("User not found");
                    }
                }
            }
            else {
                if (UserUtil.checkUserAccess(realm, userId, "discard_sample")) {
                    //save note and files
                    String kitDiscardId = null;
                    if (queryParams.value(KitDiscard.KIT_DISCARD_ID) != null) {
                        kitDiscardId = queryParams.get(KitDiscard.KIT_DISCARD_ID).value();
                    }
                    else {
                        throw new RuntimeException("No kitDiscardId query param was sent");
                    }

                    //create a kitAction with the given kitDiscardId
                    KitDiscard kitAction = new Gson().fromJson("{\"" + KitDiscard.KIT_DISCARD_ID + "\": \"" + kitDiscardId + "\"}", KitDiscard.class);
                    String userIdRequest = UserUtil.getUserId(request);

                    String pathName = null;
                    String path = null;
                    boolean deleteFile = false;
                    if (queryParams.value(KitDiscard.BSP_FILE) != null) {
                        pathName = KitDiscard.BSP_FILE;
                        path = queryParams.get(KitDiscard.BSP_FILE).value();
                    }
                    else if (queryParams.value(KitDiscard.IMAGE_FILE) != null) {
                        pathName = KitDiscard.IMAGE_FILE;
                        path = queryParams.get(KitDiscard.IMAGE_FILE).value();
                    }
                    else if (queryParams.value("note") != null) {
                        String note = queryParams.get("note").value();
                        KitDiscard.updateInfo(kitAction.getKitDiscardId(), userIdRequest, note, null, null);
                    }
                    if (queryParams.value("delete") != null) {
                        deleteFile = queryParams.get("delete").booleanValue();
                    }

                    if (path != null) {
                        if (deleteFile) {
                            //delete file
                            if (GoogleBucket.deleteFile(null,
                                    TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GOOGLE_PROJECT_NAME),
                                    TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GOOGLE_DISCARD_BUCKET), path)) {
                                KitDiscard.updateInfo(kitAction.getKitDiscardId(), userIdRequest, null, pathName, null);
                                return new Result(200);
                            }
                        }
                        else {
                            //save file
                            HttpServletRequest rawRequest = request.raw();
                            String fileName = GoogleBucket.uploadFile(null,
                                    TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GOOGLE_PROJECT_NAME),
                                    TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GOOGLE_DISCARD_BUCKET), kitDiscardId + "_" + path, rawRequest.getInputStream());
                            KitDiscard.updateInfo(kitAction.getKitDiscardId(), userIdRequest, null, pathName, fileName);
                            return new Result(200, fileName);
                        }
                    }
                }
                else {
                    response.status(500);
                    return new Result(500, UserErrorMessages.NO_RIGHTS);
                }
            }
        }
        throw new RuntimeException("Something went wrong");
    }
}
