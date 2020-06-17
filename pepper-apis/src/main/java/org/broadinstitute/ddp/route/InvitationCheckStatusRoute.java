package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.json.invitation.InvitationCheckStatusPayload.QUALIFICATION_ZIP_CODE;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.client.GoogleRecaptchaVerifyClient;
import org.broadinstitute.ddp.client.GoogleRecaptchaVerifyResponse;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.InvitationDao;
import org.broadinstitute.ddp.db.dao.JdbiClientUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.KitConfigurationDao;
import org.broadinstitute.ddp.db.dao.TemplateDao;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.json.invitation.InvitationCheckStatusPayload;
import org.broadinstitute.ddp.model.kit.KitRule;
import org.broadinstitute.ddp.model.kit.KitRuleType;
import org.broadinstitute.ddp.model.kit.KitZipCodeRule;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

/**
 * "check" here means we're checking the status of an invitation, e.g. does it exists and is valid?
 *
 * <p>NOTE: this is a public route. Be careful what we return in responses.
 */
public class InvitationCheckStatusRoute extends ValidatedJsonInputRoute<InvitationCheckStatusPayload> {

    private static final Logger LOG = LoggerFactory.getLogger(InvitationCheckStatusRoute.class);
    private static final String DEFAULT_ZIP_CODE_ERROR_MSG = "Invalid zip code invitation qualification";

    @Override
    protected int getValidationErrorStatus() {
        return HttpStatus.SC_BAD_REQUEST;
    }

    @Override
    public Object handle(Request request, Response response, InvitationCheckStatusPayload payload) throws Exception {
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        String invitationGuid = payload.getInvitationGuid();
        LOG.info("Attempting to check invitation {} in study {}", invitationGuid, studyGuid);

        ApiError error = TransactionWrapper.withTxn(handle -> {
            String langCode = RouteUtil.resolveLanguage(request, handle, studyGuid, null);
            return checkStatus(handle, studyGuid, request.ip(), langCode, payload);
        });

        if (error != null) {
            throw ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST, error);
        } else {
            response.status(HttpStatus.SC_OK);
            return null;
        }
    }

    ApiError checkStatus(Handle handle, String studyGuid, String ipAddress, String langCode, InvitationCheckStatusPayload payload) {
        StudyDto studyDto = findStudy(handle, studyGuid);
        if (studyDto == null) {
            LOG.error("Invitation check called for non-existent study with guid {}", studyGuid);
            return new ApiError(ErrorCodes.INVALID_INVITATION, "Invalid invitation");
        }

        if (studyDto.getRecaptchaSiteKey() == null) {
            LOG.error("ReCaptcha has not been enabled for study with guid: {}", studyGuid);
            throw new DDPException("Server configuration problem");
        }
        if (!isUserRecaptchaTokenValid(payload.getRecaptchaToken(), studyDto.getRecaptchaSiteKey(), ipAddress)) {
            return new ApiError(ErrorCodes.BAD_PAYLOAD, "Request was invalid");
        }

        String invitationGuid = payload.getInvitationGuid();

        List<String> permittedStudies = handle.attach(JdbiClientUmbrellaStudy.class)
                .findPermittedStudyGuidsByAuth0ClientIdAndAuth0TenantId(payload.getAuth0ClientId(), studyDto.getAuth0TenantId());
        if (permittedStudies.contains(studyGuid)) {
            LOG.info("Invitation check by client clientId={}, study={}",
                    payload.getAuth0ClientId(), studyGuid);
        } else {
            LOG.error("Invitation check by client which does not have access to study: clientId={}, study={}",
                    payload.getAuth0ClientId(), studyGuid);
            return new ApiError(ErrorCodes.INVALID_INVITATION, "Invalid invitation");
        }

        InvitationDao invitationDao = handle.attach(InvitationDao.class);
        InvitationDto invitation = invitationDao.findByInvitationGuid(studyDto.getId(), invitationGuid).orElse(null);
        if (invitation == null) {
            // It might just be a typo, so do a warn instead of error log.
            LOG.warn("Invitation {} does not exist", invitationGuid);
            return new ApiError(ErrorCodes.INVALID_INVITATION, "Invalid invitation");
        } else if (invitation.isVoid()) {
            LOG.error("Invitation {} is voided", invitationGuid);
            return new ApiError(ErrorCodes.INVALID_INVITATION, "Invalid invitation");
        } else if (invitation.isAccepted()) {
            LOG.error("Invitation {} has already been accepted", invitationGuid);
            return new ApiError(ErrorCodes.INVALID_INVITATION, "Invalid invitation");
        } else {
            LOG.info("Invitation {} is valid", invitationGuid);
        }

        List<List<KitRule>> kitZipCodeRules = handle.attach(KitConfigurationDao.class)
                .findStudyKitConfigurations(studyDto.getId())
                .stream()
                .map(kit -> kit.getRules().stream()
                        .filter(rule -> rule.getType() == KitRuleType.ZIP_CODE)
                        .collect(Collectors.toList()))
                .filter(rules -> !rules.isEmpty())
                .collect(Collectors.toList());

        if (kitZipCodeRules.isEmpty()) {
            LOG.info("Study {} does not have any kit configurations that has kit zip code rules", studyGuid);
        } else {
            LOG.info("Study {} has {} kit configurations which has kit zip code rules,"
                            + " checking user's zip code qualification to ensure it matches for all these kits",
                    studyGuid, kitZipCodeRules.size());
            String userZipCode = (String) payload.getQualificationDetails().getOrDefault(QUALIFICATION_ZIP_CODE, "");
            for (var rules : kitZipCodeRules) {
                boolean matched = rules.stream().anyMatch(rule -> rule.validate(handle, userZipCode));
                if (!matched) {
                    LOG.warn("User provided zip code does not match, invitation={} zipCode={}", invitationGuid, userZipCode);
                    String msg = DEFAULT_ZIP_CODE_ERROR_MSG;
                    Long errorTmplId = rules.stream()
                            .map(rule -> ((KitZipCodeRule) rule).getErrorMessageTemplateId())
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElse(null);
                    if (errorTmplId != null) {
                        msg = handle.attach(TemplateDao.class).loadTemplateById(errorTmplId).render(langCode);
                    }
                    return new ApiError(ErrorCodes.INVALID_INVITATION_QUALIFICATIONS, msg);
                }
            }
            LOG.info("User provided zip code {} matched for all kit configurations", userZipCode);
        }

        return null;
    }

    StudyDto findStudy(Handle handle, String studyGuid) {
        return handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
    }

    boolean isUserRecaptchaTokenValid(String recaptchaToken, String recaptchaSiteKey, String clientIpAddress) {
        var recaptchaVerifier = new GoogleRecaptchaVerifyClient(recaptchaSiteKey);
        GoogleRecaptchaVerifyResponse recaptchaResponse = recaptchaVerifier.verifyRecaptchaResponse(recaptchaToken, clientIpAddress);
        if (!recaptchaResponse.isSuccess()) {
            LOG.error("Recaptcha validation was unsuccessful: {}", new Gson().toJson(recaptchaResponse));
        }
        return recaptchaResponse.isSuccess();
    }

    @Override
    protected Class<InvitationCheckStatusPayload> getTargetClass(Request request) {
        return InvitationCheckStatusPayload.class;
    }
}
