package org.broadinstitute.dsm.db.dto.mercury;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.broadinstitute.dsm.db.dao.mercury.ClinicalOrderDao;
import org.broadinstitute.dsm.statics.DBConstants;

import java.sql.ResultSet;
import java.sql.SQLException;

@AllArgsConstructor
@Getter
public class ClinicalOrderDto {

    @SerializedName("shortId")
    String shortId;

    @SerializedName("smId")
    String sample;

    @SerializedName("orderId")
    String orderId;

    @SerializedName("orderStatus")
    String orderStatus;

    @SerializedName("orderedAtMillis")
    public long orderDate;

    @SerializedName("statusDateMillis")
    long statusDate;

    @SerializedName("statusDetail")
    String statusDetail;

    @SerializedName("sampleType")
    String sampleType;

    int mercurySequencingId;

    /**
     * Create a new one from the given result set
     */
    public static ClinicalOrderDto fromResultSet(ResultSet rs) throws SQLException {
        return new ClinicalOrderDto(
                ClinicalOrderDao.createShortIdFromCollaboratorSampleId(rs.getString(DBConstants.COLLABORATOR_SAMPLE_ID)),
                rs.getString(DBConstants.COLLABORATOR_SAMPLE_ID),
                        rs.getString(DBConstants.MERCURY_ORDER_ID), rs.getString(DBConstants.MERCURY_ORDER_STATUS),
                rs.getLong(DBConstants.MERCURY_ORDER_DATE), rs.getLong(DBConstants.MERCURY_STATUS_DATE),
                rs.getString(DBConstants.MERCURY_STATUS_DETAIL), rs.getString(DBConstants.MERCURY_SAMPLE_TYPE),
                rs.getInt(DBConstants.MERCURY_SEQUENCING_ID));
    }

}
