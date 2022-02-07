package org.broadinstitute.ddp.json.institution;

import org.broadinstitute.ddp.db.dto.InstitutionSuggestionDto;

public class InstitutionSuggestion {

    private String name;
    private String city;
    private String state;

    public InstitutionSuggestion(InstitutionSuggestionDto dto) {
        this.name = dto.getName();
        this.city = dto.getCity();
        this.state = dto.getState();
    }

    public String getName() {
        return name;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

}
