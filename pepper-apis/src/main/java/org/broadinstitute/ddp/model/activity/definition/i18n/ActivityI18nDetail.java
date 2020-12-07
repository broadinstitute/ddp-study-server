package org.broadinstitute.ddp.model.activity.definition.i18n;

import java.util.Objects;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class ActivityI18nDetail {

    private final long id;
    private final long activityId;
    private final long langCodeId;
    private final String isoLangCode;
    private final String name;
    private final String secondName;
    private final String title;
    private final String subtitle;
    private final String description;
    private final long revisionId;

    @JdbiConstructor
    public ActivityI18nDetail(
            @ColumnName("i18n_activity_detail_id") long id,
            @ColumnName("study_activity_id") long activityId,
            @ColumnName("language_code_id") long langCodeId,
            @ColumnName("iso_language_code") String isoLangCode,
            @ColumnName("name") String name,
            @ColumnName("second_name") String secondName,
            @ColumnName("title") String title,
            @ColumnName("subtitle") String subtitle,
            @ColumnName("description") String description,
            @ColumnName("revision_id") long revisionId) {
        this.id = id;
        this.activityId = activityId;
        this.langCodeId = langCodeId;
        this.isoLangCode = isoLangCode;
        this.name = name;
        this.secondName = secondName;
        this.title = title;
        this.subtitle = subtitle;
        this.description = description;
        this.revisionId = revisionId;
    }

    // For inserting new activity details.
    public ActivityI18nDetail(long activityId, String isoLangCode, String name, String secondName,
                              String title, String subtitle, String description, long revisionId) {
        this.id = 0L;
        this.activityId = activityId;
        this.langCodeId = 0L;
        this.isoLangCode = isoLangCode;
        this.name = name;
        this.secondName = secondName;
        this.title = title;
        this.subtitle = subtitle;
        this.description = description;
        this.revisionId = revisionId;
    }

    public long getId() {
        return id;
    }

    public long getActivityId() {
        return activityId;
    }

    public long getLangCodeId() {
        return langCodeId;
    }

    public String getIsoLangCode() {
        return isoLangCode;
    }

    public String getName() {
        return name;
    }

    public String getSecondName() {
        return secondName;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getDescription() {
        return description;
    }

    public long getRevisionId() {
        return revisionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ActivityI18nDetail that = (ActivityI18nDetail) o;
        return id == that.id
                && activityId == that.activityId
                && langCodeId == that.langCodeId
                && revisionId == that.revisionId
                && Objects.equals(isoLangCode, that.isoLangCode)
                && Objects.equals(name, that.name)
                && Objects.equals(secondName, that.secondName)
                && Objects.equals(title, that.title)
                && Objects.equals(subtitle, that.subtitle)
                && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, activityId, langCodeId, revisionId, isoLangCode, name, secondName, title, subtitle, description);
    }
}
