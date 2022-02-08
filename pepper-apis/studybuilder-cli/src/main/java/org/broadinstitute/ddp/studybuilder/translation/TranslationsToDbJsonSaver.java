package org.broadinstitute.ddp.studybuilder.translation;

import org.broadinstitute.ddp.db.dao.JdbiI18nTranslation;
import org.broadinstitute.ddp.db.dao.JdbiLanguageCode;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.i18n.I18nTranslation;
import org.broadinstitute.ddp.studybuilder.translation.TranslationsProcessingData.TranslationData;
import org.jdbi.v3.core.Handle;

/**
 * This class provides exporting of translations stored in i18n-files to a table `i18n_translation`.
 *
 * <p><b>Algorithm:</b>
 * <ul>
 *     <li>for each of study languages: do</li>
 *     <ol>
 *         <li>find in table i18n_translation a row by studyId and langCode;</li>
 *         <li>if it is found: do update of translations JSON;</li>
 *         <li>if it is not found: do insert of translations JSON.</li>
 *     </ol>
 * </ul>
 */
public class TranslationsToDbJsonSaver {

    public void saveTranslations(Handle handle, StudyDto studyDto) {
        if (TranslationsProcessingData.INSTANCE.isSaveTranslationsToDbJson()) {
            TranslationsProcessingData.INSTANCE.getTranslations()
                    .forEach((languageCode, translations) -> saveTranslationsForLanguage(handle, studyDto, languageCode, translations));
        }
    }

    private void saveTranslationsForLanguage(Handle handle, StudyDto studyDto, String isoLangCode, TranslationData translations) {
        JdbiI18nTranslation jdbiI18nTranslation = handle.attach(JdbiI18nTranslation.class);
        I18nTranslation i18nTranslation = jdbiI18nTranslation.getI18nTranslation(studyDto.getGuid(), isoLangCode).orElse(null);

        LanguageDto languageDto = handle.attach(JdbiLanguageCode.class).findLanguageDtoByCode(isoLangCode);
        if (languageDto == null) {
            throw new DDPException("Language information not found. LanguageCode: " + isoLangCode);
        }

        if (i18nTranslation == null) {
            insertTranslations(jdbiI18nTranslation, studyDto, languageDto.getId(), translations);
        } else {
            updateTranslations(jdbiI18nTranslation, studyDto, languageDto.getId(), translations);
        }
    }

    private void insertTranslations(
            JdbiI18nTranslation jdbiI18nTranslation, StudyDto studyDto, long languageCodeId, TranslationData translations) {
        jdbiI18nTranslation.insert(translations.getJson(), studyDto.getId(), languageCodeId);
    }

    private void updateTranslations(
            JdbiI18nTranslation jdbiI18nTranslation, StudyDto studyDto, long languageCodeId, TranslationData translations) {
        jdbiI18nTranslation.update(translations.getJson(), studyDto.getId(), languageCodeId);
    }
}
