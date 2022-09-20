package org.broadinstitute.dsm.model.elastic.export.generate;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.elastic.export.parse.ValueParser;
import org.broadinstitute.dsm.model.patch.Patch;
import org.junit.Assert;
import org.junit.Test;

public class OncHistoryDetailSourceGeneratorTest {

    @Test
    public void testGetAdditionalDataFaxSents() {
        Patch patch = new Patch();
        patch.setId("25");
        GeneratorPayload generatorPayload = new GeneratorPayload(new NameValue("oD.faxSent", "2020-01-01"), patch);
        ValueParser valueParser = new ValueParser();
        valueParser.setPropertyInfo(new PropertyInfo(OncHistoryDetail.class, true));
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

    @Test
    public void testGetAdditionalDataUnableObtain() {
        Patch patch = new Patch();
        patch.setId("-25");
        GeneratorPayload generatorPayload = new GeneratorPayload(new NameValue("oD.unableObtainTissue", false), patch);
        ValueParser valueParser = new ValueParser();
        valueParser.setPropertyInfo(new PropertyInfo(OncHistoryDetail.class, true));
        valueParser.setFieldName("unableObtainTissue");

        OncHistoryDetailSourceGenerator sourceGenerator = new OncHistoryDetailSourceGenerator() {
            @Override
            protected Generator getStrategy() {
                OncHistoryDetailUnableObtainTissueStrategy generatorStrategy =
                        (OncHistoryDetailUnableObtainTissueStrategy) super.getStrategy();
                generatorStrategy.oncHistoryDetailDao = new MockOncHistoryDetailDao();
                return generatorStrategy;
            }
        };
        sourceGenerator.setParser(valueParser);
        sourceGenerator.setPayload(generatorPayload);

        Map<String, Object> actual = sourceGenerator.generate();

        Map<String, Object> expected = new HashMap<>();
        expected.put("unableObtainTissue", false);
        expected.put("request", "sent");
        expected.put("oncHistoryDetailId", -25);

        Assert.assertEquals(expected, ((List) ((Map) actual.get("dsm")).get("oncHistoryDetail")).get(0));

    }
}
