package org.broadinstitute.dsm.model.elastic.export.generate;


import java.util.Map;
import java.util.Optional;

public abstract class AdditionalCollectionSourceGenerator extends CollectionSourceGenerator {

    protected static final String FAX_SENT = "faxSent";
    protected static final String FAX_CONFIRMED = "faxConfirmed";
    protected static final String FAX_SENT_2 = "faxSent2";
    protected static final String FAX_CONFIRMED_2 = "faxConfirmed2";
    protected static final String FAX_SENT_3 = "faxSent3";
    protected static final String FAX_CONFIRMED_3 = "faxConfirmed3";

    @Override
    protected Optional<Map<String, Object>> getAdditionalData() {
        return Optional.ofNullable(getStrategy().generate());
    }

    protected Generator getStrategy() {
        return obtainStrategyByFieldName().getOrDefault(getFieldName(), new NullObjectStrategy());
    }

    protected abstract Map<String, Generator> obtainStrategyByFieldName();
}
