package org.broadinstitute.ddp.studybuilder;

import java.util.ArrayList;
import java.util.List;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.WorkflowDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.workflow.ActivityState;
import org.broadinstitute.ddp.model.workflow.StateType;
import org.broadinstitute.ddp.model.workflow.StaticState;
import org.broadinstitute.ddp.model.workflow.StudyRedirectState;
import org.broadinstitute.ddp.model.workflow.WorkflowState;
import org.broadinstitute.ddp.model.workflow.WorkflowTransition;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.jdbi.v3.core.Handle;

@Slf4j
public class WorkflowBuilder {
    private Config cfg;
    private StudyDto studyDto;

    public WorkflowBuilder(Config cfg, StudyDto studyDto) {
        this.cfg = cfg;
        this.studyDto = studyDto;
    }

    void run(Handle handle) {
        insertWorkflow(handle);
    }

    void runUpdate(Handle handle) {
        if (!cfg.hasPath("workflowTransitions")) {
            return;
        }
        int deletionCount = handle.attach(WorkflowDao.class).deleteStudyWorkflow(studyDto.getId());
        log.info("Deleted {} workflow transitions", deletionCount);
        insertWorkflow(handle);
    }

    private void insertWorkflow(Handle handle) {
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
        log.info("Created {} workflow transitions for state={}", transitions.size(), stateAsStr(fromCfg));
    }

    private WorkflowState convertWorkflowState(Handle handle, Config stateCfg) {
        StateType type = StateType.valueOf(stateCfg.getString("type"));
        if (type.isStatic()) {
            return StaticState.of(type);
        } else if (type == StateType.ACTIVITY) {
            String activityCode = stateCfg.getString("activityCode");
            long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
            boolean checkEachInstance = ConfigUtil.getBoolOrElse(stateCfg, "checkEachInstance", false);
            return new ActivityState(activityId, checkEachInstance);
        } else if (type == StateType.STUDY_REDIRECT) {
            String studyGuid = ConfigUtil.getStrIfPresent(stateCfg, "studyGuid");
            String studyName = ConfigUtil.getStrIfPresent(stateCfg, "studyName");
            String redirectUrl = stateCfg.getString("redirectUrl");
            if (studyGuid == null && studyName == null) {
                throw new DDPException("Both studyGuid and studyName cannot be null. Atleast one of them should be provided. ");
            }
            return new StudyRedirectState(studyGuid, studyName, redirectUrl);
        } else {
            throw new DDPException("Unsupported workflow state type " + type);
        }
    }

    private String stateAsStr(Config stateCfg) {
        String type = stateCfg.getString("type");
        if (StateType.ACTIVITY.name().equals(type)) {
            String activityCode = stateCfg.getString("activityCode");
            boolean checkEachInstance = ConfigUtil.getBoolOrElse(stateCfg, "checkEachInstance", false);
            return String.format("%s/%s/%b", type, activityCode, checkEachInstance);
        } else if (StateType.STUDY_REDIRECT.name().equals(type)) {
            String studyGuid = ConfigUtil.getStrIfPresent(stateCfg, "studyGuid");
            String studyName = ConfigUtil.getStrIfPresent(stateCfg, "studyName");
            String redirectUrl = stateCfg.getString("redirectUrl");
            return String.format("%s/%s/%s/%s", type, studyGuid, studyName, redirectUrl);
        } else {
            return type;
        }
    }
}
