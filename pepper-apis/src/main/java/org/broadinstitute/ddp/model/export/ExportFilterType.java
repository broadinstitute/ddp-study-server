package org.broadinstitute.ddp.model.export;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class ExportFilterType {

  private long id;
  private String type;

  @JdbiConstructor
  public ExportFilterType(@ColumnName("export_filter_type_id") long id,
                          @ColumnName("export_filter_type") String type) {
    this.id = id;
    this.type = type;
  }

  public ExportFilterType(String type) {
    this.type = type;
  }

  public long getId() {
    return id;
  }

  public String getType() {
    return type;
  }
}
