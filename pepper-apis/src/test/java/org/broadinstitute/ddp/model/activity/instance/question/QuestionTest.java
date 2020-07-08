package org.broadinstitute.ddp.model.activity.instance.question;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.instance.validation.Rule;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.junit.Test;

public class QuestionTest {

    // Create a bare minimum question subclass to aid in testing the base behavior
    // of the templating behaviors.
    private class ConcreteQuestion extends Question<TextAnswer> {
        public ConcreteQuestion(String stableId, long promptTemplateId,
                                Long additionalInfoHeaderTemplateId, Long additionalInfoFooterTemplateId,
                                List<TextAnswer> answers, List<Rule<TextAnswer>> validations) {
            super(QuestionType.TEXT, stableId, promptTemplateId,
                    false, false, null,
                    additionalInfoHeaderTemplateId, additionalInfoFooterTemplateId,
                    answers, validations);
        }
    }

    @Test
    public void testApplyStandardRenderedTemplates() {
        Map<Long, String> rendered = new HashMap<>();
        rendered.put(1L, "<p>prompt</p>");
        rendered.put(2L, "Header information");
        rendered.put(3L, "Footer information");

        ConcreteQuestion question = new ConcreteQuestion("QUESTION_SID",
                                        1L, 2L, 3L,
                                        Collections.emptyList(), Collections.emptyList());
        
        question.applyRenderedTemplates(rendered::get, ContentStyle.STANDARD);

        assertTrue(HtmlConverter.hasSameValue(rendered.get(1L), question.getPrompt()));
        assertEquals(rendered.get(2L), question.getAdditionalInfoHeader());
        assertEquals(rendered.get(3L), question.getAdditionalInfoFooter());
    }

    @Test
    public void testApplyBasicRenderedTemplates() {
        String prompt = "question prompt";

        Map<Long, String> rendered = new HashMap<>();
        rendered.put(1L, "<p>" + prompt + "</p>");
        rendered.put(2L, "Header information");
        rendered.put(3L, "Footer information");

        ConcreteQuestion question = new ConcreteQuestion("QUESTION_SID",
                                        1L, 2L, 3L,
                                        Collections.emptyList(), Collections.emptyList());
        
        question.applyRenderedTemplates(rendered::get, ContentStyle.BASIC);

        assertEquals(prompt, question.getPrompt());
        assertEquals(rendered.get(2L), question.getAdditionalInfoHeader());
        assertEquals(rendered.get(3L), question.getAdditionalInfoFooter());
    }
}
