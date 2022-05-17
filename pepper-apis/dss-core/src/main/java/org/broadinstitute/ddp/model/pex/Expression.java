package org.broadinstitute.ddp.model.pex;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class Expression {
    @ColumnName("expression_id")
    long id;

    @ColumnName("expression_guid")
    String guid;

    @ColumnName("expression_text")
    String text;
}
