package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.ActivityInstanceStatusDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.ActivityInstanceStatusDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class PancanMRDeleteIncomplete implements CustomTask {
    private static final List<String> ACTIVITY_CODES = Collections.singletonList("RELEASE_MINOR");

    private Config studyCfg;
    private StudyDto studyDto;
    private SqlHelper helper;
    private ActivityInstanceStatusDao statusDao;
    private AnswerDao answerDao;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.studyCfg = studyCfg;
    }

    @Override
    public void run(Handle handle) {
        this.studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        this.helper = handle.attach(SqlHelper.class);
        this.statusDao = handle.attach(ActivityInstanceStatusDao.class);
        this.answerDao = handle.attach(AnswerDao.class);
        deleteIncompleteRecords();
    }

    private void deleteIncompleteRecords() {
        List<Long> activityInstances = helper.findMedicalRecordActivityInstances(studyDto.getGuid(), ACTIVITY_CODES);
        Set<Long> toHide = activityInstances.stream().filter(this::isIncompleteActivity)
                .collect(Collectors.toSet());
        if (!toHide.isEmpty()) {
            statusDao.deleteAllByInstanceIds(toHide);
            answerDao.deleteAllByInstanceIds(toHide);
            int numHidden = helper.deleteInstances(toHide);
            log.info("{} incomplete '{}' instances deleted", numHidden, ACTIVITY_CODES);
        }
    }

    private boolean isIncompleteActivity(Long id) {
        Optional<ActivityInstanceStatusDto> currentStatus = statusDao.getCurrentStatus(id);
        return currentStatus.isPresent() && currentStatus.get().getType() != InstanceStatusType.COMPLETE;
    }

    private interface SqlHelper extends SqlObject {
        @SqlQuery("select ai.activity_instance_id from activity_instance ai"
                + "                    join study_activity su on ai.study_activity_id = su.study_activity_id"
                + "                    join umbrella_study us on su.study_id = us.umbrella_study_id"
                + "                    where us.guid = :studyGuid and"
                + "                          su.study_activity_code in (<activityCode>)")
        List<Long> findMedicalRecordActivityInstances(@Bind("studyGuid")String studyGuid,
                                                      @BindList("activityCode") List<String> activityCode);

        @SqlUpdate("delete from activity_instance where activity_instance_id in (<instanceIds>) ")
        int deleteInstances(@BindList("instanceIds") Set<Long> toHide);
    }
}
