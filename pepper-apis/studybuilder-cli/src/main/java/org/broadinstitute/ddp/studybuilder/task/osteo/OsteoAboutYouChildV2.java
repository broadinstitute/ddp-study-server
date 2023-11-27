package org.broadinstitute.ddp.studybuilder.task.osteo;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

@Slf4j
public class OsteoAboutYouChildV2 implements CustomTask {

    private static final String DATA_FILE = "patches/about-child-v2.conf";
    private static final String STUDY_GUID = "CMI-OSTEO";
    private static final String ACTIVITY_CODE = "ABOUTYOU";

    private Config dataCfg;
    private Gson gson;
    private Config studyCfg;

    private SqlHelper helper;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }

        this.dataCfg = ConfigFactory.parseFile(file);

        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        this.studyCfg = studyCfg;
        this.gson = GsonUtil.standardGson();
    }

    @Override
    public void run(Handle handle) {
        helper = handle.attach(SqlHelper.class);
        ActivityDao activityDao = handle.attach(ActivityDao.class);
        SectionBlockDao sectionBlockDao = handle.attach(SectionBlockDao.class);
        JdbiUmbrellaStudy jdbiUmbrellaStudy = handle.attach(JdbiUmbrellaStudy.class);
        var studyDto = jdbiUmbrellaStudy.findByStudyGuid(studyCfg.getString("study.guid"));
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), ACTIVITY_CODE);
        Optional<ActivityVersionDto> activeVersion = activityDao.getJdbiActivityVersion()
                .getActiveVersion(activityId);
        if (activeVersion.isEmpty()) {
            throw new DDPException("Activity version not found");
        }
        long revisionId = activeVersion.get().getRevId();
        long stableId = helper.getQuestionStableIdByCode(dataCfg.getString("searchSectionByQuestionCode"), studyDto.getId());
        int updated = helper.hideQuestionNumber(stableId);
        if (updated != 1) {
            throw new DDPException("Ambiguous hideNumber update to questions. only 1 question must be updated");
        }
        long renderModeId = helper.getRenderModeId(dataCfg.getString("renderMode"));
        updated = helper.updateRenderModeForQuestion(renderModeId, stableId);
        if (updated != 1) {
            throw new DDPException("Ambiguous renderMode update to questions. only 1 question must be updated");
        }
        long blockId = helper.getBlockIdByStableId(stableId);
        long sectionId = helper.getSectionIdByBlockId(blockId);
        String guid = handle.attach(JdbiExpression.class).generateUniqueGuid();
        long shownExpr = helper.insertExpression(guid, dataCfg.getString("shownExpr"));
        helper.insertBlockExpression(blockId, shownExpr, revisionId);
        FormBlockDef blockDefChild = gson.fromJson(ConfigUtil.toJson(dataCfg.getConfig("who_filling_q_child")), FormBlockDef.class);
        sectionBlockDao.insertBlockForSection(
                activityId, sectionId, 0, blockDefChild, revisionId);
    }

    private interface SqlHelper extends SqlObject {
        @SqlQuery("select question_stable_code_id from question_stable_code qsc "
                + "where stable_id = :stableCode and umbrella_study_id = :studyId")
        int getQuestionStableIdByCode(@Bind("stableCode") String stableCode, @Bind("studyId") long studyId);

        @SqlQuery("SELECT block_id from block__question bq join question q on bq.question_id = q.question_id "
                + "where q.question_stable_code_id = :stableId")
        int getBlockIdByStableId(@Bind("stableId") long stableId);

        @SqlQuery("SELECT form_section_id FROM form_section__block WHERE block_id=:blockId")
        long getSectionIdByBlockId(@Bind("blockId") long blockId);

        @SqlUpdate("insert into expression (expression_guid, expression_text) values (:guid, :text)")
        @GetGeneratedKeys
        long insertExpression(@Bind("guid") String guid, @Bind("text") String shownExpr);

        @SqlUpdate("insert into block__expression (block_id, expression_id, revision_id) VALUES (:blockId, :exprId, :revId)")
        void insertBlockExpression(@Bind("blockId") long blockId, @Bind("exprId") long exprId, @Bind("revId") long revisionId);

        @SqlUpdate("update question set hide_number=true where question_stable_code_id = :stableId")
        int hideQuestionNumber(long stableId);

        @SqlQuery("select picklist_render_mode_id from picklist_render_mode where picklist_render_mode_code=:renderMode")
        long getRenderModeId(@Bind("renderMode") String renderMode);

        @SqlUpdate("update picklist_question pq join question q on pq.question_id = q.question_id "
                + "set picklist_render_mode_id=:renderMode WHERE q.question_stable_code_id=:stableId")
        int updateRenderModeForQuestion(@Bind("renderMode") long renderModeId, @Bind("stableId") long stableId);
    }
}
