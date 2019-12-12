package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.ValidationDao;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.validation.AgeRangeRuleDef;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrainAgeTooYoung implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(BrainAgeTooYoung.class);
    private static final String DATA_FILE = "patches/age-too-young-validation.conf";

    private Config dataCfg;
    private String studyGuid;
    private Gson gson;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

        studyGuid = dataCfg.getString("studyGuid");
        if (!studyGuid.equals(studyCfg.getString("study.guid"))) {
            throw new DDPException("This task is only for the " + studyGuid + " study!");
        }

        gson = GsonUtil.standardGson();
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);

        addAgeRangeValidation(handle, studyDto);
        updateConsentReminderEmailEvents(handle, studyDto);
    }

    private void addAgeRangeValidation(Handle handle, StudyDto studyDto) {
        String consentDOBStableId = dataCfg.getString("dobQuestionStableId");

        QuestionDto questionDto = handle.attach(JdbiQuestion.class)
                .getLatestQuestionDtoByQuestionStableIdAndUmbrellaStudyId(consentDOBStableId, studyDto.getId())
                .orElseThrow(() -> new DDPException("Could not find question " + consentDOBStableId));

        AgeRangeRuleDef rule = gson.fromJson(ConfigUtil.toJson(dataCfg.getConfig("ageRangeRule")), AgeRangeRuleDef.class);
        handle.attach(ValidationDao.class).insert(questionDto.getId(), rule, questionDto.getRevisionId());
        LOG.info("Inserted age-range validation rule with id={} questionStableId={}", rule.getRuleId(), questionDto.getStableId());
    }

    private void updateConsentReminderEmailEvents(Handle handle, StudyDto studyDto) {
        String consentActivityCode = dataCfg.getString("consentActivityCode");
        String cancelExprText = dataCfg.getString("reminderEmailCancelExpr");
        int expectedNumEvents = dataCfg.getInt("expectedReminderEmailEvents");

        SqlHelper helper = handle.attach(SqlHelper.class);
        List<Long> exprIds = helper.findConsentReminderEmailCancelExpressionIds(studyDto.getId(), consentActivityCode);
        if (exprIds.size() != expectedNumEvents) {
            throw new DDPException(String.format("Expected to find %d expressions for consent reminder email events but got %d",
                    expectedNumEvents, exprIds.size()));
        }

        helper.bulkUpdateExpressionText(cancelExprText, exprIds);
    }

    private interface SqlHelper extends SqlObject {

        @SqlQuery("select cancel_expression_id from event_configuration as e"
                + "  join activity_status_trigger as t on t.activity_status_trigger_id = e.event_trigger_id"
                + "  join study_activity as act on act.study_activity_id = t.study_activity_id"
                + "  join activity_instance_status_type as ty on ty.activity_instance_status_type_id = t.activity_instance_status_type_id"
                + " where e.umbrella_study_id = :studyId"
                + "   and act.study_activity_code = :activityCode"
                + "   and ty.activity_instance_status_type_code = 'CREATED'"
                + "   and e.cancel_expression_id is not null")
        List<Long> findConsentReminderEmailCancelExpressionIds(@Bind("studyId") long studyId,
                                                               @Bind("activityCode") String consentActivityCode);

        @SqlUpdate("update expression set expression_text = :text where expression_id in (<ids>)")
        int _updateExpressionText(@Bind("text") String text,
                                  @BindList(value = "ids", onEmpty = BindList.EmptyHandling.NULL) List<Long> ids);

        default void bulkUpdateExpressionText(String newText, List<Long> expressionIds) {
            int numUpdated = _updateExpressionText(newText, expressionIds);
            if (numUpdated != expressionIds.size()) {
                throw new DDPException(String.format("Expected to update %d expressions but did %d", expressionIds.size(), numUpdated));
            }
        }
    }
}
