package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.StudyGovernanceDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.jdbi.v3.core.Handle;

import java.io.File;
import java.nio.file.Path;

@Slf4j
public class OsteoGovernanceFix implements CustomTask {

    private static final String DATA_FILE = "patches/updated-governance.conf";

    private Config studyCfg;
    private Config dataCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {

        this.studyCfg = studyCfg;

        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
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

        var shouldCreateGovernedUserExpr = dataCfg.getConfig("governance").getString("shouldCreateGovernedUserExpr");
        expressionDao.updateById(expression.getId(), shouldCreateGovernedUserExpr);

        log.info("Successfully updated governance expression text for study {}: {}", studyDto.getGuid(), shouldCreateGovernedUserExpr);
    }
}
