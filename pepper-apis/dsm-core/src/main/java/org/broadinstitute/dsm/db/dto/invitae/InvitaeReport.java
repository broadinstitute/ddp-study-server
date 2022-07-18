package org.broadinstitute.dsm.db.dto.invitae;


import lombok.Data;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.statics.DBConstants;

@Data
@TableName(
        name = DBConstants.INVITAE_REPORT,
        alias = DBConstants.INVITAE_ALIAS,
        primaryKey = DBConstants.INVITAE_REPORT_ID,
        columnPrefix = "")
public class InvitaeReport {
    @ColumnName(DBConstants.INVITAE_REPORT_ID)
    private String invitaeReportID;
    @ColumnName(DBConstants.INVITAE_REPORT_DATE)
    private String invitaeReportDate;
    @ColumnName(DBConstants.INVITAE_BAM_FILE)
    private String invitaeBamFile;
    @ColumnName(DBConstants.INVITAE_BAM_FILE_DATE)
    private String invitaeBamFileDate;
    @ColumnName(DBConstants.INVITAE_GERMLINE_NOTES)
    private String invitaeGermlineNotes;

}
