package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.user.User;
import org.jdbi.v3.core.Handle;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

@Slf4j
public class SimpleRevisionTask implements CustomTask {
    protected String dataFile;

    protected Config dataCfg;
    protected Config varsCfg;
    protected Path cfgPath;
    protected Instant timestamp;
    protected Config studyCfg;

    protected StudyDto studyDto;

    protected User adminUser;

    @Override
    public void consumeArguments(String[] args) throws ParseException {
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(new Options(), args);
        String[] positional = cmd.getArgs();
        if (positional.length < 1) {
            throw new ParseException("Patch File is required to run.");
        }

        this.dataFile = positional[0];

        File file = cfgPath.getParent().resolve(this.dataFile).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

        timestamp = Instant.now();
    }

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.varsCfg = varsCfg;
        this.studyCfg = studyCfg;
    }

    @Override
    public void run(Handle handle) {
        throw new DDPException("Not implemented");
    }

    protected StudyDto getStudyDto(Handle handle) {
        return handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
    }

    protected User getAdminUser(Handle handle) {
        return handle.attach(UserDao.class).findUserByGuid(studyCfg.getString("adminUser.guid")).get();
    }

    protected List<? extends Config> getConfigList(Config cfg, String field, Runnable onError) {
        try {
            return cfg.getConfigList(field);
        } catch (Exception e) {
            onError.run();
            return List.of();
        }
    }
}
