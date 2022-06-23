package org.broadinstitute.dsm.model.elastic.migration;

import java.util.HashMap;
import java.util.Map;

public class NullAdditionalParticipantMigrator extends ParticipantMigrator {

    public NullAdditionalParticipantMigrator() {
        super(null, null);
    }

    @Override
    protected Map<String, Object> getDataByRealm() {
        return new HashMap<>();
    }
}
