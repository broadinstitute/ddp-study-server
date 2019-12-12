package org.broadinstitute.ddp.json.medicalprovider;

import com.google.gson.annotations.SerializedName;

public class PostPatchMedicalProviderRequestPayload {

    @SerializedName(Fields.INSTITUTION_NAME)
    private String institutionName;
    @SerializedName(Fields.PHYSICIAN_NAME)
    private String physicianName;
    @SerializedName(Fields.CITY)
    private String city;
    @SerializedName(Fields.STATE)
    private String state;

    public PostPatchMedicalProviderRequestPayload() {
        this(null, null, null, null);
    }

    public PostPatchMedicalProviderRequestPayload(
            String institutionName,
            String physicianName,
            String city,
            String state
    ) {
        this.institutionName = institutionName;
        this.physicianName = physicianName;
        this.city = city;
        this.state = state;
    }

    public String getPhysicianName() {
        return physicianName;
    }

    public String getInstitutionName() {
        return institutionName;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public static class Fields {
        public static final String INSTITUTION_NAME = "institutionName";
        public static final String PHYSICIAN_NAME = "physicianName";
        public static final String CITY = "city";
        public static final String STATE = "state";
    }

}
