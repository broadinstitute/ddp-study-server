package org.broadinstitute.dsm.model.elastic.export.tabular;

import com.google.common.net.MediaType;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/** writes out participant data as a tab-delimited file.  See TabularParticipantExporter for more detail */
public class TsvParticipantExporter extends TabularParticipantExporter {
    public static final String DELIMITER = "\t";
    public static final String MEDIA_TYPE = MediaType.TSV_UTF_8.toString();

    public TsvParticipantExporter(List<ModuleExportConfig> moduleConfigs,
                                  List<Map<String, String>> participantValueMaps, String fileFormat) {
        super(moduleConfigs, participantValueMaps, fileFormat);
    }

    public void export(OutputStream os) throws IOException {
        PrintWriter printWriter = new PrintWriter(os);
        List<String> headerRowValues = getHeaderRow();
        List<String> subHeaderRowValues = getSubHeaderRow();

        printWriter.println(getRowString(headerRowValues));
        printWriter.println(getRowString(subHeaderRowValues));
        for (Map<String, String> valueMap : participantValueMaps) {
            List<String> rowValues = getRowValues(valueMap, headerRowValues);
            String rowString = getRowString(rowValues);
            printWriter.println(rowString);
        }
        printWriter.flush();
        // do not close os -- that's the caller's responsibility
    }

    protected String getRowString(List<String> rowValues) {
        return String.join(DELIMITER, rowValues);
    }

    /**
     * replaces double quotes with single, and then encapsulates any strings which contain newlines or tabs
     * with double quotes
     * @param value the value to sanitize
     * @return the sanitized value, suitable for including in a tsv
     */
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

    @Override
    public String getExportFilename() {
        return getExportFilename("tsv");
    }


}
