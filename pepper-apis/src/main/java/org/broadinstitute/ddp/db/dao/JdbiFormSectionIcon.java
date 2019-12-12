package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.definition.SectionIcon;
import org.broadinstitute.ddp.model.activity.types.FormSectionState;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiFormSectionIcon extends SqlObject {

    @SqlUpdate("insert into form_section_icon"
            + " (form_section_id, form_section_state_id, height_points, width_points) values (:sectionId,"
            + " (select form_section_state_id from form_section_state where form_section_state_code = :state), :height, :width)")
    @GetGeneratedKeys
    long insert(@Bind("sectionId") long sectionId, @Bind("state") FormSectionState state,
                @Bind("height") int heightInPoints, @Bind("width") int widthInPoints);

    default long insert(long sectionId, SectionIcon icon) {
        long iconId = insert(sectionId, icon.getState(), icon.getHeight(), icon.getWidth());
        icon.setIconId(iconId);
        return iconId;
    }
}
