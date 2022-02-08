package org.broadinstitute.ddp.util;

import org.broadinstitute.lddp.util.JsonTransformer;
import org.junit.Assert;
import org.junit.Test;

public class JsonTransformerTest {

    JsonTransformer jsonTransformer = new JsonTransformer();

    @Test
    public void testIt() {
        String json = jsonTransformer.render(new TestDto());
        Assert.assertEquals("{\"foo\":\"bar\"}", json);
    }

    private class TestDto {
        private String foo = "bar";
    }
}
