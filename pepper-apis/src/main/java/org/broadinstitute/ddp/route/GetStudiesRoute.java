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
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.study.EnrollmentStatusCount;
import org.broadinstitute.ddp.model.study.StudySummary;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.util.I18nUtil;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class GetStudiesRoute implements Route {
    private static final Logger LOG = LoggerFactory.getLogger(GetStudiesRoute.class);

    @Override
    public Object handle(Request request, Response response) throws Exception {
        final String umbrellaIdentifier = request.queryParams(RouteConstants.QueryParam.UMBRELLA);

        if (StringUtils.isBlank(umbrellaIdentifier)) {
            ApiError error = new ApiError(ErrorCodes.REQUIRED_PARAMETER_MISSING, "Missing umbrella parameter");
            ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST, error);
        }

        LOG.debug("Received request for summary of studies under {}", umbrellaIdentifier);

        List<LanguageRange> acceptLanguages;
        String acceptLanguageHeader = request.headers(RouteConstants.ACCEPT_LANGUAGE);
        if (StringUtils.isNotEmpty(acceptLanguageHeader)) {
            acceptLanguages = LanguageRange.parse(acceptLanguageHeader);
        } else {
            acceptLanguages = new ArrayList<>();
        }

        DDPAuth authInfo = RouteUtil.getDDPAuth(request);

        return TransactionWrapper.withTxn(handle -> {
            JdbiUmbrellaStudy studyDao = handle.attach(JdbiUmbrellaStudy.class);
            JdbiUserStudyEnrollment enrollmentDao = handle.attach(JdbiUserStudyEnrollment.class);

            List<StudyDto> studiesDto = studyDao.findByUmbrella(umbrellaIdentifier);
            if (studiesDto.isEmpty()) {
                ApiError error = new ApiError(ErrorCodes.NOT_FOUND, "No studies for umbrella");
                ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, error);
            }

            List<StudySummary> umbrellaStudies = new ArrayList<>();
            for (StudyDto study : studiesDto) {
                JdbiUmbrellaStudyI18n translationDao = handle.attach(JdbiUmbrellaStudyI18n.class);

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

                List<EnrollmentStatusDto> enrollments = enrollmentDao.findByStudyGuid(study.getGuid());
                EnrollmentStatusCount enrollmentStatusCount;
                if (enrollments.isEmpty()) {
                    enrollmentStatusCount = new EnrollmentStatusCount(0, 0);
                } else {
                    enrollmentStatusCount = EnrollmentStatusCount.getEnrollmentStatusCountByEnrollments(enrollments);
                }

                String studyName;
                if (null != preferredTranslation) {
                    studyName = preferredTranslation.getName();
                } else {
                    studyName = study.getName();
                }

                boolean restricted = study.getIrbPassword() != null;
                StudySummary summary = new StudySummary(
                        study.getGuid(),
                        studyName,
                        enrollmentStatusCount.getRegisteredCount(),
                        enrollmentStatusCount.getParticipantCount(),
                        restricted,
                        study.getStudyEmail()
                );
                umbrellaStudies.add(summary);
            }

            return umbrellaStudies;
        });
    }
}
