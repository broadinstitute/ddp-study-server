package org.broadinstitute.dsm.model.gp;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
public class KitInfo {

    private static final Logger logger = LoggerFactory.getLogger(KitInfo.class);
    private final int organismClassificationId;
    private String collaboratorParticipantId;
    private String collaboratorSampleId;
    private String sampleCollectionBarcode;
    private String gender;
    private String materialInfo;
    private String receptacleName;
    private String accessionNumber;
    private String realm;
    private String kitTypeName;
    private String collectionDate;

    public KitInfo(String sampleCollectionBarcode, int organismClassificationId, String gender, String bspParticipantId, String bspSampleId,
                   String materialInfo, String receptacleName, String realm, String kitTypeName, String collectionDate) {
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
        this.collectionDate = collectionDate;
    }

    public String getKitType() {
        return kitTypeName;
    }

    public void setKitType(String kitType) {
        this.kitTypeName = kitType;
    }
}
