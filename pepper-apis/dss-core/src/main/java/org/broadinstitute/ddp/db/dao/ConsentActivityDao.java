package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.definition.ConsentActivityDef;
import org.broadinstitute.ddp.model.activity.definition.ConsentElectionDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface ConsentActivityDao extends FormActivityDao {

    Logger LOG = LoggerFactory.getLogger(ConsentActivityDao.class);

    @CreateSqlObject
    JdbiConsentCondition getConsentConditionDao();

    @CreateSqlObject
    ConsentElectionDao getConsentElectionDao();


    /**
     * Create new consent form activity by inserting all related data for activity definition. Use this to create the
     * first version of a consent activity.
     *
     * <p>See {@link FormActivityDao#insertActivity(FormActivityDef, long)} for other types of form activities,
     * and the appropriate add/disable methods for changing content.
     *
     * @param consent    the consent activity to create, should not have generated things like ids
     * @param revisionId the revision to use, will be shared for all created data
     */
    default void insertActivity(ConsentActivityDef consent, long revisionId) {
        JdbiExpression jdbiExpr = getJdbiExpression();
        JdbiConsentCondition jdbiConsentCondition = getConsentConditionDao();
        ConsentElectionDao consentElectionDao = getConsentElectionDao();

        insertActivity((FormActivityDef) consent, revisionId);

        String expressionGuid = jdbiExpr.generateUniqueGuid();
        long expressionId = jdbiExpr.insert(expressionGuid, consent.getConsentedExpr());
        long conditionId = jdbiConsentCondition.insert(consent.getActivityId(), expressionId, revisionId);
        consent.setConsentedExprId(expressionId);
        consent.setConsentConditionId(conditionId);
        LOG.info("Inserted consent condition id={} expr='{}'", conditionId, consent.getConsentedExpr());

        for (ConsentElectionDef election : consent.getElections()) {
            consentElectionDao.insertElection(consent.getActivityId(), election, revisionId);
            LOG.info("Inserted consent election id={} expr='{}'", election.getConsentElectionId(), election.getSelectedExpr());
        }
    }
}
