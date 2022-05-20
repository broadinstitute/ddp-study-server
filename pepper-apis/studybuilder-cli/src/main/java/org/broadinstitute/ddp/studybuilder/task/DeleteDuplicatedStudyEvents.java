package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.BlockContentDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.EventBuilder;
import org.broadinstitute.ddp.util.PdfBootstrapperCli;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
abstract class DeleteDuplicatedStudyEvents implements CustomTask {

    protected String studyGuid;
    protected String dataFile;
    protected Config dataCfg;
    protected Path cfgPath;
    protected Config cfg;
    protected Config varsCfg;

    DeleteDuplicatedStudyEvents(String studyGuid, String dataFile) {
        this.studyGuid = studyGuid;
        this.dataFile = dataFile;
    }

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(studyGuid)) {
            throw new DDPException("This task is only for the " + studyGuid + " study!");
        }
        File file = cfgPath.getParent().resolve(dataFile).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
        this.cfgPath = cfgPath;
        this.cfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));
        UserDto user = handle.attach(JdbiUser.class).findByUserGuid(cfg.getString("adminUser.guid"));
        deleteEvents(handle, studyDto, user.getUserId());
    }

    private void deleteEvents(Handle handle, StudyDto studyDto, long adminUserId) {
        if (!dataCfg.hasPath("events")) {
            throw new DDPException("There is no 'events' configuration.");
        }
        EventBuilder eventBuilder = new EventBuilder(cfg, studyDto, adminUserId);
        log.info("Deleting events configuration...");
        List<? extends Config> events = dataCfg.getConfigList("events");
        events.stream().skip(1).forEach(eventCfg->{
            String triggerTypeCode=eventCfg.getString("triggerTypeCode");
            String actionTypeCode=eventCfg.getString("actionTypeCode");
            String statusTypeCode=eventCfg.getString("statusTypeCode");
            String activityCode=eventCfg.getString("activityCode");

            SqlHelper sqlHelper=handle.attach(SqlHelper.class);
            List<EventInfo> eventInfos= sqlHelper.findDuplicatedEvents(studyGuid, activityCode, triggerTypeCode, actionTypeCode, statusTypeCode);
            for (EventInfo eventInfo:eventInfos) {
                sqlHelper.deleteEventConfiguration(eventInfo.configurationId);
                sqlHelper.deleteEventExpression(eventInfo.preconditionExprId);
                sqlHelper.deleteEventExpression(eventInfo.cancelExprId);
                sqlHelper.deleteEventAction(eventInfo.actionId);
                sqlHelper.deleteEventTrigger(eventInfo.triggerId);
            }
        });
//        for (Config eventCfg : events) {
////            eventBuilder.insertEvent(handle, eventCfg);
//        }
        log.info("Events configurations were removed from study {}", cfg.getString("study.guid"));
    }

    private interface SqlHelper extends SqlObject {
        /**
         * Find the content block that has the given body template text. Make sure it is from a block that belongs in the expected activity
         * (and thus the expected study). This is done using a `union` subquery to find all the top-level and nested block ids for the
         * activity and using that to match on the content block.
         */

        default List<DeleteDuplicatedStudyEvents.EventInfo> findDuplicatedEvents(
                String studyGuid,
                String activityCode,
                String triggerTypeCode,
                String actionTypeCode,
                String statusTypeCode
        ) {
            return _findDuplicatedEvents(
                    studyGuid,
                    activityCode,
                    triggerTypeCode,
                    actionTypeCode,
                    statusTypeCode
            );
        }

        @SqlUpdate("\tselect \n" +
                "\t\tet.event_trigger_id \t\t\t`trigger_id` ,\n" +
                "\t\tea.event_action_id \t\t\t\t`action_id`,\n" +
                "\t\tec.event_configuration_id \t\t`configuration_id`,\n" +
                "\t\tec.precondition_expression_id \t`precondition_expression_id`,\n" +
                "\t\tec.cancel_expression_id \t\t`cancel_expression_id`\n" +
                "\tfrom event_trigger et \n" +
                "\tinner join event_trigger_type ett \t\t\t\ton ett.event_trigger_type_id =et.event_trigger_type_id \n" +
                "\tINNER join event_configuration ec \t\t\t\ton ec.event_trigger_id =et.event_trigger_id \n" +
                "\tinner join event_action ea \t\t\t\t\t\ton ea.event_action_id =ec.event_action_id \n" +
                "\tinner join event_action_type eat \t\t\t\ton eat.event_action_type_id =ea.event_action_type_id\n" +
                "\tinner join activity_status_trigger ast \t\t\ton ast.activity_status_trigger_id =et.event_trigger_id \n" +
                "\tinner join activity_instance_status_type aist \ton aist.activity_instance_status_type_id =ast.activity_instance_status_type_id \n" +
                "\tinner join study_activity sa \t\t\t\t\ton sa.study_activity_id =ast.study_activity_id\n" +
                "\tinner join umbrella_study us \t\t\t\t\ton us.umbrella_study_id =sa.study_id \n" +
                "\t\n" +
                "\twhere \n" +
                "\tett.event_trigger_type_code=:triggerTypeCode\n" +
                "\tand sa.study_activity_code = :activityCode\n" +
                "\tand aist.activity_instance_status_type_code=:statusTypeCode\n" +
                "\tand eat.event_action_type_code=:actionTypeCode \n" +
                "\tand us.guid =:studyGuid" +
                "")
        @RegisterConstructorMapper(DeleteDuplicatedStudyEvents.EventInfo.class)
        List<DeleteDuplicatedStudyEvents.EventInfo> _findDuplicatedEvents(
                @Bind("studyGuid") String studyGuid,
                @Bind("activityCode") String activityCode,
                @Bind("triggerTypeCode") String triggerTypeCode,
                @Bind("actionTypeCode") String actionTypeCode,
                @Bind("statusTypeCode") String statusTypeCode
        );
/////////////////////
        default void deleteEventConfiguration(Long eventConfigurationId) {
            int numUpdated = _deleteEventConfiguration(eventConfigurationId);
            if (numUpdated != 1) {
                throw new DDPException("Expected to delete 1 row for eventConfigurationId="
                        + eventConfigurationId+ " but deleted " + numUpdated);
            } else {
                log.info(" Done: deleted 1 row");
            }
        }

        @SqlUpdate("delete from event_configuration WHERE event_configuration_id =:eventConfigurationId")
        int _deleteEventConfiguration(@Bind("eventConfigurationId") long eventConfigurationId);
/////////////////////

        default void deleteEventExpression(Long expressionId) {
            int numUpdated = _deleteEventExpression(expressionId);
            if (numUpdated != 1) {
                throw new DDPException("Expected to delete 1 row for expressionId="
                        + expressionId+ " but deleted " + numUpdated);
            } else {
                log.info(" Done: deleted 1 row");
            }
        }

        @SqlUpdate("delete from expression WHERE expression_id =:expressionId")
        int _deleteEventExpression(@Bind("expressionId") long expressionId);
/////////////////////

        default void deleteEventAction(Long actionId) {
            int numUpdated = _deleteEventAction(actionId);
            if (numUpdated != 1) {
                throw new DDPException("Expected to delete 1 row for actionId="
                        + actionId+ " but deleted " + numUpdated);
            } else {
                log.info(" Done: deleted 1 row");
            }
        }

        @SqlUpdate("delete from event_action where event_action_id =:actionId")
        int _deleteEventAction(@Bind("actionId") long actionId);
/////////////////////

        default void deleteEventTrigger(Long triggerId) {
            int numUpdated = _deleteEventTrigger(triggerId);
            if (numUpdated != 1) {
                throw new DDPException("Expected to delete 1 row for triggerId="
                        + triggerId+ " but deleted " + numUpdated);
            } else {
                log.info(" Done: deleted 1 row");
            }
        }

        @SqlUpdate("delete from event_trigger where event_trigger_id =:triggerId")
        int _deleteEventTrigger(@Bind("triggerId") long triggerId);
    }
    public static class EventInfo {

        final long triggerId;
        final long configurationId;
        final long cancelExprId;
        final long preconditionExprId;
        final long actionId;

        @JdbiConstructor
        public EventInfo(@ColumnName("trigger_id") long triggerId,
                           @ColumnName("action_id") long actionId,
                           @ColumnName("configuration_id") long configurationId,
                           @ColumnName("precondition_expression_id") long preconditionExprId,
                           @ColumnName("cancel_expression_id") long cancelExprId
        ) {
            this.triggerId = triggerId;
            this.actionId = actionId;
            this.configurationId = configurationId;
            this.preconditionExprId = preconditionExprId;
            this.cancelExprId = cancelExprId;
        }
    }

}


