package org.broadinstitute.ddp.route;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.db.ActivityInstanceDao;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;
import org.broadinstitute.ddp.model.activity.types.ActivityType;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.service.ActivityInstanceService;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class GetActivityInstanceRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(GetActivityInstanceRoute.class);
    private static final String DEFAULT_ISO_LANGUAGE_CODE = "en";

    private ActivityInstanceService actInstService;
    private ActivityInstanceDao activityInstanceDao;

    public GetActivityInstanceRoute(ActivityInstanceService actInstService, ActivityInstanceDao activityInstanceDao) {
        this.actInstService = actInstService;
        this.activityInstanceDao = activityInstanceDao;
    }

    @Override
    public Object handle(Request request, Response response) {
        String userGuid = request.params(PathParam.USER_GUID);
        String studyGuid = request.params(PathParam.STUDY_GUID);
        String activityInstanceGuid = request.params(PathParam.INSTANCE_GUID);
        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);

        LOG.info("Attempting to retrieve activity instance {} for participant {} in study {}", activityInstanceGuid, userGuid, studyGuid);

        return TransactionWrapper.withTxn(handle -> {
            //check activity allow_unauthenticated flag for temp users
            UserDto userDto = handle.attach(JdbiUser.class).findByUserGuid(userGuid);
            if (userDto.isTemporary()) {
                Optional<ActivityInstanceDto> activityInstanceDto =
                        handle.attach(JdbiActivityInstance.class).getByActivityInstanceGuid(activityInstanceGuid);
                if (activityInstanceDto.isEmpty()) {
                    String msg = "Activity instance " + activityInstanceGuid + " is not found";
                    throw ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.ACTIVITY_NOT_FOUND, msg));
                } else if (!activityInstanceDto.get().isAllowUnauthenticated()) {
                    String msg = "Activity instance " + activityInstanceGuid + " not accessible to unauthenticated users";
                    throw ResponseUtil.haltError(response, 401, new ApiError(ErrorCodes.OPERATION_NOT_ALLOWED, msg));
                }
            }

            ContentStyle style = RouteUtil.parseContentStyleHeaderOrHalt(request, response, ContentStyle.STANDARD);
            LOG.info("Using ddp content style {} to format activity content", style);

            ActivityType activityType = activityInstanceDao.getActivityTypeByGuids(handle, userGuid, studyGuid, activityInstanceGuid);
            if (activityType == null) {
                String msg = "Activity instance " + activityInstanceGuid + " is not found";
                ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.ACTIVITY_NOT_FOUND, msg));
            }

            String preferredIsoLanguageCode = ddpAuth.getPreferredLanguage();
            List<String> isoLangCodes = Arrays.asList(preferredIsoLanguageCode, DEFAULT_ISO_LANGUAGE_CODE);

            Optional<EnrollmentStatusType> enrollmentStatus = handle.attach(JdbiUserStudyEnrollment.class)
                    .getEnrollmentStatusByUserAndStudyGuids(userGuid, studyGuid);
            // Return an activity instance translated to the first available language
            LOG.info("Attempting to find a translation for the following languages: {}", isoLangCodes);
            for (String isoLangCode : isoLangCodes) {
                Optional<ActivityInstance> inst = actInstService.getTranslatedActivity(
                        handle, userGuid, activityType, activityInstanceGuid, isoLangCode, style
                );
                if (inst.isPresent()) {
                    LOG.info("Found a translation to the '{}' language code for the activity instance with GUID {}",
                            isoLangCode, activityInstanceGuid);
                    ActivityInstance activityInstance = inst.get();
                    // To-do: change this to just "if (enrollmentStatus.get() == EnrollmentStatusType.EXITED_BEFORE_ENROLLMENT)) {...}"
                    // when every user registered in the system will become enrolled automatically
                    // When it is implemented, the check for the enrollment status presence is not needed
                    if (enrollmentStatus.isPresent() && EnrollmentStatusType.getAllExitedStates().contains(enrollmentStatus.get())) {
                        activityInstance.makeReadonly();
                    }
                    // end To-do
                    return activityInstance;
                } else {
                    LOG.info("Failed to find a translation to the '{}' language code for the activity instance "
                            + "with GUID {}, keep on searching", isoLangCode, activityInstanceGuid);
                }
            }

            throw new DDPException("Unable to find activity instance " + activityInstanceGuid + " of type " + activityType);
        });
    }
}
