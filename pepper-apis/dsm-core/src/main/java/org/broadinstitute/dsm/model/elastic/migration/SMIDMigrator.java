package org.broadinstitute.dsm.model.elastic.migration;

import java.util.Map;

import org.broadinstitute.dsm.db.dao.ddp.tissue.TissueSMIDDao;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public class SMIDMigrator extends BaseCollectionMigrator {

    public SMIDMigrator(String index, String realm) {
        super(index, realm, ESObjectConstants.SMID);
    }

    @Override
    protected Map<String, Object> getDataByRealm() {
        return (Map) new TissueSMIDDao().getSmIdsByParticipantByStudy(realm);
    }

    @Override
    protected String getRecordIdFieldName() {
        return ESObjectConstants.SMID_PK;
    }
}
