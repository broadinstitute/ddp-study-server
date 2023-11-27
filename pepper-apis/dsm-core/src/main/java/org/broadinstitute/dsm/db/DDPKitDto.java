package org.broadinstitute.dsm.db;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.DbDateConversion;
import org.broadinstitute.dsm.db.structure.SqlDateConverter;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.statics.DBConstants;

@Data
@TableName(name = DBConstants.DDP_KIT, alias = DBConstants.DDP_KIT_ALIAS,
        primaryKey = DBConstants.DSM_KIT_REQUEST_ID, columnPrefix = "")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DDPKitDto {
    @ColumnName(DBConstants.COLLECTION_DATE)
    @DbDateConversion(SqlDateConverter.STRING_DAY)
    private String collectionDate;

    @ColumnName(DBConstants.SEQUENCING_RESTRICTION)
    private String sequencingRestriction;

    @ColumnName(DBConstants.SAMPLE_NOTES)
    private String sampleNotes;

}
