package org.broadinstitute.ddp.model.activity.types;

public enum InstitutionType {
    INSTITUTION,
    INITIAL_BIOPSY,
    PHYSICIAN;

    public static InstitutionType fromUrlComponent(String urlComponent) {
        return valueOf(urlComponent.replaceAll("-", "_").toUpperCase());
    }
}
