package org.broadinstitute.dsm.db;

import lombok.Data;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.statics.DBConstants;

@Data
@TableName(name = DBConstants.DDP_KIT, alias = "kit",
        primaryKey = DBConstants.DSM_KIT_REQUEST_ID, columnPrefix = "")
public class DDPKitDto {
    @ColumnName(DBConstants.COLLECTION_DATE)
    private String collectionDate;

    @ColumnName(DBConstants.SEQUENCING_RESTRICTION)
    private String sequencingRestriction;

}
