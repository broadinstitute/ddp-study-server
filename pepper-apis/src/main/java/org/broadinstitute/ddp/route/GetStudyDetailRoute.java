package org.broadinstitute.ddp.route;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudyI18n;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dto.EnrollmentStatusDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.StudyI18nDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.study.EnrollmentStatusCount;
import org.broadinstitute.ddp.model.study.StudyDetail;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.util.I18nUtil;
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
    public Object handle(Request request, Response response) throws Exception {
        final String studyIdentifier = request.params(RouteConstants.PathParam.STUDY_GUID);

        if (StringUtils.isBlank(studyIdentifier)) {
            ApiError error = new ApiError(ErrorCodes.MISSING_STUDY_GUID, "Missing study guid");
            ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST, error);
        }

        LOG.debug("Received request for details of study {}", studyIdentifier);

        List<LanguageRange> acceptLanguages;
        String acceptLanguageHeader = request.headers(RouteConstants.ACCEPT_LANGUAGE);
        if (StringUtils.isNotEmpty(acceptLanguageHeader)) {
            acceptLanguages = LanguageRange.parse(acceptLanguageHeader);
        } else {
            acceptLanguages = new ArrayList<LanguageRange>();
        }

        DDPAuth authInfo = RouteUtil.getDDPAuth(request);

        StudyDetail detail = TransactionWrapper.withTxn(handle -> {
            JdbiUserStudyEnrollment enrollmentDao = handle.attach(JdbiUserStudyEnrollment.class);
            JdbiUmbrellaStudy studyDao = handle.attach(JdbiUmbrellaStudy.class);
            JdbiUmbrellaStudyI18n translationDao = handle.attach(JdbiUmbrellaStudyI18n.class);

            StudyDto study = studyDao.findByStudyGuid(studyIdentifier);
            if (null == study) {
                LOG.info("[{}] Request failed for study with identifier '{}': Study not found", request.ip(), studyIdentifier);
                ApiError error = new ApiError(ErrorCodes.STUDY_NOT_FOUND, "Study " + studyIdentifier + " does not exist");
                ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, error);
            }

            // Fetch a list of all the available translations for this particular study,
            //  convert them into a set of Locale objects, and stash them away in a map
            //  for easy access later.
            List<StudyI18nDto> translations = translationDao.findTranslationsByStudyId(study.getId());
            Map<Locale, StudyI18nDto> availableTranslations = new HashMap<Locale, StudyI18nDto>();
            translations.forEach((translation) -> {
                Locale locale = Locale.forLanguageTag(translation.getLanguageCode());
                availableTranslations.put(locale, translation);
            });

            Locale userLocale = I18nUtil.resolvePreferredLanguage(authInfo.getPreferredLocale(), acceptLanguages,
                    availableTranslations.keySet());
            StudyI18nDto preferredTranslation = availableTranslations.get(userLocale);

            List<EnrollmentStatusDto> enrollments = enrollmentDao.findByStudyGuid(studyIdentifier);
            if (null == enrollments) {
                // According to http://jdbi.org/#__sqlquery, methods that return collections should
                // always be non-null
                LOG.warn("JdbiUserStudyEnrollment.findByStudyGuid( {} ) returned a null value", studyIdentifier);
                throw new DDPException("JDBI unexpectedly returned a null collection");
            }

            EnrollmentStatusCount enrollmentStatusCount = EnrollmentStatusCount.getEnrollmentStatusCountByEnrollments(enrollments);

            String name;
            String summary;
            if (null != preferredTranslation) {
                name = preferredTranslation.getName();
                summary = preferredTranslation.getSummary();
            } else {
                name = study.getName();
                summary = null;
            }

            boolean restricted = study.getIrbPassword() != null;
            return new StudyDetail(
                    study.getGuid(),
                    name,
                    summary,
                    enrollmentStatusCount.getRegisteredCount(),
                    enrollmentStatusCount.getParticipantCount(),
                    restricted,
                    study.getStudyEmail()
            );
        });

        return detail;
    }
}
