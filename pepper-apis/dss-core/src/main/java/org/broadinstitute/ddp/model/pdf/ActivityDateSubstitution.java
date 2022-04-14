package org.broadinstitute.ddp.model.pdf;

import static org.broadinstitute.ddp.model.pdf.SubstitutionType.ACTIVITY_DATE;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public final class ActivityDateSubstitution extends PdfSubstitution {

    private long activityId;

    @JdbiConstructor
    public ActivityDateSubstitution(@ColumnName("pdf_substitution_id") long id,
                                    @ColumnName("pdf_template_id") long templateId,
                                    @ColumnName("placeholder") String placeholder,
                                    @ColumnName("activity_id") long activityId) {
        super(id, templateId, ACTIVITY_DATE, placeholder);
        this.activityId = activityId;
    }

    public ActivityDateSubstitution(String placeholder, long activityId) {
        super(ACTIVITY_DATE, placeholder);
        this.activityId = activityId;
    }

    public long getActivityId() {
        return activityId;
    }
}
