package org.broadinstitute.ddp.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.dsm.DrugStore;
import org.broadinstitute.ddp.service.DsmDrugListService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DsmDrugLoader {

    private static final Logger LOG = LoggerFactory.getLogger(DsmDrugLoader.class);

    public void fetchAndLoadDrugs(String dsmBaseUrl, String dsmJWTSecret) {
        LOG.info("DsmDrugLoader fetch and load drug list...");
        List<String> dsmDrugs = fetchDsmDrugs(dsmBaseUrl, dsmJWTSecret);
        if (CollectionUtils.isNotEmpty(dsmDrugs)) {
            loadDrugList(dsmDrugs);
            LOG.info("Loaded {} drugs into pepper.", dsmDrugs.size());
        } else {
            LOG.warn("Trying to load empty drug list. skipping...");
        }
    }

    private List<String> fetchDsmDrugs(String dsmBaseUrl, String dsmJWTSecret) {
        URL dsmURL;
        try {
            dsmURL = new URL(dsmBaseUrl);
        } catch (MalformedURLException e) {
            throw new DDPException("Invalid Dsm base url: " + dsmBaseUrl);
        }

        DsmDrugListService service = new DsmDrugListService(dsmURL);

        return service.fetchDrugs(dsmJWTSecret);
    }

    private void loadDrugList(List<String> dsmDrugs) {
        DrugStore drugStore = DrugStore.getInstance();
        drugStore.populateDrugList(dsmDrugs);
    }

}
