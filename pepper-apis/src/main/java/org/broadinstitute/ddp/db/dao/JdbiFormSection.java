package org.broadinstitute.ddp.db.dao;

import static org.broadinstitute.ddp.constants.SqlConstants.FormSectionTable.ID;
import static org.broadinstitute.ddp.constants.SqlConstants.FormSectionTable.SECTION_CODE;
import static org.broadinstitute.ddp.constants.SqlConstants.FormSectionTable.TABLE_NAME;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dto.FormSectionDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiFormSection extends SqlObject {

    default String generateUniqueCode() {
        return DBUtils.uniqueStandardGuid(getHandle(), TABLE_NAME, SECTION_CODE);
    }

    @SqlUpdate("insert into form_section (form_section_code, name_template_id) values (:sectionCode, :nameTemplateId)")
    @GetGeneratedKeys
    long insert(@Bind("sectionCode") String sectionCode, @Bind("nameTemplateId") Long nameTemplateId);

    @SqlQuery("select * from " + TABLE_NAME + " where " + SECTION_CODE + "= ?")
    @RegisterRowMapper(FormSectionDto.FormSectionDtoMapper.class)
    FormSectionDto findByCode(String sectionCode);

    @SqlQuery("select * from " + TABLE_NAME + " where " + ID + "= ?")
    @RegisterRowMapper(FormSectionDto.FormSectionDtoMapper.class)
    FormSectionDto findById(long formSectionId);
}
