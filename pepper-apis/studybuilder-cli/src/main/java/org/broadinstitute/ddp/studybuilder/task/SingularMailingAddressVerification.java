package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.exception.DDPException;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.nio.file.Path;

/**
 * Task to update mailing address components of the singular study
 * Task sets require_verified & require_phone flags to true
 */
@Slf4j
@NoArgsConstructor
public class SingularMailingAddressVerification implements CustomTask {
    private static final String STUDY_GUID  = "singular";

    protected Path cfgPath;
    protected Config cfg;
    protected Config varsCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        this.cfgPath = cfgPath;
        this.cfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        log.info("TASK::{}", SingularMailingAddressVerification.class.getSimpleName());
        handle.attach(SqlHelper.class).updateMailingAddressComponents(cfg.getString("study.guid"));
    }

    private interface SqlHelper extends SqlObject {
        @SqlUpdate("UPDATE `mailing_address_component` mac" 
                 + "  JOIN component c ON mac.component_id = c.component_id"
                 + "  JOIN component_type ct ON c.component_type_id = ct.component_type_id"
                 + "  JOIN block_component bc ON c.component_id = bc.component_id"
                 + "  JOIN form_section__block fsb ON bc.block_id = fsb.block_id"
                 + "  JOIN form_activity__form_section fafs ON fafs.form_section_id = fsb.form_section_id"
                 + "  JOIN study_activity as act ON act.study_activity_id = fafs.form_activity_id"
                 + "  JOIN umbrella_study as us ON us.umbrella_study_id = act.study_id"
                 + "   SET mac.require_phone = true, mac.require_verified = true"
                 + " WHERE ct.component_type_code = 'MAILING_ADDRESS'"
                 + "   AND us.guid = :studyGuid")
        void updateMailingAddressComponents(@Bind("studyGuid") final String studyGuid);
    }
}
