package org.broadinstitute.ddp.service.participantslookup;


/**
 * Abstract layer defining a Pepper participants lookup.
 * It can have different implementations (i.e. participants lookup could
 * be done from different sources: DB, ElasticSearch, etc.).
 * Currently supported only searching from Pepper ElasticSearch database.
 */
public interface ParticipantsLookupService {

    /**
     * Lookup participants (it can return more than 1 results).
     * The results count is limited by parameter 'resultsMaxCount': this method could return no more
     * than 'resultMaxCount' rows. The real count of rows found is saved into result object
     * in {@link ParticipantsLookupResult#getTotalCount()}.
     *
     * @param studyGuid GUID of a study where to search participants
     * @param query string containing a fragment by which to lookup the participants.
     * @return ParticipantsLookupResult - an object containing the lookup results.
     */
    ParticipantsLookupResult lookupParticipants(String studyGuid, String query, int resultsMaxCount);
}
