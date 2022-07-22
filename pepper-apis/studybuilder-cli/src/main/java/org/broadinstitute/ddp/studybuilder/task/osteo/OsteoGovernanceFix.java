package org.broadinstitute.ddp.studybuilder.task.osteo;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.StudyGovernanceDao;
import org.broadinstitute.ddp.db.dao.StudyGovernanceSql;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.governance.AgeOfMajorityRule;
import org.broadinstitute.ddp.model.governance.GovernancePolicy;
import org.broadinstitute.ddp.model.pex.Expression;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.jdbi.v3.core.Handle;

import java.io.File;
import java.nio.file.Path;

@Slf4j
public class OsteoGovernanceFix implements CustomTask {

    private static final String DATA_FILE = "patches/governance-fix.conf";

    private Config studyCfg;
    private Config dataCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file);
        this.studyCfg = studyCfg;
    }

    @Override
    public void run(Handle handle) {
        var studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        updateGovernanceExpression(handle, studyDto);
    }

    private void updateGovernanceExpression(Handle handle, StudyDto studyDto) {
        var studyGovernanceDao = handle.attach(StudyGovernanceDao.class);
        var expressionDao = handle.attach(JdbiExpression.class);

        var policy = studyGovernanceDao.findPolicyByStudyId(studyDto.getId())
                .orElseThrow(() -> new DDPException("Couldn't find policy for study " + studyDto.getGuid()));

        var expression = policy.getShouldCreateGovernedUserExpr();

        var governanceCfg = dataCfg.getConfig("governance");

        var shouldCreateGovernedUserExpr = governanceCfg.getBoolean("shouldCreateGovernedUserExpr");
        expressionDao.updateById(expression.getId(), String.valueOf(shouldCreateGovernedUserExpr));

        var studyGovernanceSql = handle.attach(StudyGovernanceSql.class);
        studyGovernanceSql.deleteRulesForPolicy(policy.getId());

        var tempPolicy = new GovernancePolicy(policy.getId(), policy.getStudyId(), studyDto.getGuid(), new Expression("false"));

        for (Config aomRuleCfg : governanceCfg.getConfigList("ageOfMajorityRules")) {
            tempPolicy.addAgeOfMajorityRule(new AgeOfMajorityRule(
                    aomRuleCfg.getString("condition"),
                    aomRuleCfg.getInt("age"),
                    ConfigUtil.getIntIfPresent(aomRuleCfg, "prepMonths")));
        }

        int order = 1;
        for (AgeOfMajorityRule rule : tempPolicy.getAgeOfMajorityRules()) {
            studyGovernanceSql.insertAgeOfMajorityRule(policy.getId(), rule.getCondition(), rule.getAge(), rule.getPrepMonths(), order);
            order += 1;
        }

        log.info("Successfully updated governance for study {}", studyDto.getGuid());
    }
}
