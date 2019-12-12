package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;
import java.util.List;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.JdbiComponent;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.TemplateDao;
import org.broadinstitute.ddp.db.dto.ComponentDto;
import org.broadinstitute.ddp.db.dto.MailingAddressComponentDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackfillMailingAddressComponentTitle implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(BackfillMailingAddressComponentTitle.class);
    private static final String DEFAULT_TITLE_VAR = "mailing_address_title";
    private static final String DEFAULT_TITLE_TEXT = "Your contact information";

    private Config cfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        cfg = studyCfg;
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));
        SqlHelper helper = handle.attach(SqlHelper.class);
        JdbiComponent jdbiComponent = handle.attach(JdbiComponent.class);

        List<ComponentDto> dtos = helper.findMailingAddressComponentsInStudy(studyDto.getId());
        LOG.info("Found {} mailing address components in study {}", dtos.size(), studyDto.getGuid());

        for (ComponentDto dto : dtos) {
            long componentId = dto.getComponentId();
            MailingAddressComponentDto compDto = jdbiComponent.findMailingAddressComponentDtoById(componentId).orElse(null);

            if (compDto == null) {
                long titleTemplateId = createTitleTemplate(handle, dto.getRevisionId());
                DBUtils.checkInsert(1, jdbiComponent.insertMailingAddressComponent(componentId, titleTemplateId, null));
                LOG.info("Inserted mailing address component {} with titleTemplateId={}", componentId, titleTemplateId);
            } else if (compDto.getTitleTemplateId() == null) {
                long titleTemplateId = createTitleTemplate(handle, dto.getRevisionId());
                DBUtils.checkUpdate(1, jdbiComponent.updateMailingAddressComponent(
                        componentId, titleTemplateId, compDto.getSubtitleTemplateId()));
                LOG.info("Updated mailing address component {} with titleTemplateId={}", componentId, titleTemplateId);
            } else {
                LOG.info("Mailing address component with id={} already has titleTemplateId={}",
                        componentId, compDto.getTitleTemplateId());
            }
        }
    }

    private long createTitleTemplate(Handle handle, long revisionId) {
        TemplateDao templateDao = handle.attach(TemplateDao.class);
        Template title = Template.text("$" + DEFAULT_TITLE_VAR);
        title.addVariable(TemplateVariable.single(DEFAULT_TITLE_VAR, "en", DEFAULT_TITLE_TEXT));
        return templateDao.insertTemplate(title, revisionId);
    }

    private interface SqlHelper extends SqlObject {
        @SqlQuery("select ct.component_type_code as component_type, c.*"
                + "  from component as c"
                + "  join component_type as ct on ct.component_type_id = c.component_type_id"
                + "  join block_component as bc on bc.component_id = c.component_id"
                + " where bc.block_id in ("
                + "       select fsb.block_id"
                + "         from form_section__block as fsb"
                + "         join form_activity__form_section as fafs on fafs.form_section_id = fsb.form_section_id"
                + "         join study_activity as act on act.study_activity_id = fafs.form_activity_id"
                + "        where act.study_id = :studyId)"
                + "   and ct.component_type_code = 'MAILING_ADDRESS'")
        @RegisterConstructorMapper(ComponentDto.class)
        List<ComponentDto> findMailingAddressComponentsInStudy(@Bind("studyId") long studyId);
    }
}
