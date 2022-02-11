package org.broadinstitute.dsm.db;

import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Data
public class LabelSettings {

    private static final Logger logger = LoggerFactory.getLogger(LabelSettings.class);

    private String labelSettingId;
    private String name;
    private String description;
    private boolean defaultPage;
    private int labelOnPage;
    private double labelHeight;
    private double labelWidth;
    private double topMargin;
    private double rightMargin;
    private double bottomMargin;
    private double leftMargin;
    private boolean deleted;

    public LabelSettings(String labelSettingId, String name, String description, boolean defaultPage, int labelOnPage,
                         double labelHeight, double labelWidth, double topMargin, double rightMargin,
                         double bottomMargin, double leftMargin) {
        this.labelSettingId = labelSettingId;
        this.name = name;
        this.description = description;
        this.defaultPage = defaultPage;
        this.labelOnPage = labelOnPage;
        this.labelHeight = labelHeight;
        this.labelWidth = labelWidth;
        this.topMargin = topMargin;
        this.rightMargin = rightMargin;
        this.bottomMargin = bottomMargin;
        this.leftMargin = leftMargin;
    }

    /**
     * Read labelSettings form label_settings table
     * @return List<LabelSettings>
     * @throws Exception
     */
    public static Collection<LabelSettings> getLabelSettings() {
        List<LabelSettings> labelSettings = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GET_LABEL_SETTINGS))) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        labelSettings.add(new LabelSettings(rs.getString(DBConstants.LABEL_SETTING_ID),
                                rs.getString(DBConstants.NAME),
                                rs.getString(DBConstants.DESCRIPTION),
                                rs.getBoolean(DBConstants.DEFAULT_PAGE),
                                rs.getInt(DBConstants.LABEL_ON_PAGE),
                                rs.getDouble(DBConstants.LABEL_HEIGHT),
                                rs.getDouble(DBConstants.LABEL_WIDTH),
                                rs.getDouble(DBConstants.TOP_MARGIN),
                                rs.getDouble(DBConstants.LEFT_MARGIN),
                                rs.getDouble(DBConstants.BOTTOM_MARGIN),
                                rs.getDouble(DBConstants.LEFT_MARGIN)));
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of label settings ", results.resultException);
        }
        logger.info("Found " + labelSettings.size() + " label settings ");
        return labelSettings;
    }

    /**
     * Save value to label_settings table
     * for the given labelSettingId
     * if labelSettingId is blank, insert labelSetting
     * else update labelSetting with given labelSettingId
     */
    public static void saveLabelSettings(@NonNull LabelSettings[] labelSettings) {
        for (LabelSettings labelSetting : labelSettings) {
            if (StringUtils.isNotBlank(labelSetting.getName())) {
                String labelSettingId = labelSetting.getLabelSettingId();
                if (StringUtils.isNotBlank(labelSettingId)) {
                    updateLabelSetting(labelSettingId, labelSetting);
                }
                else {
                    addLabelSetting(labelSetting);
                }
            }
        }
    }

    private static void updateLabelSetting(@NonNull String labelSettingId, @NonNull LabelSettings labelSetting) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.UPDATE_LABEL_SETTINGS))) {
                stmt.setString(1, labelSetting.getName());
                stmt.setString(2, labelSetting.getDescription());
                stmt.setBoolean(3, labelSetting.isDefaultPage());
                stmt.setInt(4, labelSetting.getLabelOnPage());
                stmt.setDouble(5, labelSetting.getLabelHeight());
                stmt.setDouble(6, labelSetting.getLabelWidth());
                stmt.setDouble(7, labelSetting.getTopMargin());
                stmt.setDouble(8, labelSetting.getLeftMargin());
                stmt.setDouble(9, labelSetting.getBottomMargin());
                stmt.setDouble(10, labelSetting.getRightMargin());
                stmt.setBoolean(11, labelSetting.isDeleted());
                stmt.setString(12, labelSettingId);
                int result = stmt.executeUpdate();
                if (result == 1) {
                    logger.info("Updated labelSetting w/ id " + labelSettingId);
                }
                else {
                    throw new RuntimeException("Error updating labelSetting w/ id " + labelSettingId + " it was updating " + result + " rows");
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error saving labelSetting w/ id " + labelSettingId, results.resultException);
        }
    }

    private static void addLabelSetting(@NonNull LabelSettings labelSetting) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.INSERT_LABEL_SETTINGS))) {
                stmt.setString(1, labelSetting.getName());
                stmt.setString(2, labelSetting.getDescription());
                stmt.setBoolean(3, labelSetting.isDefaultPage());
                stmt.setInt(4, labelSetting.getLabelOnPage());
                stmt.setDouble(5, labelSetting.getLabelHeight());
                stmt.setDouble(6, labelSetting.getLabelWidth());
                stmt.setDouble(7, labelSetting.getTopMargin());
                stmt.setDouble(8, labelSetting.getLeftMargin());
                stmt.setDouble(9, labelSetting.getBottomMargin());
                stmt.setDouble(10, labelSetting.getRightMargin());
                int result = stmt.executeUpdate();
                if (result == 1) {
                    logger.info("Added new labelSetting ");
                }
                else {
                    throw new RuntimeException("Error adding new labelSetting, it was updating " + result + " rows");
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error adding new labelSetting", results.resultException);
        }
    }
}
