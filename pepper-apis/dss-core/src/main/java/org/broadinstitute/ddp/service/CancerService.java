package org.broadinstitute.ddp.service;

import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.ddp.db.CancerStore;
import org.broadinstitute.ddp.db.dto.CancerItem;

public class CancerService {

    public List<CancerItem> fetchCancers(String language) {
        return new ArrayList<>(CancerStore.getInstance().getCancerList(language));
    }
}
