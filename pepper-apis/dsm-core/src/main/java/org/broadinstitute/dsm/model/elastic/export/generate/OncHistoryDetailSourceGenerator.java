package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.Map;
import java.util.Optional;

public class OncHistoryDetailSourceGenerator extends CollectionSourceGenerator {

    public static final String FAX_SENT = "faxSent";
    public static final String FAX_CONFIRMED = "faxConfirmed";
    public static final String FAX_SENT_2 = "faxSent2";
    public static final String FAX_SENT_3 = "faxSent3";
    public static final String TISSUE_RECEIVED = "tissueReceived";
    public static final String UNABLE_OBTAIN_TISSUE = "unableObtainTissue";
    public static final String FAX_CONFIRMED_2 = "faxConfirmed2";
    public static final String FAX_CONFIRMED_3 = "faxConfirmed3";

    @Override
    protected Optional<Map<String, Object>> getAdditionalData() {
        Map<String, Object> resultMap = obtainStrategyByFieldName(getFieldName()).generate();
        return Optional.of(resultMap);
    }

    Generator obtainStrategyByFieldName(String fieldName) {
        return Map.of(
                FAX_SENT, new OncHistoryDetailFaxSentStrategy(new FaxSentStrategy(FAX_CONFIRMED, generatorPayload.getValue())),
                FAX_SENT_2, new OncHistoryDetailFaxSentStrategy(new FaxSentStrategy(FAX_CONFIRMED_2, generatorPayload.getValue())),
                FAX_SENT_3, new OncHistoryDetailFaxSentStrategy(new FaxSentStrategy(FAX_CONFIRMED_3, generatorPayload.getValue())),
                TISSUE_RECEIVED, new OncHistoryDetailTissueReceivedStrategy(),
                UNABLE_OBTAIN_TISSUE, new OncHistoryDetailUnableObtainTissueStrategy(generatorPayload))
                .getOrDefault(fieldName, new NullObjectStrategy());

    }

}
