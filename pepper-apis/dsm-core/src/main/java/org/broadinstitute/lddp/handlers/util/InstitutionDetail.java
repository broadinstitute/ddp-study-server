package org.broadinstitute.lddp.handlers.util;

public class InstitutionDetail extends Institution {
    private String physician;
    private String institution;
    private String streetAddress;
    private String city;
    private String state;

    public InstitutionDetail() {
    }

    public InstitutionDetail(String id, String physician, String institution, String city, String state, String type) {
        super(id, type);
        this.physician = physician;
        this.institution = institution;
        this.city = city;
        this.state = state;
    }

    public String getPhysician() {
        return physician;
    }

    public String getInstitution() {
        return institution;
    }

    public String getStreetAddress() {
        return streetAddress;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

}

