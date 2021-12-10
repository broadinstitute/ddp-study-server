package org.broadinstitute.dsm.model;

import java.util.Collection;

public class KitUploadResponse {

    private Collection<KitRequest> invalidKitAddressList;
    private Collection<KitRequest> duplicateKitList;
    private Collection<KitRequest> specialKitList;
    private String specialMessage;

    public KitUploadResponse(Collection<KitRequest> invalidKitAddressList, Collection<KitRequest> duplicateKitList, Collection<KitRequest> specialKitList,
                             String specialMessage) {
        this.invalidKitAddressList = invalidKitAddressList;
        this.duplicateKitList = duplicateKitList;
        this.specialKitList = specialKitList;
        this.specialMessage = specialMessage;
    }
}
