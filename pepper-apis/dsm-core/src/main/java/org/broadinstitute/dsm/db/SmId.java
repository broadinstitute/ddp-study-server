package org.broadinstitute.dsm.db;

import java.sql.ResultSet;
import java.sql.SQLException;

import lombok.Data;
import org.broadinstitute.dsm.db.dao.ddp.tissue.TissueSMIDDao;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
@TableName(
        name = DBConstants.SM_ID_TABLE,
        alias = DBConstants.SM_ID_TABLE_ALIAS,
        primaryKey = DBConstants.SM_ID_PK,
        columnPrefix = "")
public class SmId {

    private static final Logger logger = LoggerFactory.getLogger(SmId.class);
    public static String HE = "he";
    public static String USS = "uss";
    public static String SCROLLS = "scrolls";
    @ColumnName(DBConstants.SM_ID_VALUE)
    private String smIdValue;
    @ColumnName(DBConstants.SM_ID_TYPE)
    private String smIdType;
    @ColumnName(DBConstants.SM_ID_TISSUE_ID)
    private int tissueId;
    @ColumnName(DBConstants.SM_ID_PK)
    private int smIdPk;

    // although the database does not have this column, it is required as a field
    // for proper processing of delete request from the frontend
    @ColumnName(DBConstants.DELETED)
    private Boolean deleted;

    public SmId() {
    }

    public SmId(Integer smIdPk, String smIdType, String smIdValue, Integer tissueId) {
        this.smIdPk = smIdPk;
        this.smIdType = smIdType;
        this.smIdValue = smIdValue;
        this.tissueId = tissueId;
    }

    public SmId(int smIdPk, String smIdValue, int tissueId) {
        this.smIdPk = smIdPk;
        this.smIdValue = smIdValue;
        this.tissueId = tissueId;
    }

    public static SmId getSMIdsForTissueId(ResultSet rs) {
        SmId tissueSmId = null;

        try {
            if (rs.getString(DBConstants.SM_ID_PK) == null) {
                return null;
            }
            tissueSmId = new SmId(
                    rs.getInt(DBConstants.SM_ID_PK),
                    rs.getString(DBConstants.SM_ID_TYPE_ID),
                    rs.getString(DBConstants.SM_ID_VALUE),
                    rs.getInt("sm." + DBConstants.TISSUE_ID)
            );
        } catch (SQLException e) {
            logger.error("problem getting tissue sm ids", e);
        }
        return tissueSmId;
    }

    public static boolean isUniqueSmId(String smIdValue) {
        return new TissueSMIDDao().isUnique(smIdValue);
    }

}
