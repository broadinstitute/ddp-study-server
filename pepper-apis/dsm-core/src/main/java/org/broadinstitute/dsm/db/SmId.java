package org.broadinstitute.dsm.db;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.ddp.tissue.TissueSMIDDao;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.exception.DuplicateException;
import org.broadinstitute.dsm.model.NameValue;
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
    private Long tissueId;
    @ColumnName(DBConstants.SM_ID_PK)
    private Integer smIdPk;
    @ColumnName(DBConstants.DELETED)
    private Boolean deleted;

    public SmId() {
    }

    public SmId(Integer smIdPk, String smIdType, String smIdValue, Long tissueId) {
        this.smIdPk = smIdPk;
        this.smIdType = smIdType;
        this.smIdValue = smIdValue;
        this.tissueId = tissueId;
    }

    public SmId(Integer smIdPk, String smType, String smIdValue, Integer tissueId, Boolean deleted) {
        this(smIdPk, smType, smIdValue, tissueId.longValue());
        this.deleted = deleted;
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
                    rs.getLong("sm." + DBConstants.TISSUE_ID)
            );
            if (tissueSmId != null) {
                tissueSmId.setDeleted(rs.getBoolean("sm." + DBConstants.DELETED));
            }
        } catch (SQLException e) {
            logger.error("problem getting tissue sm ids", e);
        }
        return tissueSmId;
    }

    public static boolean isUniqueSmId(String smIdValue, String id) {
        return new TissueSMIDDao().isUnique(smIdValue, id);
    }

    public static boolean isUniqueSmId(String smIdValue) {
        return new TissueSMIDDao().isUnique(smIdValue);
    }

    public String createNewSmId(@NonNull String tissueId, String userId, @NonNull List<NameValue> smIdDetails) {
        String smIdType = null;
        String smIdValue = null;
        for (NameValue nameValue : smIdDetails) {
            if (nameValue.getName().equals("smIdType")) {
                smIdType = String.valueOf(nameValue.getValue());
            } else if (nameValue.getName().equals("smIdValue")) {
                smIdValue = String.valueOf(nameValue.getValue());
            }
        }
        if (StringUtils.isNotBlank(smIdValue) && this.isUniqueSmId(smIdValue)) {
            String smIdId = new TissueSMIDDao().createNewSMIDForTissue(tissueId, userId, smIdType, smIdValue);
            return smIdId;
        } else {
            throw new DuplicateException("Duplicate or blank value for sm id value " + smIdValue);
        }
    }
}
