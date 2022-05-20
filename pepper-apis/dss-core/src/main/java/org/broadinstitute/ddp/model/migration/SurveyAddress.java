package org.broadinstitute.ddp.model.migration;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class SurveyAddress {
    String fullName;
    String street1;
    String street2;
    String city;
    String state;
    String country;
    String postalCode;
    String phone;
}
