package org.broadinstitute.dsm.route;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.log4j.Log4j2;
import org.broadinstitute.dsm.db.dao.stoolupload.StoolUploadDao;
import org.broadinstitute.dsm.db.dto.stoolupload.StoolUploadDto;
import org.broadinstitute.dsm.exception.FileColumnMissing;
import org.broadinstitute.dsm.exception.FileWrongSeparator;
import org.broadinstitute.dsm.exception.UploadLineException;
import org.broadinstitute.dsm.model.StoolUploadObject;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.SystemUtil;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

@Log4j2
public class StoolUploadRoute extends RequestHandler {

    private static final String PARTICIPANT_ID = "participantId";
    private static final String MF_BARCODE = "mfBarcode";
    private static final String RECEIVE_DATE = "receiveDate";
    public static final String UPLOAD_ANYWAY = "uploadAnyway";


    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {
        QueryParamsMap queryParams = request.queryMap();
        String realm;

        if (queryParams.value(RoutePath.REALM) != null) {
            realm = queryParams.get(RoutePath.REALM).value();
        } else {
            throw new RuntimeException("No realm query param was sent");
        }
        String userIdRequest = UserUtil.getUserId(request);

        if (UserUtil.checkUserAccess(realm, userId, "kit_upload", userIdRequest)) {
            String content = request.body();

            AtomicBoolean uploadAnyway = new AtomicBoolean(false);
            if (queryParams.value(UPLOAD_ANYWAY) != null) {
                uploadAnyway.set(queryParams.get(UPLOAD_ANYWAY).booleanValue());
            }

            try {
                List<StoolUploadObject> stoolUploadContent;
                if (uploadAnyway.get()) {
                    stoolUploadContent =
                            Collections.singletonList(ObjectMapperSingleton.instance().readValue(content, StoolUploadObject.class));
                } else {
                    try {
                        stoolUploadContent = isFileValid(content);
                    } catch (Exception e) {
                        response.status(500);
                        return e.getMessage();
                    }
                }

                final List<StoolUploadObject> stoolUploadObjects = stoolUploadContent;
                if (stoolUploadObjects.isEmpty()) {
                    return "Text file was empty or couldn't be parsed to the agreed format";
                }

                log.info("Text file was uploaded and parsed successfully");

                StoolUploadDao stoolUploadDao = new StoolUploadDao();
                stoolUploadObjects.forEach(stoolUploadObject -> {

                    String participantId = stoolUploadObject.getParticipantId();
                    String mfBarcode = stoolUploadObject.getMfBarcode();
                    String receiveDate = stoolUploadObject.getReceiveDate();

                    Optional<StoolUploadDto> stoolUploadDto = stoolUploadDao.getStoolUploadDto(participantId, mfBarcode);

                    if(stoolUploadDto.isPresent()){
                        log.info("Successfully generated kits, trying to update the table with provided parameters...");
                        stoolUploadDao.updateKitData(receiveDate,stoolUploadDto.get().getKitId(),mfBarcode);
                    } else {
                        log.warn("Unable to fetch kits with provided parameters");
                    }
                });
            } catch (Exception e) {
                return e.getMessage();
            }
        } else {
            response.status(500);
            return (UserErrorMessages.NO_RIGHTS);
        }
        return  null;
    }

    private List<StoolUploadObject> isFileValid(String fileContent) {
        if (fileContent == null) {
            throw new RuntimeException("File is empty");
        }

        String[] rows = fileContent.split(System.lineSeparator());
        if (rows.length < 2) {
            throw new RuntimeException("Text file does not contain enough information");
        }

        String firstRow = rows[0];
        if (!firstRow.contains(SystemUtil.SEPARATOR)) {
            throw new FileWrongSeparator("Please use tab as separator in the text file");
        }

        List<String> fieldNamesFromFileHeader = Arrays.asList(firstRow.trim().split(SystemUtil.SEPARATOR));
        String missingHeader = getMissingHeader(fieldNamesFromFileHeader);
        if (missingHeader != null) {
            throw new FileColumnMissing("File is missing column " + missingHeader);
        }

        List<StoolUploadObject> stoolRequestObjectsToUpload = new ArrayList<>();
        parseParticipantDataToUpload(rows, fieldNamesFromFileHeader, stoolRequestObjectsToUpload);
        return stoolRequestObjectsToUpload;
    }

    private void parseParticipantDataToUpload(String[] rows, List<String> fieldNamesFromFileHeader,
                                              List<StoolUploadObject> stoolRequestToUpload) {
        int lastNonEmptyRowIndex = getLastNonEmptyRowIndex(rows);

        for (int rowIndex = 1; rowIndex <= lastNonEmptyRowIndex; rowIndex++) {
            Map<String, String> participantDataByFieldName = getParticipantDataAsMap(rows[rowIndex], fieldNamesFromFileHeader, rowIndex);

            StoolUploadObject stoolUploadObject = new StoolUploadObject(
                    participantDataByFieldName.get(PARTICIPANT_ID),
                    participantDataByFieldName.get(MF_BARCODE),
                    participantDataByFieldName.get(RECEIVE_DATE));

            stoolRequestToUpload.add(stoolUploadObject);
        }
    }

    private String getMissingHeader(List<String> fieldNamesFromFileHeader) {
        if (!fieldNamesFromFileHeader.contains(PARTICIPANT_ID)) {
            return PARTICIPANT_ID;
        }

        if (!fieldNamesFromFileHeader.contains(MF_BARCODE)) {
            return MF_BARCODE;
        }

        if (!fieldNamesFromFileHeader.contains(RECEIVE_DATE)) {
            return RECEIVE_DATE;
        }

        return null;
    }

    Map<String, String> getParticipantDataAsMap(String row, List<String> fieldNamesFromHeader, int rowIndex) {
        Map<String, String> participantDataByFieldName = new LinkedHashMap<>();
        String[] rowItems = row.trim().split(SystemUtil.SEPARATOR);
        if (rowItems.length != fieldNamesFromHeader.size()) {
            throw new UploadLineException("Error in line " + (rowIndex + 1));
        }

        for (int columnIndex = 0; columnIndex < fieldNamesFromHeader.size(); columnIndex++) {
            participantDataByFieldName.put(fieldNamesFromHeader.get(columnIndex), rowItems[columnIndex]);
        }
        return participantDataByFieldName;
    }

    private int getLastNonEmptyRowIndex(String[] rows) {
        int lastNonEmptyRowIndex = rows.length - 1;

        for (int i = rows.length - 1; i > 0; i--) {
            String[] row = rows[i].trim().split(SystemUtil.SEPARATOR);
            if (!"".equals(String.join("", row))) {
                lastNonEmptyRowIndex = i;
                break;
            }
        }

        return lastNonEmptyRowIndex;
    }
}
