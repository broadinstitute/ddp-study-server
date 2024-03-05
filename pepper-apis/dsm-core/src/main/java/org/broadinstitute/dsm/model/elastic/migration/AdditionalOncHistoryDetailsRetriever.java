package org.broadinstitute.dsm.model.elastic.migration;

import static org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter.NEW_OSTEO_INSTANCE_NAME;
import static org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter.OLD_OSTEO_INSTANCE_NAME;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.broadinstitute.dsm.db.OncHistoryDetail;

public class AdditionalOncHistoryDetailsRetriever extends AdditionalRecordsRetriever<OncHistoryDetail> {

    AdditionalOncHistoryDetailsRetriever(String additionalRealm) {
        super(additionalRealm);
    }

    static Optional<AdditionalOncHistoryDetailsRetriever> fromRealm(String realm) {
        /*
        if  (OLD_OSTEO_INSTANCE_NAME.equalsIgnoreCase(realm)) {
            return Optional.of(new AdditionalOncHistoryDetailsRetriever(NEW_OSTEO_INSTANCE_NAME));
        }
        if (NEW_OSTEO_INSTANCE_NAME.equalsIgnoreCase(realm)) {
            return Optional.of(new AdditionalOncHistoryDetailsRetriever(OLD_OSTEO_INSTANCE_NAME));
        }
        */
        return Optional.empty();
    }

    public Map<String, List<OncHistoryDetail>> retrieve() {
        return OncHistoryDetail.getOncHistoryDetails(additionalRealm);
    }
}
