package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.Map;

public class OncHistoryDetailSourceGenerator extends AdditionalCollectionSourceGenerator {

    public static final String TISSUE_RECEIVED = "tissueReceived";
    public static final String UNABLE_OBTAIN_TISSUE = "unableObtainTissue";

    @Override
    protected Map<String, Generator> obtainStrategyByFieldName() {
        return Map.of(
                FAX_SENT, new OncHistoryDetailFaxSentStrategyDecorator(new BaseStrategy(FAX_CONFIRMED, generatorPayload.getValue())),
                FAX_SENT_2, new OncHistoryDetailFaxSentStrategyDecorator(new BaseStrategy(FAX_CONFIRMED_2, generatorPayload.getValue())),
                FAX_SENT_3, new OncHistoryDetailFaxSentStrategyDecorator(new BaseStrategy(FAX_CONFIRMED_3, generatorPayload.getValue())),
                TISSUE_RECEIVED, new OncHistoryDetailTissueReceivedStrategy(),
                UNABLE_OBTAIN_TISSUE, new OncHistoryDetailUnableObtainTissueStrategy(generatorPayload));

    }

}
