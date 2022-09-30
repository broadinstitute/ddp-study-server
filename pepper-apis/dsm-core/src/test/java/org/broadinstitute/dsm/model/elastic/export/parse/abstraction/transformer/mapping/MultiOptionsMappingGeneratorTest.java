
package org.broadinstitute.dsm.model.elastic.export.parse.abstraction.transformer.mapping;

import static org.broadinstitute.dsm.model.elastic.export.generate.BaseGenerator.PROPERTIES;
import static org.broadinstitute.dsm.model.elastic.export.generate.MappingGenerator.NESTED;
import static org.broadinstitute.dsm.model.elastic.export.parse.MedicalRecordAbstractionFieldTypeParser.OTHER;
import static org.broadinstitute.dsm.model.elastic.export.parse.MedicalRecordAbstractionFieldTypeParser.VALUES;

import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.dsm.model.elastic.export.generate.MappingGenerator;
import org.broadinstitute.dsm.model.elastic.export.parse.TypeParser;
import org.junit.Assert;
import org.junit.Test;

public class MultiOptionsMappingGeneratorTest {

    @Test
    public void toMap() {
        var generator = new MultiOptionsMappingGenerator();
        var actual = generator.toMap("MET_SITES_EVERY");
        var expected = new HashMap<>(Map.of(
                MappingGenerator.TYPE, NESTED,
                PROPERTIES, new HashMap<>(Map.of(
                        OTHER, TypeParser.TEXT_KEYWORD_MAPPING,
                        VALUES, new HashMap<>(Map.of(
                                MappingGenerator.TYPE, NESTED,
                                PROPERTIES, new HashMap<>(Map.of(
                                        VALUES, TypeParser.TEXT_KEYWORD_MAPPING))))))));
        Assert.assertEquals(expected, actual);
    }

}
