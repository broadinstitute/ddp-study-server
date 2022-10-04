
package org.broadinstitute.dsm.model.elastic.export.parse.abstraction.mapping;

import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.dsm.model.elastic.export.parse.TypeParser;
import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.mapping.DateMappingGenerator;
import org.junit.Assert;
import org.junit.Test;

public class DateMappingGeneratorTest {

    @Test
    public void toMap() {
        var generator = new DateMappingGenerator();
        var actual = generator.toMap("dxDate");
        var expected = new HashMap<String, Object>(Map.of(
                "properties", new HashMap<>(Map.of(
                        "dateString", TypeParser.DATE_MAPPING,
                        "est", TypeParser.BOOLEAN_MAPPING))));
        Assert.assertEquals(expected, actual);
    }

}
