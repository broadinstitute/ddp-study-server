package org.broadinstitute.dsm.model;

import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.ddp.handlers.util.ParticipantSurveyInfo;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.exception.FileColumnMissing;
import org.broadinstitute.dsm.exception.UploadLineException;
import org.broadinstitute.dsm.util.SystemUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Data
public class ParticipantSurveyStatusResponse {

    private static final Logger logger = LoggerFactory.getLogger(ParticipantSurveyStatusResponse.class);

    private static final String DDP_PARTICIPANT_ID = "participantId";
    private static final String SHORT_ID = "shortId";
    private static final String FIRST_NAME = "firstName";
    private static final String LAST_NAME = "lastName";
    private static final String EMAIL = "email";

    private final ParticipantSurveyInfo surveyInfo;
    private String reason;
    private String user;
    private long triggeredDate;

    public ParticipantSurveyStatusResponse(@NonNull ParticipantSurveyInfo surveyInfo) {
        this.surveyInfo = surveyInfo;
    }

    public static List<ParticipantSurveyUploadObject> isFileValid(@NonNull DDPInstance instance, @NonNull String fileContent){
        if (fileContent != null) {
            String linebreak = SystemUtil.lineBreak(fileContent);
            String[] rows = fileContent.split(linebreak);
            if (rows.length > 1) {
                String firstRow = rows[0];
                List<String> fieldNames = new ArrayList<>(Arrays.asList(firstRow.trim().split(SystemUtil.SEPARATOR)));
                String missingFieldName = fieldNameMissing(instance, fieldNames);
                if (missingFieldName == null) {
                    List<ParticipantSurveyUploadObject> uploadObjects = new ArrayList<>();
                    for (int rowIndex = 1; rowIndex < rows.length; rowIndex++) {
                        Map<String, String> obj = new LinkedHashMap<>();
                        String[] row = rows[rowIndex].trim().split(SystemUtil.SEPARATOR);
                        if (row.length == fieldNames.size()) {
                            for (int columnIndex = 0; columnIndex < fieldNames.size(); columnIndex++) {
                                obj.put(fieldNames.get(columnIndex), row[columnIndex]);
                            }
                            try {
                                ParticipantSurveyUploadObject object;
                                if (instance.isHasRole()) {
                                    object = new ParticipantSurveyUploadObject(obj.get(SHORT_ID),
                                            obj.get(FIRST_NAME), obj.get(LAST_NAME), obj.get(EMAIL));
                                }
                                else {
                                    object = new ParticipantSurveyUploadObject(obj.get(DDP_PARTICIPANT_ID));
                                }
                                if (object != null) {
                                    uploadObjects.add(object);
                                }
                            }
                            catch (Exception e) {
                                throw new RuntimeException("Text file is not valid. Couldn't be parsed to upload object ", e);
                            }
                        }
                        else {
                            throw new UploadLineException("Error in line " + (rowIndex + 1));
                        }
                    }
                    logger.info(uploadObjects.size() + " participants were uploaded for followup surveys ");
                    return uploadObjects;
                }
                else {
                    throw new FileColumnMissing("File is missing column " + missingFieldName);
                }
            }
        }
        return null;
    }

    private static String fieldNameMissing(@NonNull DDPInstance instance, @NonNull List<String> fieldName) {
        if (instance.isHasRole()) {
            if (!fieldName.contains(SHORT_ID)) {
                return SHORT_ID;
            }
            if (!fieldName.contains(FIRST_NAME)) {
                return FIRST_NAME;
            }
            if (!fieldName.contains(LAST_NAME)) {
                return LAST_NAME;
            }
            if (!fieldName.contains(EMAIL)) {
                return EMAIL;
            }
        }
        else {
            if (!fieldName.contains(DDP_PARTICIPANT_ID)) {
                return DDP_PARTICIPANT_ID;
            }
        }
        return null;
    }
}
