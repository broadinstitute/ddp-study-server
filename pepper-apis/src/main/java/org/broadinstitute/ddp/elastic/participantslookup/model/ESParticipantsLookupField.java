package org.broadinstitute.ddp.elastic.participantslookup.model;

import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupIndexType.ALL_TYPES;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupIndexType.PARTICIPANTS_STRUCTURED;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupIndexType.USERS;

/**
 * Names of fields in ElasticSearch indices ("users", "participants_structured")
 * by which to do searching.
 */
public enum ESParticipantsLookupField {

    PROFILE__GUID("profile.guid", ALL_TYPES),
    PROFILE__HRUID("profile.hruid", ALL_TYPES),
    PROFILE__FIRST_NAME("profile.firstName", ALL_TYPES),
    PROFILE__LAST_NAME("profile.lastName", ALL_TYPES),
    PROFILE__EMAIL_TEXT("profile.email.text", ALL_TYPES),
    PROFILE__EMAIL("profile.email", ALL_TYPES),
    PROFILE__LEGACY_ALT_PID("profile.legacyAltPid", ALL_TYPES),
    PROFILE__LEGACY_SHORT_ID("profile.legacyShortId", ALL_TYPES),
    INVITATIONS__GUID("invitations.guid", PARTICIPANTS_STRUCTURED),
    GOVERNED_USERS("governedUsers", USERS);

    private String esField;
    private ESParticipantsLookupIndexType indexType;

    ESParticipantsLookupField(String esField, ESParticipantsLookupIndexType indexType) {
        this.esField = esField;
        this.indexType = indexType;
    }

    public String getEsField() {
        return esField;
    }

    public ESParticipantsLookupIndexType getIndexType() {
        return indexType;
    }

    public boolean isQueryFieldForIndex(ESParticipantsLookupIndexType index) {
        return getIndexType() == ALL_TYPES || getIndexType() == index;
    }
}
