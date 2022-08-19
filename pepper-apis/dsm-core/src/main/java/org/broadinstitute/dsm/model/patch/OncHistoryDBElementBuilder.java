package org.broadinstitute.dsm.model.patch;

import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.statics.DBConstants;

// The primary key of DBElement built by this class should equal to "participant_id".
// The reason behind this is that since in ElasticSearch dsm.participant holds data for `ddp_participant`, `ddp_participant_record`
// and'ddp_onc_history'. We need to make sure that the primary key for Patch#patch will be `participant_id`
// for correctly exercising DB operations.
public class OncHistoryDBElementBuilder extends DefaultDBElementBuilder {

    @Override
    public DBElement fromName(String name) {
        DBElement dbElement = super.fromName(name);
        dbElement.setPrimaryKey(DBConstants.PARTICIPANT_ID);
        return dbElement;
    }
}
