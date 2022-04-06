package org.broadinstitute.ddp.service;

import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.ddp.db.CancerStore;
import org.broadinstitute.ddp.model.dsm.Cancer;

public class CancerService {

    public List<Cancer> fetchCancers() {
        List<Cancer> cancers = new ArrayList<>();
        cancers.addAll(CancerStore.getInstance().getCancerList());
        return cancers;
    }
}
