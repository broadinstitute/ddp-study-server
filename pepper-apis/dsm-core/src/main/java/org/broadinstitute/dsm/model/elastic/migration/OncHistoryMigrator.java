package org.broadinstitute.dsm.model.elastic.migration;

import java.util.Map;

import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDao;
import org.broadinstitute.dsm.model.elastic.ObjectTransformer;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public class OncHistoryMigrator extends BaseSingleMigrator {

    public OncHistoryMigrator(String index, String realm) {
        super(index, realm, ESObjectConstants.ONC_HISTORY);
    }

    @Override
    protected ObjectTransformer getObjectTransformer() {
        return new ObjectTransformer(realm, true);
    }

    @Override
    protected Map<String, Object> getDataByRealm() {
        return (Map) new OncHistoryDao().getOncHistoryByParticipant(realm);
    }
}
