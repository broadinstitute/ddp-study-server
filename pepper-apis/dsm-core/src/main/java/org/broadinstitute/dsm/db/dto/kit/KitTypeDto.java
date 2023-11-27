package org.broadinstitute.dsm.db.dto.kit;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(setterPrefix = "with")
public class KitTypeDto {

    Integer kitTypeId;
    String kitTypeName;
    String bspMaterialType;
    String bspReceptacleType;
    String customsJson;
    Integer requiredRole;
    Boolean manualSentTrack;
    Boolean requiresInsertInKitTracking;
    Boolean noReturn;
    String sampleType;
    String displayName;

}
