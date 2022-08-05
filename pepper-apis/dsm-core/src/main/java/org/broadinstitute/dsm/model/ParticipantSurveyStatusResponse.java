package org.broadinstitute.dsm.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.dsm.exception.FileColumnMissing;
import org.broadinstitute.dsm.exception.UploadLineException;
import org.broadinstitute.dsm.util.SystemUtil;
import org.broadinstitute.lddp.handlers.util.ParticipantSurveyInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
public class ParticipantSurveyStatusResponse {

    private static final Logger logger = LoggerFactory.getLogger(ParticipantSurveyStatusResponse.class);

    private static final String DDP_PARTICIPANT_ID = "participantId";

    private final ParticipantSurveyInfo surveyInfo;
    private String reason;
    private String user;
    private long triggeredDate;

    public ParticipantSurveyStatusResponse(@NonNull ParticipantSurveyInfo surveyInfo) {
        this.surveyInfo = surveyInfo;
    }

    public static List<ParticipantSurveyUploadObject> isFileValid(@NonNull String fileContent) {
        if (fileContent != null) {
            String linebreak = SystemUtil.lineBreak(fileContent);
            String[] rows = fileContent.split(linebreak);
            if (rows.length > 1) {
                String firstRow = rows[0];
                List<String> fieldNames = new ArrayList<>(Arrays.asList(firstRow.trim().split(SystemUtil.TAB_SEPARATOR)));
                String missingFieldName = fieldNameMissing(fieldNames);
                if (missingFieldName == null) {
                    List<ParticipantSurveyUploadObject> uploadObjects = new ArrayList<>();
                    for (int rowIndex = 1; rowIndex < rows.length; rowIndex++) {
                        Map<String, String> obj = new LinkedHashMap<>();
                        String[] row = rows[rowIndex].trim().split(SystemUtil.TAB_SEPARATOR);
                        if (row.length == fieldNames.size()) {
                            for (int columnIndex = 0; columnIndex < fieldNames.size(); columnIndex++) {
                                obj.put(fieldNames.get(columnIndex), row[columnIndex]);
                            }
                            try {
                                ParticipantSurveyUploadObject object = new ParticipantSurveyUploadObject(obj.get(DDP_PARTICIPANT_ID));
                                if (object != null) {
                                    uploadObjects.add(object);
                                }
                            } catch (Exception e) {
                                throw new RuntimeException("Text file is not valid. Couldn't be parsed to upload object ", e);
                            }
                        } else {
                            throw new UploadLineException("Error in line " + (rowIndex + 1));
                        }
                    }
                    logger.info(uploadObjects.size() + " participants were uploaded for followup surveys ");
                    return uploadObjects;
                } else {
                    throw new FileColumnMissing("File is missing column " + missingFieldName);
                }
            }
        }
        return null;
    }

    private static String fieldNameMissing(@NonNull List<String> fieldName) {
        if (!fieldName.contains(DDP_PARTICIPANT_ID)) {
            return DDP_PARTICIPANT_ID;
        }
        return null;
    }
}
