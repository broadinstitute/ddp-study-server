package org.broadinstitute.dsm.model.elastic.export.tabular;

import com.google.common.net.MediaType;
import org.apache.commons.lang3.StringUtils;
import spark.Response;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

public class TsvParticipantExporter extends TabularParticipantExporter {
    public static final String DELIMITER = "\t";

    public TsvParticipantExporter(List<ModuleExportConfig> moduleConfigs,
                                          List<Map<String, String>> participantValueMaps, String fileFormat) {
        super(moduleConfigs, participantValueMaps, fileFormat);
    }


    public void export(Response response) throws IOException {
        setResponseHeaders(response);

        PrintWriter writer = response.raw().getWriter();
        List<String> headerRowValues = getHeaderRow();
        List<String> subHeaderRowValues = getSubHeaderRow();

        writer.println(getRowString(headerRowValues));
        writer.println(getRowString(subHeaderRowValues));
        for (Map<String, String> valueMap : participantValueMaps) {
            List<String> rowValues = getRowValues(valueMap, headerRowValues);
            String rowString = getRowString(rowValues);
            writer.println(rowString);
        }
        writer.flush();
    }

    public void setResponseHeaders(Response response) {
        response.type(MediaType.TSV_UTF_8.toString());
        response.header("Access-Control-Expose-Headers", "Content-Disposition");
        response.header("Content-Disposition", "attachment;filename=" + getExportFilename(fileFormat));
    }

    protected String getRowString(List<String> rowValues) {
        return String.join(DELIMITER, rowValues);
    }

    protected String sanitizeValue(String value) {
        if (value == null) {
            value = StringUtils.EMPTY;
        }
        // first replace double quotes with single '
        String sanitizedValue = value.replace("\"", "'");
        // then quote the whole string if needed
        if (sanitizedValue.indexOf("\n") >= 0 || sanitizedValue.indexOf(DELIMITER) >= 0) {
            sanitizedValue = String.format("\"%s\"", sanitizedValue);
        }
        return sanitizedValue;
    }


}
