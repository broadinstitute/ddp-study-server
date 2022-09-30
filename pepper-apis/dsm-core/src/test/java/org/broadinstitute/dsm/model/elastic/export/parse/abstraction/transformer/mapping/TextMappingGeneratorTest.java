
package org.broadinstitute.dsm.model.elastic.export.parse.abstraction.transformer.mapping;

import org.broadinstitute.dsm.model.elastic.export.parse.TypeParser;
import org.junit.Assert;
import org.junit.Test;

public class TextMappingGeneratorTest {

    @Test
    public void toMap() {
        var generator = new TextMappingGenerator();
        var actual = generator.toMap("Type of Cancer");
        var expected = TypeParser.TEXT_KEYWORD_MAPPING;
        Assert.assertEquals(expected, actual);
    }

}
