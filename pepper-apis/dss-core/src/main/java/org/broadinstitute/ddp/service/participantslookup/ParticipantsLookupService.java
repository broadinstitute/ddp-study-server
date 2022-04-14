package org.broadinstitute.ddp.service.participantslookup;


import static org.broadinstitute.ddp.service.participantslookup.error.ParticipantsLookupErrorType.INVALID_RESULT_MAX_COUNT;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.service.participantslookup.error.ParticipantsLookupException;

/**
 * Abstract class defining a service for Pepper participants lookup.<br>
 * It implements a backend functionality for PRISM participants lookup component.<br>
 * It can have different implementations (i.e. participants searching could
 * be done in different sources: DB, ElasticSearch, etc.).<br>
 * Currently supported only searching in Pepper ElasticSearch database.
 */
@Slf4j
public abstract class ParticipantsLookupService {
    /**
     * Lookup participants (it can return more than 1 results).
     * The results count is limited by parameter 'resultsMaxCount': so, this method could return no more
     * than 'resultMaxCount' rows. The real count of rows which are found is saved into result object
     * in {@link ParticipantsLookupResult#getTotalCount()}.
     *
     * @param participantLookupType type of participants lookup:
     *              {@link ParticipantLookupType#FULL_TEXT_SEARCH_BY_QUERY_STRING} - search participants and proxy users by
     *              a specified `query` (substring to do full-text search in a specified fields);
     *              {@link ParticipantLookupType#BY_PARTICIPANT_GUID} - search by participant's GUID: in successful case
     *              it should return 1 row
     * @param studyDto              dto with info about a study which participants to search for
     * @param query                 string containing a fragment by which to lookup the participants (do a full-text search)
     * @param resultsMaxCount       max count of result rows which will be fetched (in case of type=FULL_TEXT_SEARCH_BY_QUERY_STRING);
     *                              in case of type=BY_PARTICIPANT_GUID this parameter is not used).
     * @return ParticipantsLookupResult - an object containing the participants lookup results.
     */
    public ParticipantsLookupResult lookupParticipants(
            ParticipantLookupType participantLookupType,
            StudyDto studyDto,
            String query,
            Integer resultsMaxCount) {

        if (resultsMaxCount != null && resultsMaxCount <= 0) {
            throw new ParticipantsLookupException(INVALID_RESULT_MAX_COUNT, "resultsMaxCount should be greater than 0");
        }

        log.info("Participants lookup started (study={}, query=\"{}\"), maxCount={}", studyDto.getGuid(), query, resultsMaxCount);

        ParticipantsLookupResult participantsLookupResult = new ParticipantsLookupResult();

        if (StringUtils.isNotBlank(query)) {
            try {
                doLookupParticipants(
                        participantLookupType, studyDto, preProcessQuery(query), resultsMaxCount, participantsLookupResult);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                handleException(e);
                throw new DDPException(e);
            }
        }

        log.info("Participants lookup finished (study={}, query=\"{}\"), found {} rows, fetched {} rows",
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
