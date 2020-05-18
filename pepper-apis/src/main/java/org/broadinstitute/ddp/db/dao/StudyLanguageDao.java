package org.broadinstitute.ddp.db.dao;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;

public interface StudyLanguageDao extends SqlObject {

    @CreateSqlObject
    StudyLanguageSql getStudyLanguageSql();

    @CreateSqlObject
    JdbiUmbrellaStudy getUmbrellaStudy();

    @CreateSqlObject
    JdbiLanguageCode getLanguageCode();

    default long insert(long umbrellaStudyId, long languageCodeId) {
        return getStudyLanguageSql().insert(umbrellaStudyId, languageCodeId);
    }

    default long insert(String studyGuid, String languageCode) {
        StudyDto studyDto = getUmbrellaStudy().findByStudyGuid(studyGuid);
        if (studyDto == null) {
            throw new DDPException("Study not found for guid : " + studyGuid);
        }

        Long languageCodeId = getLanguageCode().getLanguageCodeId(languageCode);
        if (languageCodeId == null) {
            throw new DDPException("Language code : " + languageCode + " not found ");
        }

        return getStudyLanguageSql().insert(studyDto.getId(), languageCodeId);
    }

    default int setAsDefaultLanguage(long umbrellaStudyId, long languageCodeId) {
        //select first to see if we have any default language set
        //if we do: update All existing languages as isDefault false if different language is default or
        //more than 1 language is default and update this one as default
        List<Long> defaultLanguages = getStudyLanguageSql().selectDefaultLanguageCodeId(umbrellaStudyId);
        if (CollectionUtils.isEmpty(defaultLanguages)) {
            return getStudyLanguageSql().updateDefaultLanguage(umbrellaStudyId, languageCodeId, true);
        }

        if (defaultLanguages.size() > 1 || defaultLanguages.get(0).longValue() != languageCodeId) {
            //update existing as non-default first
            getStudyLanguageSql().updateExistingAsNonDefaultLanguages(umbrellaStudyId);
            return getStudyLanguageSql().updateDefaultLanguage(umbrellaStudyId, languageCodeId, true);
        }

        return 0;
    }

    default int setAsDefaultLanguage(long umbrellaStudyId, String languageCode) {
        return setAsDefaultLanguage(umbrellaStudyId, getLanguageCode().getLanguageCodeId(languageCode));
    }

}
