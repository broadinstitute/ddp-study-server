package org.broadinstitute.dsm.careevolve;

import com.google.gson.annotations.SerializedName;

public class Patient {

    @SerializedName("PatientID")
    private String patientId;

    @SerializedName("LastName")
    private String lastName;

    @SerializedName("FirstName")
    private String firstName;

    // YYYY-MM-DD
    @SerializedName("DateOfBirth")
    private String dateOfBirth;

    // cdc 1000-9 values
    @SerializedName("Race")
    private String race;

    @SerializedName("Ethnicity")
    private String ethnicity;

    @SerializedName("Gender")
    private String gender;

    @SerializedName("Address")
    private Address address;

    public Patient(String patientId,
                   String firstName,
                   String lastName,
                   String dateOfBirth,
                   String race,
                   String ethnicity,
                   String gender,
                   Address address) {
        this.patientId = patientId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
        this.race = race;
        this.ethnicity = ethnicity;
        this.gender = gender;
        this.address = address;
    }
}
