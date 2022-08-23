package org.broadinstitute.dsm.model.patch;

import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.statics.DBConstants;

public class OncHistoryDBElementBuilder extends DefaultDBElementBuilder {
    @Override
    public DBElement fromName(String name) {
        DBElement dbElement = super.fromName(name);
        dbElement.setPrimaryKey(DBConstants.PARTICIPANT_ID);
        return dbElement;
    }
}
