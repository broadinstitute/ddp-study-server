package org.broadinstitute.ddp.route;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;

import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudyCached;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudyI18n;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.StudyDao;
import org.broadinstitute.ddp.db.dto.EnrollmentStatusDto;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.StudyI18nDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.study.EnrollmentStatusCount;
import org.broadinstitute.ddp.model.study.StudyDetail;
import org.broadinstitute.ddp.model.study.StudySettings;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Request;
import spark.Response;
import spark.Route;

public class GetStudyDetailRoute implements Route {
    private static final Logger LOG = LoggerFactory.getLogger(GetStudyDetailRoute.class);

    @Override
    public StudyDetail handle(Request request, Response response) throws Exception {
        final String studyIdentifier = request.params(RouteConstants.PathParam.STUDY_GUID);

        if (StringUtils.isBlank(studyIdentifier)) {
            ApiError error = new ApiError(ErrorCodes.MISSING_STUDY_GUID, "Missing study guid");
            ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST, error);
        }

        LOG.debug("Received request for details of study {}", studyIdentifier);

        DDPAuth authInfo = RouteUtil.getDDPAuth(request);

        StudyDetail detail = TransactionWrapper.withTxn(handle -> {
            JdbiUserStudyEnrollment enrollmentDao = handle.attach(JdbiUserStudyEnrollment.class);
            JdbiUmbrellaStudy studyDao = new JdbiUmbrellaStudyCached(handle);
            JdbiUmbrellaStudyI18n translationDao = handle.attach(JdbiUmbrellaStudyI18n.class);

            StudyDto study = studyDao.findByStudyGuid(studyIdentifier);
            if (null == study) {
                LOG.info("[{}] Request failed for study with identifier '{}': Study not found", request.ip(), studyIdentifier);
                ApiError error = new ApiError(ErrorCodes.STUDY_NOT_FOUND, "Study " + studyIdentifier + " does not exist");
                ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, error);
            }

            LanguageDto preferredLanguage = RouteUtil.getUserLanguage(request);
            Optional<StudyI18nDto> preferredTranslation = translationDao.findTranslationByStudyIdAndLanguageCodeId(
                    study.getId(),
                    preferredLanguage.getId()
            );

            List<EnrollmentStatusDto> enrollments = enrollmentDao.findByStudyGuid(studyIdentifier);
            if (null == enrollments) {
                // According to http://jdbi.org/#__sqlquery, methods that return collections should
                // always be non-null
                LOG.warn("JdbiUserStudyEnrollment.findByStudyGuid( {} ) returned a null value", studyIdentifier);
                throw new DDPException("JDBI unexpectedly returned a null collection");
            }

            EnrollmentStatusCount enrollmentStatusCount = EnrollmentStatusCount.getEnrollmentStatusCountByEnrollments(enrollments);

            String name = preferredTranslation.map(trans -> trans.getName()).orElse(study.getName());
            String summary = preferredTranslation.map(trans -> trans.getSummary()).orElse(null);

            Boolean shouldDisplayLanguageChangePopup = handle.attach(StudyDao.class)
                  .findSettings(study.getId())
                  .map(StudySettings::shouldDisplayLanguageChangePopup)
                  .orElse(false);

            boolean restricted = study.getIrbPassword() != null;
            return new StudyDetail(
                    study.getGuid(),
                    name,
                    summary,
                    enrollmentStatusCount.getRegisteredCount(),
                    enrollmentStatusCount.getParticipantCount(),
                    restricted,
                    study.getStudyEmail(),
                    shouldDisplayLanguageChangePopup
            );
        });

        return detail;
    }
}
