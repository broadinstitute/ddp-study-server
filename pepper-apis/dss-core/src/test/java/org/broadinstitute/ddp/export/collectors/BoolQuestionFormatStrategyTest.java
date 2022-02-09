package org.broadinstitute.ddp.export.collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.junit.Before;
import org.junit.Test;

public class BoolQuestionFormatStrategyTest {

    private BoolQuestionFormatStrategy fmt;

    @Before
    public void setup() {
        fmt = new BoolQuestionFormatStrategy();
    }

    @Test
    public void testMappings() {
        BoolQuestionDef def = BoolQuestionDef.builder("sid", Template.text(""), Template.text("yes"), Template.text("no")).build();
        Map<String, Object> actual = fmt.mappings(def);

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertTrue(actual.containsKey("sid"));
        assertEquals("boolean", ((Map) actual.get("sid")).get("type"));
    }

    @Test
    public void testHeaders() {
        BoolQuestionDef def = BoolQuestionDef.builder("sid", Template.text(""), Template.text("yes"), Template.text("no")).build();
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

    private BoolQuestionDef buildQuestion() {
        return BoolQuestionDef.builder("sid", Template.text(""), Template.text("yes"), Template.text("no")).build();
    }

    private BoolAnswer buildAnswer(Boolean value) {
        return new BoolAnswer(1L, "sid", "abc", value);
    }
}
