package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.QuestionDao;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.pex.Expression;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.WorkflowBuilder;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class UpdateWorkflowInternationalAbstract implements CustomTask {
    private static final Logger LOG = LoggerFactory.getLogger(UpdateWorkflowInternationalAbstract.class);
    private static final String DATA_FILE = "patches/update-international-workflow.conf";

    private static final int NUM_TRANSITIONS = 1;
    private Config cfg;
    private Config dataCfg;
    private String studyGuid;
    private String fromState;
    private String toState;

    UpdateWorkflowInternationalAbstract(String studyGuid, String fromState, String toState) {
        this.studyGuid = studyGuid;
        this.fromState = fromState;
        this.toState = toState;
    }

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(studyGuid)) {
            throw new DDPException("This task is only for the brain/angio studies!");
        }
        this.cfg = studyCfg;

        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
    }

    @Override
    public void run(Handle handle) {
        // First get all the relevant values
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
        LOG.info("Updating study " + studyGuid + " with id " + studyDto.getId());


        long fromStateActivityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), fromState);
        long toStateActivityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), toState);

        int workflowTransitions = handle.createUpdate("delete from workflow_transition where from_state_id = "
                + " (select workflow_state_id from workflow_activity_state where study_activity_id = "
                + fromStateActivityId + ") "
                + " and next_state_id = (select workflow_state_id from workflow_activity_state where study_activity_id = "
                + toStateActivityId + ")").execute();

        if (workflowTransitions != 1) {
            throw new RuntimeException("Wrong number of workflow transitions deleted: " + workflowTransitions);
        }

        QuestionDto questionDto = handle.attach(JdbiQuestion.class)
                .getLatestQuestionDtoByQuestionStableIdAndUmbrellaStudyId("COUNTRY", studyDto.getId())
                .orElseThrow(() -> new RuntimeException("Couldn't find country question"));

        handle.attach(QuestionDao.class).addRequiredRule(questionDto.getId(), new RequiredRuleDef(Template.text("Country is required")),
                questionDto.getRevisionId());

        addNewWorkflowTransitions(handle, studyDto);

        Expression expression = handle.attach(JdbiExpression.class).insertExpression(
                "user.studies[\"" + studyGuid + "\"].forms[\"" + fromState + "\"]"
                        + ".questions[\"COUNTRY\"].answers.hasOption(\"US\") || user.studies[\"" + studyGuid + "\"]"
                        + ".forms[\"" + fromState + "\"].questions[\"COUNTRY\"].answers.hasOption(\"CA\")");


        int emailConfigs = handle.createUpdate("update event_configuration "
                + "    join activity_status_trigger ast on event_configuration.event_trigger_id "
                + " = ast.activity_status_trigger_id "
                + "    join study_activity sa on ast.study_activity_id = sa.study_activity_id "
                + "    join activity_instance_status_type aist on ast.activity_instance_status_type_id "
                + " = aist.activity_instance_status_type_id"
                + "    set event_configuration.precondition_expression_id = " + expression.getId()
                + "    where sa.study_activity_code = '" + toState + "'"
                + "    and sa.study_id = " + studyDto.getId()
                + "    and aist.activity_instance_status_type_code = 'CREATED'").execute();

        if (emailConfigs != 4) {
            throw new RuntimeException("We expected to update four email configs, but updated:"
                    + emailConfigs);
        }


    }

    private void addNewWorkflowTransitions(Handle handle, StudyDto studyDto) {
        List<? extends Config> transitions = dataCfg.getConfigList("workflowTransitions");
        if (transitions.size() != NUM_TRANSITIONS) {
            throw new DDPException("Expected " + NUM_TRANSITIONS
                    + " sets of workflow transitions but got " + transitions.size());
        }

        WorkflowBuilder workflowBuilder = new WorkflowBuilder(cfg, studyDto);
        for (Config transitionCfg : transitions) {
            workflowBuilder.insertTransitionSet(handle, transitionCfg);
        }
    }


}
