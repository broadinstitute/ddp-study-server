package org.broadinstitute.dsm.util;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SystemUtil {

    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String PARTIAL_DATE_FORMAT = "yyyy-MM";
    public static final String YEAR_DATE_FORMAT = "yyyy";
    public static final String DDP_DATE_FORMAT = "MM-dd-yyyy";
    public static final String US_DATE_FORMAT = "MM/dd/yyyy";
    public static final String PARTIAL_US_DATE_FORMAT = "MM/yyyy";
    public static final String END_DATE_FORMAT = DATE_FORMAT + " HH:mm:ss";

    public static final long MILLIS_PER_DAY = 1000 * 60 * 60 * 24;
    public static final long MILLIS_PER_HOUR = 1000 * 60 * 60;

    private static final String LINEBREAK_UNIVERSAL = "\n";
    private static final String LINEBREAK = "\r";
    public static final String SEPARATOR = "\t";

    public static String getDateFormatted(@NonNull long inputDate) {
        Date date = new Date(inputDate);
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
        return sdf.format(date);
    }

    public static long getLongFromDateString(String dateString) {
        if (StringUtils.isNotBlank(dateString)) {
            SimpleDateFormat sdf = new SimpleDateFormat(SystemUtil.DATE_FORMAT);
            return getLong(dateString, sdf);
        }
        return 0;
    }

    public static long getLongFromDetailDateString(@NonNull String dateString) {
        SimpleDateFormat sdf = new SimpleDateFormat(SystemUtil.END_DATE_FORMAT);
        return getLong(dateString, sdf);
    }

    public static String changeDateFormat(@NonNull String inputDateFormat, @NonNull String outputDateFormat, @NonNull String dateString) {
        if (StringUtils.isNotBlank(dateString)) {
            try {
                DateFormat format = new SimpleDateFormat(inputDateFormat);
                Date date = format.parse(dateString);
                return new SimpleDateFormat(outputDateFormat).format(date);
            }
            catch (ParseException e) {
                try {
                    DateFormat format = new SimpleDateFormat(PARTIAL_DATE_FORMAT);
                    Date date = format.parse(dateString);
                    return new SimpleDateFormat(PARTIAL_US_DATE_FORMAT).format(date);
                }
                catch (ParseException e2) {
                    throw new RuntimeException("Couldn't change format of dateString " + e, e2);
                }
            }
        }
        return dateString;
    }

    public static long getLong(@NonNull String dateString, @NonNull SimpleDateFormat sdf) {
        try {
            Date date = sdf.parse(dateString);
            return date.getTime();
        }
        catch (ParseException e) {
            throw new RuntimeException("Couldn't parse date string to date ", e);
        }
    }

    public static long getLongFromString(@NonNull String dateString) throws ParseException {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(SystemUtil.DATE_FORMAT);
            Date date = sdf.parse(dateString);
            return date.getTime();
        }
        catch (ParseException e) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(SystemUtil.PARTIAL_DATE_FORMAT);
                Date date = sdf.parse(dateString);
                return date.getTime();
            }
            catch (ParseException e1) {
                SimpleDateFormat sdf = new SimpleDateFormat(SystemUtil.YEAR_DATE_FORMAT);
                if (dateString.length() != 4) {
                    throw new ParseException("String " + dateString + " is not a year", 0);
                }
                Date date = sdf.parse(dateString);
                return date.getTime();
            }
        }
    }

    //TODO Simone - delete if not used at the end...
    public static int getDaysTillNow(@NonNull String date) {
        long dateSent = SystemUtil.getLongFromDateString(date);
        long current = System.currentTimeMillis();
        return (int) ((current - dateSent) / SystemUtil.MILLIS_PER_DAY);
    }

    public static String getBody(HttpServletRequest request) throws IOException {
        String body = null;
        StringBuilder stringBuilder = new StringBuilder();
        BufferedReader bufferedReader = null;

        try {
            InputStream inputStream = request.getInputStream();
            if (inputStream != null) {
                bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                char[] charBuffer = new char[128];
                int bytesRead = -1;
                while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
                    stringBuilder.append(charBuffer, 0, bytesRead);
                }
            }
            else {
                stringBuilder.append("");
            }
        }
        catch (IOException ex) {
            throw ex;
        }
        finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                }
                catch (IOException ex) {
                    throw ex;
                }
            }
        }
        body = stringBuilder.toString();
        return body;
    }

    public static String lineBreak(String content) {
        if (content.split(LINEBREAK_UNIVERSAL).length > 1) {
            return LINEBREAK_UNIVERSAL;
        }
        if (content.split(LINEBREAK).length > 1) {
            return LINEBREAK;
        }
        return null;
    }

    public static JSONObject mergeJSONStrings(@NonNull String json1, @NonNull String json2) {
        if (StringUtils.isNotBlank(json1) && StringUtils.isNotBlank(json2)) {
            System.out.println(json1 + " " + json2);
            return mergeJSONObjects(new JSONObject(json1), new JSONObject(json2));
        }
        return null;
    }

    public static JSONObject mergeJSONObjects(@NonNull JSONObject json1, @NonNull JSONObject json2) {
        JSONObject mergedJson = new JSONObject();
        try {
            mergedJson = new JSONObject(json1, JSONObject.getNames(json1));
            for (String key : JSONObject.getNames(json2)) {
                mergedJson.put(key, json2.get(key));
            }
        }
        catch (JSONException e) {
            throw new RuntimeException("JSON exception ", e);
        }
        return mergedJson;
    }
}
