package org.broadinstitute.ddp.db.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.db.dto.StudyI18nDto;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiUmbrellaStudyI18n extends SqlObject {
    @SqlQuery("SELECT lang.iso_language_code, i18n.name, i18n.summary "
            + "FROM i18n_umbrella_study AS i18n, language_code AS lang "
            + "WHERE i18n.umbrella_study_id = :id "
                + "AND i18n.language_code_id = lang.language_code_id")
    @RegisterRowMapper(UmbrellaStudyI18nDtoMapper.class)
    List<StudyI18nDto> findTranslationsByStudyId(@Bind("id") long id);

    @SqlUpdate("insert into i18n_umbrella_study "
            + "(umbrella_study_id,language_code_id,name,summary) values "
            + "(:studyId, :languageCodeId, :name, :summary)")
    @GetGeneratedKeys()
    long insert(@Bind("studyId") long umbrellaStudyId,
                @Bind("languageCodeId") long languageCodeId, 
                @Bind("name") String name,
                @Bind("summary") String summary);

    class UmbrellaStudyI18nDtoMapper implements RowMapper<StudyI18nDto> {
        @Override
        public StudyI18nDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new StudyI18nDto(rs.getString(SqlConstants.LanguageCodeTable.CODE),
                    rs.getString(SqlConstants.I18nUmbrellaStudyTable.NAME),
                    rs.getString(SqlConstants.I18nUmbrellaStudyTable.SUMMARY));
        }
    }
}
