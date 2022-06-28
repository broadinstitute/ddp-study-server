package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.model.activity.types.EventTriggerType.JOIN_MAILING_LIST;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiMailingList;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudyCached;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dao.StudyLanguageDao;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.json.JoinMailingListPayload;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import spark.Request;
import spark.Response;

/**
 * Route that adds a person to the mailing address for a study
 * so that the person can stay informed of developments in the study
 */
@Slf4j
public class JoinMailingListRoute extends ValidatedJsonInputRoute<JoinMailingListPayload> {
    public Object handle(Request request, Response response, JoinMailingListPayload payload) throws Exception {
        // The two types of ways of using this endpoint are to join a a mailing list for a specific study,
        // or to join a mailing list for a particular umbrella.

        int choices = (StringUtils.isNotEmpty(payload.getStudyGuid()) ? 1 : 0)
                + (StringUtils.isNotEmpty(payload.getUmbrellaGuid()) ? 1 : 0);

        if (choices != 1) {
            ResponseUtil.haltError(response, 400,
                    new ApiError(ErrorCodes.BAD_PAYLOAD, "Must provide either studyGuid or umbrellaGuid"));
        }

        TransactionWrapper.useTxn(handle -> {
            EventDao eventDao = handle.attach(EventDao.class);
            QueuedEventDao queuedEventDao = handle.attach(QueuedEventDao.class);
            JdbiMailingList jdbiMailingList = handle.attach(JdbiMailingList.class);

            String info = payload.getInfo() != null && payload.getInfo().size() > 0 ? String.join(", ", payload.getInfo()) : null;
            String languageCode = payload.getLanguageCode();
            Long languageCodeId = null;
            if (StringUtils.isEmpty(languageCode)) {
                LanguageDto preferredUserLangDto = RouteUtil.getUserLanguage(request);
                if (preferredUserLangDto != null) {
                    languageCode = preferredUserLangDto.getIsoCode();
                }
            }

            if (StringUtils.isNotEmpty(languageCode)) {
                LanguageDto languageDto = LanguageStore.get(languageCode);
                if (languageDto == null) {
                    ResponseUtil.haltError(response, 400,
                            new ApiError(ErrorCodes.BAD_PAYLOAD, "Invalid isoLanguageCode"));
                }
                languageCodeId = LanguageStore.get(languageCode).getId();
            } else {
                //use study default language if exists
                if (StringUtils.isNotEmpty(payload.getStudyGuid())) {
                    StudyDto studyDto = new JdbiUmbrellaStudyCached(handle).findByStudyGuid(payload.getStudyGuid());
                    StudyLanguageDao studyLanguageDao = handle.attach(StudyLanguageDao.class);
                    List<Long> defaultLanguages = studyLanguageDao.getStudyLanguageSql().selectDefaultLanguageCodeId(studyDto.getId());
                    if (!defaultLanguages.isEmpty()) {
                        languageCodeId = defaultLanguages.get(0);
                    } else {
                        //fallback to default
                        languageCodeId = LanguageStore.getDefault().getId();
                    }
                } else {
                    //fallback to default
                    languageCodeId = LanguageStore.getDefault().getId();
                }
            }

            int rowsInserted = 0;
            if (StringUtils.isNotEmpty(payload.getStudyGuid())) {
                rowsInserted = jdbiMailingList.insertByStudyGuidIfNotStoredAlready(
                        payload.getFirstName(),
                        payload.getLastName(),
                        payload.getEmailAddress(),
                        payload.getStudyGuid(),
                        info,
                        Instant.now().toEpochMilli(),
                        languageCodeId
                );
            } else if (StringUtils.isNotEmpty(payload.getUmbrellaGuid())) {
                rowsInserted = jdbiMailingList.insertByUmbrellaGuidIfNotStoredAlready(
                        payload.getFirstName(),
                        payload.getLastName(),
                        payload.getEmailAddress(),
                        info,
                        Instant.now().toEpochMilli(),
                        payload.getUmbrellaGuid(),
                        languageCodeId
                );
            }
            boolean studyProvidedAndShouldSendEmail =
                    (rowsInserted == 0 || rowsInserted == 1) && StringUtils.isNotEmpty(payload.getStudyGuid());

            if (rowsInserted == 0) {
                log.info("{} is already on the contact list for study {} or umbrella {}", payload.getEmailAddress(),
                        payload.getStudyGuid(), payload.getUmbrellaGuid());
            } else if (rowsInserted == 1) {
                log.info("Added {} to the contact list for study {} or umbrella {}", payload.getEmailAddress(),
                        payload.getStudyGuid(), payload.getUmbrellaGuid());
            } else {
                throw new DaoException(String.format("%s rows were inserted for the contact list for study %s or umbrella %s and email %s",
                        rowsInserted,
                        payload.getStudyGuid(),
                        payload.getUmbrellaGuid(),
                        payload.getEmailAddress()));
            }

            if (studyProvidedAndShouldSendEmail) {
                List<EventConfigurationDto> eventConfigs =
                        eventDao.getNotificationConfigsForMailingListByEventType(payload.getStudyGuid(), JOIN_MAILING_LIST);

                if (eventConfigs.isEmpty()) {
                    log.info("No email configured for mailing list for {}, nothing to send to {}.",
                            payload.getStudyGuid(),
                            payload.getEmailAddress());
                }

                for (EventConfigurationDto eventConfig : eventConfigs) {
                    queuedEventDao.insertNotification(eventConfig.getEventConfigurationId(),
                            0,
                            payload.getEmailAddress(),
                            Collections.<String, String>emptyMap());
                    log.info("Queued mailing list email send to {}", payload.getEmailAddress());
                }
            }
        });

        response.status(HttpStatus.SC_NO_CONTENT);
        return "";
    }
}
