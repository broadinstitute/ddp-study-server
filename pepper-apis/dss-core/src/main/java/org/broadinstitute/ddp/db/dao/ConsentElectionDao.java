package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.model.activity.definition.ConsentElectionDef;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;

public interface ConsentElectionDao extends SqlObject {

    @CreateSqlObject
    JdbiExpression getExpressionDao();

    @CreateSqlObject
    JdbiConsentElection getConsentElectionDao();

    default void insertElection(long activityId, ConsentElectionDef election, long revisionId) {
        if (election.getConsentElectionId() != null) {
            throw new DaoException("consent election id is already set");
        }
        if (election.getSelectedExprId() != null) {
            throw new DaoException("consent election selected expression id already set");
        }

        JdbiExpression expressionDao = getExpressionDao();
        String guid = expressionDao.generateUniqueGuid();

        long expressionId = expressionDao.insert(guid, election.getSelectedExpr());
        long electionId = getConsentElectionDao()
                .insert(activityId, election.getStableId(), expressionId, revisionId);
        election.setSelectedExprId(expressionId);
        election.setConsentElectionId(electionId);
    }
}
