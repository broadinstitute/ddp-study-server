package org.broadinstitute.ddp.model.activity.definition.i18n;

import java.util.Objects;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.util.MiscUtil;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class SummaryTranslation extends Translation {

    @NotNull
    @SerializedName("statusCode")
    private InstanceStatusType statusType;

    private transient long activityId;

    @JdbiConstructor
    public SummaryTranslation(
            @ColumnName("i18n_study_activity_summary_trans_id") long id,
            @ColumnName("study_activity_id") long activityId,
            @ColumnName("instance_status_type") InstanceStatusType statusType,
            @ColumnName("iso_language_code") String languageCode,
            @ColumnName("translation_text") String text) {
        super(id, languageCode, text, null);
        this.activityId = activityId;
        this.statusType = statusType;
    }

    public SummaryTranslation(
            String languageCode,
            String text,
            InstanceStatusType statusType
    ) {
        super(languageCode, text);
        this.statusType = MiscUtil.checkNonNull(statusType, "statusType");
    }

    public long getActivityId() {
        return activityId;
    }

    public InstanceStatusType getStatusType() {
        return statusType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SummaryTranslation that = (SummaryTranslation) o;
        return Objects.equals(id, that.id)
                && activityId == that.activityId
                && statusType == that.statusType
                && Objects.equals(languageCode, that.languageCode)
                && Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, activityId, statusType, languageCode, text);
    }
}
