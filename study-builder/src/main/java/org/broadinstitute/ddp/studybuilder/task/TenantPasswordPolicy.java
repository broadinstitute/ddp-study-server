package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.studybuilder.StudyBuilder;
import org.jdbi.v3.core.Handle;

/**
 * A general task to help insert password policy for a tenant.
 */
public class TenantPasswordPolicy implements CustomTask {

    protected Path cfgPath;
    protected Config cfg;
    protected Config varsCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.cfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        var builder = new StudyBuilder(cfgPath, cfg, varsCfg);
        String domain = cfg.getString("tenant.domain");
        Auth0TenantDto tenantDto = handle.attach(JdbiAuth0Tenant.class).findByDomain(domain);
        builder.insertTenantPasswordPolicy(handle, tenantDto);
    }
}
