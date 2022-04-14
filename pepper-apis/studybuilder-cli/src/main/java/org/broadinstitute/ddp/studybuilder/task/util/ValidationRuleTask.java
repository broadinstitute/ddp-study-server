package org.broadinstitute.ddp.studybuilder.task.util;

import static org.broadinstitute.ddp.studybuilder.task.util.TaskConfConstants.CONF_PARAM__CHANGE_TYPE;
import static org.broadinstitute.ddp.studybuilder.task.util.TaskConfConstants.CONF_PARAM__QUESTION_STABLE_IDS;
import static org.broadinstitute.ddp.studybuilder.task.util.TaskConfConstants.CONF_PARAM__STUDY_GUID;
import static org.broadinstitute.ddp.studybuilder.task.util.TaskConfConstants.CONF_PARAM__VALIDATIONS;
import static org.broadinstitute.ddp.studybuilder.task.util.TaskUtil.readConfigFromFile;

import java.nio.file.Path;
import java.util.List;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiQuestionValidation;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.ValidationDao;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.validation.RuleDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.validation.AgeRangeRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.CompleteRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.DateFieldRequiredRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.DateRangeRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.IntRangeRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.DecimalRangeRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.LengthRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.NumOptionsSelectedRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RegexRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.UniqueRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.UniqueValueRuleDef;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;

/**Ø
 * Custom task providing insert/update/delete of validation rules to specified questions.
 * Note: before a validation rule insert it is checked if a validation rule (for such questionStableId/ruleType) already exist.
 * If it is exist then insert operation just ignored (without throwing any error - just a warn message to log).
 *
 * <p>Example of patch config: defined a rule UNIQUE to be added (INSERT) to 3 different questions.
 * {
 * "studyGuid": "cmi-pancan",
 * "questionStableIds": "PRIMARY_CANCER_LIST_SELF,PRIMARY_CANCER_LIST_CHILD,PRIMARY_CANCER_LIST_ADD_CHILD",
 * "validations": [
 *   {
 *     "changeType": "INSERT"
 *     "ruleType": "UNIQUE",
 *     "allowSave": true,
 *     "hintTemplate": {
 *        "templateType": "TEXT",
 *        "templateText": "$hint",
 *        "variables": [
 *          {
 *            "name": "hint",
 *            "translations": [
 *              { "language": "en", "text": """Please do not provide duplicated cancer(s).""" },
 *              { "language": "es", "text": """Por favor verifique su respuestas por duplicación.""" }
 *            ]
 *          }
 *        ]
 *     }
 *   }
 *  ]
 * }
 */
@Slf4j
public class ValidationRuleTask implements CustomTask {
    private static final Gson gson = GsonUtil.standardGson();
    private final String patchCfgFile;
    private Config dataCfg;

    public ValidationRuleTask(String patchCfgFile) {
        this.patchCfgFile = patchCfgFile;
    }

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        dataCfg = readConfigFromFile(cfgPath, patchCfgFile, varsCfg);
    }

    @Override
    public void run(Handle handle) {
        String studyGuid = dataCfg.getString(CONF_PARAM__STUDY_GUID);
        String[] questionStableIds = StringUtils.split(dataCfg.getString(CONF_PARAM__QUESTION_STABLE_IDS), ',');
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
        processValidationRules(handle, questionStableIds, studyDto);
    }

    /**
     * Iterate through all validation rules specified inside "validations" and
     * add each rule to each of specified question
     */
    private void processValidationRules(Handle handle, String[] questionStableIds, StudyDto studyDto) {
        for (Config ruleCfg : dataCfg.getConfigList(CONF_PARAM__VALIDATIONS)) {
            for (String questionStableId : questionStableIds) {
                doValidationRuleAction(handle, studyDto, ruleCfg, questionStableId);
            }
        }
    }

    /**
     * Do an action (depending on parameter "changeType" for a selected rule/selected question)
     */
    private void doValidationRuleAction(Handle handle, StudyDto studyDto, Config ruleCfg, String questionStableId) {
        String insertType = ruleCfg.getString(CONF_PARAM__CHANGE_TYPE);
        if (insertType.equals(OperationType.INSERT.name())) {
            insertValidation(handle, studyDto.getId(), questionStableId, ruleCfg);
        }
    }

    /**
     * Insert validation rule to a specified question.
     */
    private static void insertValidation(Handle handle, long studyId, String stableId, Config ruleConfig) {
        ValidationDao validationDao = handle.attach(ValidationDao.class);
        QuestionDto questionDto = handle.attach(JdbiQuestion.class)
                .findLatestDtoByStudyIdAndQuestionStableId(studyId, stableId)
                .orElseThrow(() -> new DDPException("Could not find question " + stableId));
        RuleDef rule = gson.fromJson(ConfigUtil.toJson(ruleConfig), RuleDef.class);

        JdbiQuestionValidation jdbiQuestionValidation = handle.attach(JdbiQuestionValidation.class);
        List<RuleDto> existingRules = jdbiQuestionValidation.getAllActiveValidations(questionDto.getId());
        if (existingRules.stream().anyMatch(r -> r.getRuleType() == rule.getRuleType())) {
            log.warn("Rule of type={} already exist for question with stableId={}", rule.getRuleType(), questionDto.getStableId());
        } else {
            switch (rule.getRuleType()) {
                case AGE_RANGE:
                    validationDao.insert(questionDto.getId(),
                            gson.fromJson(ConfigUtil.toJson(ruleConfig), AgeRangeRuleDef.class),
                            questionDto.getRevisionId());
                    break;
                case COMPLETE:
                    validationDao.insert(questionDto.getId(),
                            gson.fromJson(ConfigUtil.toJson(ruleConfig), CompleteRuleDef.class),
                            questionDto.getRevisionId());
                    break;
                case DATE_RANGE:
                    validationDao.insert(questionDto.getId(),
                            gson.fromJson(ConfigUtil.toJson(ruleConfig), DateRangeRuleDef.class),
                            questionDto.getRevisionId());
                    break;
                case YEAR_REQUIRED:
                case MONTH_REQUIRED:
                case DAY_REQUIRED:
                    validationDao.insert(questionDto.getId(),
                            gson.fromJson(ConfigUtil.toJson(ruleConfig), DateFieldRequiredRuleDef.class),
                            questionDto.getRevisionId());
                    break;
                case INT_RANGE:
                    validationDao.insert(questionDto.getId(),
                            gson.fromJson(ConfigUtil.toJson(ruleConfig), IntRangeRuleDef.class),
                            questionDto.getRevisionId());
                    break;
                case DECIMAL_RANGE:
                    validationDao.insert(questionDto.getId(),
                            gson.fromJson(ConfigUtil.toJson(ruleConfig), DecimalRangeRuleDef.class),
                            questionDto.getRevisionId());
                    break;
                case LENGTH:
                    validationDao.insert(questionDto.getId(),
                            gson.fromJson(ConfigUtil.toJson(ruleConfig), LengthRuleDef.class),
                            questionDto.getRevisionId());
                    break;
                case NUM_OPTIONS_SELECTED:
                    validationDao.insert(questionDto.getId(),
                            gson.fromJson(ConfigUtil.toJson(ruleConfig), NumOptionsSelectedRuleDef.class),
                            questionDto.getRevisionId());
                    break;
                case REGEX:
                    validationDao.insert(questionDto.getId(),
                            gson.fromJson(ConfigUtil.toJson(ruleConfig), RegexRuleDef.class),
                            questionDto.getRevisionId());
                    break;
                case REQUIRED:
                    validationDao.insert(questionDto.getId(),
                            gson.fromJson(ConfigUtil.toJson(ruleConfig), RequiredRuleDef.class),
                            questionDto.getRevisionId());
                    break;
                case UNIQUE:
                    validationDao.insert(questionDto.getId(),
                            gson.fromJson(ConfigUtil.toJson(ruleConfig), UniqueRuleDef.class),
                            questionDto.getRevisionId());
                    break;
                case UNIQUE_VALUE:
                    validationDao.insert(questionDto.getId(),
                            gson.fromJson(ConfigUtil.toJson(ruleConfig), UniqueValueRuleDef.class),
                            questionDto.getRevisionId());
                    break;
                default:
                    throw new DDPException("Unhandled validation rule type: " + rule.getRuleType());
            }
            log.info("Inserted validation rule of type={} to question with stableId={}", rule.getRuleType(), questionDto.getStableId());
        }
    }
}
