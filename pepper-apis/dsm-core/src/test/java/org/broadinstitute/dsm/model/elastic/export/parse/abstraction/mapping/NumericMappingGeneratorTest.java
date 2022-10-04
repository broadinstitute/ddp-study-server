
package org.broadinstitute.dsm.model.elastic.export.parse.abstraction.mapping;

import org.broadinstitute.dsm.model.elastic.export.parse.TypeParser;
import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.mapping.NumericMappingGenerator;
import org.junit.Assert;
import org.junit.Test;

public class NumericMappingGeneratorTest {

    @Test
    public void toMap() {
        var generator = new NumericMappingGenerator();
        var actual = generator.toMap("DX percent ER");
        var expected = TypeParser.LONG_MAPPING;
        Assert.assertEquals(expected, actual);
    }

}
