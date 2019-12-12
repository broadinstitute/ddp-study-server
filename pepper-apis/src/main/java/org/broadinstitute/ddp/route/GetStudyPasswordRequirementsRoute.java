package org.broadinstitute.ddp.route;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiStudyPasswordRequirements;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.StudyPasswordRequirementsDto;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.study.StudyPasswordRequirements;
import org.broadinstitute.ddp.util.ResponseUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Request;
import spark.Response;
import spark.Route;

public class GetStudyPasswordRequirementsRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(GetStudyPasswordRequirementsRoute.class);

    @Override
    public StudyPasswordRequirements handle(Request request, Response response) {
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        // Malformed study GUID
        if (StringUtils.isBlank(studyGuid)) {
            String errMsg = "Study GUID is blank";
            LOG.warn(errMsg);
            throw ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.MISSING_STUDY_GUID, errMsg));
        }
        return TransactionWrapper.withTxn(
                handle -> {
                    StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
                    // Non-existent study
                    if (studyDto == null) {
                        String errMsg = "Study with GUID " + studyGuid + " does not exist";
                        LOG.warn(errMsg);
                        throw ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.STUDY_NOT_FOUND, errMsg));
                    }
                    Optional<StudyPasswordRequirementsDto> passwdReqDto = handle.attach(
                            JdbiStudyPasswordRequirements.class
                    ).getById(studyDto.getAuth0TenantId());
                    // Missing password requirements
                    if (!passwdReqDto.isPresent()) {
                        String errMsg = "Could not find password requirements for the study with GUID " + studyGuid;
                        LOG.warn(errMsg);
                        throw ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.STUDY_PASSWORD_REQUIREMENTS_NOT_FOUND, errMsg));
                    }
                    return new StudyPasswordRequirements(passwdReqDto.get());
                }
        );
    }

}
