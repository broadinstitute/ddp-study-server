package org.broadinstitute.dsm.model.gp;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
public class KitInfo {

    private static final Logger logger = LoggerFactory.getLogger(KitInfo.class);

    private String collaboratorParticipantId, collaboratorSampleId, sampleCollectionBarcode, gender, materialInfo, receptacleName,
            accessionNumber, realm, kitTypeName;
    private final int organismClassificationId;

    public KitInfo(String sampleCollectionBarcode,
                   int organismClassificationId,
                   String gender,
                   String bspParticipantId,
                   String bspSampleId,
                   String materialInfo,
                   String receptacleName,
                   String realm,
                   String kitTypeName) {
        this.sampleCollectionBarcode = sampleCollectionBarcode;
        // note that organism is bsp's internal organismClassificationId, as per Damien
        this.organismClassificationId = organismClassificationId;
        this.gender = gender;
        this.collaboratorParticipantId = bspParticipantId;
        this.collaboratorSampleId = bspSampleId;
        this.materialInfo = materialInfo;
        this.receptacleName = receptacleName;
        this.realm = realm;
        this.kitTypeName = kitTypeName;
    }

    public String getKitType() {
        return kitTypeName;
    }

    public void setKitType(String kitType) {
        this.kitTypeName = kitType;
    }
}
