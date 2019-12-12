package org.broadinstitute.ddp.db.dao;

import static org.broadinstitute.ddp.constants.SqlConstants.ActivityInstanceTable;
import static org.broadinstitute.ddp.constants.SqlConstants.ExpressionTable;
import static org.broadinstitute.ddp.constants.SqlConstants.StudyActivityTable;
import static org.broadinstitute.ddp.constants.SqlConstants.WorkflowActivityStateTable;
import static org.broadinstitute.ddp.constants.SqlConstants.WorkflowStateTypeTable;
import static org.broadinstitute.ddp.constants.SqlConstants.WorkflowTransitionTable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.json.workflow.WorkflowActivityResponse;
import org.broadinstitute.ddp.model.pex.Expression;
import org.broadinstitute.ddp.model.workflow.ActivityState;
import org.broadinstitute.ddp.model.workflow.NextStateCandidate;
import org.broadinstitute.ddp.model.workflow.StateType;
import org.broadinstitute.ddp.model.workflow.WorkflowState;
import org.broadinstitute.ddp.model.workflow.WorkflowTransition;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface WorkflowDao extends SqlObject {

    @CreateSqlObject
    JdbiExpression getJdbiExpression();

    @CreateSqlObject
    JdbiWorkflowState getJdbiWorkflowState();

    @CreateSqlObject
    JdbiWorkflowActivityState getJdbiWorkflowActivityState();

    @CreateSqlObject
    JdbiWorkflowTransition getJdbiWorkflowTransition();


    default void insertTransitions(List<WorkflowTransition> transitions) {
        JdbiExpression jdbiExpr = getJdbiExpression();
        JdbiWorkflowTransition jdbiTrans = getJdbiWorkflowTransition();

        for (WorkflowTransition transition : transitions) {
            if (transition.getId() != null) {
                throw new IllegalStateException("Workflow transition id already set to " + transition.getId());
            }

            long fromStateId = findWorkflowStateIdOrInsert(transition.getFromState());
            long nextStateId = findWorkflowStateIdOrInsert(transition.getNextState());

            Expression precondition = jdbiExpr.insertExpression(transition.getPrecondition());
            long preconditionId = precondition.getId();

            long id = jdbiTrans.insertActive(transition.getStudyId(), fromStateId, nextStateId, preconditionId, transition.getOrder());
            transition.setId(id);
        }
    }

    default Optional<Long> findWorkflowStateId(WorkflowState state) {
        if (state.getType() == StateType.ACTIVITY) {
            long activityId = ((ActivityState) state).getActivityId();
            return getJdbiWorkflowActivityState().findIdByActivityId(activityId);
        } else {
            return getJdbiWorkflowState().findIdByType(state.getType());
        }
    }

    default long findWorkflowStateIdOrInsert(WorkflowState state) {
        return findWorkflowStateId(state).orElseGet(() -> {
            long id = getJdbiWorkflowState().insert(state.getType());
            if (state.getType() == StateType.ACTIVITY) {
                long activityId = ((ActivityState) state).getActivityId();
                if (1 != getJdbiWorkflowActivityState().insert(id, activityId)) {
                    throw new DaoException("Unable to insert " + state);
                }
            }
            return id;
        });
    }

    @UseStringTemplateSqlLocator
    @SqlQuery("queryOrderedActiveNextStateCandidatesByStudyIdAndFromStateId")
    @RegisterRowMapper(NextStateCandidateMapper.class)
    List<NextStateCandidate> findOrderedActiveNextStateCandidates(@Bind("studyId") long studyId, @Bind("fromStateId") long fromStateId);

    class NextStateCandidateMapper implements RowMapper<NextStateCandidate> {
        @Override
        public NextStateCandidate map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new NextStateCandidate(
                    rs.getLong(WorkflowTransitionTable.ID),
                    StateType.valueOf(rs.getString(WorkflowStateTypeTable.CODE)),
                    (Long) rs.getObject(WorkflowActivityStateTable.ACTIVITY_ID),
                    rs.getString(ExpressionTable.TEXT));
        }
    }

    @UseStringTemplateSqlLocator
    @SqlQuery("queryActivityCodeAndLatestInstanceGuidByActivityIdAndUserGuid")
    @RegisterRowMapper(WorkflowActivityResponseMapper.class)
    Optional<WorkflowActivityResponse> findActivityCodeAndLatestInstanceGuidAsResponse(@Bind("activityId") long activityId,
                                                                                       @Bind("userGuid") String userGuid);

    class WorkflowActivityResponseMapper implements RowMapper<WorkflowActivityResponse> {
        @Override
        public WorkflowActivityResponse map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new WorkflowActivityResponse(
                    rs.getString(StudyActivityTable.CODE),
                    rs.getString(ActivityInstanceTable.GUID),
                    rs.getBoolean(StudyActivityTable.ALLOW_UNAUTHENTICATED));
        }
    }
}
