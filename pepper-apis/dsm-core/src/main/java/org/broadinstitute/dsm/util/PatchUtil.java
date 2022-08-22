package org.broadinstitute.dsm.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.AbstractionActivity;
import org.broadinstitute.dsm.db.AbstractionFieldValue;
import org.broadinstitute.dsm.db.AbstractionQCValue;
import org.broadinstitute.dsm.db.AbstractionReviewValue;
import org.broadinstitute.dsm.db.ClinicalOrder;
import org.broadinstitute.dsm.db.DDPKitDto;
import org.broadinstitute.dsm.db.Drug;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.ParticipantData;
import org.broadinstitute.dsm.db.SmId;
import org.broadinstitute.dsm.db.Tissue;
import org.broadinstitute.dsm.db.ViewFilter;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantRecordDto;
import org.broadinstitute.dsm.db.dto.onchistory.OncHistoryDto;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.db.structure.DbDateConversion;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.model.KitRequest;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PatchUtil {

    private static final Logger logger = LoggerFactory.getLogger(PatchUtil.class);

    private static Map<String, DBElement> columnNameMap;
    private static Map<String, String> dataBaseMap;
    private static Set<String> tableAliases = new HashSet<>();

    static {
        columnNameMap = new HashMap<>();
        dataBaseMap = new HashMap<>();
        getColumnNames(Participant.class);
        getColumnNames(MedicalRecord.class);
        getColumnNames(OncHistoryDetail.class);
        getColumnNames(OncHistoryDto.class);
        getColumnNames(Tissue.class);
        getColumnNames(AbstractionFieldValue.class);
        getColumnNames(AbstractionReviewValue.class);
        getColumnNames(AbstractionQCValue.class);
        getColumnNames(AbstractionActivity.class);
        getColumnNames(ViewFilter.class);
        getColumnNames(KitRequestShipping.class);
        getColumnNames(DDPKitDto.class);
        getColumnNames(KitRequest.class);
        getColumnNames(Drug.class);
        getColumnNames(ParticipantData.class);
        getColumnNames(ParticipantRecordDto.class);
        getColumnNames(SmId.class);
        getColumnNames(CohortTag.class);
        getColumnNames(ClinicalOrder.class);
        logger.info("Loaded patch utils");
    }

    public PatchUtil() {
    }

    public static Map<String, DBElement> getColumnNameMap() {
        if (columnNameMap != null && !columnNameMap.isEmpty()) {
            return columnNameMap;
        }
        return null;
    }

    public static Set<String> getTableAliasArray() {
        if (tableAliases != null && !tableAliases.isEmpty()) {
            return tableAliases;
        }
        return null;
    }

    public static Map<String, String> getDataBaseMap() {
        if (dataBaseMap != null && !dataBaseMap.isEmpty()) {
            return dataBaseMap;
        }
        return null;
    }

    private static void getColumnNames(Class<?> obj) {
        String tableName = null;
        String tableAlias = null;
        String primaryKey = null;
        String columnPrefix = null;

        if (obj.isAnnotationPresent(TableName.class)) {
            Annotation annotation = obj.getAnnotation(TableName.class);
            TableName tableNameAnnotation = (TableName) annotation;
            tableName = tableNameAnnotation.name();
            tableAlias = tableNameAnnotation.alias();
            primaryKey = tableNameAnnotation.primaryKey();
            columnPrefix = tableNameAnnotation.columnPrefix();
        }

        boolean tableNamePerClass = tableName != null ? true : false;
        Field[] fields = obj.getDeclaredFields();
        for (Field field : fields) {
            if (!tableNamePerClass) {
                TableName[] tableNames = field.getAnnotationsByType(TableName.class);
                for (TableName table : tableNames) {
                    tableName = table.name();
                    tableAlias = table.alias();
                    primaryKey = table.primaryKey();
                    columnPrefix = table.columnPrefix();
                }
            }

            ColumnName[] columnName = field.getAnnotationsByType(ColumnName.class);
            if (columnName.length > 0) {
                for (ColumnName column : columnName) {
                    String nameKey = column.value();
                    String fieldKey = field.getName();
                    if (StringUtils.isNotBlank(tableAlias)) {
                        fieldKey = tableAlias.concat(DBConstants.ALIAS_DELIMITER).concat(field.getName());
                        nameKey = tableAlias.concat(DBConstants.ALIAS_DELIMITER).concat(column.value());
                        tableAliases.add(tableAlias);
                    }
                    if (StringUtils.isNotBlank(columnPrefix)) {
                        fieldKey = columnPrefix.concat("_").concat(field.getName());
                    }

                    columnNameMap.put(fieldKey,
                            new DBElement(tableName, tableAlias, primaryKey, column.value(), field.getAnnotation(DbDateConversion.class)));
                    dataBaseMap.put(nameKey, field.getName());
                }
            }
        }
    }
}
