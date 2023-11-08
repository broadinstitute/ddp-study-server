package org.broadinstitute.dsm.util;

import org.broadinstitute.dsm.db.Tissue;
import org.broadinstitute.dsm.db.dao.ddp.tissue.TissueSMIDDao;

import java.util.HashSet;
import java.util.Set;

public class TissueTestUtil {

    private final Set<Integer> createdTissueIds = new HashSet<>();

    private final TissueSMIDDao dao = new TissueSMIDDao();

    public int createTissue(int oncHistoryId, String createdByUser) {
        int tissueId = Tissue.createNewTissue(oncHistoryId, createdByUser);
        createdTissueIds.add(tissueId);
        return tissueId;
    }

    public void deleteCreatedTissues() {
        for (Integer createdTissueId : createdTissueIds) {
            dao.deleteTissueById(createdTissueId);
        }
    }

}
