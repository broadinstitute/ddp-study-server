package org.broadinstitute.dsm.model.nonpepperkit;

import java.util.ArrayList;

import lombok.Getter;
import org.broadinstitute.dsm.db.dto.kit.nonPepperKit.NonPepperKitStatusDto;

@Getter
public class StatusKitResponse extends KitResponse {
    ArrayList<NonPepperKitStatusDto> kits;

    public StatusKitResponse(ArrayList kits) {
        this.kits = kits;
    }

}
