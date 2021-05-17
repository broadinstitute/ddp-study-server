package org.broadinstitute.ddp.service.participantslookup;


import static org.broadinstitute.ddp.service.participantslookup.error.ParticipantsLookupErrorType.INVALID_RESULT_MAX_COUNT;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.service.participantslookup.error.ParticipantsLookupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract class defining a service for Pepper participants lookup.<br>
 * It implements a backend functionality for PRISM participants lookup component.<br>
 * It can have different implementations (i.e. participants searching could
 * be done in different sources: DB, ElasticSearch, etc.).<br>
 * Currently supported only searching in Pepper ElasticSearch database.
 */
public abstract class ParticipantsLookupService {

    protected static final Logger LOG = LoggerFactory.getLogger(ParticipantsLookupService.class);

    /**
     * Lookup participants (it can return more than 1 results).
     * The results count is limited by parameter 'resultsMaxCount': so, this method could return no more
     * than 'resultMaxCount' rows. The real count of rows which are found is saved into result object
     * in {@link ParticipantsLookupResult#getTotalCount()}.
     *
     * @param studyDto dto with info about a study which participants to search for
     * @param query string containing a fragment by which to lookup the participants (do a full-text search).
     * @return ParticipantsLookupResult - an object containing the participants lookup results.
     */
    public ParticipantsLookupResult lookupParticipants(
            ParticipantLookupType participantLookupType,
            StudyDto studyDto,
            String query,
            Integer resultsMaxCount) {

        if (resultsMaxCount <= 0) {
            throw new ParticipantsLookupException(INVALID_RESULT_MAX_COUNT, "resultsMaxCount should be greater than 0");
        }

        LOG.info("Participants lookup started (study={}, query=\"{}\"), maxCount={}", studyDto.getGuid(), query, resultsMaxCount);

        ParticipantsLookupResult participantsLookupResult = new ParticipantsLookupResult();

        if (StringUtils.isNotBlank(query)) {
            try {
                doLookupParticipants(participantLookupType, studyDto, preProcessQuery(query), resultsMaxCount, participantsLookupResult);
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
                handleException(e);
                throw new DDPException(e);
            }
        }

        LOG.info("Participants lookup finished (study={}, query=\"{}\"), found {} rows, fetched {} rows",
                studyDto.getGuid(), query, participantsLookupResult.getTotalCount(), participantsLookupResult.getResultRows().size());

        return participantsLookupResult;
    }

    /**
     * Run the process of participants lookup.
     * This method should be overridden in a concrete implementation of the participants lookup service.
     */
    protected abstract void doLookupParticipants(
            ParticipantLookupType participantLookupType,
            StudyDto studyDto,
            String query,
            Integer resultsMaxCount,
            ParticipantsLookupResult participantsLookupResult) throws Exception;

    /**
     * Query string preprocessor. Can be overridden in a concrete implementation of the participants lookup service.
     * For example, it could require to escape special characters which used in a query syntax.
     * The default implementation returns the same string.
     */
    protected String preProcessQuery(String query) {
        return query;
    }

    /**
     * Custom exception handling. Can be overridden in a concrete implementation of the participants lookup service.
     * Default implementation does nothing.
     */
    protected void handleException(Exception e) {
    }
}
