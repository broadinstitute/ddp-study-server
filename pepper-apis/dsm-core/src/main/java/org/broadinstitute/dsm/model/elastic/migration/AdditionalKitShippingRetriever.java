package org.broadinstitute.dsm.model.elastic.migration;

import static org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter.NEW_OSTEO_INSTANCE_NAME;
import static org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter.OLD_OSTEO_INSTANCE_NAME;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.broadinstitute.dsm.db.KitRequestShipping;

public class AdditionalKitShippingRetriever extends AdditionalRecordsRetriever<KitRequestShipping> {

    AdditionalKitShippingRetriever(String additionalRealm) {
        super(additionalRealm);
    }

    static Optional<AdditionalKitShippingRetriever> fromRealm(String realm) {
        if  (OLD_OSTEO_INSTANCE_NAME.equals(realm)) {
            return Optional.of(new AdditionalKitShippingRetriever(NEW_OSTEO_INSTANCE_NAME));
        }
        if (NEW_OSTEO_INSTANCE_NAME.equals(realm)) {
            return Optional.of(new AdditionalKitShippingRetriever(OLD_OSTEO_INSTANCE_NAME));
        }
        return Optional.empty();
    }

    public Map<String, List<KitRequestShipping>> retrieve() {
        return KitRequestShipping.getAllKitRequestsByRealm(additionalRealm, null, null, true);
    }
}
