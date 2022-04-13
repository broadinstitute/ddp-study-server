package org.broadinstitute.dsm.db.dao.ddp.onchistory;

import org.broadinstitute.dsm.db.dao.Dao;

public interface OncHistoryDetailDao<T> extends Dao<T> {

    boolean hasReceivedDate(int oncHistoryDetailId);

}
