package org.broadinstitute.ddp.service.participantslookup;


import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.exception.DDPException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract class defining a service for Pepper participants lookup.<br>
 * It implements a backend functionality for PRISM participants lookup component.<br>
 * It can have different implementations (i.e. participants searching could
 * be done from different sources: DB, ElasticSearch, etc.).<br>
 * Currently supported only searching from Pepper ElasticSearch database.
 */
public abstract class ParticipantsLookupService {

    private static final Logger LOG = LoggerFactory.getLogger(ParticipantsLookupService.class);

    private static final int QUERY_MAX_LENGTH = 100;

    /**
     * Lookup participants (it can return more than 1 results).
     * The results count is limited by parameter 'resultsMaxCount': so, this method could return no more
     * than 'resultMaxCount' rows. The real count of rows which are found is saved into result object
     * in {@link ParticipantsLookupResult#getTotalCount()}.
     *
     * @param studyGuid GUID of a study where to search participants
     * @param query string containing a fragment by which to lookup the participants (do a full-text search).
     * @return ParticipantsLookupResult - an object containing the participants lookup results.
     */
    public ParticipantsLookupResult lookupParticipants(String studyGuid, String query, int resultsMaxCount) {

        if (studyGuid == null) {
            throw new IllegalArgumentException("studyGuid cannot be null");
        }
        if (query != null && query.length() > QUERY_MAX_LENGTH) {
            throw new IllegalArgumentException("query length should be <= 100");
        }
        if (resultsMaxCount <= 0) {
            throw new IllegalArgumentException("resultsMaxCount should be greater than 0");
        }

        LOG.info("Participants lookup started (study={}, query=\"{}\"), maxCount={}", studyGuid, query, resultsMaxCount);

        ParticipantsLookupResult participantsLookupResult = new ParticipantsLookupResult();

        if (StringUtils.isNotBlank(query)) {
            try {
                doLookupParticipants(studyGuid, query, resultsMaxCount, participantsLookupResult);
            } catch (Exception e) {
                throw new DDPException(e);
            }
        }

        LOG.info("Participants lookup finished (study={}, query=\"{}\"), found {} rows, fetched {} rows",
                studyGuid, query, participantsLookupResult.getTotalCount(), participantsLookupResult.getResultRows().size());

        return participantsLookupResult;
    }

    protected abstract void doLookupParticipants(
            String studyGuid,
            String query,
            int resultsMaxCount,
            ParticipantsLookupResult participantsLookupResult) throws Exception;
}
