package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.Optional;

import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDao;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDto;

class MockOncHistoryDetailDao implements OncHistoryDetailDao<OncHistoryDetailDto> {

    @Override
    public int create(OncHistoryDetailDto oncHistoryDetailDto) {
        return 0;
    }

    @Override
    public int delete(int id) {
        return 0;
    }

    @Override
    public Optional<OncHistoryDetailDto> get(long id) {
        return Optional.empty();
    }

    @Override
    public boolean hasReceivedDate(int oncHistoryDetailId) {
        return oncHistoryDetailId >= 0;
    }
}
