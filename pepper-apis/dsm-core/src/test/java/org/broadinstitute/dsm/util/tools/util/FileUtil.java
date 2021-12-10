package org.broadinstitute.dsm.util.tools.util;

import lombok.NonNull;
import org.broadinstitute.dsm.exception.FileWrongSeparator;

import java.io.Writer;
import java.util.*;

public class FileUtil {

    private static final String LINEBREAK_UNIVERSAL = "\n";
    private static final String LINEBREAK = "\r";
    private static final String SEPARATOR = "\t";

    public static List<Map<String, String>>  readFileContent(@NonNull String fileContent) throws Exception {
        List<Map<String, String>> content = new ArrayList();
        if (fileContent != null) {
            String linebreak = lineBreak(fileContent);
            String[] rows = fileContent.split(linebreak);
            if (rows.length > 1) {
                String firstRow = rows[0];
                if (firstRow.contains(SEPARATOR)) {
                    List<String> fieldNames = new ArrayList<>(Arrays.asList(firstRow.split(SEPARATOR)));
                    for (int rowIndex = 1; rowIndex < rows.length; rowIndex++) {
                        Map<String, String> obj = new LinkedHashMap<>();
                        String[] row = rows[rowIndex].split(SEPARATOR);
                        for (int columnIndex = 0; columnIndex < fieldNames.size(); columnIndex++) {
                            if (row.length - 1 >= columnIndex) {
                                obj.put(fieldNames.get(columnIndex), row[columnIndex]);
                            } else {
                                obj.put(fieldNames.get(columnIndex), null);
                            }
                        }
                        content.add(obj);
                    }
                }
                else {
                    throw new FileWrongSeparator("Please use tab as separator in the text file");
                }
            }
        }
        return content;
    }

    private static String lineBreak(String content) {
        if (content.split(LINEBREAK_UNIVERSAL).length > 1) {
            return LINEBREAK_UNIVERSAL;
        }
        if (content.split(LINEBREAK).length > 1) {
            return LINEBREAK;
        }
        return null;
    }

    public static void writeCSV(@NonNull Writer writer, ArrayList<String> lineOutput) throws Exception {
        StringBuilder builder = new StringBuilder();
        for(String data : lineOutput) {
            builder.append("\""+data + "\",");
        }
        builder.append("\n");
        writer.write(builder.toString());
    }
}
