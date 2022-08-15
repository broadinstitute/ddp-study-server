package org.broadinstitute.ddp.json.medicalprovider;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class PostPatchMedicalProviderRequestPayload {
    @SerializedName(Fields.INSTITUTION_NAME)
    String institutionName;
    
    @SerializedName(Fields.PHYSICIAN_NAME)
    String physicianName;
    
    @SerializedName(Fields.CITY)
    String city;
    
    @SerializedName(Fields.STATE)
    String state;

    @SerializedName(Fields.COUNTRY)
    String country;

    public PostPatchMedicalProviderRequestPayload() {
        this(null, null, null, null, null);
    }

    public static class Fields {
        public static final String INSTITUTION_NAME = "institutionName";
        public static final String PHYSICIAN_NAME = "physicianName";
        public static final String CITY = "city";
        public static final String STATE = "state";
        public static final String COUNTRY = "country";
    }
}
