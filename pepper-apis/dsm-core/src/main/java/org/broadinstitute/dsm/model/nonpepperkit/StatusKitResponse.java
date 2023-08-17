package org.broadinstitute.dsm.model.nonpepperkit;

import java.util.List;

import lombok.Getter;
import org.broadinstitute.dsm.db.dto.kit.nonPepperKit.NonPepperKitStatusDto;

@Getter
public class StatusKitResponse extends KitResponse {
    List<NonPepperKitStatusDto> kits;

    public StatusKitResponse(List kits) {
        this.kits = kits;
    }

}
