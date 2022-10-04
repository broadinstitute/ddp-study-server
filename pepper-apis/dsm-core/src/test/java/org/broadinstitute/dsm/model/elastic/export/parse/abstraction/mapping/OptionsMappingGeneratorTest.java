
package org.broadinstitute.dsm.model.elastic.export.parse.abstraction.mapping;

import static org.broadinstitute.dsm.model.elastic.export.generate.BaseGenerator.PROPERTIES;
import static org.broadinstitute.dsm.model.elastic.export.parse.MedicalRecordAbstractionFieldTypeParser.OTHER;

import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.dsm.model.elastic.export.parse.TypeParser;
import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.mapping.OptionsMappingGenerator;
import org.junit.Assert;
import org.junit.Test;

public class OptionsMappingGeneratorTest {

    @Test
    public void toMap() {
        var generator = new OptionsMappingGenerator();
        var actual = generator.toMap("DX Stage");
        var expected = new HashMap<>(Map.of(
                PROPERTIES, Map.of(
                        OTHER, TypeParser.TEXT_KEYWORD_MAPPING,
                        "dxStage", TypeParser.TEXT_KEYWORD_MAPPING)));
        Assert.assertEquals(expected, actual);
    }

}
