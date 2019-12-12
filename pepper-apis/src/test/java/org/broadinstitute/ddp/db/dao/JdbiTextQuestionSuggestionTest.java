package org.broadinstitute.ddp.db.dao;

import static junit.framework.TestCase.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestUtil;
import org.jdbi.v3.core.Handle;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdbiTextQuestionSuggestionTest extends TxnAwareBaseTest {


    private static final Logger LOG = LoggerFactory.getLogger(JdbiTextQuestionSuggestion.class);

    private static TestDataSetupUtil.GeneratedTestData testData;

    private List<String> testTypeItems = new ArrayList<String>();

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
    }

    @Test
    public void testJdbiTxtQuestionSuggestions() {
        //insert a text type ahead question
        testTypeItems.add("test typeahead#1");
        testTypeItems.add("test typeahead#2");
        testTypeItems.add("test typeahead#3");

        String stableId = "TYPE_TYPEAHEAD_TEST";
        Template prompt = new Template(TemplateType.TEXT, null, "Test type ahead");
        TextQuestionDef txtDef = TextQuestionDef.builder(TextInputType.TEXT, stableId, prompt)
                .addSuggestions(testTypeItems)
                .build();
        TransactionWrapper.useTxn(handle -> {

            FormActivityDef form = FormActivityDef.generalFormBuilder("act", "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "typeahead text question test activity"))
                    .addSection(new FormSectionDef(null, TestUtil.wrapQuestions(txtDef)))
                    .build();
            handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(),
                    "add typeahead txt question activity"));
            assertNotNull(form.getActivityId());

            TextQuestionDef def = extractQuestion(form, stableId);
            assertNotNull(def);
            def.getSuggestions().size();
            long questionId = def.getQuestionId();

            testGetSuggestionByQuestion(handle, questionId);

            handle.rollback();
        });

    }

    private void testGetSuggestionByQuestion(Handle handle, long questionId) {
        JdbiTextQuestionSuggestion dao = handle.attach(JdbiTextQuestionSuggestion.class);
        List<String> suggestions = dao.getTextQuestionSuggestions(questionId);
        Assert.assertNotNull(suggestions);
        Assert.assertEquals(3, suggestions.size());
        Assert.assertTrue(suggestions.get(1).equals("test typeahead#2"));
    }

    private TextQuestionDef extractQuestion(FormActivityDef activity, String stableId) {
        return activity.getSections().get(0).getBlocks().stream()
                .map(block -> (TextQuestionDef) ((QuestionBlockDef) block).getQuestion())
                .filter(question -> question.getStableId().equals(stableId))
                .findFirst().get();
    }

}
