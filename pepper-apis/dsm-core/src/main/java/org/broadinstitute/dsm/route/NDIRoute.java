package org.broadinstitute.dsm.route;

import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.db.NationalDeathIndex;
import org.broadinstitute.dsm.exception.FileColumnMissing;
import org.broadinstitute.dsm.exception.FileWrongFormat;
import org.broadinstitute.dsm.exception.FileWrongSeparator;
import org.broadinstitute.dsm.exception.UploadLineException;
import org.broadinstitute.dsm.model.NDIUploadObject;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.SystemUtil;
import org.broadinstitute.dsm.util.UserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

public class NDIRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(NDIRoute.class);

    private static final String PARTICIPANT_ID = "participantId";
    private static final String FIRST_NAME = "First";
    private static final String LAST_NAME = "Last";
    private static final String MIDDLE = "Middle";
    private static final String YEAR = "Year";
    private static final String MONTH = "Month";
    private static final String DAY = "Day";

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        String userIdRequest = UserUtil.getUserId(request);
        if (UserUtil.checkUserAccess(null, userId, "ndi_download", userIdRequest)) {
            HttpServletRequest rawRequest = request.raw();
            String content = SystemUtil.getBody(rawRequest);
            try {
                List<NDIUploadObject> requests = isFileValid(content);
                if (requests != null) {
                    response.header("Content-Type", "text/plain; charset=utf-8");
                    String s = NationalDeathIndex.createOutputTxtFile(requests, userIdRequest);
                    return s;
                }
            }
            catch (FileColumnMissing e) {
                response.status(500);
                return new Result(500, e.getMessage());
            }
            catch (FileWrongFormat e) {
                response.status(500);
                return new Result(500, e.getMessage());
            }
            catch (FileWrongSeparator e) {
                response.status(500);
                return new Result(500, e.getMessage());
            }
        }
        else {
            response.status(500);
            return new Result(500, UserErrorMessages.NO_RIGHTS);
        }
        response.status(500);
        return new Result(500, "Failed to generate NDI file,\n please contact your DSM Developer");
    }

    public static List<NDIUploadObject> isFileValid(String fileContent) throws FileWrongFormat, FileColumnMissing {
        if (fileContent != null) {
            String linebreak = SystemUtil.lineBreak(fileContent);
            String[] rows = fileContent.split(linebreak);
            if (rows.length > 1) {
                String firstRow = rows[0];
                if (firstRow.contains(SystemUtil.SEPARATOR)) {
                    List<String> fieldNames = new ArrayList<>(Arrays.asList(firstRow.trim().split(SystemUtil.SEPARATOR)));
                    String missingFieldName = fieldNameMissing(fieldNames);
                    if (missingFieldName == null) {
                        List<NDIUploadObject> uploadObjects = new ArrayList<>();
                        for (int rowIndex = 1; rowIndex < rows.length; rowIndex++) {
                            Map<String, String> obj = new LinkedHashMap<>();
                            String[] row = rows[rowIndex].trim().split(SystemUtil.SEPARATOR);
                            if (row.length == fieldNames.size()) {
                                for (int columnIndex = 0; columnIndex < fieldNames.size(); columnIndex++) {
                                    obj.put(fieldNames.get(columnIndex), row[columnIndex]);
                                }

                                try {
                                    NDIUploadObject object;
                                    if (obj.get(YEAR).length() != 4 || obj.get(MONTH).length() != 2 || obj.get(DAY).length() != 2) {
                                        if (obj.get(DAY).length() == 1) {
                                            obj.put(DAY, "0" + obj.get(DAY));
                                        }
                                        if (obj.get(MONTH).length() == 1) {
                                            obj.put(MONTH, "0" + obj.get(MONTH));
                                        }
                                        else if (obj.get(YEAR).length() != 4) {
                                            throw new FileWrongFormat("Please use the YYYY format for year");
                                        }
                                    }
                                    if (obj.get(FIRST_NAME).length() == 0 || obj.get(LAST_NAME).length() == 0 || obj.get(YEAR).length() == 0
                                            || obj.get(MONTH).length() == 0 || obj.get(DAY).length() == 0 || obj.get(PARTICIPANT_ID).length() == 0) {
                                        throw new FileWrongFormat("A mandatory column was empty! Error in line " + (rowIndex + 1));
                                    }
                                    if (obj.get(MIDDLE).length() > 1) {
                                        obj.put(MIDDLE, obj.get(MIDDLE).charAt(0) + "");
                                    }
                                    object = new NDIUploadObject(obj.get(FIRST_NAME), obj.get(LAST_NAME), obj.get(MIDDLE), obj.get(YEAR), obj.get(MONTH),
                                            obj.get(DAY), obj.get(PARTICIPANT_ID));
                                    uploadObjects.add(object);
                                }
                                catch (Exception e) {
                                    throw new RuntimeException("Text file is not valid. Couldn't be parsed to upload object ", e);
                                }
                            }
                            else {
                                throw new UploadLineException("Error in line " + (rowIndex + 1));
                            }
                        }
                        logger.info(uploadObjects.size() + " NDI requests were uploaded. ");

                        return uploadObjects;
                    }
                    else {
                        throw new FileColumnMissing("File is missing column " + missingFieldName);
                    }
                }
                else {
                    throw new FileWrongSeparator("Please use tab as separator in the text file");
                }
            }
        }
        return null;
    }

    public static String fieldNameMissing(List<String> fieldName) {
        if (!fieldName.contains(PARTICIPANT_ID)) {
            return PARTICIPANT_ID;
        }
        if (!fieldName.contains(FIRST_NAME)) {
            return FIRST_NAME;
        }
        if (!fieldName.contains(LAST_NAME)) {
            return LAST_NAME;
        }
        if (!fieldName.contains(MIDDLE)) {
            return MIDDLE;
        }
        if (!fieldName.contains(YEAR)) {
            return YEAR;
        }
        if (!fieldName.contains(MONTH)) {
            return MONTH;
        }
        if (!fieldName.contains(DAY)) {
            return DAY;
        }
        return null;
    }
}
