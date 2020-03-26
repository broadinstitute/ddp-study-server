package org.broadinstitute.ddp.model.study;

/**
 * Mapping types to indicate an activity has a certain attribute of interest to the platform.
 */
public enum ActivityMappingType {
    /**
     * Whether or not the user has agreed to donate blood
     */
    BLOOD,

    /**
     * Whether or not the user has agreed to donate tissue
     */
    TISSUE,

    /**
     * Whether or not the user as agreed to the medical release
     */
    MEDICAL_RELEASE,

    /**
     * The field name to use to find the date of the participant's diagnosis
     */
    DATE_OF_DIAGNOSIS,

    /**
     * The field name to use to find the participant's date of birth
     */
    DATE_OF_BIRTH
}
