package org.broadinstitute.ddp.model.dsm;

import java.util.List;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.service.DsmAddressValidationStatus;
import org.broadinstitute.ddp.transformers.UtcMillisToIsoDateAdapter;

public class ParticipantInstitution {
    @SerializedName("firstName")
    private String firstName;
    @SerializedName("lastName")
    private String lastName;
    @SerializedName("shortId")
    private String userHruid;
    @SerializedName("legacyShortId")
    private String legacyShortId;
    @SerializedName("country")
    private String country;
    @SerializedName("addressValid")
    private int addressValid;
    @SerializedName("participantId")
    private String participantId;
    @SerializedName("address")
    private Address address;
    @SerializedName("institutions")
    private List<Institution> institutions;

    @JsonAdapter(UtcMillisToIsoDateAdapter.class)
    @SerializedName("surveyCreated")
    private Long surveyCreatedMillisSinceEpoch;
    @JsonAdapter(UtcMillisToIsoDateAdapter.class)
    @SerializedName("surveyLastUpdated")
    private Long surveyLastUpdatedMillisSinceEpoch;
    @JsonAdapter(UtcMillisToIsoDateAdapter.class)
    @SerializedName("surveyFirstCompleted")
    private Long surveyFirstCompletedMillisSinceEpoch;

    public ParticipantInstitution(String firstName, String lastName,
                                  String userHruid, String legacyShortId, String country,
                                  Long surveyCreatedMillisSinceEpoch,
                                  Long surveyLastUpdatedMillisSinceEpoch,
                                  Long surveyFirstCompletedMillisSinceEpoch, int addressValid,
                                  String participantId, Address address,
                                  List<Institution> institutions) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.userHruid = userHruid;
        this.legacyShortId = legacyShortId;
        this.country = country;
        this.surveyCreatedMillisSinceEpoch = surveyCreatedMillisSinceEpoch;
        this.surveyLastUpdatedMillisSinceEpoch = surveyLastUpdatedMillisSinceEpoch;
        this.surveyFirstCompletedMillisSinceEpoch = surveyFirstCompletedMillisSinceEpoch;
        this.addressValid = addressValid;
        this.participantId = participantId;
        this.address = address;
        this.institutions = institutions;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getUserHruid() {
        return userHruid;
    }

    public String getLegacyShortId() {
        return legacyShortId;
    }

    public String getCountry() {
        return country;
    }

    public Long getSurveyCreatedMillisSinceEpoch() {
        return surveyCreatedMillisSinceEpoch;
    }

    public Long getSurveyLastUpdatedMillisSinceEpoch() {
        return surveyLastUpdatedMillisSinceEpoch;
    }

    public Long getSurveyFirstCompletedMillisSinceEpoch() {
        return surveyFirstCompletedMillisSinceEpoch;
    }

    public int getAddressValid() {
        return addressValid;
    }

    public String getParticipantId() {
        return participantId;
    }

    public Address getAddress() {
        return address;
    }

    public List<Institution> getInstitutions() {
        return institutions;
    }

    public static class Address {
        @SerializedName("id")
        private String id;
        @SerializedName("name")
        private String name;
        @SerializedName("street1")
        private String street1;
        @SerializedName("street2")
        private String street2;
        @SerializedName("city")
        private String city;
        @SerializedName("state")
        private String state;
        @SerializedName("zip")
        private String zip;
        @SerializedName("country")
        private String country;
        @SerializedName("phone")
        private String phone;
        @SerializedName("empty")
        private boolean empty;
        @SerializedName("valid")
        private boolean valid;

        public Address(String id, String name,
                       String street1, String street2,
                       String city, String state, String zip,
                       String country, String phone, boolean empty, boolean valid) {
            this.id = id;
            this.name = name;
            this.street1 = street1;
            this.street2 = street2;
            this.city = city;
            this.state = state;
            this.zip = zip;
            this.country = country;
            this.phone = phone;
            this.empty = empty;
            this.valid = valid;
        }

        public Address(MailAddress mailAddress) throws Exception {
            if (mailAddress != null) {
                this.street1 = mailAddress.getStreet1();
                this.street2 = mailAddress.getStreet2();
                this.city = mailAddress.getCity();
                this.state = mailAddress.getState();
                this.zip = mailAddress.getZip();
                this.country = mailAddress.getCountry();
                this.phone = mailAddress.getPhone();
                this.empty = false;
                // Check to see if the validation status is one of the Valid Allowable Address Statuses
                this.valid = DsmAddressValidationStatus.addressValidStatuses()
                        .contains(DsmAddressValidationStatus.getByCode(mailAddress.getValidationStatus()));
            } else {
                this.empty = true;
            }
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getStreet1() {
            return street1;
        }

        public String getStreet2() {
            return street2;
        }

        public String getCity() {
            return city;
        }

        public String getState() {
            return state;
        }

        public String getZip() {
            return zip;
        }

        public String getCountry() {
            return country;
        }

        public String getPhone() {
            return phone;
        }

        public boolean isEmpty() {
            return empty;
        }

        public boolean isValid() {
            return valid;
        }
    }

}
