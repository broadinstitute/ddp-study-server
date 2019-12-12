package org.broadinstitute.ddp.db.dao;

import static org.broadinstitute.ddp.constants.SqlConstants.InstitutionPhysicianComponentTable.ALLOW_MULTIPLE;
import static org.broadinstitute.ddp.constants.SqlConstants.InstitutionPhysicianComponentTable.BUTTON_TEMPLATE_ID;
import static org.broadinstitute.ddp.constants.SqlConstants.InstitutionPhysicianComponentTable.HIDE_NUMBER;
import static org.broadinstitute.ddp.constants.SqlConstants.InstitutionPhysicianComponentTable.SHOW_FIELDS;
import static org.broadinstitute.ddp.constants.SqlConstants.InstitutionPhysicianComponentTable.SUBTITLE_TEMPLATE_ID;
import static org.broadinstitute.ddp.constants.SqlConstants.InstitutionPhysicianComponentTable.TITLE_TEMPLATE_ID;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiInstitutionPhysicianComponent extends SqlObject {

    @SqlQuery("select comp.*,c.hide_number,it.institution_type_code  "
            + "from institution_physician_component comp,institution_type it,component c "
            + "where "
            + "c.component_id = comp.institution_physician_component_id "
            + "and "
            + "comp.institution_physician_component_id = :componentId "
            + "and "
            + "it.institution_type_id = comp.institution_type_id")
    @RegisterRowMapper(InstitutionComponentDtoRowMapper.class)
    InstitutionPhysicianComponentDto findById(@Bind("componentId") long componentId);

    @SqlUpdate("insert into institution_physician_component "
            + "(institution_physician_component_id,"
            + "allow_multiple,add_button_template_id,"
            + "title_template_id,"
            + "subtitle_template_id,"
            + "institution_type_id,"
            + "show_fields_initially)"
            + "values(:id,:allowMultiple,:buttonTemplateId,:titleTemplateId,:subtitleTemplateId,:instTypeId,"
            + ":showFields)")
    int insert(@Bind("id") long componentId,
               @Bind("allowMultiple") boolean allowMultiple,
               @Bind("buttonTemplateId") Long addButtonTemplateId,
               @Bind("titleTemplateId") Long titleTemplateId,
               @Bind("subtitleTemplateId") Long subtitleTemplateId,
               @Bind("instTypeId") long institutionTypeId,
               @Bind("showFields") boolean showFields);

    class InstitutionPhysicianComponentDto {

        private Long buttonTemplateId;
        private Long titleTemplateId;
        private Long subtitleTemplateId;
        private boolean allowMultiple;
        private InstitutionType institutionType;
        private boolean showFields;
        private boolean hideNumber;

        public InstitutionPhysicianComponentDto(Long buttonTemplateId,
                                                Long titleTemplateId,
                                                Long subtitleTemplateId,
                                                boolean allowMultiple,
                                                InstitutionType institutionType,
                                                boolean showFields,
                                                boolean hideNumber) {
            this.buttonTemplateId = buttonTemplateId;
            this.titleTemplateId = titleTemplateId;
            this.subtitleTemplateId = subtitleTemplateId;
            this.allowMultiple = allowMultiple;
            this.institutionType = institutionType;
            this.showFields = showFields;
            this.hideNumber = hideNumber;
        }

        public boolean shouldHideNumber() {
            return hideNumber;
        }

        public Long getButtonTemplateId() {
            return buttonTemplateId;
        }

        public Long getTitleTemplateId() {
            return titleTemplateId;
        }

        public Long getSubtitleTemplateId() {
            return subtitleTemplateId;
        }

        public boolean getAllowMultiple() {
            return allowMultiple;
        }

        public InstitutionType getInstitutionType() {
            return institutionType;
        }

        public boolean showFields() {
            return showFields;
        }
    }

    class InstitutionComponentDtoRowMapper implements RowMapper<InstitutionPhysicianComponentDto> {

        @Override
        public InstitutionPhysicianComponentDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            Long buttonTemplateId = (Long) rs.getObject(BUTTON_TEMPLATE_ID);
            Long titleTemplateId = (Long) rs.getObject(TITLE_TEMPLATE_ID);
            Long subtitleTemplateId = (Long) rs.getObject(SUBTITLE_TEMPLATE_ID);
            boolean allowMultiple = rs.getBoolean(ALLOW_MULTIPLE);
            boolean showFields = rs.getBoolean(SHOW_FIELDS);
            boolean hideNumber = rs.getBoolean(HIDE_NUMBER);
            InstitutionType institutionType = InstitutionType.valueOf(rs.getString(SqlConstants.InstitutionTypeTable
                                                                                           .INSTITUTION_TYPE_CODE));
            return new InstitutionPhysicianComponentDto(buttonTemplateId,
                                                        titleTemplateId,
                                                        subtitleTemplateId,
                                                        allowMultiple,
                                                        institutionType,
                                                        showFields,
                                                        hideNumber);
        }
    }
}
