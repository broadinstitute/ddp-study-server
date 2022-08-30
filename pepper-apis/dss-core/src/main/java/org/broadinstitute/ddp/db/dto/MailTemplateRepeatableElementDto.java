package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@SuperBuilder(toBuilder = true)
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class MailTemplateRepeatableElementDto {
    @ColumnName("mail_template_repeatable_element_id")
    long id;

    @ColumnName("mail_template_id")
    long mailTemplateId;

    @ColumnName("name")
    String name;
    
    @ColumnName("content")
    String content;
}
