
package org.broadinstitute.dsm.model.elastic.export.parse.abstraction;

import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * An enumerated representation of `medical_record_abstraction_field` types in DB.
 */
public enum MedicalRecordAbstractionFieldType {

    DATE("date"),
    BUTTON_SELECT("button_select"),
    NUMBER("number"),
    MULTI_OPTIONS("multi_options"),
    TEXT_AREA("textarea"),
    MULTI_TYPE_ARRAY("multi_type_array"),
    TEXT("text"),
    TABLE("table"),
    OPTIONS("options"),
    DRUGS("drugs"),
    MULTI_TYPE("multi_type"),
    CHECKBOX("checkbox"),
    CANCERS("cancers");

    private final String innerValue;

    MedicalRecordAbstractionFieldType(String innerValue) {
        this.innerValue = innerValue;
    }

    public String asString() {
        return this.innerValue;
    }

    /**
     * returns the Enum value based on if the argument matches the innerValue
     * @value a value to match against an innerValue field
     */
    public static MedicalRecordAbstractionFieldType of(String value) {
        return Arrays.stream(MedicalRecordAbstractionFieldType.values())
                .filter(fieldType -> fieldType.asString().equals(value))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(String.format("requested type %s does not exist", value)));
    }

}
