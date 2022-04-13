package org.broadinstitute.ddp.pex;

import org.broadinstitute.ddp.db.dto.UserActivityInstanceSummary;
import org.broadinstitute.ddp.model.event.EventSignal;
import org.jdbi.v3.core.Handle;

/**
 * An interpreter that parses and evaluates pex expressions.
 */
public interface PexInterpreter {

    /**
     * Evaluates the given expression against data we currently have in system.
     *
     * @param expression the pex expression
     * @param handle     the database handle to a source where we can query data to evaluate against
     * @param activityInstanceGuid the activity instance guid for evaluation context
     * @param userGuid   the guid of user for evaluation context
     * @return result of expression evaluation
     * @throws PexLexicalException     if there are lexical or token errors
     * @throws PexParseException       if there are syntax errors
     * @throws PexFetchException       if there are issues fetching data
     * @throws PexUnsupportedException if unsupported features are used in expression
     */
    boolean eval(String expression, Handle handle, String userGuid, String operatorGuid, String activityInstanceGuid);

    boolean eval(String expression, Handle handle, String userGuid, String operatorGuid, String activityInstanceGuid,
                 UserActivityInstanceSummary activityInstanceSummary);

    boolean eval(String expression, Handle handle, String userGuid, String operatorGuid, String activityInstanceGuid,
                 UserActivityInstanceSummary activityInstanceSummary, EventSignal signal);
}
