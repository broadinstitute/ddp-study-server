package org.broadinstitute.dsm.model.elastic.migration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.broadinstitute.dsm.service.adminoperation.ExportLog;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public abstract class BaseCollectionMigrator extends BaseMigrator {

    protected List<Map<String, Object>> transformedList;
    protected Set<String> primaryKeys;

    public BaseCollectionMigrator(String index, String realm, String object, List<ExportLog> exportLog) {
        super(index, realm, object, exportLog);
        this.primaryKeys = new HashSet<>();
    }

    @Override
    public Map<String, Object> generate() {
        return new HashMap<>(Map.of(ESObjectConstants.DSM, new HashMap<>(Map.of(entity, transformedList))));
    }

    @Override
    protected void transformObject(Object object) {
        transformedList = objectTransformer.transformObjectCollectionToCollectionMap((List) object);
    }
}
