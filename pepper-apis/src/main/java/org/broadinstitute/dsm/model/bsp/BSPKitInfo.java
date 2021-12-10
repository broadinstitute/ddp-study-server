package org.broadinstitute.dsm.model.bsp;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
public class BSPKitInfo {

    private static final Logger logger = LoggerFactory.getLogger(BSPKitInfo.class);

    private final String collaboratorParticipantId, collaboratorSampleId, sampleCollectionBarcode, gender, materialInfo, receptacleName;
    private final int organismClassificationId;

    public BSPKitInfo (String sampleCollectionBarcode,
                       int organismClassificationId,
                       String gender,
                       String bspParticipantId,
                       String bspSampleId,
                       String materialInfo,
                       String receptacleName) {
        this.sampleCollectionBarcode = sampleCollectionBarcode;
        // note that organism is bsp's internal organismClassificationId, as per Damien
        this.organismClassificationId = organismClassificationId;
        this.gender = gender;
        this.collaboratorParticipantId = bspParticipantId;
        this.collaboratorSampleId = bspSampleId;
        this.materialInfo = materialInfo;
        this.receptacleName = receptacleName;
    }
}
