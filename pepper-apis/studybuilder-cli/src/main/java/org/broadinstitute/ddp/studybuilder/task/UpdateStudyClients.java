package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;
import java.util.List;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.db.dto.ClientDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.studybuilder.StudyBuilder;
import org.jdbi.v3.core.Handle;

/**
 * General task to update study's list of clients.
 */
@Slf4j
public class UpdateStudyClients implements CustomTask {
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
        var builder = new StudyBuilder(cfgPath, studyCfg, varsCfg);
        StudyDto studyDto = builder.getStudy(handle);
        log.info("Updating list of clients for study {} ...", studyDto.getGuid());

        Auth0TenantDto tenantDto = builder.getTenantOrInsert(handle);
        List<ClientDto> clientDtos = builder.getClientsOrInsert(handle, tenantDto);
        builder.grantClientsAccessToStudy(handle, clientDtos, studyDto);
    }
}
