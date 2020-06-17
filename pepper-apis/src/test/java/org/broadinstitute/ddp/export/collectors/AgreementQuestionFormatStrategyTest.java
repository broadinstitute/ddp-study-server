package org.broadinstitute.ddp.export.collectors;

import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.broadinstitute.ddp.model.activity.definition.question.AgreementQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.answer.AgreementAnswer;
import org.junit.Before;
import org.junit.Test;

public class AgreementQuestionFormatStrategyTest {

    private AgreementQuestionFormatStrategy fmt;

    @Before
    public void setup() {
        fmt = new AgreementQuestionFormatStrategy();
    }

    @Test
    public void testMappings() {
        AgreementQuestionDef def = new AgreementQuestionDef("sid",
                                                            false,
                                                            Template.text("prompt"),
                                                            null,
                                                            Template.text("header"),
                                                            Template.text("footer"),
                                                            emptyList(),
                                                            false);
        Map<String, Object> actual = fmt.mappings(def);

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertTrue(actual.containsKey("sid"));
        assertEquals("boolean", ((Map) actual.get("sid")).get("type"));
    }

    @Test
    public void testHeaders() {
        AgreementQuestionDef def = new AgreementQuestionDef("sid",
                                                            false,
                                                            Template.text("prompt"),
                                                            null,
                                                            Template.text("header"),
                                                            Template.text("footer"),
                                                            emptyList(),
                                                            false);
        List<String> actual = fmt.headers(def);

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals("sid", actual.get(0));
    }

    @Test
    public void testCollect_nullAnswer() {
        Map<String, String> actual = fmt.collect(buildQuestion(), buildAnswer(null));

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertTrue(actual.containsKey("sid"));
        assertEquals("", actual.get("sid"));
    }

    @Test
    public void testCollect_true() {
        Map<String, String> actual = fmt.collect(buildQuestion(), buildAnswer(true));
        assertEquals("true", actual.get("sid"));
    }

    @Test
    public void testCollect_false() {
        Map<String, String> actual = fmt.collect(buildQuestion(), buildAnswer(false));
        assertEquals("false", actual.get("sid"));
    }

    private AgreementQuestionDef buildQuestion() {
        return new AgreementQuestionDef("sid",
                false,
                Template.text("prompt"),
                null,
                Template.text("header"),
                Template.text("footer"),
                emptyList(),
                false);
    }

    private AgreementAnswer buildAnswer(Boolean value) {
        return new AgreementAnswer(1L, "sid", "abc", value);
    }
}
