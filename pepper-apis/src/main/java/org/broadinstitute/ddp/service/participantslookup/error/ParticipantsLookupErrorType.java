package org.broadinstitute.ddp.service.participantslookup.error;

/**
 * Participants Lookup service error types
 */
public enum ParticipantsLookupErrorType {

    INVALID_QUERY,
    STUDY_GUID_UNKNOWN,
    INVALID_RESULT_MAX_COUNT,
    ELASTIC_SEARCH_STATUS
}
