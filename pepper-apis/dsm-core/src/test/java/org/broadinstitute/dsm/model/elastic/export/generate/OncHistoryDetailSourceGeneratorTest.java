package org.broadinstitute.dsm.model.elastic.export.generate;


import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.elastic.export.parse.ValueParser;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OncHistoryDetailSourceGeneratorTest {

    @Test
    public void testGetAdditionalData() {
        GeneratorPayload generatorPayload = new GeneratorPayload(new NameValue("oD.faxSent", "2020-01-01"), 25);
        ValueParser valueParser = new ValueParser();
        valueParser.setPropertyInfo(new BaseGenerator.PropertyInfo(OncHistoryDetail.class, true));
        valueParser.setFieldName("faxSent");

        SourceGenerator sourceGenerator = new OncHistoryDetailSourceGenerator();
        sourceGenerator.setParser(valueParser);
        sourceGenerator.setPayload(generatorPayload);

        Map<String, Object> actual = sourceGenerator.generate();

        Map<String, Object> expected = new HashMap<>();
        expected.put("faxSent", "2020-01-01");
        expected.put("faxConfirmed", "2020-01-01");
        expected.put("request", "sent");
        expected.put("oncHistoryDetailId", 25);

        Assert.assertEquals(expected, ((List) ((Map) actual.get("dsm")).get("oncHistoryDetail")).get(0));

    }
}