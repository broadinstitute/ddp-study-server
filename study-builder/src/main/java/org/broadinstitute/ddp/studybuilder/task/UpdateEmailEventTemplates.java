package org.broadinstitute.ddp.studybuilder.task;

import static org.broadinstitute.ddp.studybuilder.EventBuilder.ACTION_INVITATION_EMAIL;
import static org.broadinstitute.ddp.studybuilder.EventBuilder.ACTION_SENDGRID_EMAIL;
import static org.broadinstitute.ddp.studybuilder.EventBuilder.ACTION_STUDY_EMAIL;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValueFactory;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.EventActionSql;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.broadinstitute.ddp.model.event.NotificationTemplate;
import org.broadinstitute.ddp.studybuilder.EventBuilder;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * General task to update sendgrid templates for email event configurations.
 */
public class UpdateEmailEventTemplates implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(UpdateEmailEventTemplates.class);

    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class)
                .findByStudyGuid(studyCfg.getString("study.guid"));
        LOG.info("Comparing {} email event templates...", studyDto.getGuid());

        Map<String, EventConfiguration> emailEvents =  new HashMap<>();
        handle.attach(EventDao.class)
                .getAllEventConfigurationsByStudyId(studyDto.getId())
                .forEach(event -> {
                    if (event.getEventActionType() == EventActionType.NOTIFICATION) {
                        String eventKey = hashEvent(handle, event);
                        emailEvents.put(eventKey, event);
                    }
                });

        for (var eventCfg : studyCfg.getConfigList("events")) {
            String type = eventCfg.getString("action.type");
            if (ACTION_SENDGRID_EMAIL.equals(type) || ACTION_STUDY_EMAIL.equals(type) || ACTION_INVITATION_EMAIL.equals(type)) {
                String eventKey = hashEvent(eventCfg);
                EventConfiguration currentEvent = emailEvents.get(eventKey);
                if (currentEvent != null) {
                    compareEmailTemplates(handle, eventKey, eventCfg.getConfig("action"), currentEvent);
                } else {
                    throw new DDPException("Could not find email event configuration for: " + eventKey);
                }
            }
        }
    }

    private String hashEvent(Config eventCfg) {
        return String.format("%s-%d",
                EventBuilder.triggerAsStr(eventCfg.getConfig("trigger")),
                eventCfg.getInt("order"));
    }

    private String hashEvent(Handle handle, EventConfiguration eventConfig) {
        return String.format("%s-%d",
                EventBuilder.triggerAsStr(handle, eventConfig.getEventTrigger()),
                eventConfig.getExecutionOrder());
    }

    private void compareEmailTemplates(Handle handle, String eventKey, Config actionCfg, EventConfiguration event) {
        long eventId = event.getEventConfigurationId();
        long actionId = findEventActionId(handle, eventId);

        Map<String, NotificationTemplate> currentTemplates = handle.attach(EventDao.class)
                .getNotificationTemplatesForEvent(eventId)
                .stream()
                .collect(Collectors.toMap(NotificationTemplate::getLanguageCode, Function.identity()));

        Map<String, Config> latestTemplates = new LinkedHashMap<>();
        if (actionCfg.hasPath("emailTemplate")) {
            // old format
            String language = actionCfg.getString("language");
            Map<String, Object> object = new HashMap<>();
            object.put("emailTemplate", actionCfg.getString("emailTemplate"));
            object.put("language", language);
            object.put("isDynamic", false);
            ConfigObject configObject = ConfigValueFactory.fromMap(object);
            latestTemplates.put(language, configObject.toConfig());
        } else {
            // new format
            for (var tmplCfg : actionCfg.getConfigList("templates")) {
                String language = tmplCfg.getString("language");
                latestTemplates.put(language, tmplCfg);
            }
        }

        for (var language : latestTemplates.keySet()) {
            Config tmplCfg = latestTemplates.get(language);
            String latestTemplateKey = tmplCfg.getString("emailTemplate");
            boolean isDynamic = tmplCfg.hasPath("isDynamic") && tmplCfg.getBoolean("isDynamic");
            NotificationTemplate current = currentTemplates.remove(language);

            if (current == null) {
                addEmailTemplate(handle, actionId, language, latestTemplateKey, isDynamic);
                LOG.info("[{}] language {}: added template {}", eventKey, language, latestTemplateKey);
            } else {
                if (!current.getTemplateKey().equals(latestTemplateKey)) {
                    String currentTemplateKey = current.getTemplateKey();
                    updateEmailTemplate(handle, actionId, language, currentTemplateKey, latestTemplateKey, isDynamic);
                    LOG.info("[{}] language {}: un-assigned template {} and added template {}",
                            eventKey, language, currentTemplateKey, latestTemplateKey);
                }
            }
        }

        // Anything remaining should be deleted since they're not in latest configs.
        for (var template : currentTemplates.values()) {
            String language = template.getLanguageCode();
            String currentTemplateKey = template.getTemplateKey();
            long currentTemplateId = handle.attach(EventActionSql.class)
                    .findNotificationTemplate(currentTemplateKey, language)
                    .map(NotificationTemplate::getId)
                    .orElseThrow(() -> new DDPException("Could not find email template with key " + currentTemplateKey));
            unassignTemplateFromEmailAction(handle, actionId, currentTemplateId);
            LOG.info("[{}] language {}: un-assigned template {}", eventKey, language, currentTemplateKey);
        }
    }

    private void addEmailTemplate(Handle handle, long actionId, String language, String templateKey, boolean isDynamic) {
        var eventActionSql = handle.attach(EventActionSql.class);
        long templateId = eventActionSql.findOrInsertNotificationTemplateId(templateKey, language, isDynamic);
        eventActionSql.bulkAddNotificationTemplatesToAction(actionId, Set.of(templateId));
    }

    private void updateEmailTemplate(Handle handle, long actionId, String language,
                                     String currentTemplateKey, String latestTemplateKey, boolean isDynamic) {
        var eventActionSql = handle.attach(EventActionSql.class);

        long currentTemplateId = eventActionSql.findNotificationTemplate(currentTemplateKey, language)
                .map(NotificationTemplate::getId)
                .orElseThrow(() -> new DDPException("Could not find email template with key " + currentTemplateKey));
        unassignTemplateFromEmailAction(handle, actionId, currentTemplateId);

        long latestTemplateId = eventActionSql.findOrInsertNotificationTemplateId(latestTemplateKey, language, isDynamic);
        eventActionSql.bulkAddNotificationTemplatesToAction(actionId, Set.of(latestTemplateId));
    }

    private long findEventActionId(Handle handle, long eventId) {
        String sql = "select event_action_id from event_configuration where event_configuration_id = :id";
        return handle.createQuery(sql).bind("id", eventId).mapTo(Long.class).findOnly();
    }

    private void unassignTemplateFromEmailAction(Handle handle, long actionId, long templateId) {
        String sql = "delete from user_notification_template"
                + " where user_notification_event_action_id = :actionId"
                + " and notification_template_id = :templateId";
        int deleted = handle.createUpdate(sql)
                .bind("actionId", actionId)
                .bind("templateId", templateId)
                .execute();
        DBUtils.checkDelete(1, deleted);
    }
}
