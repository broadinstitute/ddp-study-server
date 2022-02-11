package org.broadinstitute.dsm.model.elastic.migration;

import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.statics.ESObjectConstants;

import java.util.List;
import java.util.Map;

public class KitRequestShippingMigrator extends BaseCollectionMigrator {

    public KitRequestShippingMigrator(String index, String realm) {
        super(index, realm, ESObjectConstants.KIT_REQUEST_SHIPPING);
    }

    @Override
    protected Map<String, Object> getDataByRealm() {
        Map<String, List<KitRequestShipping>> kitRequests = KitRequestShipping.getAllKitRequestsByRealm(realm, null, null, true);
        return (Map) kitRequests;
    }

}
