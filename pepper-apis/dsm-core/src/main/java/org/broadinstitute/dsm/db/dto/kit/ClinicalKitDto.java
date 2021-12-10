package org.broadinstitute.dsm.db.dto.kit;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class ClinicalKitDto {

    @SerializedName ("participant_id")
    String collaboratorParticipantId;

    @SerializedName ("sample_id")
    String sampleId;

    @SerializedName ("sample_collection")
    String sampleCollection;

    @SerializedName ("material_type")
    String materialType;

    @SerializedName ("vessel_type")
    String vesselType;

    @SerializedName ("mail_to_name")
    String mailToName;

    @SerializedName ("date_of_birth")
    String dateOfBirth;
    public ClinicalKitDto(){}

}
