package org.broadinstitute.ddp.studybuilder.task;

import static org.broadinstitute.ddp.studybuilder.task.util.TaskUtil.readConfigFromFile;

import java.nio.file.Path;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.ValidationDao;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.validation.UniqueRuleDef;
import org.broadinstitute.ddp.studybuilder.task.util.TaskUtil;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Patch to add validation rule of type UNIQUE to a cancer list.
 */
public class PanCanUniqueCancerValidation implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(TaskUtil.class);

    private static Gson gson = GsonUtil.standardGson();
    private static final String DATA_FILE = "patches/unique-cancer-validation.conf";

    private Config dataCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        dataCfg = readConfigFromFile(cfgPath, DATA_FILE, varsCfg);
    }

    @Override
    public void run(Handle handle) {
        String studyGuid = dataCfg.getString("studyGuid");
        String[] questionStableIds = StringUtils.split(dataCfg.getString("questionStableIds"), ',');
        Config ruleConfig = dataCfg.getConfig("uniqueRule");
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
        for (String questionStableId :questionStableIds) {
            addValidation(handle, studyDto.getId(), questionStableId, ruleConfig);
        }
    }

    public static void addValidation(Handle handle, long studyId, String stableId, Config ruleConfig) {
        ValidationDao validationDao = handle.attach(ValidationDao.class);
        QuestionDto questionDto = handle.attach(JdbiQuestion.class)
                .findLatestDtoByStudyIdAndQuestionStableId(studyId, stableId)
                .orElseThrow(() -> new DDPException("Could not find question " + stableId));
        UniqueRuleDef rule = gson.fromJson(ConfigUtil.toJson(ruleConfig), UniqueRuleDef.class);
        validationDao.insert(questionDto.getId(), rule, questionDto.getRevisionId());
        LOG.info("Inserted validation rule with id={} questionStableId={}", rule.getRuleId(), questionDto.getStableId());
    }
}
