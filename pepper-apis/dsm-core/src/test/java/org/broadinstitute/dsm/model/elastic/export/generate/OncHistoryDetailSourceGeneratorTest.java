package org.broadinstitute.dsm.model.elastic.export.generate;


import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDao;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDto;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.elastic.export.parse.ValueParser;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class OncHistoryDetailSourceGeneratorTest {

    @Test
    public void testGetAdditionalDataFaxSents() {
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
    @Test
    public void testGetAdditionalDataUnableObtain() {
        GeneratorPayload generatorPayload = new GeneratorPayload(new NameValue("oD.unableObtainTissue", false), -25);
        ValueParser valueParser = new ValueParser();
        valueParser.setPropertyInfo(new BaseGenerator.PropertyInfo(OncHistoryDetail.class, true));
        valueParser.setFieldName("unableObtainTissue");

        OncHistoryDetailSourceGenerator sourceGenerator = new OncHistoryDetailSourceGenerator() {
            @Override
            Generator obtainStrategyByFieldName(String fieldName) {
                OncHistoryDetailUnableObtainTissueStrategy generatorStrategy = (OncHistoryDetailUnableObtainTissueStrategy) super.obtainStrategyByFieldName(fieldName);
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

class MockOncHistoryDetailDao implements OncHistoryDetailDao<OncHistoryDetailDto> {

    @Override
    public int create(OncHistoryDetailDto oncHistoryDetailDto) {
        return 0;
    }

    @Override
    public int delete(int id) {
        return 0;
    }

    @Override
    public Optional<OncHistoryDetailDto> get(long id) {
        return Optional.empty();
    }

    @Override
    public boolean hasReceivedDate(int oncHistoryDetailId) {
        return oncHistoryDetailId >= 0;
    }
}