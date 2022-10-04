
package org.broadinstitute.dsm.model.elastic.export.parse.abstraction.source;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class NumericSourceGeneratorTest {

    @Test
    public void toMap() {
        var generator = new NumericSourceGenerator();
        var actual = generator.toMap("DX Percent ER", "100");
        var expected = Map.of("dxPercentEr", 100L);
        Assert.assertEquals(expected, actual);
    }

}
