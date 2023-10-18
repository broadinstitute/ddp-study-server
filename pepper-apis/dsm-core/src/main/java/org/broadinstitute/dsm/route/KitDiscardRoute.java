package org.broadinstitute.dsm.route;

import java.io.IOException;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.KitDiscard;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.security.Auth0Util;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.DSMConfig;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.lddp.handlers.util.Result;
import org.broadinstitute.lddp.util.GoogleBucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

public class KitDiscardRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(KitDiscardRoute.class);

    private static final String DIFFERENT_USER = "DIFFERENT_USER";

    private final Auth0Util auth0Util;
    private final String auth0Domain;

    public KitDiscardRoute(@NonNull Auth0Util auth0Util, @NonNull String auth0Domain) {
        this.auth0Util = auth0Util;
        this.auth0Domain = auth0Domain;
    }

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        QueryParamsMap queryParams = request.queryMap();
        String realm;
        if (queryParams.value(RoutePath.REALM) != null) {
            realm = queryParams.get(RoutePath.REALM).value();
        } else {
            throw new DSMBadRequestException("No realm query param was sent");
        }
        String userIdRequest = UserUtil.getUserId(request);

        if (RoutePath.RequestMethod.GET.toString().equals(request.requestMethod())) {
            if (canDiscardSample(realm, userId, userIdRequest)
                    || canExitParticipant(realm, userId, userIdRequest)) {
                return KitDiscard.getExitedKits(realm);
            }
            return new Result(403, UserErrorMessages.NO_RIGHTS);
        }

        if (RoutePath.RequestMethod.PATCH.toString().equals(request.requestMethod())) {
            String requestBody = request.body();
            KitDiscard kitAction = new Gson().fromJson(requestBody, KitDiscard.class);
            if (request.url().contains(RoutePath.DISCARD_SHOW_UPLOAD)) {
                if (!canDiscardSample(realm, userId, userIdRequest)
                        && !canExitParticipant(realm, userId, userIdRequest)) {
                    return new Result(403, UserErrorMessages.NO_RIGHTS);
                }
                if (kitAction.getPath() != null) {
                    byte[] bytes = GoogleBucket.downloadFile(null,
                            DSMConfig.getSqlFromConfig(ApplicationConfigConstants.GOOGLE_PROJECT_NAME),
                            DSMConfig.getSqlFromConfig(ApplicationConfigConstants.GOOGLE_DISCARD_BUCKET), kitAction.getPath());
                    if (bytes != null) {
                        logger.info("Got file from bucket");
                        try {
                            HttpServletResponse rawResponse = response.raw();
                            rawResponse.getOutputStream().write(bytes);
                            rawResponse.setStatus(200);
                            rawResponse.getOutputStream().flush();
                            rawResponse.getOutputStream().close();
                            return new Result(200);
                        } catch (IOException e) {
                            throw new DsmInternalError("Couldn't send file ", e);
                        }
                    }
                }
            } else {
                if (StringUtils.isNotBlank(kitAction.getKitDiscardId())) {
                    if (StringUtils.isNotBlank(kitAction.getAction())) {
                        if (canDiscardSample(realm, userId, userIdRequest)
                                || canExitParticipant(realm, userId, userIdRequest)) {
                            kitAction.setAction(kitAction.getKitDiscardId(), kitAction.getAction());
                            return new Result(200);
                        } else {
                            return new Result(403, UserErrorMessages.NO_RIGHTS);
                        }
                    }
                    if (StringUtils.isNotBlank(kitAction.getDiscardDate())) {
                        if (canDiscardSample(realm, userId, userIdRequest)) {
                            kitAction.setKitDiscarded(kitAction.getKitDiscardId(), userIdRequest, kitAction.getDiscardDate());
                            return new Result(200);
                        } else {
                            return new Result(403, UserErrorMessages.NO_RIGHTS);
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
                    String email = auth0Util.getAuth0User(token, auth0Domain);
                    UserDto userDto = new UserDao().getUserByEmail(email).orElseThrow();
                    if (userDto.getId() <= 0) {
                        throw new DSMBadRequestException("Invalid DSM user " + email);
                    }
                    List<String> userSetting = UserUtil.getUserAccessRoles(email);
                    if (!userSetting.contains(DBConstants.KIT_SHIPPING) && !userSetting.contains(DBConstants.DISCARD_SAMPLE)) {
                        return new Result(403, UserErrorMessages.NO_RIGHTS);
                    }
                    KitDiscard kit = KitDiscard.getKitDiscard(kitAction.getKitDiscardId());
                    if (kit.getChangedById() != userDto.getId()) {
                        if (KitDiscard.setConfirmed(kitAction.getKitDiscardId(), userDto.getId())) {
                            return new Result(200, userDto.getName().orElse(""));
                        }
                        throw new DsmInternalError("Error confirming kit discarded. dsm_kit_id: " + kitAction.getKitDiscardId());
                    } else {
                        return new Result(500, DIFFERENT_USER);
                    }
                }
            } else {
                if (canDiscardSample(realm, userId, userIdRequest)) {
                    //save note and files
                    String kitDiscardId = null;
                    if (queryParams.value(KitDiscard.KIT_DISCARD_ID) != null) {
                        kitDiscardId = queryParams.get(KitDiscard.KIT_DISCARD_ID).value();
                    } else {
                        throw new DSMBadRequestException("No kitDiscardId query param was sent");
                    }

                    //create a kitAction with the given kitDiscardId
                    KitDiscard kitAction =
                            new Gson().fromJson("{\"" + KitDiscard.KIT_DISCARD_ID + "\": \"" + kitDiscardId + "\"}", KitDiscard.class);

                    String pathName = null;
                    String path = null;
                    boolean deleteFile = false;
                    if (queryParams.value(KitDiscard.BSP_FILE) != null) {
                        pathName = KitDiscard.BSP_FILE;
                        path = queryParams.get(KitDiscard.BSP_FILE).value();
                    } else if (queryParams.value(KitDiscard.IMAGE_FILE) != null) {
                        pathName = KitDiscard.IMAGE_FILE;
                        path = queryParams.get(KitDiscard.IMAGE_FILE).value();
                    } else if (queryParams.value("note") != null) {
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
                                    DSMConfig.getSqlFromConfig(ApplicationConfigConstants.GOOGLE_PROJECT_NAME),
                                    DSMConfig.getSqlFromConfig(ApplicationConfigConstants.GOOGLE_DISCARD_BUCKET), path)) {
                                KitDiscard.updateInfo(kitAction.getKitDiscardId(), userIdRequest, null, pathName, null);
                                return new Result(200);
                            }
                        } else {
                            //save file
                            HttpServletRequest rawRequest = request.raw();
                            String fileName = GoogleBucket.uploadFile(null,
                                    DSMConfig.getSqlFromConfig(ApplicationConfigConstants.GOOGLE_PROJECT_NAME),
                                    DSMConfig.getSqlFromConfig(ApplicationConfigConstants.GOOGLE_DISCARD_BUCKET),
                                    kitDiscardId + "_" + path, rawRequest.getInputStream());
                            KitDiscard.updateInfo(kitAction.getKitDiscardId(), userIdRequest, null, pathName, fileName);
                            return new Result(200, fileName);
                        }
                    }
                } else {
                    return new Result(403, UserErrorMessages.NO_RIGHTS);
                }
            }
        }
        // TOOO I did some initial work to clean up this method, largely to ensure DSM was returning the correct
        // response codes and to remove unnecessary complexity (still more to do on that). But this catch-all
        // needs improvement. It looks like a fall through from several of the clauses above, which means DSM
        // likely wants to throw a DSMBadRequestException for at least some of those cases, but of course we need to
        // add a response body describing what is wrong. It also looks like this is where a request without a token ends up.  -DC
        throw new RuntimeException("Something went wrong");
    }

    private static boolean canDiscardSample(String realm, String userId, String userIdRequest) {
        return UserUtil.checkUserAccess(realm, userId, "discard_sample", userIdRequest);
    }

    private static boolean canExitParticipant(String realm, String userId, String userIdRequest) {
        return UserUtil.checkUserAccess(realm, userId, "participant_exit", userIdRequest);
    }
}
