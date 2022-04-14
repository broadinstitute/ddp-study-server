package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.OncHistoryDetail;

public class TissueSourceGenerator extends AdditionalCollectionSourceGenerator {

    CollectionSourceGenerator baseCollectionGenerator;
    public static final String RETURN_DATE = "returnDate";

    public TissueSourceGenerator() {
        baseCollectionGenerator = new ParentChildRelationGenerator();
    }

    @Override
    protected Optional<Map<String, Object>> getAdditionalData() {
        baseCollectionGenerator.getAdditionalData()
        return super.getAdditionalData().fl;
    }

    @Override
    protected Map<String, Generator> obtainStrategyByFieldName() {
        Map<String, Generator> resultMap = new HashMap<>();
        if (StringUtils.isNotBlank(String.valueOf(generatorPayload.getValue()))) {
            resultMap.put(RETURN_DATE, new BaseStrategy(OncHistoryDetail.STATUS_REQUEST, OncHistoryDetail.STATUS_RETURNED));
        } else {
            resultMap.put(RETURN_DATE, new UnableObtainTissueStrategy(generatorPayload));
        }
        return resultMap;
    }
}
