package org.broadinstitute.ddp.transformers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.gson.Gson;
import org.junit.Before;
import org.junit.Test;

public class NullableJsonTransformerTest {

    private NullableJsonTransformer transformer;

    @Before
    public void setup() {
        transformer = new NullableJsonTransformer(new Gson());
    }

    @Test
    public void testRender_json() {
        var obj = new TestObj("bar");
        var actual = transformer.render(obj);
        assertNotNull(actual);
        assertEquals("{\"foo\":\"bar\"}", actual);
    }

    @Test
    public void testRender_emptyString() {
        var actual = transformer.render("");
        assertEquals("\"\"", actual);
    }

    @Test
    public void testRender_null() {
        var actual = transformer.render(null);
        assertEquals("", actual);
    }

    private static final class TestObj {
        public String foo;

        public TestObj(String foo) {
            this.foo = foo;
        }
    }
}
