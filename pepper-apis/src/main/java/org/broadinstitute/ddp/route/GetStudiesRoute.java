package org.broadinstitute.ddp.route;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudyI18n;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.StudyLanguageDao;
import org.broadinstitute.ddp.db.dto.EnrollmentStatusDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.StudyI18nDto;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.study.EnrollmentStatusCount;
import org.broadinstitute.ddp.model.study.StudyLanguage;
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
        String umbrellaGuid = request.queryParams(RouteConstants.QueryParam.UMBRELLA);

        if (StringUtils.isBlank(umbrellaGuid)) {
            ApiError error = new ApiError(ErrorCodes.REQUIRED_PARAMETER_MISSING, "Missing umbrella parameter");
            ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST, error);
        }

        LOG.debug("Received request for summary of studies under {}", umbrellaGuid);

        List<LanguageRange> acceptLanguages;
        String acceptLanguageHeader = request.headers(RouteConstants.Header.ACCEPT_LANGUAGE);
        if (StringUtils.isNotEmpty(acceptLanguageHeader)) {
            acceptLanguages = LanguageRange.parse(acceptLanguageHeader);
        } else {
            acceptLanguages = new ArrayList<>();
        }

        DDPAuth authInfo = RouteUtil.getDDPAuth(request);

        return TransactionWrapper.withTxn(handle -> {
            JdbiUmbrellaStudy studyDao = handle.attach(JdbiUmbrellaStudy.class);
            JdbiUserStudyEnrollment enrollmentDao = handle.attach(JdbiUserStudyEnrollment.class);

            List<StudyDto> studiesDto = studyDao.findByUmbrellaGuid(umbrellaGuid);
            if (studiesDto.isEmpty()) {
                ApiError error = new ApiError(ErrorCodes.NOT_FOUND, "No studies for umbrella");
                ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, error);
            }

            List<StudySummary> umbrellaStudies = new ArrayList<>();
            for (StudyDto study : studiesDto) {
                JdbiUmbrellaStudyI18n translationDao = handle.attach(JdbiUmbrellaStudyI18n.class);
                Set<Locale> supportedLocales = handle.attach(StudyLanguageDao.class)
                        .findLanguages(study.getGuid())
                        .stream()
                        .map(StudyLanguage::toLocale)
                        .collect(Collectors.toSet());
                Locale userLocale = I18nUtil.resolvePreferredLanguage(authInfo.getPreferredLocale(), acceptLanguages, supportedLocales);
                long langCodeId = LanguageStore.getOrCompute(handle, userLocale.toLanguageTag()).getId();
                Optional<StudyI18nDto> preferredTranslation = translationDao.findTranslationByStudyIdAndLanguageCodeId(
                        study.getId(),
                        langCodeId
                );

                List<EnrollmentStatusDto> enrollments = enrollmentDao.findByStudyGuid(study.getGuid());
                EnrollmentStatusCount enrollmentStatusCount;
                if (enrollments.isEmpty()) {
                    enrollmentStatusCount = new EnrollmentStatusCount(0, 0);
                } else {
                    enrollmentStatusCount = EnrollmentStatusCount.getEnrollmentStatusCountByEnrollments(enrollments);
                }

                String studyName = preferredTranslation.map(StudyI18nDto::getName).orElse(study.getName());

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
