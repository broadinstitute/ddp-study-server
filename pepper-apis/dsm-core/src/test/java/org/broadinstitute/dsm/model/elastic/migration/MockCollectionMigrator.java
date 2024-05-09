package org.broadinstitute.dsm.model.elastic.migration;

import java.util.Map;

public class MockCollectionMigrator extends BaseCollectionMigrator {

    public MockCollectionMigrator() {
        super(null, "fakeRealm", "fakeEntity");

    }

    @Override
    protected Map<String, Object> getDataByRealm() {
        return Map.of();
    }

    @Override
    protected String getRecordIdFieldName() {
        return null;
    }
}
