
package org.broadinstitute.dsm.model.elastic.export.parse;

import lombok.Getter;

import java.util.Arrays;
import java.util.NoSuchElementException;

@Getter
enum MedicalRecordAbstractionFieldType {

    DATE("date"),
    BUTTON_SELECT("button_select"),
    NUMBER("number"),
    MULTI_OPTIONS("multi_options"),
    TEXT_AREA("text_area"),
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

    public static boolean isMedicalRecordAbstractionFieldType(String fieldType) {
        try {
            MedicalRecordAbstractionFieldType.valueOf(fieldType);
            return true;
        } catch (IllegalArgumentException iae) {
            return false;
        }
    }

    public static MedicalRecordAbstractionFieldType of(String value) {
        return Arrays.stream(MedicalRecordAbstractionFieldType.values())
                .filter(fieldType -> fieldType.getInnerValue().equals(value))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(String.format("requested type %s does not exist", value)));
    }
}
