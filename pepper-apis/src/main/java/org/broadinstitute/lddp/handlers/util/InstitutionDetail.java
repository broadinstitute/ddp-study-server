package org.broadinstitute.lddp.handlers.util;

import com.google.gson.JsonElement;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;

public class InstitutionDetail extends Institution {
    public InstitutionDetail() {
    }

    private String physician;
    private String institution;
    private String streetAddress;
    private String city;
    private String state;

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

    public static ArrayList<InstitutionDetail> getCombinedInstitutions(JsonElement physiciansElement, JsonElement institutionsElement,
                                                                       JsonElement biopsyInstElement, JsonElement biopsyCityElement, JsonElement biopsyStateElement) {

        ArrayList<PhysicianInfo> physicians = null;
        if (!physiciansElement.isJsonNull()) {
            physicians = PhysicianInfo.jsonToArrayList(physiciansElement.getAsString());
        }

        ArrayList<InstitutionInfo> institutions = null;
        if (!institutionsElement.isJsonNull()) {
            institutions = InstitutionInfo.jsonToArrayList(institutionsElement.getAsString());
        }

        return getCombinedInstitutions(physicians, institutions,
                (biopsyInstElement.isJsonNull() ? null : biopsyInstElement.getAsString()),
                (biopsyCityElement.isJsonNull() ? null : biopsyCityElement.getAsString()),
                (biopsyStateElement.isJsonNull() ? null : biopsyStateElement.getAsString()));
    }

    public static ArrayList<InstitutionDetail> getCombinedInstitutions(ArrayList<PhysicianInfo> physicians, ArrayList<InstitutionInfo> institutions,
                                                                       String initialBiopsyInstitution, String initialBiopsyCity, String initialBiopsyState) {

        ArrayList<InstitutionDetail> combinedInstitutions = new ArrayList<>();

        //physicians
        if ((physicians != null)&&(!physicians.isEmpty())) {
            for (PhysicianInfo physician : physicians) {
                if (!StringUtils.isBlank(physician.getPhysicianId())) {
                    combinedInstitutions.add(new InstitutionDetail(physician.getPhysicianId(), physician.getName(), physician.getInstitution(), physician.getCity(), physician.getState(),
                            InstitutionType.PHYSICIAN.toString()));
                }
            }
        }

        //institutions
        if ((institutions != null)&&(!institutions.isEmpty())) {
            for (InstitutionInfo institution : institutions) {
                if (!StringUtils.isBlank(institution.getInstitutionId())) {
                    combinedInstitutions.add(new InstitutionDetail(institution.getInstitutionId(), null, institution.getInstitution(), institution.getCity(), institution.getState(),
                            InstitutionType.INSTITUTION.toString()));
                }
            }
        }

        //initial biopsy institution
        if (!(StringUtils.isBlank(initialBiopsyInstitution)&&StringUtils.isBlank(initialBiopsyCity)&&StringUtils.isBlank(initialBiopsyState))) {
            combinedInstitutions.add(new InstitutionDetail(Institution.INITIAL_BIOPSY_ID, null, initialBiopsyInstitution, initialBiopsyCity, initialBiopsyState, InstitutionType.INITIAL_BIOPSY.toString()));
        }

        return combinedInstitutions;
    }
}

