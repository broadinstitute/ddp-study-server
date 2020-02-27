package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.db.dto.StudyPasswordRequirementsDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiStudyPasswordRequirements extends SqlObject {
    @SqlQuery("select * from study_password_requirements where auth0_tenant_id = :id")
    @RegisterRowMapper(StudyPasswordRequirementsDto.FieldMapper.class)
    Optional<StudyPasswordRequirementsDto> getById(@Bind("id") long id);

    @SqlUpdate(
            "insert into study_password_requirements(auth0_tenant_id, min_length,"
            + " is_uppercase_letter_required, is_lowercase_letter_required,"
            + " is_special_character_required, is_number_required, max_identical_consecutive_characters)"
            + " values (:id, :minLength, :isUppercaseLetterRequired, :isLowercaseLetterRequired,"
            + " :isSpecialCharacterRequired, :isNumberRequired, :maxIdenticalConsecutiveCharacters)"
    )
    int insert(
            @Bind("id") long id,
            @Bind("minLength") int minLength,
            @Bind("isUppercaseLetterRequired") boolean isUppercaseLetterRequired,
            @Bind("isLowercaseLetterRequired") boolean isLowercaseLetterRequired,
            @Bind("isSpecialCharacterRequired") boolean isSpecialCharacterRequired,
            @Bind("isNumberRequired") boolean isNumberRequired,
            @Bind("maxIdenticalConsecutiveCharacters") Integer maxIdenticalConsecutiveCharacters
    );

    @SqlUpdate("delete from study_password_requirements where auth0_tenant_id = :id")
    int deleteById(@Bind("id") long id);
}
