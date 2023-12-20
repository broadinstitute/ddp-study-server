package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.KitConfigurationDao;
import org.broadinstitute.ddp.db.dao.KitTypeDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.kit.KitConfigurationDto;
import org.broadinstitute.ddp.db.dto.kit.KitPexRuleDto;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.broadinstitute.ddp.model.kit.KitConfiguration;
import org.broadinstitute.ddp.model.kit.KitRule;
import org.broadinstitute.ddp.model.kit.KitRuleType;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.nio.file.Path;
import java.util.List;

@Slf4j
public class PecgsKitRuleUpdates implements CustomTask {

    public static String PEX_RULE_EXPR_LMS = " (user.studies[\"cmi-lms\"].forms[\"CONSENT\"].hasInstance() "
            + " && user.studies[\"cmi-lms\"].forms[\"CONSENT\"].isStatus(\"COMPLETE\")) "
            + " || (user.studies[\"cmi-lms\"].forms[\"CONSENT_ASSENT\"].hasInstance() "
            + " && user.studies[\"cmi-lms\"].forms[\"CONSENT_ASSENT\"].isStatus(\"COMPLETE\")) "
            + " || (user.studies[\"cmi-lms\"].forms[\"PARENTAL_CONSENT\"].hasInstance() "
            + " && user.studies[\"cmi-lms\"].forms[\"PARENTAL_CONSENT\"].isStatus(\"COMPLETE\"))";

    public static String PEX_RULE_EXPR_OS = " (user.studies[\"CMI-OSTEO\"].forms[\"CONSENT\"].hasInstance() "
            + " && user.studies[\"CMI-OSTEO\"].forms[\"CONSENT\"].isStatus(\"COMPLETE\")) "
            + " || (user.studies[\"CMI-OSTEO\"].forms[\"CONSENT_ASSENT\"].hasInstance() "
            + " && user.studies[\"CMI-OSTEO\"].forms[\"CONSENT_ASSENT\"].isStatus(\"COMPLETE\")) "
            + " || (user.studies[\"CMI-OSTEO\"].forms[\"PARENTAL_CONSENT\"].hasInstance() "
            + " && user.studies[\"CMI-OSTEO\"].forms[\"PARENTAL_CONSENT\"].isStatus(\"COMPLETE\"))";

    public static String PEX_RULE_EXPR_BK_LMS = " (user.studies[\"cmi-lms\"].forms[\"CONSENT\"].hasInstance() "
            + " && user.studies[\"cmi-lms\"].forms[\"CONSENT\"].isStatus(\"COMPLETE\")"
            + " && user.studies[\"cmi-lms\"].forms[\"CONSENT\"].questions[\"CONSENT_BLOOD\"].answers.hasTrue()) "
            + " || (user.studies[\"cmi-lms\"].forms[\"CONSENT_ASSENT\"].hasInstance() "
            + " && user.studies[\"cmi-lms\"].forms[\"CONSENT_ASSENT\"].isStatus(\"COMPLETE\") "
            + " && user.studies[\"cmi-lms\"].forms[\"CONSENT_ASSENT\"].questions[\"CONSENT_ASSENT_BLOOD\"].answers.hasTrue()) "
            + " || (user.studies[\"cmi-lms\"].forms[\"PARENTAL_CONSENT\"].hasInstance() "
            + " && user.studies[\"cmi-lms\"].forms[\"PARENTAL_CONSENT\"].isStatus(\"COMPLETE\")"
            + " && user.studies[\"cmi-lms\"].forms[\"PARENTAL_CONSENT\"].questions[\"PARENTAL_CONSENT_BLOOD\"].answers.hasTrue())";

    public static String PEX_RULE_EXPR_BK_OS = " (user.studies[\"CMI-OSTEO\"].forms[\"CONSENT\"].hasInstance() "
            + " && user.studies[\"CMI-OSTEO\"].forms[\"CONSENT\"].isStatus(\"COMPLETE\")"
            + " && user.studies[\"CMI-OSTEO\"].forms[\"CONSENT\"].questions[\"CONSENT_BLOOD\"].answers.hasTrue()) "
            + " || (user.studies[\"CMI-OSTEO\"].forms[\"CONSENT_ASSENT\"].hasInstance() "
            + " && user.studies[\"CMI-OSTEO\"].forms[\"CONSENT_ASSENT\"].isStatus(\"COMPLETE\") "
            + " && user.studies[\"CMI-OSTEO\"].forms[\"CONSENT_ASSENT\"].questions[\"CONSENT_ASSENT_BLOOD\"].answers.hasTrue()) "
            + " || (user.studies[\"CMI-OSTEO\"].forms[\"PARENTAL_CONSENT\"].hasInstance() "
            + " && user.studies[\"CMI-OSTEO\"].forms[\"PARENTAL_CONSENT\"].isStatus(\"COMPLETE\")"
            + " && user.studies[\"CMI-OSTEO\"].forms[\"PARENTAL_CONSENT\"].questions[\"PARENTAL_CONSENT_BLOOD\"].answers.hasTrue())";

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        ///not used
    }

    @Override
    public void run(Handle handle) {
        updateExistingSalivaKitConfig(handle, "CMI-OSTEO", PEX_RULE_EXPR_OS);
        updateExistingSalivaKitConfig(handle, "cmi-lms", PEX_RULE_EXPR_LMS);

        updateBloodKitEvent(handle, "CMI-OSTEO", PEX_RULE_EXPR_BK_OS);
        updateBloodKitEvent(handle, "cmi-lms", PEX_RULE_EXPR_BK_LMS);
    }

    private void updateExistingSalivaKitConfig(final Handle handle, String studyGuid, String pexExpr) {
        KitConfigurationDao kitConfigDao = handle.attach(KitConfigurationDao.class);
        List<KitConfigurationDto> kitConfigs = kitConfigDao.getKitConfigurationDtosByStudyId(handle.attach(JdbiUmbrellaStudy.class)
                .findByStudyGuid(studyGuid).getId());

        long salivaKitTypeid = handle.attach(KitTypeDao.class).getSalivaKitType().getId();
        KitConfigurationDto kitConfigDto = kitConfigs.stream().filter(dto -> dto.getKitTypeId()
                == salivaKitTypeid).findFirst().get();

        KitConfiguration kitConfig = kitConfigDao.getKitConfigurationForDto(kitConfigDto);
        //get the pex rule. only 1 pex rule exists.
        KitRule kitRule = kitConfig.getRules().stream().filter(thisRule -> thisRule.getType().equals(KitRuleType.PEX)).findFirst().get();
        KitPexRuleDto kitPexRuleDto = kitConfigDao.getJdbiKitRules().getKitPexRuleById(kitRule.getId()).get();
        long expressionId = kitPexRuleDto.getExpressionId();
        //update the expression
        int udpCount = kitConfigDao.getJdbiExpression().updateById(expressionId, pexExpr);
        DBUtils.checkUpdate(1, udpCount);
        log.info("Successfully updated {} Saliva Kit Config {} of {}.", 1, kitConfigDto.getId(), studyGuid);
    }

    public void updateBloodKitEvent(Handle handle, String studyGuid, String expr) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);

        List<EventConfiguration> events = handle.attach(EventDao.class)
                .getAllEventConfigurationsByStudyId(studyDto.getId());

        //update blood kit expr
        EventConfiguration bloodKitEvent = events.stream()
                .filter(event -> event.getEventTriggerType() == EventTriggerType.DSM_NOTIFICATION)
                .filter(event -> event.getEventActionType() == EventActionType.CREATE_KIT)
                .findFirst().get();

        long expressionId = handle.attach(PecgsKitRuleUpdates.SqlHelper.class).getPreCondExpressionIdByEventId(
                bloodKitEvent.getEventConfigurationId());
        int udpCount =  handle.attach(JdbiExpression.class).updateById(expressionId, expr);
        DBUtils.checkUpdate(1, udpCount);
        log.info("Updated blood kit event configuration  {} of study {} with precond exprId: {} ", bloodKitEvent.getEventConfigurationId(),
                studyGuid, expressionId);
    }

    private interface SqlHelper extends SqlObject {
        @SqlQuery("select precondition_expression_id from event_configuration where event_configuration_id = :eventConfigId")
        long getPreCondExpressionIdByEventId(@Bind("eventConfigId") long eventConfigId);
    }

}
