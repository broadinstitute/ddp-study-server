package org.broadinstitute.dsm.util;

import org.broadinstitute.dsm.db.dao.ddp.tissue.TissueSMIDDao;

import java.util.HashSet;
import java.util.Set;

public class SampleIdTestUtil {

    private final TissueSMIDDao smIdDao = new TissueSMIDDao();

    private final Set<Integer> createdSampleIds = new HashSet<>();

    public int createSampleForTissue(int tissueId, String user, String sampleType, String sampleName) {
        int sampleId = smIdDao.createNewSMIDForTissue(tissueId, user, sampleType, sampleName);
        createdSampleIds.add(sampleId);
        return sampleId;
    }

    public void deleteCreatedSamples() {
        for (Integer createdSampleId : createdSampleIds) {
            smIdDao.deleteSampleById(createdSampleId);
        }
    }
}
