package org.broadinstitute.ddp.studybuilder.task.ddp3934;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.db.dao.JdbiDateQuestion;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiTemplate;
import org.broadinstitute.ddp.db.dao.JdbiTemplateVariable;
import org.broadinstitute.ddp.db.dao.JdbiTextQuestion;
import org.broadinstitute.ddp.db.dao.JdbiVariableSubstitution;
import org.broadinstitute.ddp.db.dao.TemplateDao;
import org.broadinstitute.ddp.db.dto.DateQuestionDto;
import org.broadinstitute.ddp.db.dto.TextQuestionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.broadinstitute.ddp.studybuilder.task.ddp3934.util.db.LanguagesDao;
import org.broadinstitute.ddp.studybuilder.task.ddp3934.util.db.QuestionMetadataDao;
import org.broadinstitute.ddp.studybuilder.task.ddp3934.util.l10n.Placeholders;
import org.broadinstitute.ddp.studybuilder.task.ddp3934.util.model.Language;
import org.broadinstitute.ddp.studybuilder.task.ddp3934.util.model.QuestionMetadata;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InsertAngioPlaceholders implements CustomTask {
    private static final Logger LOG = LoggerFactory.getLogger(InsertAngioPlaceholders.class);
    
    private interface Constants {
        public final String PLACEHOLDER_KEY_SUFFIX = "_PLACEHOLDER";
        public final String ANGIO_STUDY_GUID = "ANGIO";
        public final String ABOUT_YOU_ACTIVITY_CODE = "ANGIOABOUTYOU";
        public final String CONSENT_ACTIVITY_CODE = "ANGIOCONSENT";
        public final String LOVED_ONE_ACTIVITY_CODE = "ANGIOLOVEDONE";
        public final String FOLLOWUP_CONSENT_ACTIVITY_CODE = "followupconsent";
    }

    private class ActivityReference {
        private String studyGuid;
        private String activityCode;

        ActivityReference(String studyGuid, String activityCode) {
            this.studyGuid = studyGuid;
            this.activityCode = activityCode;
        }

        public String getStudyGuid() {
            return studyGuid;
        }

        public String getActivityCode() {
            return activityCode;
        }

        @Override
        public boolean equals(Object other) {
            if (super.equals(other)) {
                return true;
            } else if (other instanceof ActivityReference) {
                return this.equals((ActivityReference)other);
            } else {
                return false;
            }
        }

        public boolean equals(ActivityReference reference) {
            return activityCode.equals(reference.activityCode)
                    && studyGuid.equals(reference.studyGuid);
        }
    }

    private Map<ActivityReference, String[]> questionUpdates;

    /**
     * Customize the task as needed, using the given commandline and study config.
     *
     * @param cfgPath  the path to study config file
     * @param studyCfg the study configuration
     * @param varsCfg  the secrets/variables configuration for study
     */
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        Map<ActivityReference, String[]> questionUpdates = new HashMap<>();
        ActivityReference aboutYou = new ActivityReference(Constants.ANGIO_STUDY_GUID, Constants.ABOUT_YOU_ACTIVITY_CODE);
        questionUpdates.put(aboutYou, new String[] {
                "DIAGNOSIS_DATE",
                "OTHER_CANCER_LIST_YEAR",
                "BIRTH_YEAR",
                "POSTAL_CODE",
                "ALL_TREATMENTS",
                "CURRENT_THERAPIES",
                "TREATMENT_PAST",
                "TREATMENT_NOW",
                "SUPPORT_MEMBERSHIP_TEXT"
        });

        ActivityReference consent = new ActivityReference(Constants.ANGIO_STUDY_GUID, Constants.CONSENT_ACTIVITY_CODE);
        questionUpdates.put(consent, new String[] {
                "CONSENT_FULLNAME",
                "CONSENT_DOB"
        });

        ActivityReference followUpConsent = new ActivityReference(Constants.ANGIO_STUDY_GUID, Constants.FOLLOWUP_CONSENT_ACTIVITY_CODE);
        questionUpdates.put(followUpConsent, new String[] {
                "FOLLOWUPCONSENT_FULLNAME",
                "FOLLOWUPCONSENT_DOB"
        });

        ActivityReference lovedOne = new ActivityReference(Constants.ANGIO_STUDY_GUID, Constants.LOVED_ONE_ACTIVITY_CODE);
        questionUpdates.put(lovedOne, new String[] {
                "LOVEDONE_FIRST_NAME",
                "LOVEDONE_LAST_NAME",
                "LOVEDONE_DIAGNOSIS_POSTAL_CODE",
                "LOVEDONE_PASSED_POSTAL_CODE",
                "LOVEDONE_OTHER_CANCER_TEXT",
                "LOVEDONE_EXPERIENCE",
                "LOVEDONE_DIAGNOSIS_DATE",
                "LOVEDONE_PASSING_DATE",
                "LOVEDONE_DOB"
        });

        this.questionUpdates = questionUpdates;
    }

    /**
     * Executes the work of the task.
     *
     * @param handle the database handle
     */
    public void run(Handle handle) {
        LanguagesDao languagesDao = handle.attach(LanguagesDao.class);
        Map<Language, Locale> cachedLocales = languagesDao.getLanguages()
                .stream()
                .collect(Collectors.toMap(language -> language,
                    language -> language.getLocale()));

        // Iterate through all the activities, get the question metadata for each question
        // in the activity, 
        for (Map.Entry<ActivityReference, String[]> entry : questionUpdates.entrySet()) {
            ActivityReference currentActivity = entry.getKey();

            String activityCode = currentActivity.getActivityCode();
            String studyGuid = currentActivity.getStudyGuid();

            LOG.info("updating placeholders for activity {}", currentActivity.getActivityCode());
            List<QuestionMetadata> questions = handle.attach(QuestionMetadataDao.class)
                    .getQuestionsWithPlaceholders(studyGuid, activityCode)
                    .stream()
                    // Only DATE and TEXT types have placeholder fields as of 2019.09.10
                    .filter(question -> (question.type == QuestionType.DATE) || (question.type == QuestionType.TEXT))
                    .collect(Collectors.toList());

            for (QuestionMetadata question : questions) {
                LOG.debug("updating question {}.{}.{}", question.stableIdentifier, question.type.toString(), question.revision);

                String localizationKey = question.stableIdentifier + Constants.PLACEHOLDER_KEY_SUFFIX;
                List<Translation> localizations = new ArrayList<>();

                for (Map.Entry<Language, Locale> cachedLanguage : cachedLocales.entrySet()) {
                    Locale locale = cachedLanguage.getValue();
                    ResourceBundle bundle = getPlaceholderBundle(locale);

                    String localizedString = null;
                    if (bundle.containsKey(localizationKey)) {
                        localizedString = bundle.getString(localizationKey);
                    }

                    if (localizedString == null) {
                        LOG.info("no {} localization defined for key {}", locale.getLanguage(), localizationKey);
                        continue;
                    } else if (localizationKey.equals(localizedString)) {
                        LOG.info("localization for key {} in {} defined, but no value set", localizationKey, locale.getLanguage());
                        continue;
                    } else {
                        LOG.debug("adding {} localization for key {}", locale.getLanguage(), localizationKey);

                        Translation placeholder = new Translation(locale.getLanguage(), localizedString);
                        localizations.add(placeholder);
                    }
                }

                if (localizations.isEmpty()) {
                    LOG.info("no localizations found for key {}", localizationKey);
                    continue;
                }

                // Go through and update/create any templates we might need.
                Long targetTemplateId = null;
                Long activeRevisionId = question.revision;
                String templateText = "$" + localizationKey;

                TemplateDao templateDao = handle.attach(TemplateDao.class);
                if (question.placeholderTemplateId != null) {
                    // If the question already has a placeholder it was using, clear out any variables
                    // or substitutions tied to it.
                    Template existingTemplate = templateDao.loadTemplateById(question.placeholderTemplateId);
                    
                    String templateCode = existingTemplate.getTemplateCode();
                    long templateId = existingTemplate.getTemplateId().longValue();
                    
                    boolean result = clearTemplate(handle, templateId);
                    if (result == false) {
                        String message = String.format("failed to clear placeholder for question %s (%d)",
                                                        question.stableIdentifier,
                                                        activeRevisionId);
                        throw new DDPException(message);
                    } else {
                        LOG.info("cleared placeholder template '{}' ({}, {}) for question {}", templateText,
                                                                    templateCode,
                                                                    templateId, 
                                                                    question.stableIdentifier);
                    }

                    targetTemplateId = templateId;
                } else {
                    LOG.info("creating new placeholder template for question {}", question.stableIdentifier);
                    String code = templateDao.getJdbiTemplate().generateUniqueCode();
                    targetTemplateId = templateDao.getJdbiTemplate().insert(code,
                                                                            TemplateType.TEXT,
                                                                            templateText,
                                                                            activeRevisionId);
                }

                Template template =  templateDao.loadTemplateById(targetTemplateId);
                boolean result = templateDao.getJdbiTemplate().update(template.getTemplateId(),
                                                                        template.getTemplateCode(),
                                                                        template.getTemplateType(),
                                                                        templateText,
                                                                        template.getRevisionId().get());
                if (result) {
                    LOG.info("persisted placeholder template '{}' ({}, {})", templateText,
                                                                                template.getTemplateCode(),
                                                                                template.getTemplateId());
                }


                long variableId = templateDao.getJdbiTemplateVariable()
                        .insertVariable(targetTemplateId, localizationKey);

                JdbiVariableSubstitution substitutionDao = templateDao.getJdbiVariableSubstitution();
                for (Translation substitution : localizations) {
                    long substitutionId = substitutionDao.insert(substitution.getLanguageCode(),
                                                                    substitution.getText(),
                                                                    activeRevisionId,
                                                                    variableId);

                    LOG.info("updated translation with id {} ({},{})",
                                substitutionId,
                                localizationKey,
                                substitution.getLanguageCode());
                }

                switch (question.type) {
                    case TEXT: {
                        JdbiTextQuestion questionDao = handle.attach(JdbiTextQuestion.class);
                        TextQuestionDto questionDetail = (TextQuestionDto) handle.attach(JdbiQuestion.class)
                                .findQuestionDtoById(question.id).orElseThrow();
                        questionDao.update(question.id,
                                            questionDetail.getInputType(),
                                            questionDetail.getSuggestionType(),
                                            targetTemplateId);
                    } break;
                    
                    case DATE: {
                        JdbiDateQuestion questionDao = handle.attach(JdbiDateQuestion.class);
                        DateQuestionDto questionDetail = (DateQuestionDto) handle.attach(JdbiQuestion.class)
                                .findQuestionDtoById(question.id).orElseThrow();
                        questionDao.update(question.id,
                                            questionDetail.getRenderMode(),
                                            questionDetail.shouldDisplayCalendar(),
                                            targetTemplateId);
                    } break;

                    default:
                        /* Do Nothing */
                }
            }
        }
    }

    private ResourceBundle getPlaceholderBundle(Locale locale) {
        String qualifiedBundleName = Placeholders.class.getName();
        ResourceBundle bundle = ResourceBundle.getBundle(qualifiedBundleName,
                locale,
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_DEFAULT));
        return bundle;
    }

    private boolean clearTemplate(Handle handle, long templateId) {
        TemplateDao templateDao = handle.attach(TemplateDao.class);

        JdbiTemplate jdbiTemplate = templateDao.getJdbiTemplate();
        JdbiTemplateVariable jdbiTemplateVariable = templateDao.getJdbiTemplateVariable();
        JdbiVariableSubstitution jdbiVariableSubstitution = templateDao.getJdbiVariableSubstitution();

        Template template = templateDao.loadTemplateById(templateId);
        if (template == null) {
            String msg = String.format("no template with id %d found",
                    templateId);
            throw new DDPException(msg);
        }
        
        for (TemplateVariable variable : template.getVariables()) {
            if (variable.getId().isPresent() == false) {
                String message = String.format("deletion of variable named %s failed, no valid id found", variable.getName());
                throw new DDPException(message);
            }

            long variableId = variable.getId().get().longValue();

            for (Translation translation : variable.getTranslations()) {
                if (translation.getId().isPresent() == false) {
                    String message = String.format("deletion of translation '%s' failed, no valid id found", translation.getText());
                    throw new DDPException(message);
                }

                long translationId = translation.getId().get().longValue();
                Long revisionId = translation.getRevisionId().get();
                boolean result = jdbiVariableSubstitution.delete(translationId);
                if (result == false) {
                    String message = String.format("failed to delete translation with id %ld", translationId);
                    throw new DDPException(message);
                } else {
                    LOG.info("deleted translation '{}' ({}.{}, {})", translation.getText(),
                                                                    translationId,
                                                                    revisionId,
                                                                    translation.getLanguageCode());
                }
            }

            boolean result = jdbiTemplateVariable.delete(variableId);
            if (false == result) {
                String message = String.format("failed to delete variable with id %ld", variableId);
                throw new DDPException(message);
            } else {
                LOG.info("deleted variable {} with id {}", variable.getName(), variableId);
            }
        }

        boolean result = jdbiTemplate.update(template.getTemplateId(),
                                                template.getTemplateCode(),
                                                template.getTemplateType(),
                                                "",
                                                template.getRevisionId().get());

        return result;
    }
}
