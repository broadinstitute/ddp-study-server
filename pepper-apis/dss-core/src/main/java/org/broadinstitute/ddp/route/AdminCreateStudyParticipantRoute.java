package org.broadinstitute.ddp.route;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.InvitationDao;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.StudyLanguageCachedDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.event.publish.TaskPublisher;
import org.broadinstitute.ddp.event.publish.pubsub.TaskPubSubPublisher;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.admin.CreateStudyParticipantPayload;
import org.broadinstitute.ddp.json.admin.CreateStudyParticipantResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.study.StudyLanguage;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import spark.Request;
import spark.Response;

@Slf4j
@AllArgsConstructor
public class AdminCreateStudyParticipantRoute extends ValidatedJsonInputRoute<CreateStudyParticipantPayload> {
    private final TaskPublisher taskPublisher;

    @Override
    protected int getValidationErrorStatus() {
        return HttpStatus.SC_BAD_REQUEST;
    }

    @Override
    public Object handle(Request request, Response response, CreateStudyParticipantPayload payload) {
        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        String invitationGuid = payload.getInvitationGuid();

        log.info("Attempting to create participant in study {} with invitation {} by operator {}",
                studyGuid, invitationGuid, ddpAuth.getOperator());

        return TransactionWrapper.withTxn(handle -> {
            StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
            if (studyDto == null) {
                String msg = "Could not find study with guid " + studyGuid;
                log.warn(msg);
                throw ResponseUtil.haltError(HttpStatus.SC_NOT_FOUND, new ApiError(ErrorCodes.NOT_FOUND, msg));
            }

            Auth0TenantDto tenantDto = handle.attach(JdbiAuth0Tenant.class).findById(studyDto.getAuth0TenantId())
                    .orElseThrow(() -> new DDPException("Could not find auth0 tenant for study " + studyGuid));

            var invitationDao = handle.attach(InvitationDao.class);
            InvitationDto invitation = invitationDao.findByInvitationGuid(studyDto.getId(), invitationGuid).orElse(null);
            if (invitation == null) {
                String msg = "Could not find invitation " + invitationGuid;
                log.warn(msg);
                throw ResponseUtil.haltError(HttpStatus.SC_NOT_FOUND, new ApiError(ErrorCodes.NOT_FOUND, msg));
            } else if (invitation.isVoid()) {
                String msg = String.format("Invitation %s is voided", invitationGuid);
                log.error(msg);
                throw ResponseUtil.haltError(HttpStatus.SC_BAD_REQUEST, new ApiError(ErrorCodes.INVALID_INVITATION, msg));
            } else if (invitation.isAccepted()) {
                String msg = String.format("Invitation %s has already been accepted", invitationGuid);
                log.info(msg);
                throw ResponseUtil.haltError(HttpStatus.SC_BAD_REQUEST, new ApiError(ErrorCodes.INVALID_INVITATION, msg));
            } else if (invitation.getUserId() != null) {
                String msg = String.format("Invitation %s has already been assigned to another user", invitationGuid);
                log.error(msg);
                throw ResponseUtil.haltError(HttpStatus.SC_BAD_REQUEST, new ApiError(ErrorCodes.INVALID_INVITATION, msg));
            }

            User user = handle.attach(UserDao.class).createUser(tenantDto.getDomain(), ddpAuth.getClient(), null);
            log.info("Created user with guid {}", user.getGuid());

            long defaultLangId = new StudyLanguageCachedDao(handle)
                    .findLanguages(studyDto.getId())
                    .stream()
                    .filter(StudyLanguage::isDefault)
                    .findFirst()
                    .map(StudyLanguage::toLanguageDto)
                    .orElseGet(LanguageStore::getDefault)
                    .getId();

            UserProfile profile = UserProfile.builder()
                    .userId(user.getId())
                    .preferredLangId(defaultLangId)
                    .preferredLangCode(null)
                    .build();
            handle.attach(UserProfileDao.class).createProfile(profile);
            log.info("Created profile for user {}", user.getGuid());

            handle.attach(JdbiUserStudyEnrollment.class)
                    .changeUserStudyEnrollmentStatus(user.getGuid(), studyGuid, EnrollmentStatusType.REGISTERED);
            log.info("Registered user {} with status {} in study {}", user.getGuid(), EnrollmentStatusType.REGISTERED, studyGuid);

            invitationDao.assignAcceptingUser(invitation.getInvitationId(), user.getId(), Instant.now());
            log.info("Assigned invitation {} to user {}", invitationGuid, user.getGuid());

            handle.attach(DataExportDao.class).queueDataSync(user.getId());
            taskPublisher.publishTask(
                    TaskPubSubPublisher.TASK_PARTICIPANT_REGISTERED,
                    "", studyGuid, user.getGuid());

            response.status(HttpStatus.SC_CREATED);
            return new CreateStudyParticipantResponse(user.getGuid());
        });
    }
}
