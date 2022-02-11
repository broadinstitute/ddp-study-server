package org.broadinstitute.dsm.model.elastic.export.painless;

import org.junit.Assert;
import org.junit.Test;

public class ScriptBuilderTest {

    @Test
    public void buildNested() {

        String propertyName = "kitRequestShipping";
        String uniqueIdentifier = "kitRequestId";

        ScriptBuilder builder = new NestedScriptBuilder(propertyName, uniqueIdentifier);
        String script = builder.build();

        String expectedScript = "if (ctx._source.dsm.kitRequestShipping == null) {ctx._source.dsm.kitRequestShipping = [params.dsm.kitRequestShipping]} " +
                "else {def targets = ctx._source.dsm.kitRequestShipping.findAll(obj -> obj.containsKey('kitRequestId') && obj.kitRequestId == params.dsm.kitRequestShipping.kitRequestId);" +
                " if (targets.size() == 0) { ctx._source.dsm.kitRequestShipping.add(params.dsm.kitRequestShipping) } else { for(target in targets) { for (entry in params.dsm.kitRequestShipping.entrySet()) { target.put(entry.getKey(), entry.getValue()) } " +
                "}}}";

        Assert.assertEquals(expectedScript, script);
    }

    @Test
    public void buildSingle() {

        String propertyName = "kitRequestShipping";

        ScriptBuilder builder = new SingleScriptBuilder(propertyName);
        String script = builder.build();

        String expectedScript = "" +
                "if (ctx._source.dsm.kitRequestShipping == null) " +
                "{ctx._source.dsm.kitRequestShipping = params.dsm.kitRequestShipping} " +
                "else {" +
                "for (entry in params.dsm.kitRequestShipping.entrySet()) " +
                "{ ctx._source.dsm.kitRequestShipping.put(entry.getKey(), entry.getValue()) }" +
                "}";

        Assert.assertEquals(expectedScript, script);
    }
}
