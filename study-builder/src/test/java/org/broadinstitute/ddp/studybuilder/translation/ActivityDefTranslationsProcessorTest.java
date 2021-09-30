package org.broadinstitute.ddp.studybuilder.translation;


import static org.broadinstitute.ddp.model.activity.types.BlockType.QUESTION;
import static org.broadinstitute.ddp.studybuilder.BuilderUtils.validateActivityDef;
import static org.broadinstitute.ddp.studybuilder.StudyBuilderContext.CONTEXT;
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
    private static final String SUBS_CONF_FILE = TEST_STUDY_FOLDER + "subs.conf";
    private static final String ACTIVITY_CONF_FILE = TEST_STUDY_FOLDER + "activity.conf";

    private static final String LANG_EN = "en";
    private static final String LANG_ES = "es";

    private static final Gson gson = GsonUtil.standardGson();
    private static final GsonPojoValidator validator = new GsonPojoValidator();


    /**
     * Tests how translations (for new languages) are added to a generated {@link FormActivityDef}.
     * Translations are added using method {@link ActivityDefTranslationsProcessor#run(ActivityDef)}
     * <br>
     * <b>Testing steps:</b>
     * <ul>
     *     <li>[1] read subs.conf and read translations to {@link StudyBuilderContext#getTranslations()};</li>
     *     <li>[2] read form activity conf-file and build FormActivityDef;</li>
     *     <li>[3] run service ActivityDefTranslationsProcessor which go through all translations/templates
     *         of an activity and adds translations for languages which not added yet;</li>
     *     <li>[4] run FormActivityDef validation (validator.validateAsJson(def));</li>
     * </ul>
     */
    @Test
    public void testTranslationsEnrichment() {
        // [1]
        Config subsCfg = parseFile(SUBS_CONF_FILE);
        readTranslationsFromConfSectionI18n(subsCfg);
        CONTEXT.setProcessTranslations(true);

        // [2]
        Config activityConf = parseFile(ACTIVITY_CONF_FILE).resolveWith(subsCfg, ConfigResolveOptions.defaults());
        ActivityDef activityDef = gson.fromJson(ConfigUtil.toJson(activityConf), ActivityDef.class);
        FormActivityDef formDef = (FormActivityDef) activityDef;
        QuestionDef questionDef1 = ((QuestionBlockDef) formDef.getSections().get(0).getBlocks().get(0)).getQuestion();
        QuestionDef questionDef2 = ((QuestionBlockDef) formDef.getSections().get(0).getBlocks().get(1)).getQuestion();
        QuestionDef questionDef3 = ((QuestionBlockDef) formDef.getSections().get(0).getBlocks().get(2)).getQuestion();

        // check the processed activity before translations processing
        assertEquals(1, activityDef.getTranslatedNames().size());
        assertEquals(QUESTION, formDef.getSections().get(0).getBlocks().get(0).getBlockType());
        assertEquals(QUESTION, formDef.getSections().get(0).getBlocks().get(1).getBlockType());
        assertEquals(QUESTION, formDef.getSections().get(0).getBlocks().get(2).getBlockType());
        assertEquals(0, questionDef1.getPromptTemplate().getVariables().size());
        assertEquals(1, questionDef2.getPromptTemplate().getVariables().size());
        assertEquals(1, questionDef2.getPromptTemplate().getVariables().iterator().next().getTranslations().size());

        // [3]
        ActivityDefTranslationsProcessor activityDefTranslationsProcessor =
                new ActivityDefTranslationsProcessor(CONTEXT.getTranslations());
        activityDefTranslationsProcessor.run(activityDef);
        // [4]
        validateActivityDef(activityDef, validator);

        // check the processed activity after translations processing
        assertEquals(2, activityDef.getTranslatedNames().size());
        assertEquals(LANG_EN, activityDef.getTranslatedNames().get(0).getLanguageCode());
        assertEquals(LANG_ES, activityDef.getTranslatedNames().get(1).getLanguageCode());
        assertEquals(1, formDef.getSections().size());
        assertEquals(3, formDef.getSections().get(0).getBlocks().size());
        assertEquals(2, questionDef1.getPromptTemplate().getVariables().size());
        assertEquals(1, questionDef2.getPromptTemplate().getVariables().size());
        assertEquals(2, questionDef2.getPromptTemplate().getVariables().iterator().next().getTranslations().size());
        assertEquals(3, questionDef3.getPromptTemplate().getVariables().size());

        String templateRendered = questionDef1.getPromptTemplate().renderWithDefaultValues(LANG_EN);
        assertEquals(
                "What primary cancer(s) has your child been diagnosed with? <br/> "
                        + "<small><em>If your child have been diagnosed with more than one cancer type, "
                        + "please use the button below to add an additional diagnosis.</em></small>", templateRendered);

        templateRendered = questionDef2.getPromptTemplate().renderWithDefaultValues(LANG_EN);
        assertEquals("Where do you live?", templateRendered);

        templateRendered = questionDef3.getPromptTemplate().renderWithDefaultValues(LANG_EN);
        assertEquals(
                "Currently, Count Me In is open only to patients in the United States or Canada. "
                        + "If you do live or are treated in the United States or Canada, please reach out to us at "
                        + "<a href=\"mailto:info@joincountmein.org\" class=\"Link\">info@joincountmein.org</a>.,"
                        + "Currently, Count Me In is open only to patients in the United States or Canada. "
                        + "If your child does live or is treated in the United States or Canada, please reach out to us "
                        + "at <a href=\"mailto:info@joincountmein.org\" class=\"Link\">info@joincountmein.org</a>.,"
                        + "<span class=\"bold\">In order to participate in the project, a parent needs to help you.</span> "
                        + "When your parent is with you, click back and select \"My child has been diagnosed\" "
                        + "and complete the registration together..", templateRendered);
    }

    private static Config parseFile(String fileName) {
        return ConfigFactory.parseFile(new File(fileName));
    }
}
