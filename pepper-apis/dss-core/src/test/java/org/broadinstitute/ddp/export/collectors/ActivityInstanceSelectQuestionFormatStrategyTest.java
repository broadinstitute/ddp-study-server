package org.broadinstitute.ddp.export.collectors;

import org.broadinstitute.ddp.model.activity.definition.question.ActivityInstanceSelectQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.answer.ActivityInstanceSelectAnswer;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ActivityInstanceSelectQuestionFormatStrategyTest {

    private ActivityInstanceSelectQuestionFormatStrategy fmt;

    @Before
    public void setup() {
        fmt = new ActivityInstanceSelectQuestionFormatStrategy(null);
    }

    @Test
    public void testMappings() {
        Map<String, Object> actual = fmt.mappings(buildQuestion());

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertTrue(actual.containsKey("sid"));
        assertEquals("text", ((Map) actual.get("sid")).get("type"));
    }

    @Test
    public void testHeaders() {
        List<String> actual = fmt.headers(buildQuestion());

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
    public void testCollect_ai() {
        Map<String, String> actual = fmt.collect(buildQuestion(), buildAnswer("foobar"));
        assertEquals("foobar", actual.get("sid"));
    }

    @Test
    public void testCollect_containsDelimiters() {
        Map<String, String> actual = fmt.collect(buildQuestion(), buildAnswer("foo, bar.baz ; bob"));
        assertEquals("foo, bar.baz ; bob", actual.get("sid"));
    }

    private ActivityInstanceSelectQuestionDef buildQuestion() {
        return ActivityInstanceSelectQuestionDef.builder("sid", Template.text("")).build();
    }

    private ActivityInstanceSelectAnswer buildAnswer(String value) {
        return new ActivityInstanceSelectAnswer(1L, "sid", "abc", value);
    }
}
