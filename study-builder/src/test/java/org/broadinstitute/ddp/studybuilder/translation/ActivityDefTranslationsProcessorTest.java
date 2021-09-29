package org.broadinstitute.ddp.studybuilder.translation;


import static org.broadinstitute.ddp.model.activity.types.BlockType.QUESTION;
import static org.broadinstitute.ddp.studybuilder.BuilderUtils.validateActivityDef;
import static org.broadinstitute.ddp.studybuilder.StudyBuilderContext.readTranslationsFromConfSectionI18n;
import static org.junit.Assert.assertEquals;

import java.io.File;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigResolveOptions;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.studybuilder.StudyBuilderContext;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonPojoValidator;
import org.broadinstitute.ddp.util.GsonUtil;
import org.junit.Test;

/**
 * Test {@link ActivityDefTranslationsProcessor}
 * used for adding translations to the {@link ActivityDef} generated from StudyBuilder conf-files
 */
public class ActivityDefTranslationsProcessorTest {

    private static final String TEST_STUDY_FOLDER = "src/test/resources/teststudy/";
    private static final String SUBS_CONF_FILE =  TEST_STUDY_FOLDER + "subs.conf";
    private static final String ACTIVITY_CONF_FILE =  TEST_STUDY_FOLDER + "activity.conf";

    private static final String LANG_EN = "en";
    private static final String LANG_ES = "es";

    private static Gson gson = GsonUtil.standardGson();
    private static GsonPojoValidator validator = new GsonPojoValidator();


    /**
     * Tests how translations (for new languages) are added to a generated {@link FormActivityDef}.
     * Translations are added using method {@link ActivityDefTranslationsProcessor#enrichActivityDefWithTranslations(ActivityDef)}
     * <br>
     * <b>Testing steps:</b>
     * <ul>
     *     <li>[1] read subs.conf and read translations to {@link StudyBuilderContext#getTranslations()};</li>
     *     <li>[2] read form activity conf-file and build FormActivityDef;</li>
     *     <li>[3] run service ActivityDefTranslationsProcessor which go through all translations/templates
     *         of an activity and adds translations for languages which not added yet;</li>
     *     <li>[4] run FormActivityDef validation (validator.validateAsJson(def));</li>
     * </ul>
     * During verification it is checked that before activity translations processing:
     * - activity.translatedNames.size = 1
     * - question prompt template variables size = 0
     * after translations processing:
     * - activity.translatedNames.size = 2
     * - question prompt template variables size = 2
     */
    @Test
    public void testTranslationsEnrichment() {
        // [1]
        Config subsCfg = parseFile(SUBS_CONF_FILE);
        readTranslationsFromConfSectionI18n(subsCfg);
        // [2]
        Config activityConf = parseFile(ACTIVITY_CONF_FILE).resolveWith(subsCfg, ConfigResolveOptions.defaults());
        ActivityDef activityDef = gson.fromJson(ConfigUtil.toJson(activityConf), ActivityDef.class);
        FormActivityDef formDef = (FormActivityDef)activityDef;
        QuestionDef questionDef = ((QuestionBlockDef)formDef.getSections().get(0).getBlocks().get(0)).getQuestion();

        // check the processed activity before translations processing
        assertEquals(1, activityDef.getTranslatedNames().size());
        assertEquals(0, questionDef.getPromptTemplate().getVariables().size());

        // [3]
        ActivityDefTranslationsProcessor activityDefTranslationsProcessor =
                new ActivityDefTranslationsProcessor(StudyBuilderContext.CONTEXT.getTranslations());
        activityDefTranslationsProcessor.enrichActivityDefWithTranslations(activityDef);
        // [4]
        validateActivityDef(activityDef, validator);

        // check the processed activity after translations processing
        assertEquals(2, activityDef.getTranslatedNames().size());
        assertEquals(LANG_EN, activityDef.getTranslatedNames().get(0).getLanguageCode());
        assertEquals(LANG_ES, activityDef.getTranslatedNames().get(1).getLanguageCode());
        assertEquals(1, formDef.getSections().size());
        assertEquals(1, formDef.getSections().get(0).getBlocks().size());
        assertEquals(QUESTION, formDef.getSections().get(0).getBlocks().get(0).getBlockType());
        assertEquals(2, questionDef.getPromptTemplate().getVariables().size());
    }

    private static Config parseFile(String fileName) {
        return ConfigFactory.parseFile(new File(fileName));
    }
}
