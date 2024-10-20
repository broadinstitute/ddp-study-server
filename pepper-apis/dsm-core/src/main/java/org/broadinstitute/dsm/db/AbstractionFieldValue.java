package org.broadinstitute.dsm.db;

import lombok.Data;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.AbstractionUtil;

@Data
@TableName(
        name = DBConstants.MEDICAL_RECORD_ABSTRACTION,
        alias = "",
        primaryKey = DBConstants.MEDICAL_RECORD_ABSTRACTION_ID,
        columnPrefix = AbstractionUtil.ACTIVITY_ABSTRACTION)
public class AbstractionFieldValue {

    @ColumnName(DBConstants.MEDICAL_RECORD_ABSTRACTION_FIELD_ID)
    private final Integer medicalRecordAbstractionFieldId;
    @ColumnName(DBConstants.PARTICIPANT_ID)
    private final String participantId;
    @ColumnName(DBConstants.VALUE)
    private final String value;
    @ColumnName(DBConstants.VALUE_CHANGED_COUNTER)
    private final int valueCounter;
    @ColumnName(DBConstants.NOTE)
    private final String note;
    @ColumnName(DBConstants.QUESTION)
    private final String question;
    @ColumnName(DBConstants.FILE_PAGE)
    private final String filePage;
    @ColumnName(DBConstants.FILE_NAME)
    private final String fileName;
    @ColumnName(DBConstants.MATCH_PHRASE)
    private final String matchPhrase;
    @ColumnName(DBConstants.DOUBLE_CHECK)
    private final boolean doubleCheck;
    @ColumnName(DBConstants.NO_DATA)
    private final boolean noData;
    private Integer primaryKeyId;

    public AbstractionFieldValue(Integer primaryKeyId, Integer medicalRecordAbstractionFieldId, String participantId,
                                 String value, int valueCounter, String note, String question, String filePage, String fileName,
                                 String matchPhrase,
                                 boolean doubleCheck, boolean noData) {
        this.primaryKeyId = primaryKeyId;
        this.medicalRecordAbstractionFieldId = medicalRecordAbstractionFieldId;
        this.participantId = participantId;
        this.value = value;
        this.valueCounter = valueCounter;
        this.note = note;
        this.question = question;
        this.filePage = filePage;
        this.fileName = fileName;
        this.matchPhrase = matchPhrase;
        this.doubleCheck = doubleCheck;
        this.noData = noData;
    }
}
