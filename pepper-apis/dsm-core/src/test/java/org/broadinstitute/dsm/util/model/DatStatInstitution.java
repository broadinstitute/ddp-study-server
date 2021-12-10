package org.broadinstitute.dsm.util.model;

public class DatStatInstitution {

    private String id;
    private String type;
    private String institution;
    private String institutionName;
    private String city;
    private String state;

    public DatStatInstitution(String id, String type, String institution, String institutionName, String city, String state) {
        this.id = id;
        this.type = type;
        this.institution = institution;
        this.institutionName = institutionName;
        this.city = city;
        this.state = state;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getInstitution() {
        return institution;
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
}