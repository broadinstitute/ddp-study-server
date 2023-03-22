package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class OsteoPediatricGermlineFix implements CustomTask {
    private static final String OSTEO_STUDY_GUID = "CMI-OSTEO";
    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;

    private String newExpr = " user.studies[\"CMI-OSTEO\"].forms[\"GERMLINE_CONSENT_ADDENDUM_PEDIATRIC\"].questions[\"ADDENDUM_CONSENT_BOOL_PEDIATRIC\"].answers.hasTrue()\n" +
            "                              && (( (operator.studies[\"CMI-OSTEO\"].forms[\"PREQUAL\"].hasInstance() && operator.studies[\"CMI-OSTEO\"].forms[\"PREQUAL\"].questions[\"CHILD_CURRENT_AGE\"].answers.value() > 7)\n" +
            "                                  || (user.studies[\"CMI-OSTEO\"].forms[\"PREQUAL\"].hasInstance() && user.studies[\"CMI-OSTEO\"].forms[\"PREQUAL\"].questions[\"CHILD_CURRENT_AGE\"].answers.value() > 7)\n" +
            "                                  )) ";


    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!(studyCfg.getString("study.guid").equals(OSTEO_STUDY_GUID))) {
            throw new DDPException("This task is only for Osteo! study-guid in config: "
                    + studyCfg.getString("study.guid"));
        }
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        log.info("TASK:: OsteoPediatricGermlineFix  ");
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class)
                .findByStudyGuid(studyCfg.getString("study.guid"));
        long activityId = handle.attach(JdbiActivity.class).findIdByStudyIdAndCode(studyDto.getId(), "GERMLINE_CONSENT_ADDENDUM_PEDIATRIC").get();

        List<Long> exprIds = new ArrayList<>();
        exprIds.add(getExpressionIdByActivityContentBlock(handle, activityId));
        exprIds.add(getExpressionIdByQuestionStableCode(handle, activityId, "ADDENDUM_CONSENT_PATIENT_SINGATURE_PEDIATRIC", newExpr));
        exprIds.add(getExpressionIdByQuestionStableCode(handle, activityId, "ADDENDUM_CONSENT_PATIENT_DOB_PEDIATRIC", newExpr));
        SqlHelper helper = handle.attach(OsteoPediatricGermlineFix.SqlHelper.class);
        helper.updateExpressionText(newExpr, exprIds);
        log.info("updated expression Ids: {}", exprIds);
    }

    private long getExpressionIdByQuestionStableCode(Handle handle, long activityId, String questionStableCode, String expr) {
        SqlHelper helper = handle.attach(OsteoPediatricGermlineFix.SqlHelper.class);
        Long exprId = helper.findBlockExpressionIdsByQuestionStableCode(activityId, questionStableCode);
        return exprId;
    }

    private long getExpressionIdByActivityContentBlock(Handle handle, long activityId) {
        SqlHelper helper = handle.attach(OsteoPediatricGermlineFix.SqlHelper.class);
        Long exprId = helper.findBlockExpressionIdByActivityContentAndExpr(activityId);
        return exprId;
    }

    private interface SqlHelper extends SqlObject {
        @SqlUpdate("update expression set expression_text = :text where expression_id in (<ids>)")
        int updateExpressionText(@Bind("text") String text,
                                 @BindList(value = "ids", onEmpty = BindList.EmptyHandling.THROW) List<Long> ids);

        @SqlQuery("select e.expression_id from block__question as bt "
                + "left join block__expression be on be.block_id = bt.block_id "
                + "join expression e on e.expression_id = be.expression_id "
                + "join question q on q.question_id = bt.question_id "
                + "join question_stable_code qsc on qsc.question_stable_code_id = q.question_stable_code_id "
                + "  where qsc.stable_id = :stableId and "
                + "  bt.block_id in "
                + "  (select fsb.block_id "
                + "  from form_activity__form_section as fafs "
                + "  join form_section__block as fsb on fsb.form_section_id = fafs.form_section_id "
                + "  where fafs.form_activity_id = :activityId)")
        Long findBlockExpressionIdsByQuestionStableCode(@Bind("activityId") long activityId,
                                                              @Bind("stableId") String stableId);


        @SqlQuery("select e.expression_id, e.* from block_content as bt \n" +
                "  left join block__expression be on be.block_id = bt.block_id \n" +
                "  join expression e on e.expression_id = be.expression_id \n" +
                "  where e.expression_text like '%operator.studies[\"CMI-OSTEO\"].forms[\"PREQUAL\"].questions[\"CHILD_CURRENT_AGE\"].answers.value() > 7%'\n" +
                "  and bt.block_id in \n" +
                "  (select fsb.block_id \n" +
                "  from form_activity__form_section as fafs \n" +
                "  join form_section__block as fsb on fsb.form_section_id = fafs.form_section_id \n" +
                "  where fafs.form_activity_id = :activityId)")
        Long findBlockExpressionIdByActivityContentAndExpr(@Bind("activityId") long activityId);

    }

}
