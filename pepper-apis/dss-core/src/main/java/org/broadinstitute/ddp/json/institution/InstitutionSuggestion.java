package org.broadinstitute.ddp.json.institution;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.db.dto.InstitutionSuggestionDto;

@Value
@AllArgsConstructor
public class InstitutionSuggestion {
    String name;
    String city;
    String state;
    String country;

    public InstitutionSuggestion(final InstitutionSuggestionDto dto) {
        this(dto.getName(), dto.getCity(), dto.getState(), dto.getCountry());
    }
}
