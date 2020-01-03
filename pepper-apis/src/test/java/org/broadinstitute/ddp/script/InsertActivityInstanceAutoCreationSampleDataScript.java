package org.broadinstitute.ddp.script;

import java.time.Instant;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.constants.TestConstants;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstanceCreationAction;
import org.broadinstitute.ddp.db.dao.JdbiActivityStatusTrigger;
import org.broadinstitute.ddp.db.dao.JdbiEventAction;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiEventTrigger;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Not really a test, but a one-off script that inserts the test data for DDP-1981
 */
@Ignore
public class InsertActivityInstanceAutoCreationSampleDataScript extends TxnAwareBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(InsertActivityInstanceAutoCreationSampleDataScript.class);

    private long studyActivityToCreateId;
    private long activityStatusTriggerId;
    private long eventConfigurationId;
    private long eventActionId;
    private long eventTriggerId;
    private long activityInstanceStatusTypeId;
    private long studyActivityTriggeringActionId;
    private long umbrellaStudyId;
    private long creationExprId;
    private long precondExprId;

    @Test
    @Ignore
    public void insertSampleData() throws Exception {
        TransactionWrapper.useTxn(
                handle -> {
                    eventActionId = handle.attach(JdbiEventAction.class).insert(
                            null,
                            EventActionType.ACTIVITY_INSTANCE_CREATION
                    );
                    umbrellaStudyId = handle.attach(JdbiUmbrellaStudy.class)
                            .getIdByGuid(TestConstants.TEST_STUDY_GUID).get();
                    studyActivityToCreateId = handle.attach(JdbiActivity.class).findIdByStudyIdAndCode(umbrellaStudyId,
                            TestData.ACTIVITY_TO_CREATE_CODE).get();
                    handle.attach(JdbiActivityInstanceCreationAction.class).insert(
                            eventActionId,
                            studyActivityToCreateId
                    );

                    eventTriggerId = handle.attach(JdbiEventTrigger.class).insert(EventTriggerType.ACTIVITY_STATUS);
                    studyActivityTriggeringActionId = handle.attach(JdbiActivity.class)
                            .findIdByStudyIdAndCode(umbrellaStudyId, TestData.ACTIVITY_TRIGGERING_ACTION_CODE)
                            .get();

                    activityStatusTriggerId = handle.attach(JdbiActivityStatusTrigger.class).insert(
                            eventTriggerId,
                            studyActivityTriggeringActionId,
                            InstanceStatusType.IN_PROGRESS
                    );

                    precondExprId = handle.attach(JdbiExpression.class).getByGuid(TestData.EXPRESSION_GUID).get().getId();
                    String expressionGuid = DBUtils.uniqueStandardGuid(
                            handle,
                            SqlConstants.ExpressionTable.TABLE_NAME,
                            SqlConstants.ExpressionTable.GUID
                    );
                    creationExprId = handle.attach(JdbiExpression.class).insert(
                            expressionGuid,
                            "true"
                    );
                    eventConfigurationId = handle.attach(JdbiEventConfiguration.class).insert(
                            eventTriggerId,
                            eventActionId,
                            umbrellaStudyId,
                            Instant.now().toEpochMilli(),
                            5,
                            null,
                            precondExprId,
                            null,
                            false,
                            1
                    );
                }
        );
    }

    private static final class TestData {
        public static final String ACTIVITY_TO_CREATE_CODE = "READONLY01";
        public static final String EXPRESSION_GUID = "5D0916D879";
        public static final String ACTIVITY_TRIGGERING_ACTION_CODE = "DEF281089B";
    }

}
