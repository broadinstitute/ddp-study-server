package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@SuperBuilder(toBuilder = true)
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class MailTemplateDto {
    @ColumnName("mail_template_id")
    long id;

    @ColumnName("content_type")
    String contentType;

    @ColumnName("subject")
    String subject;

    @ColumnName("body")
    String body;
}
