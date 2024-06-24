package org.broadinstitute.ddp.studybuilder.task.lms;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.CopyConfigurationDao;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.elastic.ElasticSearchIndexType;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.copy.CopyAnswerLocation;
import org.broadinstitute.ddp.model.copy.CopyConfiguration;
import org.broadinstitute.ddp.model.event.ActivityStatusChangeTrigger;
import org.broadinstitute.ddp.model.event.CopyAnswerEventAction;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.EventBuilder;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.broadinstitute.ddp.util.ElasticsearchServiceUtil;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class LmsProxyFixes implements CustomTask {

    private static final String REQUEST_TYPE = "_doc";
    private static final String STUDY = "cmi-lms";
    private static final String STUDY_GOV_PEX = "(!user.studies[\"cmi-lms\"].forms[\"PREQUAL\"].questions[\"WHO_ENROLLING\"].answers.hasOption(\"DIAGNOSED\") " +
            " && user.studies[\"cmi-lms\"].forms[\"PREQUAL\"].questions[\"WHO_ENROLLING\"].answers.hasOption(\"CHILD_DIAGNOSED\")" +
            " && (user.studies[\"cmi-lms\"].forms[\"PARENTAL_CONSENT\"].hasInstance() || user.studies[\"cmi-lms\"].forms[\"CONSENT_ASSENT\"].hasInstance()))";

    private static String PREQUAL_OPERATOR_COUNTRY_PEX_TXT = "operator.studies[\"cmi-lms\"].forms[\"PREQUAL\"].questions[\"CHILD_COUNTRY\"]";
    private static String PREQUAL_USER_COUNTRY_PEX_TXT = "user.studies[\"cmi-lms\"].forms[\"PREQUAL\"].questions[\"CHILD_COUNTRY\"]";
    private static String PREQUAL_OPERATOR_STATE_PEX_TXT = "operator.studies[\"cmi-lms\"].forms[\"PREQUAL\"].questions[\"CHILD_STATE\"]";
    private static String PREQUAL_USER_STATE_PEX_TXT = "user.studies[\"cmi-lms\"].forms[\"PREQUAL\"].questions[\"CHILD_STATE\"]";
    private static String PREQUAL_OPERATOR_PEX_TXT = "operator.studies[\"cmi-lms\"].forms[\"PREQUAL\"]";
    private static String PREQUAL_USER_PEX_TXT = "user.studies[\"cmi-lms\"].forms[\"PREQUAL\"]";
    private static final String ACTIVITY_DATA_FILE = "patches/lms-copy-events-upd.conf";
    private RestHighLevelClient esClient = null;

    private Config dataCfg;
    private Config varsCfg;
    private Path cfgPath;
    private Config cfg;
    private Config activityDataCfg;
    private SqlHelper sqlHelper;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(ACTIVITY_DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
        this.cfgPath = cfgPath;
        this.varsCfg = varsCfg;

        String studyGuid = studyCfg.getString("study.guid");
        if (!studyGuid.equalsIgnoreCase(STUDY)) {
            throw new DDPException("This task is only for the " + STUDY + " study!");
        }
        this.activityDataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
        this.cfg = studyCfg;
        try {
            esClient = ElasticsearchServiceUtil.getElasticsearchClient(cfg);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run(Handle handle) {
        sqlHelper = handle.attach(SqlHelper.class);
        sqlHelper.updateStudyGovExpr("cmi-lms", STUDY_GOV_PEX);
        updatePrequalProxyPex(handle);
        updateProxyEvents(handle);
        updateValidationPrequalPex(handle, PREQUAL_OPERATOR_PEX_TXT, PREQUAL_USER_PEX_TXT);
        updateExistingPrequals(handle);
    }

    private void updatePrequalProxyPex(Handle handle) {
        Map<String, String> pexMap = new HashMap<>();
        pexMap.put(PREQUAL_OPERATOR_COUNTRY_PEX_TXT, PREQUAL_USER_COUNTRY_PEX_TXT);
        pexMap.put(PREQUAL_OPERATOR_STATE_PEX_TXT, PREQUAL_USER_STATE_PEX_TXT);
        pexMap.put(PREQUAL_OPERATOR_PEX_TXT, PREQUAL_USER_PEX_TXT);
        pexMap.entrySet().forEach(entry -> updatePrequalProxyPex(handle, entry.getKey(), entry.getValue()));
    }


    private void updatePrequalProxyPex(Handle handle, String currentTxt, String newTxt) {

        String searchTxt = String.format("%s%s%s", "%", currentTxt, "%");
        List<Long> matchedExprIds = handle.attach(LmsProxyFixes.SqlHelper.class).lmsPrequalProxyPex(searchTxt);
        log.info("Matched {} pex expressions for \n searchTxt: {} \n newTxt: {} ", matchedExprIds.size(), searchTxt, newTxt);
        JdbiExpression jdbiExpression = handle.attach(JdbiExpression.class);
        int updatedPexCount = 0;
        for (Long expressionId : matchedExprIds) {
            String currentExpr = jdbiExpression.getExpressionById(expressionId);
            String updatedExpr = currentExpr.replace(currentTxt, newTxt);
            int udpCount = jdbiExpression.updateById(expressionId, updatedExpr);
            DBUtils.checkUpdate(1, udpCount);
            updatedPexCount++;
            log.info("Updated expressionId  {} with expr text {}. \nOld expr: {} ", expressionId, updatedExpr, currentExpr);
        }
        log.info("Updated {} pex expressions", updatedPexCount);
    }

    private void updateValidationPrequalPex(Handle handle, String currentTxt, String newTxt) {
        String searchTxt = String.format("%s%s%s", "%", currentTxt, "%");
        List<Long> matchedExprIds = sqlHelper.lmsValidationProxyPex(searchTxt);
        log.info("Matched validation {} pex expressions for \n searchTxt: {} \n newTxt: {} ", matchedExprIds.size(), searchTxt, newTxt);
        for (Long activityValidationId : matchedExprIds) {
            String currentExpr = sqlHelper.getValidationPexById(activityValidationId);
            String updatedExpr = currentExpr.replace(currentTxt, newTxt);
            sqlHelper.updateStudyValidationExpr(activityValidationId, updatedExpr);
            log.info("Updated validation ID:  {} with expr text {}. \nOld expr: {} ", activityValidationId, currentExpr, updatedExpr);
        }
    }

    private void updateProxyEvents(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));
        UserDto adminUser = handle.attach(JdbiUser.class).findByUserGuid(cfg.getString("adminUser.guid"));
        deleteCopyConfigEvents(handle, studyDto, "PARENTAL_CONSENT");
        deleteCopyConfigEvents(handle, studyDto, "CONSENT_ASSENT");
        insertNewCopyConfigEvents(handle, studyDto, adminUser);
    }

    private void updateExistingPrequals(Handle handle) {
        DataExportDao dataExportDao = handle.attach(DataExportDao.class);
        JdbiUmbrellaStudy jdbiUmbrellaStudy = handle.attach(JdbiUmbrellaStudy.class);
        JdbiActivityInstance jdbiActivityInstance = handle.attach(JdbiActivityInstance.class);
        Long studyId = jdbiUmbrellaStudy.findByStudyGuid("cmi-lms").getId();

        //get existing prequal instances that need to be updated
        List<SqlHelper.ProxyActivityActivityInfo> prequals = sqlHelper.getPrequalProxyActivities("cmi-lms");
        log.info("existing prequals to update: {}", prequals.size());
        prequals.forEach(prequalActivity -> {
            DBUtils.checkUpdate(1, sqlHelper.updateProxyInstance(prequalActivity.activityInstanceId, prequalActivity.governedUserId, prequalActivity.operatorId));
            log.info("remapped activity instance : {} of operator: {} with gov user : {} ", prequalActivity.activityInstanceId, prequalActivity.governedUserId, prequalActivity.operatorId);

            //delete proxy enrollment
            if (!jdbiActivityInstance.findAllByUserGuidAndActivityCode(prequalActivity.operatorGuid, "CONSENT", studyId).isEmpty()) {
                DBUtils.checkUpdate(1, sqlHelper.deleteProxyEnrollmentStatusById(prequalActivity.operatorId));
                log.info("deleted proxy study enrollment for proxy: {}", prequalActivity.operatorId);

                //delete proxy from elastic
                try {
                    deleteElasticSearchData(handle, prequalActivity.operatorGuid, "cmi-lms");
                } catch (IOException e) {
                    log.warn("Failed to delted proxy : {} from ES . ignoring...", prequalActivity.operatorGuid);
                }
            } else {
                log.info("operator: {} seem to have self consent, not deleting enrollment status ", prequalActivity.operatorGuid);
            }
            dataExportDao.queueDataSync(prequalActivity.operatorId, studyId);
        });
    }

    private void deleteCopyConfigEvents(Handle handle, StudyDto studyDto, String activityCode) {
        log.info("Working on copy event for {}...", activityCode);
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        long copyConfigId = handle.attach(EventDao.class)
                .getAllEventConfigurationsByStudyId(studyDto.getId())
                .stream()
                .filter(event -> event.getEventActionType().equals(EventActionType.COPY_ANSWER))
                .filter(event -> {
                    if (event.getEventTriggerType().equals(EventTriggerType.ACTIVITY_STATUS)) {
                        var trigger = (ActivityStatusChangeTrigger) event.getEventTrigger();
                        return trigger.getStudyActivityId() == activityId
                                && trigger.getInstanceStatusType() == InstanceStatusType.COMPLETE;
                    }
                    return false;
                })
                .map(event -> ((CopyAnswerEventAction) event.getEventAction()).getCopyConfigurationId())
                .findFirst()
                .orElseThrow(() -> new DDPException("Could not find copy event for activity " + activityCode));
        log.info("Found copy event with copy configuration id " + copyConfigId);

        var copyConfigDao = handle.attach(CopyConfigurationDao.class);
        CopyConfiguration currentConfig = copyConfigDao
                .findCopyConfigById(copyConfigId)
                .orElseThrow(() -> new DDPException("Could not find copy configuration with id " + copyConfigId));
        Set<Long> pairsToDelete = currentConfig.getPairs().stream().filter(pair -> {
            String stableId = ((CopyAnswerLocation) pair.getSource()).getQuestionStableId();
            if ("CHILD_COUNTRY".equals(stableId) || "CHILD_STATE".equals(stableId)) {
                return true;
            }
            return false;
        }).map(pair -> pair.getId()).collect(Collectors.toSet());
        if (pairsToDelete.size() != 2) {
            throw new DDPException("Expected 2 copy config pairs to delete; found : " + pairsToDelete.size());
        }
        //delete these pairs
        DBUtils.checkUpdate(2, sqlHelper.deleteCopyConfigPairById(pairsToDelete));
        log.info("Deleted 2 CopyConfigPairs : {} ", pairsToDelete);
    }

    private void insertNewCopyConfigEvents(Handle handle, StudyDto studyDto, UserDto adminUser) {
        //insert new CopyConfigEvent
        var eventBuilder = new EventBuilder(cfg, studyDto, adminUser.getUserId());
        List<? extends Config> eventCfgs = activityDataCfg.getConfigList("copy-events");
        eventCfgs.forEach(eventCfg -> {
            long eventId = eventBuilder.insertEvent(handle, eventCfg);
            log.info("Inserting copy event : {}  ", eventId);
        });
    }


    private void deleteElasticSearchData(Handle handle, String userGuid, String studyGuid)
            throws IOException {
        log.info("deleting proxy from ES " + "participants, participants_structured, users", userGuid);
        BulkRequest bulkRequest = new BulkRequest().timeout("2m");
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);

        String indexParticipant = ElasticsearchServiceUtil.getIndexForStudy(handle, studyDto,
                ElasticSearchIndexType.PARTICIPANTS);
        String indexParticipantStructured = ElasticsearchServiceUtil.getIndexForStudy(handle, studyDto,
                ElasticSearchIndexType.PARTICIPANTS_STRUCTURED);
        String indexUsers = ElasticsearchServiceUtil.getIndexForStudy(handle, studyDto,
                ElasticSearchIndexType.USERS);

        bulkRequest.add(new DeleteRequest()
                .index(indexParticipant)
                .type(REQUEST_TYPE)
                .id(userGuid));

        bulkRequest.add(new DeleteRequest()
                .index(indexParticipantStructured)
                .type(REQUEST_TYPE)
                .id(userGuid));

        bulkRequest.add(new DeleteRequest()
                .index(indexUsers)
                .type(REQUEST_TYPE)
                .id(userGuid));

        BulkResponse bulkResponse = null; //esClient.bulk(bulkRequest, RequestOptions.DEFAULT);

        if (bulkResponse != null && bulkResponse.hasFailures()) {
            log.warn(bulkResponse.buildFailureMessage());
        }
    }


    private interface SqlHelper extends SqlObject {

        @SqlQuery("select expression_id from expression where expression_text like :searchTxt")
        List<Long> lmsPrequalProxyPex(@Bind("searchTxt") String searchtxt);

        @SqlQuery("select activity_validation_id from activity_validation where expression_text like :searchTxt")
        List<Long> lmsValidationProxyPex(@Bind("searchTxt") String searchtxt);

        @SqlQuery("select expression_text from activity_validation where activity_validation_id = :activityValidationId")
        String getValidationPexById(@Bind("activityValidationId") Long activityValidationId);

        @SqlUpdate("delete from copy_configuration_pair where copy_configuration_pair_id in (<copyConfigPairIds>)")
        int deleteCopyConfigPairById(@BindList(value = "copyConfigPairIds") Set<Long> copyConfigPairIds);


        @SqlUpdate("update expression e \n"
                + " set e.expression_text = :exprTxt "
                + " where e.expression_id = \n"
                + " (select should_create_governed_user_expression_id from study_governance_policy p, umbrella_study s where p.study_id = s.umbrella_study_id\n"
                + " and s.guid = :studyGuid)")
        int updateStudyGovExpr(@Bind("studyGuid") String studyGuid, @Bind("exprTxt") String exprTxt);


        @SqlUpdate("update activity_validation av \n"
                + " set av.expression_text = :exprTxt "
                + " where av.activity_validation_id = :activityValidationId")
        int updateStudyValidationExpr(@Bind("activityValidationId") Long activityValidationId, @Bind("exprTxt") String exprTxt);

        @SqlQuery("select u.user_id as operatorId, u.guid as operatorGuid, " +
                "gu.user_id as governedUserId, ai.activity_instance_id as activityInstanceId\n" +
                "from user u, umbrella_study s, study_activity sa, activity_instance ai , user_governance ug, user gu\n" +
                "where sa.study_id = s.umbrella_study_id\n" +
                "and ai.study_activity_id = sa.study_activity_id\n" +
                "and u.user_id = ai.participant_id\n" +
                "and ug.operator_user_id = u.user_id\n" +
                "and gu.user_id = ug.participant_user_id\n" +
                "and s.guid = :studyGuid\n" +
                "and sa.study_activity_code = 'PREQUAL'")
        @RegisterConstructorMapper(ProxyActivityActivityInfo.class)
        List<ProxyActivityActivityInfo> getPrequalProxyActivities(@Bind("studyGuid") String studyGuid);

        @SqlUpdate("delete from user_study_enrollment where user_id :proxyId")
        int deleteProxyEnrollmentStatusById(@Bind("proxyId") Long proxyId);

        @SqlUpdate("update activity_instance ai" +
                " set ai.participant_id = :govUserId" +
                " where ai.activity_instance_id = :activityValidationId " +
                " and ai.participant_id = :operatorId")
        int updateProxyInstance(@Bind("activityValidationId") Long activityValidationId, @Bind("govUserId") Long govUserId, @Bind("operatorId") Long operatorId);

        @AllArgsConstructor
        class ProxyActivityActivityInfo {
            Long operatorId;
            String operatorGuid;
            Long governedUserId;
            Long activityInstanceId;
        }
    }
}
