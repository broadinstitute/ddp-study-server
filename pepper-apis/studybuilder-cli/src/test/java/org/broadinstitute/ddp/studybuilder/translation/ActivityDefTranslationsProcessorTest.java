package org.broadinstitute.ddp.studybuilder.translation;


import static org.broadinstitute.ddp.model.activity.types.InstanceStatusType.CREATED;
import static org.broadinstitute.ddp.model.activity.types.InstanceStatusType.IN_PROGRESS;
import static org.broadinstitute.ddp.studybuilder.BuilderUtils.validateActivityDef;
import static org.broadinstitute.ddp.studybuilder.translation.TranslationsProcessingData.INSTANCE;
import static org.broadinstitute.ddp.studybuilder.translation.TranslationsProcessingType.PROCESS_ALL_TEMPLATES;
import static org.broadinstitute.ddp.studybuilder.translation.I18nReader.readI18nTranslations;
import static org.broadinstitute.ddp.studybuilder.translation.I18nReader.readTranslationsFromFilesInSpecifiedFolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigResolveOptions;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.SummaryTranslation;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.studybuilder.translation.TranslationsProcessingData.TranslationData;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonPojoValidator;
import org.broadinstitute.ddp.util.GsonUtil;
import org.junit.Test;

/**
 * Test {@link ActivityDefTranslationsProcessor}
 * used for adding translations to the {@link ActivityDef} generated from StudyBuilder conf-files.<br>
 * Tests how translations (for new languages) are added to a generated {@link FormActivityDef}.
 * Translations are added using method {@link ActivityDefTranslationsProcessor#run(FormActivityDef)}
 * <br>
 * <b>Testing steps:</b>
 * <ul>
 *     <li>[1] read subs.conf and read translations to {@link TranslationsProcessingData#getTranslations()};</li>
 *     <li>[2] read form activity conf-file and build FormActivityDef;</li>
 *     <li>[3] run service ActivityDefTranslationsProcessor which go through all translations/templates
 *         of an activity and adds translations for languages which not added yet;</li>
 *     <li>[4] run FormActivityDef validation (validator.validateAsJson(def));</li>
 * </ul>
 */
public class ActivityDefTranslationsProcessorTest {

    private static final String TEST_STUDY_FOLDER = "src/test/resources/teststudy/";
    private static final String SUBS_CONF_FILE = TEST_STUDY_FOLDER + "subs.conf";
    private static final String ACTIVITY_CONF_FILE = TEST_STUDY_FOLDER + "activity.conf";
    private static final String ACTIVITY_INVALID_CONF_FILE = TEST_STUDY_FOLDER + "activity-invalid.conf";
    private static final String I18N_FOLDER = TEST_STUDY_FOLDER + "i18n";
    private static final String I18N_NON_EXISTING_FOLDER = TEST_STUDY_FOLDER + "i18n-non-existing-folder";

    private static final String LANG_EN = "en";
    private static final String LANG_FI = "fi";

    private static final Gson gson = GsonUtil.standardGson();
    private static final GsonPojoValidator validator = new GsonPojoValidator();

    private QuestionDef questionDef1;
    private QuestionDef questionDef2;
    private QuestionDef questionDef3;


    /**
     * Positive test: Test translations processing.
     * Read i18n from subs.conf
     */
    @Test
    public void testTranslationsEnrichmentReadI18nFromSubs() {
        // [1] read i18n translations (from subs.conf)
        Config subsCfg = parseSubsAndTranslations(null);

        FormActivityDef formDef = buildActivityAndProcessTranslations(subsCfg, ACTIVITY_CONF_FILE);
        assertAfterTranslationsProcessingPositiveActivity(formDef);

        assertEquals(3, formDef.getTranslatedNames().size());
        assertEquals(3, formDef.getTranslatedTitles().size());
        List<SummaryTranslation> summ = formDef.getTranslatedSummaries();
        assertEquals(6, summ.size());
        assertEquals(3, summ.stream().filter(s -> s.getStatusType() == CREATED).count());
        assertEquals(3, summ.stream().filter(s -> s.getStatusType() == IN_PROGRESS).count());
    }

    /**
     * Positive test: Test translations processing.
     * Read i18n from a specified folder
     */
    @Test
    public void testTranslationsEnrichmentReadI18nFromFolder() {
        // [1] read i18n translations (from a specified folder)
        Config subsCfg = parseSubsAndTranslations(I18N_FOLDER);

        FormActivityDef formDef = buildActivityAndProcessTranslations(subsCfg, ACTIVITY_CONF_FILE);
        assertAfterTranslationsProcessingPositiveActivity(formDef);
    }

    /**
     * Negative test: Test translations processing of activity with non-valid data.
     */
    @Test
    public void testTranslationsEnrichmentNegative() {
        // [1] read i18n translations (from subs.conf)
        Config subsCfg = parseSubsAndTranslations(null);

        try {
            buildActivityAndProcessTranslations(subsCfg, ACTIVITY_INVALID_CONF_FILE);
            fail();
        } catch (Exception e) {
            assertEquals("Translation not found: langCde=fi, key=prequal.non_existing_translation", e.getMessage());
        }
    }

    /**
     * Negative test: Read i18n from a specified folder (non-existing folder)
     */
    @Test
    public void testReadI18nFromFolderNegative() {
        Map<String, TranslationData> translations = readTranslationsFromFilesInSpecifiedFolder(I18N_NON_EXISTING_FOLDER);
        assertNull(translations);
    }


    private Config parseSubsAndTranslations(String i18nFolder) {
        Config subsCfg = parseFile(SUBS_CONF_FILE);
        INSTANCE.setTranslations(readI18nTranslations(subsCfg, i18nFolder));
        INSTANCE.setTranslationsProcessingType(PROCESS_ALL_TEMPLATES);
        return subsCfg;
    }

    private FormActivityDef buildActivityAndProcessTranslations(Config subsCfg, String activityConfFile) {
        // [2] build ActivityDef from config
        Config activityConf = parseFile(activityConfFile).resolveWith(subsCfg, ConfigResolveOptions.defaults());
        FormActivityDef activityDef = gson.fromJson(ConfigUtil.toJson(activityConf), FormActivityDef.class);
        List<FormBlockDef> blocks = activityDef.getSections().get(0).getBlocks();
        if (blocks.size() > 0) {
            questionDef1 = ((QuestionBlockDef) blocks.get(0)).getQuestion();
        }
        if (blocks.size() > 1) {
            questionDef2 = ((QuestionBlockDef) blocks.get(1)).getQuestion();
        }
        if (blocks.size() > 2) {
            questionDef3 = ((QuestionBlockDef) blocks.get(2)).getQuestion();
        }

        // [3] run translations processing
        ActivityDefTranslationsProcessor activityDefTranslationsProcessor =
                new ActivityDefTranslationsProcessor(INSTANCE.getTranslations());
        activityDefTranslationsProcessor.run(activityDef);

        // [4] validate ActivityDef
        validateActivityDef(activityDef, validator);

        return activityDef;
    }

    private void assertAfterTranslationsProcessingPositiveActivity(FormActivityDef formDef) {
        assertEquals(3, formDef.getTranslatedNames().size());
        assertEquals(LANG_FI, formDef.getTranslatedNames().get(0).getLanguageCode());
        assertEquals(LANG_EN, formDef.getTranslatedNames().get(1).getLanguageCode());
        assertEquals(1, formDef.getSections().size());
        assertEquals(3, formDef.getSections().get(0).getBlocks().size());
        assertEquals(4, questionDef1.getPromptTemplate().getVariables().size());
        assertEquals(1, questionDef2.getPromptTemplate().getVariables().size());
        assertEquals(3, questionDef2.getPromptTemplate().getVariables().iterator().next().getTranslations().size());
        assertEquals(3, questionDef3.getPromptTemplate().getVariables().size());
    }

    private static Config parseFile(String fileName) {
        return ConfigFactory.parseFile(new File(fileName));
    }
}
