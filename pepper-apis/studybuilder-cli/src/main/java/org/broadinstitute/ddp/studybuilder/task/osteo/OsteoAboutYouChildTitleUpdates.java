package org.broadinstitute.ddp.studybuilder.task.osteo;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.BlockContentDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public class OsteoAboutYouChildTitleUpdates implements CustomTask {
    private static final String STUDY_GUID = "CMI-OSTEO";
    private static final String DATA_FILE = "patches/osteo-content-title-updates.conf";
    private Config cfg;
    private Config dataCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
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
        SqlHelper helper = handle.attach(SqlHelper.class);
        JdbiUmbrellaStudy jdbiUmbrellaStudy = handle.attach(JdbiUmbrellaStudy.class);
        var studyDto = jdbiUmbrellaStudy.findByStudyGuid(cfg.getString("study.guid"));
        long studyId = studyDto.getId();
        List<? extends Config> activities = dataCfg.getConfigList("activities");
        for (Config activity : activities) {
            String activityCode = activity.getString("activityCode");
            String bodyText = activity.getString("bodyText");
            long activityId = ActivityBuilder.findActivityId(handle, studyId, activityCode);
            BlockContentDto contentBlock = helper.findContentBlockByBodyText(activityId, bodyText);
            helper.deleteContentBlock(contentBlock.getBlockId());
            log.info("Deleted content block: {} in activity: {}", contentBlock.getId(), activityCode);
        }
    }

    private interface SqlHelper extends SqlObject {
        @SqlQuery("select bt.* from block_content as bt"
                + "  join template as tmpl on tmpl.template_id = bt.body_template_id"
                + " where tmpl.template_text = :text"
                + "   and bt.block_id in (select fsb.block_id"
                + "                         from form_activity__form_section as fafs"
                + "                         join form_section__block as fsb on fsb.form_section_id = fafs.form_section_id"
                + "                        where fafs.form_activity_id = :activityId"
                + "                        union"
                + "                       select bn.nested_block_id"
                + "                         from form_activity__form_section as fafs"
                + "                         join form_section__block as fsb on fsb.form_section_id = fafs.form_section_id"
                + "                         join block_nesting as bn on bn.parent_block_id = fsb.block_id"
                + "                        where fafs.form_activity_id = :activityId)")
        @RegisterConstructorMapper(BlockContentDto.class)
        BlockContentDto findContentBlockByBodyText(@Bind("activityId") long activityId, @Bind("text") String bodyTemplateText);

        @SqlUpdate("delete from form_section__block where block_id = :blockId")
        int deleteSectionBlockMembershipByBlockId(@Bind("blockId") long blockId);

        @SqlUpdate("delete from block_content where block_id = :blockId")
        int deleteBlockContentByBlockId(@Bind("blockId") long blockContentId);

        @SqlUpdate("delete from block where block_id = :id")
        int deleteBlockById(@Bind("id") long id);

        default void deleteContentBlock(long blockId) {
            int numDeleted = deleteSectionBlockMembershipByBlockId(blockId);
            if (numDeleted != 1) {
                throw new DDPException("Could not remove content block with blockId=" + blockId + " from section");
            }
            numDeleted = deleteBlockContentByBlockId(blockId);
            if (numDeleted != 1) {
                throw new DDPException("Could not delete content block with blockId=" + blockId);
            }
            numDeleted = deleteBlockById(blockId);
            if (numDeleted != 1) {
                throw new DDPException("Could not delete block with blockId=" + blockId);
            }
        }
    }
}
