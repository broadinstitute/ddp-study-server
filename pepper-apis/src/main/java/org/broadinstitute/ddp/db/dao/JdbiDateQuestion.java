package org.broadinstitute.ddp.db.dao;

import static org.broadinstitute.ddp.constants.SqlConstants.DateQuestionMonthPicklistTable;
import static org.broadinstitute.ddp.constants.SqlConstants.DateQuestionYearPicklistTable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.broadinstitute.ddp.db.dto.DateQuestionDto;
import org.broadinstitute.ddp.model.activity.definition.question.DatePicklistDef;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiDateQuestion extends SqlObject {

    @UseStringTemplateSqlLocator
    @SqlQuery("queryDtoByQuestionId")
    @RegisterConstructorMapper(DateQuestionDto.class)
    Optional<DateQuestionDto> findDtoByQuestionId(@Bind("questionId") long questionId);

    @UseStringTemplateSqlLocator
    @SqlUpdate("insert")
    int insert(@Bind("questionId") long questionId, @Bind("renderModeId") long renderModeId,
               @Bind("displayCalendar") boolean displayCalendar, @Bind("placeholderTemplateId") Long placeholderTemplateId);

    @SqlUpdate("UPDATE date_question as dq "
            + "INNER JOIN date_render_mode AS mode ON mode.date_render_mode_code = :dateRenderMode "
            + "SET dq.date_render_mode_id = mode.date_render_mode_id, "
            + "     dq.display_calendar = :shouldDisplayCalendar, "
            + "     dq.placeholder_template_id = :placeholderTemplateId "
            + "WHERE dq.question_id = :questionId")
    boolean update(@Bind("questionId") long questionId,
                    @Bind("dateRenderMode") DateRenderMode inputType,
                    @Bind("shouldDisplayCalendar") boolean suggestionType,
                    @Bind("placeholderTemplateId") Long placeholderTemplateId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryDatePicklistQuestionInfoByQuestionId")
    @RegisterRowMapper(DatePicklistDefMapper.class)
    Optional<DatePicklistDef> getDatePicklistDefByQuestionId(long questionId);

    class DatePicklistDefMapper implements RowMapper<DatePicklistDef> {
        @Override
        public DatePicklistDef map(ResultSet rs, StatementContext ctx) throws SQLException {
            Boolean useMonthNames = null;
            Integer yearsForward = null;
            Integer yearsBack = null;
            Integer yearAnchor = null;
            Integer firstSelectedYear = null;

            // Grab month picklist data if available
            Long monthIndicator = (Long) rs.getObject("dqmp_id");
            if (monthIndicator != null) {
                useMonthNames = rs.getBoolean(DateQuestionMonthPicklistTable.USE_MONTH_NAMES);
            }

            // Grab year picklist data if available
            Long yearIndicator = (Long) rs.getObject("dqyp_id");
            if (yearIndicator != null) {
                yearsForward = (Integer) rs.getObject(DateQuestionYearPicklistTable.YEARS_FORWARD);
                yearsBack = (Integer) rs.getObject(DateQuestionYearPicklistTable.YEARS_BACK);
                yearAnchor = (Integer) rs.getObject(DateQuestionYearPicklistTable.YEAR_ANCHOR);
                firstSelectedYear = (Integer) rs.getObject(DateQuestionYearPicklistTable.FIRST_SELECTED_YEAR);
            }

            return new DatePicklistDef(useMonthNames, yearsForward, yearsBack, yearAnchor, firstSelectedYear);
        }
    }
}
