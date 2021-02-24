package org.broadinstitute.ddp.util;

import static org.broadinstitute.ddp.constants.RouteConstants.Header.BEARER;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import com.google.gson.Gson;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudyCached;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserActivityInstanceSummary;
import org.broadinstitute.ddp.filter.StudyLanguageResolutionFilter;
import org.broadinstitute.ddp.filter.TokenConverterFilter;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.security.DDPAuth;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.utils.SparkUtils;

public class RouteUtil {

    private static final Logger LOG = LoggerFactory.getLogger(RouteUtil.class);

    private static final String STUDIES_PATH_MARKER = "studies";
    private static final int STUDIES_MARKER_IDX = 4;
    private static final int STUDY_GUID_IDX = 5;
    private static final int ADMIN_STUDIES_MARKER_IDX = 3;
    private static final int ADMIN_STUDY_GUID_IDX = 4;

    /**
     * Returns the {@link org.broadinstitute.ddp.security.DDPAuth auth object} associated with this request.  Will always be non-null.
     */
    @Nonnull
    public static DDPAuth getDDPAuth(Request req) {
        DDPAuth ddpAuth = req.attribute(TokenConverterFilter.DDP_TOKEN);
        if (ddpAuth == null) {
            ddpAuth = new DDPAuth();
        }
        return ddpAuth;
    }

    public static LanguageDto getUserLanguage(Request req) {
        return req.attribute(StudyLanguageResolutionFilter.USER_LANGUAGE);
    }

    public static String makeAuthBearerHeader(String headerValue) {
        return BEARER + headerValue;
    }

    /**
     * Gets the internal guid of the client for this request. May be null.
     */
    public static String getClientGuid(Request req) {
        return getDDPAuth(req).getClient();
    }

    public static String getAcceptLanguageHeader(Request req) {
        return req.headers("Accept-Language");
    }

    /**
     * Helper to parse out the content style header. The default is returned if header is missing, and the request is halted with an error
     * response if the given style is invalid.
     *
     * @param request      the request
     * @param response     the response
     * @param defaultStyle the default style to use
     * @return the content style
     */
    public static ContentStyle parseContentStyleHeaderOrHalt(Request request, Response response, ContentStyle defaultStyle) {
        String value = request.headers(RouteConstants.Header.DDP_CONTENT_STYLE);
        if (value == null) {
            return defaultStyle;
        }

        try {
            // Massage the value to match enum, and in turn allow a bit of flexibility to clients.
            return ContentStyle.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            LOG.warn("Unable to convert ddp content style header value '{}'", value, e);
            String styleValues = Arrays.stream(ContentStyle.values())
                    .map(ContentStyle::name)
                    .collect(Collectors.joining(", "));
            String msg = String.format("Invalid content style: '%s'. Should be one of: %s.", value, styleValues);
            ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.MALFORMED_HEADER, msg));
            return null;
        }
    }

    /**
     * Grab the study guid from the URI path. This assumes it is a study path and does a bit of sanity check for that.
     *
     * @param path the URI path
     * @return the study guid or null if the study could not be parsed
     */
    public static String parseStudyGuid(String path) {
        List<String> parts = SparkUtils.convertRouteToList(path);
        String studyGuid = null;
        if (parts.size() > STUDY_GUID_IDX && STUDIES_PATH_MARKER.equals(parts.get(STUDIES_MARKER_IDX))) {
            studyGuid = parts.get(STUDY_GUID_IDX);
        }
        return studyGuid;
    }

    /**
     * Grab the study guid from the Admin URI path, e.g. `/pepper/v1/admin/studies/...`.
     *
     * @param path the admin URI path
     * @return the study guid or null if the study could not be parsed
     */
    public static String parseAdminStudyGuid(String path) {
        List<String> parts = SparkUtils.convertRouteToList(path);
        String studyGuid = null;
        if (parts.size() > ADMIN_STUDY_GUID_IDX && STUDIES_PATH_MARKER.equals(parts.get(ADMIN_STUDIES_MARKER_IDX))) {
            studyGuid = parts.get(ADMIN_STUDY_GUID_IDX);
        }
        return studyGuid;
    }

    public static UserActivityInstanceSummary findUserActivityInstanceSummaryOrHalt(Response response, Handle handle,
                                                                                    String participantGuid, String studyGuid,
                                                                                    String instanceGuid,
                                                                                    boolean isOperatorStudyAdmin) {
        var found = findUserAndStudyOrHalt(handle, participantGuid, studyGuid);
        var studyDto = found.getStudyDto();
        User user = found.getUser();

        Supplier<HaltException> handleMissing = () -> {
            String msg = "Could not find activity instance with guid " + instanceGuid;
            return ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.ACTIVITY_NOT_FOUND, msg));
        };


        UserActivityInstanceSummary activityInstanceSummary = handle.attach(JdbiActivityInstance.class)
                .getActivityInstanceSummary(user, studyDto.getId())
                .orElseThrow(handleMissing);

        ActivityInstanceDto instanceDto = activityInstanceSummary.getActivityInstanceByGuid(instanceGuid).orElseThrow(handleMissing);

        haltIfError(user, participantGuid, studyGuid, instanceGuid, studyDto, instanceDto, response, isOperatorStudyAdmin);

        return activityInstanceSummary;
    }

    /**
     * Get the activity instance, ensuring the study/user/instance exists and the instance is accessible. Otherwise,
     * will set the appropriate route response.
     *
     * @param response             the route response
     * @param handle               the database handle
     * @param participantGuid      the user guid
     * @param studyGuid            the study guid
     * @param instanceGuid         the activity instance guid
     * @param isOperatorStudyAdmin whether the operator user is study admin
     * @return the activity instance dto
     */
    public static ActivityInstanceDto findAccessibleInstanceOrHalt(Response response, Handle handle,
                                                                   String participantGuid, String studyGuid, String instanceGuid,
                                                                   boolean isOperatorStudyAdmin) {
        var found = findUserAndStudyOrHalt(handle, participantGuid, studyGuid);
        var studyDto = found.getStudyDto();
        var user = found.getUser();

        ActivityInstanceDto instanceDto = handle.attach(JdbiActivityInstance.class)
                .getByActivityInstanceGuid(instanceGuid)
                .orElseThrow(() -> {
                    String msg = "Could not find activity instance with guid " + instanceGuid;
                    return ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.ACTIVITY_NOT_FOUND, msg));
                });

        haltIfError(user, participantGuid, studyGuid, instanceGuid, studyDto, instanceDto, response, isOperatorStudyAdmin);

        return instanceDto;
    }

    public static ActivityInstanceDto findAccessibleInstanceOrHalt(Response response, Handle handle,
                                                                   User participantUser, StudyDto studyDto,
                                                                   String instanceGuid, boolean isOperatorStudyAdmin) {
        ActivityInstanceDto instanceDto = handle.attach(JdbiActivityInstance.class)
                .getByActivityInstanceGuid(instanceGuid)
                .orElseThrow(() -> {
                    String msg = "Could not find activity instance with guid " + instanceGuid;
                    return ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.ACTIVITY_NOT_FOUND, msg));
                });
        haltIfError(participantUser, participantUser.getGuid(),
                studyDto.getGuid(), instanceGuid, studyDto,
                instanceDto, response, isOperatorStudyAdmin);
        return instanceDto;
    }

    public static void haltIfError(User user, String participantGuid, String studyGuid, String instanceGuid, StudyDto studyDto,
                                   ActivityInstanceDto instanceDto, Response response, boolean isOperatorStudyAdmin) {
        if (instanceDto.getStudyId() != studyDto.getId() || instanceDto.getParticipantId() != user.getId()) {
            LOG.warn("Activity instance {} does not belong to participant {} in study {}", instanceGuid, participantGuid, studyGuid);
            LOG.warn("InstanceDTO: " + new Gson().toJson(instanceDto));
            LOG.warn("User: " + new Gson().toJson(user));
            LOG.warn("StudyDto: " + new Gson().toJson(studyDto));
            String msg = "Could not find activity instance with guid " + instanceGuid;
            throw ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.ACTIVITY_NOT_FOUND, msg));
        } else if (!isOperatorStudyAdmin && instanceDto.isHidden()) {
            String msg = "Activity instance " + instanceDto.getGuid() + " is hidden and cannot be retrieved or interacted with";
            throw ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.ACTIVITY_NOT_FOUND, msg));
        } else if (!isOperatorStudyAdmin && user.isTemporary() && !instanceDto.isAllowUnauthenticated()) {
            String msg = "Activity instance " + instanceDto.getGuid() + " not accessible to unauthenticated users";
            throw ResponseUtil.haltError(response, 401, new ApiError(ErrorCodes.OPERATION_NOT_ALLOWED, msg));
        }
    }


    /**
     * Resolve the language to use for request. If study guid is provided, will lookup study's supported languages.
     *
     * @param request             the request, which might contain the Accept-Language header
     * @param handle              the database handle
     * @param studyGuid           the study guid, if available
     * @param userPreferredLocale the user's preferred language, if available
     * @return resolved language code
     */
    public static String resolveLanguage(Request request, Handle handle, String studyGuid, Locale userPreferredLocale) {
        String acceptLanguageHeader = request.headers(RouteConstants.Header.ACCEPT_LANGUAGE);
        Locale locale = I18nUtil.resolveLocale(handle, studyGuid, userPreferredLocale, acceptLanguageHeader);
        return locale.getLanguage();
    }

    /**
     * Find the user and study, halting request if either is not found. If not already set, caller should set the
     * `application/json` header on the response manually to ensure response is interpreted properly.
     *
     * @param handle    the database handle
     * @param userGuid  the user guid
     * @param studyGuid the study guid
     * @return user and study
     */
    public static UserAndStudy findUserAndStudyOrHalt(Handle handle, String userGuid, String studyGuid) {
        StudyDto studyDto = new JdbiUmbrellaStudyCached(handle).findByStudyGuid(studyGuid);
        if (studyDto == null) {
            var err = new ApiError(ErrorCodes.STUDY_NOT_FOUND, "Could not find study with guid " + studyGuid);
            LOG.warn(err.getMessage());
            throw ResponseUtil.haltError(HttpStatus.SC_NOT_FOUND, err);
        }
        User user = handle.attach(UserDao.class).findUserByGuid(userGuid).orElse(null);
        if (user == null) {
            var err = new ApiError(ErrorCodes.USER_NOT_FOUND, "Could not find user with guid " + userGuid);
            LOG.warn(err.getMessage());
            throw ResponseUtil.haltError(HttpStatus.SC_NOT_FOUND, err);
        }
        return new UserAndStudy(user, studyDto);
    }

    /**
     * Find user and study. User may be a legacy user.
     *
     * @param handle           the database handle
     * @param userGuidOrAltPid the user guid or altpid
     * @param studyGuid        the study guid
     * @return user and study
     */
    public static UserAndStudy findPotentiallyLegacyUserAndStudyOrHalt(Handle handle, String userGuidOrAltPid, String studyGuid) {
        StudyDto studyDto = new JdbiUmbrellaStudyCached(handle).findByStudyGuid(studyGuid);
        if (studyDto == null) {
            var err = new ApiError(ErrorCodes.STUDY_NOT_FOUND, "Could not find study with guid " + studyGuid);
            LOG.warn(err.getMessage());
            throw ResponseUtil.haltError(HttpStatus.SC_NOT_FOUND, err);
        }
        User user = handle.attach(UserDao.class).findUserByGuidOrAltPid(userGuidOrAltPid).orElse(null);
        if (user == null) {
            var err = new ApiError(ErrorCodes.USER_NOT_FOUND, "Could not find user with guid/legacy_altpid " + userGuidOrAltPid);
            LOG.warn(err.getMessage());
            throw ResponseUtil.haltError(HttpStatus.SC_NOT_FOUND, err);
        }
        return new UserAndStudy(user, studyDto);
    }

    public static final class UserAndStudy {
        private final User user;
        private final StudyDto studyDto;

        public UserAndStudy(User user, StudyDto studyDto) {
            this.user = user;
            this.studyDto = studyDto;
        }

        public User getUser() {
            return user;
        }

        public StudyDto getStudyDto() {
            return studyDto;
        }
    }
}
