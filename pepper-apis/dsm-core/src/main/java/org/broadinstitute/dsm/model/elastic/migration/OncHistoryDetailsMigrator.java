package org.broadinstitute.dsm.model.elastic.migration;

import java.util.Map;

import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.statics.ESObjectConstants;


public class OncHistoryDetailsMigrator extends BaseCollectionMigrator {

    public OncHistoryDetailsMigrator(String index, String realm) {
        super(index, realm, ESObjectConstants.ONC_HISTORY_DETAIL);
    }

    @Override
    protected Map<String, Object> getDataByRealm() {
        return (Map) OncHistoryDetail.getOncHistoryDetails(realm);
    }

}
