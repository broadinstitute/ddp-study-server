package org.broadinstitute.ddp.model.event;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class NotificationTemplate {

    private long id;
    private String templateKey;
    private boolean isDynamicTemplate;
    private long languageId;
    private String languageCode;

    @JdbiConstructor
    public NotificationTemplate(
            @ColumnName("notification_template_id") long id,
            @ColumnName("template_key") String templateKey,
            @ColumnName("is_dynamic") boolean isDynamic,
            @ColumnName("language_code_id") long languageId,
            @ColumnName("iso_language_code") String languageCode) {
        this.id = id;
        this.templateKey = templateKey;
        this.isDynamicTemplate = isDynamic;
        this.languageId = languageId;
        this.languageCode = languageCode;
    }

    public long getId() {
        return id;
    }

    public String getTemplateKey() {
        return templateKey;
    }

    public long getLanguageId() {
        return languageId;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public boolean isDynamicTemplate() {
        return isDynamicTemplate;
    }
}
