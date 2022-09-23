package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.jdbi.v3.core.Handle;

import java.nio.file.Path;
import java.util.List;

@Slf4j
public abstract class PrequalEmptyBlock implements CustomTask {
    private final String studyGuid;
    private final String activityCode;
    private final String precondition;

    public PrequalEmptyBlock(String activityCode, String studyGuid, String precondition) {
        this.activityCode = activityCode;
        this.studyGuid = studyGuid;
        this.precondition = precondition;
    }

    public PrequalEmptyBlock(String activityCode, String studyGuid) {
        this(activityCode, studyGuid, "true");
    }

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(studyGuid)) {
            throw new DDPException("This task is only for the " + studyGuid + " study!");
        }
    }

    @Override
    public void run(Handle handle) {
        addBlockToActivity(handle);
    }

    private void addBlockToActivity(Handle handle) {
        ContentBlockDef contentBlockDef = new ContentBlockDef(Template.html(""));
        contentBlockDef.setShownExpr(precondition);
        ActivityDto activityDto = handle.attach(JdbiActivity.class)
                .findActivityByStudyGuidAndCode(studyGuid, activityCode).orElseThrow();
        ActivityVersionDto ver = handle.attach(JdbiActivityVersion.class).getActiveVersion(activityDto.getActivityId()).orElseThrow();
        FormActivityDef currentDef = (FormActivityDef) handle.attach(ActivityDao.class).findDefByDtoAndVersion(activityDto, ver);
        List<FormSectionDef> sections = currentDef.getSections();
        FormSectionDef currentSectionDef = sections.get(sections.size() - 1);
        SectionBlockDao sectionBlockDao = handle.attach(SectionBlockDao.class);
        int displayOrder = currentSectionDef.getBlocks().size() * 10 + 10;
        sectionBlockDao.insertBlockForSection(activityDto.getActivityId(), currentSectionDef.getSectionId(),
                displayOrder, contentBlockDef, ver.getRevId());
        log.info("New empty block was added to activity {} into section #{} with display order {}",
                "PREQUAL", 1, displayOrder);
    }

}
