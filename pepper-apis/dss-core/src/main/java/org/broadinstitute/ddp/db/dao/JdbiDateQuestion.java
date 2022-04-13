package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiDateQuestion extends SqlObject {

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

}
