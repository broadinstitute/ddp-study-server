package org.broadinstitute.ddp.route;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.json.security.IrbStudyStudyCredentials;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class CheckIrbPasswordRoute extends ValidatedJsonInputRoute<IrbStudyStudyCredentials> {

    private static final Logger LOG = LoggerFactory.getLogger(CheckIrbPasswordRoute.class);

    @Override
    public Object handle(Request request, Response response, IrbStudyStudyCredentials paswordHolder) {
        String studyGuid = request.params(PathParam.STUDY_GUID);
        String passwordToBeChecked = paswordHolder.getPassword();
        return TransactionWrapper.withTxn(
                handle -> {
                    StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
                    boolean studyNotFound = studyDto == null;
                    if (studyNotFound) {
                        String errMsg = "A study with GUID " + studyGuid + " you try to get data for is not found";
                        ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.NOT_FOUND, errMsg));
                    }
                    boolean passwordCheckResult = true;
                    boolean isPasswordApplicable = studyDto.getIrbPassword() != null;
                    // Passwords are not mandatory and access for some studies can be passwordless
                    // We do not impose any restrictions on the stored password's length and content
                    if (isPasswordApplicable) {
                        // NULL or empty passwords supplied by the user are not allowed
                        if (StringUtils.isBlank(passwordToBeChecked)) {
                            LOG.info("A password is required for the study with GUID {}, but it was not specified", studyGuid);
                            passwordCheckResult = false;
                        } else {
                            // The user-supplied password is well-formed, comparing it with the stored one
                            passwordCheckResult = studyDto.getIrbPassword().equals(passwordToBeChecked);
                            String passwordCheckResultMessage = passwordCheckResult ? "valid" : "invalid";
                            String passwordMasked = passwordToBeChecked.replaceAll("\\w", "*");
                            LOG.info(
                                    "The provided IRB password {} for the study with GUID {} is {}",
                                    passwordMasked, studyGuid, passwordCheckResultMessage
                            );
                        }
                    } else {
                        // We assume that having no password for the study means unrestricted access
                        // and thus perform no password check, just log the event
                        LOG.info("The password is not defined for the study with GUID {}, so access is unrestricted", studyGuid);
                    }
                    Map<String, Boolean> responseBody = new HashMap<>();
                    responseBody.put("result", passwordCheckResult);
                    return responseBody;
                }
        );
    }

}
