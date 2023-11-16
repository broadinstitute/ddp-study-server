package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.KitConfigurationDao;
import org.broadinstitute.ddp.db.dao.KitTypeDao;
import org.broadinstitute.ddp.db.dto.kit.KitConfigurationDto;
import org.broadinstitute.ddp.db.dto.kit.KitPexRuleDto;
import org.broadinstitute.ddp.model.kit.KitConfiguration;
import org.broadinstitute.ddp.model.kit.KitRule;
import org.broadinstitute.ddp.model.kit.KitRuleType;
import org.jdbi.v3.core.Handle;

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

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        ///not used
    }

    @Override
    public void run(Handle handle) {
        updateExistingSalivaKitConfig(handle, "CMI-OSTEO", PEX_RULE_EXPR_OS);
        updateExistingSalivaKitConfig(handle, "cmi-lms", PEX_RULE_EXPR_LMS);
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

}
