package org.broadinstitute.dsm.model.elastic.migration;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.statics.ESObjectConstants;

@Slf4j
public class KitRequestShippingMigrator extends BaseCollectionMigrator {

    public KitRequestShippingMigrator(String index, String realm) {
        super(index, realm, ESObjectConstants.KIT_REQUEST_SHIPPING);
    }

    /**
     * returns the list of KitRequestShippings for the realm mapped to the participant guid
     */
    @Override
    protected Map<String, Object> getDataByRealm() {
        Map<String, List<KitRequestShipping>> kitRequests =
                KitRequestShipping.getAllKitRequestsByRealmForES(realm, true);
        int recordsFromRealm = kitRequests.size();
        AdditionalKitShippingRetriever.fromRealm(realm)
                .ifPresent(retriever -> retriever.mergeRecords(kitRequests));
        log.info("Migrator retrieved {} kit request records from realm {}, and {} additional records",
                recordsFromRealm, realm, kitRequests.size() - recordsFromRealm);
        return (Map) kitRequests;
    }

    @Override
    protected String getRecordIdFieldName() {
        return ESObjectConstants.DSM_KIT_REQUEST_ID;
    }
}
