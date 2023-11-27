package org.broadinstitute.dsm.model.elastic.migration;

import java.util.Map;

import org.broadinstitute.dsm.db.dao.mercury.ClinicalOrderDao;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public class ClinicalOrderMigrator extends BaseCollectionMigrator {
    private ClinicalOrderDao clinicalOrderDao;

    public ClinicalOrderMigrator(String index, String realm, ClinicalOrderDao clinicalOrderDao) {
        super(index, realm, ESObjectConstants.CLINICAL_ORDER);
        this.clinicalOrderDao = clinicalOrderDao;
    }

    @Override
    protected Map<String, Object> getDataByRealm() {
        return (Map)  clinicalOrderDao.getOrdersForRealmMap(realm);
    }

}
