
package org.broadinstitute.dsm.model.elastic.migration;

import java.util.Map;

public class MedicalRecordFinalMigrator extends BaseCollectionMigrator {

    public MedicalRecordFinalMigrator(String index, String realm, String object) {
        super(index, realm, object);
    }

    @Override
    protected Map<String, Object> getDataByRealm() {
        return null;
    }

}

