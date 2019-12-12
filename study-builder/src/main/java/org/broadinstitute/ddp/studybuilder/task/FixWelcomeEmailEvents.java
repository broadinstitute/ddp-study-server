package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.db.dao.JdbiLanguageCode;
import org.broadinstitute.ddp.db.dao.JdbiNotificationTemplate;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Task to update the "welcome" email events for Angio/Brain to link them to the appropriate activity, so we render
 * the proper URLs in the email to link user back to activity.
 */
public class FixWelcomeEmailEvents implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(FixWelcomeEmailEvents.class);

    private static final String ANGIO_STUDY = "ANGIO";
    private static final String BRAIN_STUDY = "cmi-brain";
    private static final String ACT_ANGIO_ABOUT_YOU = "ANGIOABOUTYOU";
    private static final String ACT_ANGIO_LOVED_ONE = "ANGIOLOVEDONE";
    private static final String ACT_BRAIN_ABOUT_YOU = "ABOUTYOU";
    private static final String EN_LANG_CODE = "en";

    private Config cfg;
    private Config varsCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        String studyGuid = studyCfg.getString("study.guid");
        if (!studyGuid.equals(ANGIO_STUDY) && !studyGuid.equals(BRAIN_STUDY)) {
            throw new DDPException("This task is only for the " + ANGIO_STUDY + " or " + BRAIN_STUDY + " studies!");
        }

        this.cfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));

        if (ANGIO_STUDY.equals(studyDto.getGuid())) {
            updateNotificationAction(handle, studyDto, varsCfg.getString("emails.participantWelcome"), ACT_ANGIO_ABOUT_YOU);
            updateNotificationAction(handle, studyDto, varsCfg.getString("emails.lovedOneWelcome"), ACT_ANGIO_LOVED_ONE);
        } else if (BRAIN_STUDY.equals(studyDto.getGuid())) {
            updateNotificationAction(handle, studyDto, varsCfg.getString("emails.participantWelcome"), ACT_BRAIN_ABOUT_YOU);
        } else {
            throw new DDPException("Unsupported study: " + studyDto.getGuid());
        }
    }

    private void updateNotificationAction(Handle handle, StudyDto studyDto, String templateKey, String activityCode) {
        SqlHelper helper = handle.attach(SqlHelper.class);
        long langId = handle.attach(JdbiLanguageCode.class).getLanguageCodeId(EN_LANG_CODE);

        long notificationTemplateId = handle.attach(JdbiNotificationTemplate.class)
                .findByKeyAndLanguage(templateKey, langId).get();
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        helper.updateLinkedActivityId(studyDto.getId(), notificationTemplateId, activityId);

        LOG.info("Updated notification template with id={} to use linkedActivityId={} ({})",
                notificationTemplateId, activityId, activityCode);
    }

    private interface SqlHelper extends SqlObject {

        @SqlUpdate("UPDATE user_notification_event_action AS act"
                + "   JOIN notification_template AS t ON t.notification_template_id = act.notification_template_id"
                + "   JOIN event_configuration as e on e.event_action_id = act.user_notification_event_action_id"
                + "    SET act.linked_activity_id = :linkedActivityId"
                + "  WHERE t.notification_template_id = :notificationTemplateId"
                + "    AND e.umbrella_study_id = :studyId")
        int _updateLinkedActivityId(@Bind("studyId") long studyId,
                                    @Bind("notificationTemplateId") long id,
                                    @Bind("linkedActivityId") long activityId);

        default void updateLinkedActivityId(long studyId, long notificationTemplateId, long linkedActivityId) {
            int numChanged = _updateLinkedActivityId(studyId, notificationTemplateId, linkedActivityId);
            if (numChanged != 1) {
                throw new DDPException("Expected to update one notification template with id="
                        + notificationTemplateId + " but changed " + numChanged);
            }
        }
    }
}
