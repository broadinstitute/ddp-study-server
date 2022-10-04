
package org.broadinstitute.dsm.model.elastic.export.parse.abstraction.source;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class ButtonSelectSourceGeneratorTest {

    @Test
    public void toMap() {
        var generator = new ButtonSelectSourceGenerator();
        var actual = generator.toMap("Histology", "Pre-menopausal");
        var expected = Map.of("histology", "Pre-menopausal");
        Assert.assertEquals(expected, actual);
    }

}
