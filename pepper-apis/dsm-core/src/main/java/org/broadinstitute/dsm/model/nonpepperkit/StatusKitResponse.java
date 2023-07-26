package org.broadinstitute.dsm.model.nonpepperkit;

import java.util.ArrayList;

import lombok.Getter;

@Getter
public class StatusKitResponse extends KitResponse {
    ArrayList<NonPepperKitStatus> kits;

    public StatusKitResponse(ArrayList kits) {
        this.kits = kits;
    }

}
