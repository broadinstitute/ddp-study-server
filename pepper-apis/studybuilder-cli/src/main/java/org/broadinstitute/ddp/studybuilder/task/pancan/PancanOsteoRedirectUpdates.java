package org.broadinstitute.ddp.studybuilder.task.pancan;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.WorkflowBuilder;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public class PancanOsteoRedirectUpdates implements CustomTask {

    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;
    private Config dataCfg;

    private SqlHelper sqlHelper;
    private static final String DATA_FILE = "patches/osteo-redirect-workflows.conf";

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
        var studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        if (!studyDto.getGuid().equals("cmi-pancan")) {
            throw new DDPException("This task is only for the pancan study!");
        }

        sqlHelper = handle.attach(SqlHelper.class);

        //find and delete existing OSTEO study redirect workflow transition
        List<Long> workflowTransitionIds = sqlHelper.getPancanOsteoStudyRedirectWorkflowIds();
        DBUtils.checkDelete(workflowTransitionIds.size(), sqlHelper.deleteOsteoRedirectWorkflowTransitions(workflowTransitionIds));
        log.info("Deleted workflow transition with IDs: {}", workflowTransitionIds);

        //insert updated osteo study redirect workflow transitions
        addWorkflows(handle, studyDto);

        //update block visibility pex expressions to handle OSTEO (C_SARCOMAS_OSTEOSARCOMA) from non english REDIRECT pex
        int rowCount = sqlHelper.updatePancanOsteoBlockPex1();
        log.info("Updated {} rows in expression table for Expr1", rowCount);

        rowCount = sqlHelper.updatePancanOsteoBlockPex2();
        log.info("Updated {} rows in expression table for Expr2", rowCount);

        rowCount = sqlHelper.updatePancanOsteoBlockPex3();
        log.info("Updated {} rows in expression table for Expr3", rowCount);

    }

    private void addWorkflows(Handle handle, StudyDto studyDto) {
        List<? extends Config> workflows = dataCfg.getConfigList("workflowTransitions");
        log.info("adding workflows");
        WorkflowBuilder workflowBuilder = new WorkflowBuilder(studyCfg, studyDto);
        for (Config workflowCfg : workflows) {
            workflowBuilder.insertTransitionSet(handle, workflowCfg);
            log.info("Inserted workflow transition: {}", workflowCfg);
        }
    }

    private interface SqlHelper extends SqlObject {

        @SqlQuery("select trans.workflow_transition_id "
                + "  from workflow_transition as trans "
                + "  join umbrella_study as s on s.umbrella_study_id = trans.umbrella_study_id "
                + "  join workflow_state as next_state on next_state.workflow_state_id = trans.next_state_id "
                + "  join workflow_state_type as next_state_type on next_state_type.workflow_state_type_id "
                + " = next_state.workflow_state_type_id "
                + " left join workflow_activity_state as next_act_state on next_act_state.workflow_state_id "
                + " = next_state.workflow_state_id "
                + " left join expression as expr on expr.expression_id = trans.precondition_expression_id "
                + " left join workflow_study_redirect_state as next_redirect_state on "
                + " next_redirect_state.workflow_state_id = next_state.workflow_state_id "
                + " where s.guid = 'cmi-pancan' "
                + " and trans.is_active "
                + " and next_state_type.workflow_state_type_code = 'STUDY_REDIRECT' "
                + " and study_guid = 'CMI-OSTEO'")
        List<Long> getPancanOsteoStudyRedirectWorkflowIds();

        @SqlUpdate("delete workflow_transition  "
                + "from workflow_transition "
                + "where workflow_transition_id in (<workflowTransitionIds>)")
        int deleteOsteoRedirectWorkflowTransitions(@BindList("workflowTransitionIds") List<Long> workflowTransitionIds);


        @SqlUpdate("update expression\n" +
                "set expression_text = '  !( (user.profile.language() == \"en\" &&\n" +
                "                    (user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].children[\"PRIMARY_CANCER_SELF\"].answers.hasOptionStartsWith(\"C_BRAIN_\")\n" +
                "                      || user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_CHILD\"].children[\"PRIMARY_CANCER_CHILD\"].answers.hasOptionStartsWith(\"C_BRAIN_\")\n" +
                "                      || user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].children[\"PRIMARY_CANCER_SELF\"].answers.hasAnyOption(\"C_SARCOMAS_S_LEIOMYO_LMS_SARCOMA\", \"C_GYNECOLOGIC_UTERINE_LEIOMYOSARCOMA\", \"C_SARCOMA_CUTANEOUS_LEIMYOSARCOMA\")\n" +
                "                      || user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_CHILD\"].children[\"PRIMARY_CANCER_CHILD\"].answers.hasAnyOption(\"C_SARCOMAS_S_LEIOMYO_LMS_SARCOMA\", \"C_GYNECOLOGIC_UTERINE_LEIOMYOSARCOMA\", \"C_SARCOMA_CUTANEOUS_LEIMYOSARCOMA\")\n" +
                "                    ) &&\n" +
                "                    !(\n" +
                "                      (user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].numChildAnswers(\"PRIMARY_CANCER_SELF\") > 1\n" +
                "                        && user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].children[\"PRIMARY_CANCER_SELF\"].answers.hasOptionStartsWith( \"C_GASTRO_ESOPHAGEAL_CANCER\", \"C_GASTRO_GASTRIC_STOMACH_CANCER\", \"C_GENITOURINARY_PROSTATE\", \"C_BREAST_\")\n" +
                "                      )\n" +
                "                      ||\n" +
                "                      (user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_CHILD\"].numChildAnswers(\"PRIMARY_CANCER_CHILD\") > 1\n" +
                "                        && user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_CHILD\"].children[\"PRIMARY_CANCER_CHILD\"].answers.hasOptionStartsWith( \"C_GASTRO_ESOPHAGEAL_CANCER\", \"C_GASTRO_GASTRIC_STOMACH_CANCER\", \"C_GENITOURINARY_PROSTATE\", \"C_BREAST_\")\n" +
                "                      )\n" +
                "                    )\n" +
                "                  )\n" +
                "               || ( user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].numChildAnswers(\"PRIMARY_CANCER_SELF\") == 1\n" +
                "                      && user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].children[\"PRIMARY_CANCER_SELF\"]\n" +
                "                      .answers.hasOptionStartsWith( \"C_GASTRO_ESOPHAGEAL_CANCER\", \"C_GASTRO_GASTRIC_STOMACH_CANCER\")\n" +
                "                      && !user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"DESCRIBE\"].answers.hasOption(\"CHILD_DIAGNOSED\")\n" +
                "                      && user.profile.language() == \"en\"\n" +
                "                   )\n" +
                "               || ( user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].numChildAnswers(\"PRIMARY_CANCER_SELF\") == 1\n" +
                "                      && user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].children[\"PRIMARY_CANCER_SELF\"].answers.hasOptionStartsWith(\"C_BREAST_\")\n" +
                "                      && !user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"DESCRIBE\"].answers.hasOption(\"CHILD_DIAGNOSED\")\n" +
                "                      && user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"ADVANCED_BREAST\"].answers.hasOption(\"YES\")\n" +
                "                  )\n" +
                "               || (user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].children[\"PRIMARY_CANCER_SELF\"].answers.hasOptionStartsWith(\"C_SARCOMAS_OSTEOSARCOMA\")\n" +
                "                      || user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_CHILD\"].children[\"PRIMARY_CANCER_CHILD\"].answers.hasOptionStartsWith(\"C_SARCOMAS_OSTEOSARCOMA\")\n" +
                "                  )\n" +
                "               || ( user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].numChildAnswers(\"PRIMARY_CANCER_SELF\") == 1\n" +
                "                      && user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].children[\"PRIMARY_CANCER_SELF\"].answers.hasOptionStartsWith(\"C_GENITOURINARY_PROSTATE\")\n" +
                "                      && !user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"DESCRIBE\"].answers.hasOption(\"CHILD_DIAGNOSED\")\n" +
                "                      && user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"ADVANCED_PROSTATE\"].answers.hasOption(\"YES\")\n" +
                "                      && user.profile.language() == \"en\"\n" +
                "                  )\n" +
                "                )\n" +
                "'\n" +
                "  where expression_text like '%!( (user.profile.language() == \"en\" &&\n" +
                "          (user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].children[\"PRIMARY_CANCER_SELF\"].answers.hasOptionStartsWith(\"C_SARCOMAS_OSTEOSARCOMA\", \"C_BRAIN_\")\n" +
                "            || user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_CHILD\"].children[\"PRIMARY_CANCER_CHILD\"].answers.hasOptionStartsWith(\"C_SARCOMAS_OSTEOSARCOMA\", \"C_BRAIN_\")\n" +
                "            || user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].children[\"PRIMARY_CANCER_SELF\"].answers.hasAnyOption(\"C_SARCOMAS_S_LEIOMYO_LMS_SARCOMA\", \"C_GYNECOLOGIC_UTERINE_LEIOMYOSARCOMA\", \"C_SARCOMA_CUTANEOUS_LEIMYOSARCOMA\")\n" +
                "            || user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_CHILD\"].children[\"PRIMARY_CANCER_CHILD\"].answers.hasAnyOption(\"C_SARCOMAS_S_LEIOMYO_LMS_SARCOMA\", \"C_GYNECOLOGIC_UTERINE_LEIOMYOSARCOMA\", \"C_SARCOMA_CUTANEOUS_LEIMYOSARCOMA\")\n" +
                "          ) &&\n" +
                "          !(\n" +
                "            (user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].numChildAnswers(\"PRIMARY_CANCER_SELF\") > 1\n" +
                "              && user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].children[\"PRIMARY_CANCER_SELF\"].answers.hasOptionStartsWith( \"C_GASTRO_ESOPHAGEAL_CANCER\", \"C_GASTRO_GASTRIC_STOMACH_CANCER\", \"C_GENITOURINARY_PROSTATE\", \"C_BREAST_\")\n" +
                "            )\n" +
                "            ||\n" +
                "            (user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_CHILD\"].numChildAnswers(\"PRIMARY_CANCER_CHILD\") > 1\n" +
                "              && user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_CHILD\"].children[\"PRIMARY_CANCER_CHILD\"].answers.hasOptionStartsWith( \"C_GASTRO_ESOPHAGEAL_CANCER\", \"C_GASTRO_GASTRIC_STOMACH_CANCER\", \"C_GENITOURINARY_PROSTATE\", \"C_BREAST_\")\n" +
                "            )\n" +
                "          )\n" +
                "        )\n" +
                "     || ( user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].numChildAnswers(\"PRIMARY_CANCER_SELF\") == 1\n" +
                "            && user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].children[\"PRIMARY_CANCER_SELF\"]\n" +
                "            .answers.hasOptionStartsWith( \"C_GASTRO_ESOPHAGEAL_CANCER\", \"C_GASTRO_GASTRIC_STOMACH_CANCER\")\n" +
                "            && !user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"DESCRIBE\"].answers.hasOption(\"CHILD_DIAGNOSED\")\n" +
                "            && user.profile.language() == \"en\"\n" +
                "         )\n" +
                "     || ( user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].numChildAnswers(\"PRIMARY_CANCER_SELF\") == 1\n" +
                "            && user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].children[\"PRIMARY_CANCER_SELF\"].answers.hasOptionStartsWith(\"C_BREAST_\")\n" +
                "            && !user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"DESCRIBE\"].answers.hasOption(\"CHILD_DIAGNOSED\")\n" +
                "            && user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"ADVANCED_BREAST\"].answers.hasOption(\"YES\")\n" +
                "        )\n" +
                "     || ( user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].numChildAnswers(\"PRIMARY_CANCER_SELF\") == 1\n" +
                "            && user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].children[\"PRIMARY_CANCER_SELF\"].answers.hasOptionStartsWith(\"C_GENITOURINARY_PROSTATE\")\n" +
                "            && !user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"DESCRIBE\"].answers.hasOption(\"CHILD_DIAGNOSED\")\n" +
                "            && user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"ADVANCED_PROSTATE\"].answers.hasOption(\"YES\")\n" +
                "            && user.profile.language() == \"en\"\n" +
                "        )\n" +
                "      )%'")
        int updatePancanOsteoBlockPex2();


        @SqlUpdate("      update expression\n" +
                "      set expression_text = '(user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"DESCRIBE\"].answers.hasOption(\"DIAGNOSED\"))\n" +
                "    &&\n" +
                "     !( (user.profile.language() == \"en\" &&\n" +
                "                    (user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].children[\"PRIMARY_CANCER_SELF\"].answers.hasOptionStartsWith(\"C_BRAIN_\")\n" +
                "                      || user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_CHILD\"].children[\"PRIMARY_CANCER_CHILD\"].answers.hasOptionStartsWith(\"C_BRAIN_\")\n" +
                "            || user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].children[\"PRIMARY_CANCER_SELF\"].answers.hasAnyOption(\"C_SARCOMAS_S_LEIOMYO_LMS_SARCOMA\", \"C_GYNECOLOGIC_UTERINE_LEIOMYOSARCOMA\", \"C_SARCOMA_CUTANEOUS_LEIMYOSARCOMA\")\n" +
                "            || user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_CHILD\"].children[\"PRIMARY_CANCER_CHILD\"].answers.hasAnyOption(\"C_SARCOMAS_S_LEIOMYO_LMS_SARCOMA\", \"C_GYNECOLOGIC_UTERINE_LEIOMYOSARCOMA\", \"C_SARCOMA_CUTANEOUS_LEIMYOSARCOMA\")\n" +
                "          ) &&\n" +
                "          !(\n" +
                "            (user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].numChildAnswers(\"PRIMARY_CANCER_SELF\") > 1\n" +
                "              && user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].children[\"PRIMARY_CANCER_SELF\"].answers.hasOptionStartsWith( \"C_GASTRO_ESOPHAGEAL_CANCER\", \"C_GASTRO_GASTRIC_STOMACH_CANCER\", \"C_GENITOURINARY_PROSTATE\", \"C_BREAST_\")\n" +
                "            )\n" +
                "            ||\n" +
                "            (user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_CHILD\"].numChildAnswers(\"PRIMARY_CANCER_CHILD\") > 1\n" +
                "              && user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_CHILD\"].children[\"PRIMARY_CANCER_CHILD\"].answers.hasOptionStartsWith( \"C_GASTRO_ESOPHAGEAL_CANCER\", \"C_GASTRO_GASTRIC_STOMACH_CANCER\", \"C_GENITOURINARY_PROSTATE\", \"C_BREAST_\")\n" +
                "            )\n" +
                "          )\n" +
                "        )\n" +
                "     || ( user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].numChildAnswers(\"PRIMARY_CANCER_SELF\") == 1\n" +
                "            && user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].children[\"PRIMARY_CANCER_SELF\"]\n" +
                "            .answers.hasOptionStartsWith( \"C_GASTRO_ESOPHAGEAL_CANCER\", \"C_GASTRO_GASTRIC_STOMACH_CANCER\")\n" +
                "            && !user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"DESCRIBE\"].answers.hasOption(\"CHILD_DIAGNOSED\")\n" +
                "            && user.profile.language() == \"en\"\n" +
                "         )\n" +
                "     || ( user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].numChildAnswers(\"PRIMARY_CANCER_SELF\") == 1\n" +
                "            && user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].children[\"PRIMARY_CANCER_SELF\"].answers.hasOptionStartsWith(\"C_BREAST_\")\n" +
                "            && !user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"DESCRIBE\"].answers.hasOption(\"CHILD_DIAGNOSED\")\n" +
                "            && user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"ADVANCED_BREAST\"].answers.hasOption(\"YES\")\n" +
                "        )\n" +
                "               || (user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].children[\"PRIMARY_CANCER_SELF\"].answers.hasOptionStartsWith(\"C_SARCOMAS_OSTEOSARCOMA\")\n" +
                "                                     || user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_CHILD\"].children[\"PRIMARY_CANCER_CHILD\"].answers.hasOptionStartsWith(\"C_SARCOMAS_OSTEOSARCOMA\")\n" +
                "                  )\n" +
                "     || ( user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].numChildAnswers(\"PRIMARY_CANCER_SELF\") == 1\n" +
                "            && user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].children[\"PRIMARY_CANCER_SELF\"].answers.hasOptionStartsWith(\"C_GENITOURINARY_PROSTATE\")\n" +
                "            && !user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"DESCRIBE\"].answers.hasOption(\"CHILD_DIAGNOSED\")\n" +
                "            && user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"ADVANCED_PROSTATE\"].answers.hasOption(\"YES\")\n" +
                "            && user.profile.language() == \"en\"\n" +
                "        )\n" +
                "      )'\n" +
                "      where expression_text like '%(user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"DESCRIBE\"].answers.hasOption(\"DIAGNOSED\"))\n" +
                "    &&\n" +
                "     !( (user.profile.language() == \"en\" &&\n" +
                "          (user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].children[\"PRIMARY_CANCER_SELF\"].answers.hasOptionStartsWith(\"C_SARCOMAS_OSTEOSARCOMA\", \"C_BRAIN_\")\n" +
                "            || user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_CHILD\"].children[\"PRIMARY_CANCER_CHILD\"].answers.hasOptionStartsWith(\"C_SARCOMAS_OSTEOSARCOMA\", \"C_BRAIN_\")\n" +
                "            || user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].children[\"PRIMARY_CANCER_SELF\"].answers.hasAnyOption(\"C_SARCOMAS_S_LEIOMYO_LMS_SARCOMA\", \"C_GYNECOLOGIC_UTERINE_LEIOMYOSARCOMA\", \"C_SARCOMA_CUTANEOUS_LEIMYOSARCOMA\")\n" +
                "            || user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_CHILD\"].children[\"PRIMARY_CANCER_CHILD\"].answers.hasAnyOption(\"C_SARCOMAS_S_LEIOMYO_LMS_SARCOMA\", \"C_GYNECOLOGIC_UTERINE_LEIOMYOSARCOMA\", \"C_SARCOMA_CUTANEOUS_LEIMYOSARCOMA\")\n" +
                "          ) &&\n" +
                "          !(\n" +
                "            (user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].numChildAnswers(\"PRIMARY_CANCER_SELF\") > 1\n" +
                "              && user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].children[\"PRIMARY_CANCER_SELF\"].answers.hasOptionStartsWith( \"C_GASTRO_ESOPHAGEAL_CANCER\", \"C_GASTRO_GASTRIC_STOMACH_CANCER\", \"C_GENITOURINARY_PROSTATE\", \"C_BREAST_\")\n" +
                "            )\n" +
                "            ||\n" +
                "            (user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_CHILD\"].numChildAnswers(\"PRIMARY_CANCER_CHILD\") > 1\n" +
                "              && user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_CHILD\"].children[\"PRIMARY_CANCER_CHILD\"].answers.hasOptionStartsWith( \"C_GASTRO_ESOPHAGEAL_CANCER\", \"C_GASTRO_GASTRIC_STOMACH_CANCER\", \"C_GENITOURINARY_PROSTATE\", \"C_BREAST_\")\n" +
                "            )\n" +
                "          )\n" +
                "        )\n" +
                "     || ( user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].numChildAnswers(\"PRIMARY_CANCER_SELF\") == 1\n" +
                "            && user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].children[\"PRIMARY_CANCER_SELF\"]\n" +
                "            .answers.hasOptionStartsWith( \"C_GASTRO_ESOPHAGEAL_CANCER\", \"C_GASTRO_GASTRIC_STOMACH_CANCER\")\n" +
                "            && !user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"DESCRIBE\"].answers.hasOption(\"CHILD_DIAGNOSED\")\n" +
                "            && user.profile.language() == \"en\"\n" +
                "         )\n" +
                "     || ( user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].numChildAnswers(\"PRIMARY_CANCER_SELF\") == 1\n" +
                "            && user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].children[\"PRIMARY_CANCER_SELF\"].answers.hasOptionStartsWith(\"C_BREAST_\")\n" +
                "            && !user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"DESCRIBE\"].answers.hasOption(\"CHILD_DIAGNOSED\")\n" +
                "            && user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"ADVANCED_BREAST\"].answers.hasOption(\"YES\")\n" +
                "        )\n" +
                "     || ( user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].numChildAnswers(\"PRIMARY_CANCER_SELF\") == 1\n" +
                "            && user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"PRIMARY_CANCER_LIST_SELF\"].children[\"PRIMARY_CANCER_SELF\"].answers.hasOptionStartsWith(\"C_GENITOURINARY_PROSTATE\")\n" +
                "            && !user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"DESCRIBE\"].answers.hasOption(\"CHILD_DIAGNOSED\")\n" +
                "            && user.studies[\"cmi-pancan\"].forms[\"PREQUAL\"].questions[\"ADVANCED_PROSTATE\"].answers.hasOption(\"YES\")\n" +
                "            && user.profile.language() == \"en\"\n" +
                "        )\n" +
                "      )%'")
        int updatePancanOsteoBlockPex1();

        @SqlUpdate("update expression\n" +
                "      set expression_text = '!(user.profile.language() == \"en\" &&\n" +
                "          ((user.studies[\"cmi-pancan\"].forms[\"ADD_CHILD\"].questions[\"PRIMARY_CANCER_LIST_ADD_CHILD\"].children[\"PRIMARY_CANCER_ADD_CHILD\"].answers.hasOptionStartsWith(\"C_BRAIN_\")\n" +
                "            || user.studies[\"cmi-pancan\"].forms[\"ADD_CHILD\"].questions[\"PRIMARY_CANCER_LIST_ADD_CHILD\"].children[\"PRIMARY_CANCER_ADD_CHILD\"].answers.hasAnyOption(\"C_SARCOMAS_S_LEIOMYO_LMS_SARCOMA\", \"C_GYNECOLOGIC_UTERINE_LEIOMYOSARCOMA\", \"C_SARCOMA_CUTANEOUS_LEIMYOSARCOMA\")\n" +
                "          ) &&\n" +
                "          !(\n" +
                "            (user.studies[\"cmi-pancan\"].forms[\"ADD_CHILD\"].questions[\"PRIMARY_CANCER_LIST_ADD_CHILD\"].numChildAnswers(\"PRIMARY_CANCER_ADD_CHILD\") > 1\n" +
                "              && user.studies[\"cmi-pancan\"].forms[\"ADD_CHILD\"].questions[\"PRIMARY_CANCER_LIST_ADD_CHILD\"].children[\"PRIMARY_CANCER_ADD_CHILD\"].answers.hasOptionStartsWith( \"C_GASTRO_ESOPHAGEAL_CANCER\", \"C_GASTRO_GASTRIC_STOMACH_CANCER\", \"C_GENITOURINARY_PROSTATE\", \"C_BREAST_\")\n" +
                "            )\n" +
                "          ))\n" +
                "          || (user.studies[\"cmi-pancan\"].forms[\"ADD_CHILD\"].questions[\"PRIMARY_CANCER_LIST_ADD_CHILD\"].children[\"PRIMARY_CANCER_ADD_CHILD\"].answers.hasOptionStartsWith(\"C_SARCOMAS_OSTEOSARCOMA\"))\n" +
                "        )\n" +
                "'\n" +
                "      where expression_text like '%!(user.profile.language() == \"en\" &&\n" +
                "          (user.studies[\"cmi-pancan\"].forms[\"ADD_CHILD\"].questions[\"PRIMARY_CANCER_LIST_ADD_CHILD\"].children[\"PRIMARY_CANCER_ADD_CHILD\"].answers.hasOptionStartsWith(\"C_SARCOMAS_OSTEOSARCOMA\", \"C_BRAIN_\")\n" +
                "            || user.studies[\"cmi-pancan\"].forms[\"ADD_CHILD\"].questions[\"PRIMARY_CANCER_LIST_ADD_CHILD\"].children[\"PRIMARY_CANCER_ADD_CHILD\"].answers.hasAnyOption(\"C_SARCOMAS_S_LEIOMYO_LMS_SARCOMA\", \"C_GYNECOLOGIC_UTERINE_LEIOMYOSARCOMA\", \"C_SARCOMA_CUTANEOUS_LEIMYOSARCOMA\")\n" +
                "          ) &&\n" +
                "          !(\n" +
                "            (user.studies[\"cmi-pancan\"].forms[\"ADD_CHILD\"].questions[\"PRIMARY_CANCER_LIST_ADD_CHILD\"].numChildAnswers(\"PRIMARY_CANCER_ADD_CHILD\") > 1\n" +
                "              && user.studies[\"cmi-pancan\"].forms[\"ADD_CHILD\"].questions[\"PRIMARY_CANCER_LIST_ADD_CHILD\"].children[\"PRIMARY_CANCER_ADD_CHILD\"].answers.hasOptionStartsWith( \"C_GASTRO_ESOPHAGEAL_CANCER\", \"C_GASTRO_GASTRIC_STOMACH_CANCER\", \"C_GENITOURINARY_PROSTATE\", \"C_BREAST_\")\n" +
                "            )\n" +
                "          )\n" +
                "        )%'")
        int updatePancanOsteoBlockPex3();

    }

}
