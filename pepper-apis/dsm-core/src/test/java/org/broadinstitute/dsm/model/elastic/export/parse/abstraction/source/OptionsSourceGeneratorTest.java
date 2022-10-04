
package org.broadinstitute.dsm.model.elastic.export.parse.abstraction.source;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class OptionsSourceGeneratorTest {

    @Test
    public void simpleOptionsToMap() {
        var generator = new OptionsSourceGenerator();
        var actual = generator.toMap("DX Type", "Equivocal");
        var expected = Map.of("dxType", "Equivocal");
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void complexOptionsToMap() {
        var generator = new OptionsSourceGenerator();
        var actual = generator.toMap("DX Type", "{\"other\":\"Unknown type\",\"DX Type\":\"other\"}");
        var expected = Map.of("dxType", Map.of("other", "Unknown type", "dxType", "other"));
        Assert.assertEquals(expected, actual);
    }

}
