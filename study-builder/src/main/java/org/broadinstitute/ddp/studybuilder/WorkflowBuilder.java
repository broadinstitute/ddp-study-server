package org.broadinstitute.ddp.studybuilder;

import java.util.ArrayList;
import java.util.List;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.db.dao.WorkflowDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.workflow.ActivityState;
import org.broadinstitute.ddp.model.workflow.StateType;
import org.broadinstitute.ddp.model.workflow.StaticState;
import org.broadinstitute.ddp.model.workflow.WorkflowState;
import org.broadinstitute.ddp.model.workflow.WorkflowTransition;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkflowBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowBuilder.class);

    private Config cfg;
    private StudyDto studyDto;

    public WorkflowBuilder(Config cfg, StudyDto studyDto) {
        this.cfg = cfg;
        this.studyDto = studyDto;
    }

    void run(Handle handle) {
        insertTransitions(handle);
    }

    private void insertTransitions(Handle handle) {
        if (!cfg.hasPath("workflowTransitions")) {
            return;
        }
        for (Config transitionCfg : cfg.getConfigList("workflowTransitions")) {
            insertTransitionSet(handle, transitionCfg);
        }
    }

    public void insertTransitionSet(Handle handle, Config transitionCfg) {
        WorkflowDao workflowDao = handle.attach(WorkflowDao.class);
        List<WorkflowTransition> transitions = new ArrayList<>();

        Config fromCfg = transitionCfg.getConfig("from");
        WorkflowState fromState = convertWorkflowState(handle, fromCfg);

        int order = 1;
        for (Config toCfg : transitionCfg.getConfigList("to")) {
            WorkflowState toState = convertWorkflowState(handle, toCfg);
            String expr = toCfg.getString("expression");
            transitions.add(new WorkflowTransition(studyDto.getId(), fromState, toState, expr, order));
            order++;
        }

        workflowDao.insertTransitions(transitions);
        LOG.info("Created {} workflow transitions for state={}", transitions.size(), stateAsStr(fromCfg));
    }

    private WorkflowState convertWorkflowState(Handle handle, Config stateCfg) {
        StateType type = StateType.valueOf(stateCfg.getString("type"));
        if (type.isStatic()) {
            return StaticState.of(type);
        } else if (type == StateType.ACTIVITY) {
            String activityCode = stateCfg.getString("activityCode");
            long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
            return new ActivityState(activityId);
        } else {
            throw new DDPException("Unsupported workflow state type " + type);
        }
    }

    private String stateAsStr(Config stateCfg) {
        String type = stateCfg.getString("type");
        if (StateType.ACTIVITY.name().equals(type)) {
            String activityCode = stateCfg.getString("activityCode");
            return String.format("%s/%s", type, activityCode);
        } else {
            return type;
        }
    }
}
