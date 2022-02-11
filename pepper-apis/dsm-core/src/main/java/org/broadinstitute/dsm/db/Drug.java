package org.broadinstitute.dsm.db;

import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Data
@TableName (
        name = DBConstants.DRUG_LIST,
        alias = DBConstants.DRUG_ALIAS,
        primaryKey = DBConstants.DRUG_ID,
        columnPrefix = "")
public class Drug {

    private static final Logger logger = LoggerFactory.getLogger(Drug.class);

    private static final String SQL_SELECT_DRUGS = "SELECT display_name FROM drug_list ORDER BY display_name asc";
    private static final String SQL_SELECT_DRUGS_ALL_INFO = "SELECT drug_id, display_name, generic_name, brand_name, chemocat2, chemo_type, study_drug, " +
            "treatment_type, chemotherapy, active FROM drug_list ORDER BY display_name asc";
    private static final String SQL_UPDATE_DRUG = "UPDATE drug_list SET display_name = ?, generic_name = ?, brand_name = ?, chemocat2 = ?, chemo_type = ?, " +
            "study_drug = ?, treatment_type = ?, chemotherapy = ?, active = ?, date_updated = ?, changed_by = ? WHERE drug_id = ?";
    private static final String SQL_INSERT_DRUG = "INSERT INTO drug_list SET display_name = ?, generic_name = ?, brand_name = ?, chemocat2 = ?, " +
            "chemo_type = ?, study_drug = ?, treatment_type = ?, chemotherapy = ?, date_created = ?, active = ?, changed_by = ?";

    @ColumnName (DBConstants.DRUG_ID)
    private final int drugId;

    @ColumnName (DBConstants.DISPLAY_NAME)
    private String displayName;

    @ColumnName (DBConstants.GENERIC_NAME)
    private String genericName;

    @ColumnName (DBConstants.BRAND_NAME)
    private String brandName;

    @ColumnName (DBConstants.CHEMOCAT)
    private String chemocat;

    @ColumnName (DBConstants.CHEMO_TYPE)
    private String chemoType;

    @ColumnName (DBConstants.STUDY_DRUG)
    private boolean studyDrug;

    @ColumnName (DBConstants.TREATMENT_TYPE)
    private String treatmentType;

    @ColumnName (DBConstants.CHEMOTHERAPY)
    private String chemotherapy;

    @ColumnName (DBConstants.ACTIVE)
    private boolean active;

    private String changedBy;


    public Drug(int drugId, String displayName, String genericName, String brandName, String chemocat, String chemoType,
                boolean studyDrug, String treatmentType, String chemotherapy, boolean active) {
        this.drugId = drugId;
        this.displayName = displayName;
        this.genericName = genericName;
        this.brandName = brandName;
        this.chemocat = chemocat;
        this.chemoType = chemoType;
        this.studyDrug = studyDrug;
        this.treatmentType = treatmentType;
        this.chemotherapy = chemotherapy;
        this.active = active;
    }

    // Display names only (original method to show in MBC followup survey)
    public static List<String> getDrugList() {
        List<String> drugList = new ArrayList<>();

        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();

            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_DRUGS)) {
                if (stmt != null) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            drugList.add(rs.getString(DBConstants.DISPLAY_NAME));
                        }
                    }
                }
                else {
                    throw new RuntimeException("Drug list is empty");
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting drug list ", results.resultException);
        }
        logger.info("Drug list has " + drugList.size() + " drugs");

        return drugList;
    }

    public static List<Drug> getDrugListALL() {
        List<Drug> drugList = new ArrayList<>();

        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_DRUGS_ALL_INFO)) {
                if (stmt != null) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            Drug drug = new Drug(rs.getInt(DBConstants.DRUG_ID),
                                    rs.getString(DBConstants.DISPLAY_NAME),
                                    rs.getString(DBConstants.GENERIC_NAME),
                                    rs.getString(DBConstants.BRAND_NAME),
                                    rs.getString(DBConstants.CHEMOCAT),
                                    rs.getString(DBConstants.CHEMO_TYPE),
                                    rs.getBoolean(DBConstants.STUDY_DRUG),
                                    rs.getString(DBConstants.TREATMENT_TYPE),
                                    rs.getString(DBConstants.CHEMOTHERAPY),
                                    rs.getBoolean(DBConstants.ACTIVE));
                            drugList.add(drug);
                        }
                    }
                }
                else {
                    throw new RuntimeException("Drug list is empty");
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting drug list entries ", results.resultException);
        }
        logger.info("Drug list has " + drugList.size() + " drugs");

        return drugList;
    }

    public static void addDrug(@NonNull String user, @NonNull Drug newDrugEntry) {
        Long now = System.currentTimeMillis() / 1000; //druglist has date as epoch

        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_DRUG)) {
                stmt.setString(1, newDrugEntry.getDisplayName());
                stmt.setString(2, newDrugEntry.getGenericName());
                stmt.setString(3, newDrugEntry.getBrandName());
                stmt.setString(4, newDrugEntry.getChemocat());
                stmt.setString(5, newDrugEntry.getChemoType());
                stmt.setBoolean(6, newDrugEntry.isStudyDrug());
                stmt.setString(7, newDrugEntry.getTreatmentType());
                stmt.setString(8, newDrugEntry.getChemotherapy());
                stmt.setLong(9, now);
                stmt.setBoolean(10, newDrugEntry.isActive());
                stmt.setString(11, user);
                int result = stmt.executeUpdate();
                if (result == 1) {
                    logger.info("Added new drug ");
                }
                else {
                    throw new RuntimeException("Error adding new drug, it was updating " + result + " rows");
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error saving drug: " + newDrugEntry.getDisplayName(), results.resultException);
        }
    }
}
