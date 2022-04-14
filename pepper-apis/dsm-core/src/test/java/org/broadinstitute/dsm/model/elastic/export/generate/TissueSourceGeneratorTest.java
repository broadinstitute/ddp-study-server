package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.Tissue;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.elastic.export.parse.ValueParser;
import org.broadinstitute.dsm.model.patch.TissuePatch;
import org.broadinstitute.dsm.statics.DBConstants;
import org.junit.Assert;
import org.junit.Test;

public class TissueSourceGeneratorTest {

    @Test
    public void getAdditionalDataReturnDateExists() {
        GeneratorPayload generatorPayload = new GeneratorPayload(new NameValue(getReturnedDateWithAlias(), "2020-01-01"), 25);
        ValueParser valueParser = new ValueParser();
        valueParser.setPropertyInfo(new BaseGenerator.PropertyInfo(Tissue.class, true));
        valueParser.setFieldName(TissueSourceGenerator.RETURN_DATE);

        SourceGenerator sourceGenerator = new TissueSourceGenerator();
        sourceGenerator.setParser(valueParser);
        sourceGenerator.setPayload(generatorPayload);

        Map<String, Object> actual = sourceGenerator.generate();

        Map<String, Object> expected = new HashMap<>();
        expected.put(TissueSourceGenerator.RETURN_DATE, "2020-01-01");
        expected.put(OncHistoryDetail.STATUS_REQUEST, OncHistoryDetail.STATUS_RETURNED);
        expected.put(TissuePatch.TISSUE_ID, 25);

        Assert.assertEquals(expected, ((List) ((Map) actual.get("dsm")).get("tissue")).get(0));
    }


    @Test
    public void getAdditionalDataReturnDateNotExistsHasReceivedDate() {
        GeneratorPayload generatorPayload = new GeneratorPayload(new NameValue(getReturnedDateWithAlias(), StringUtils.EMPTY), 25);
        Map<String, Object> actual = getDateNotExistsActualMap(generatorPayload);

        Map<String, Object> expected = new HashMap<>();
        expected.put(OncHistoryDetail.STATUS_REQUEST, OncHistoryDetail.STATUS_RECEIVED);
        expected.put(TissuePatch.TISSUE_ID, 25);

        Assert.assertEquals(expected, ((List) ((Map) actual.get("dsm")).get("tissue")).get(0));
    }



    @Test
    public void getAdditionalDataReturnDateNotExistsHasNotReceivedDate() {
        GeneratorPayload generatorPayload = new GeneratorPayload(new NameValue(getReturnedDateWithAlias(), StringUtils.EMPTY), -1);
        Map<String, Object> actual = getDateNotExistsActualMap(generatorPayload);

        Map<String, Object> expected = new HashMap<>();
        expected.put(OncHistoryDetail.STATUS_REQUEST, OncHistoryDetail.STATUS_SENT);
        expected.put(TissuePatch.TISSUE_ID, -1);

        Assert.assertEquals(expected, ((List) ((Map) actual.get("dsm")).get("tissue")).get(0));
    }

    private String getReturnedDateWithAlias() {
        return String.join(DBConstants.ALIAS_DELIMITER, DBConstants.DDP_TISSUE_ALIAS, TissueSourceGenerator.RETURN_DATE);
    }

    private Map<String, Object> getDateNotExistsActualMap(GeneratorPayload generatorPayload) {
        ValueParser valueParser = new ValueParser();
        valueParser.setPropertyInfo(new BaseGenerator.PropertyInfo(Tissue.class, true));
        valueParser.setFieldName(TissueSourceGenerator.RETURN_DATE);

        TissueSourceGenerator sourceGenerator = new TissueSourceGenerator() {
            @Override
            protected Generator getStrategy() {
                UnableObtainTissueStrategy strategy = (UnableObtainTissueStrategy) super.getStrategy();
                strategy.oncHistoryDetailDao = new MockOncHistoryDetailDao();
                return strategy;
            }
        };
        sourceGenerator.setParser(valueParser);
        sourceGenerator.setPayload(generatorPayload);

        return sourceGenerator.generate();
    }

}
