package org.broadinstitute.dsm.model;

import org.broadinstitute.dsm.exception.DsmInternalError;

/** Please do not add to this class or use it in new places
 * This is a component in the inconsistent identification of ddp_instance by study_guid and instance_name.
 * TODO: We need to fix having two different non-key identifiers for a ddp_instance (that is, either instance name or
 * study guid, not both). -DC
 *
 */
public enum Study {
    ATCP("ATCP"),
    RGP("RGP"),
    CMI_OSTEO("CMI-OSTEO");

    private String value;

    Study(String value) {
        this.value = value;
    }

    public static Study of(String studyGuid) {
        for (Study study : Study.values()) {
            if (study.value.equalsIgnoreCase(studyGuid)) {
                return study;
            }
        }
        throw new DsmInternalError("Study: ".concat(studyGuid).concat(" does not exist"));
    }
}
