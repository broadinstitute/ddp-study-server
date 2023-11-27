package org.broadinstitute.dsm.service.onchistory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Data;

@Data
public class OncHistoryUploadColumn {

    private final String columnName;
    private final String columnAlias;
    private final String tableAlias;
    private final String parseType;
    private String description;

    public OncHistoryUploadColumn(String columnName, String columnAlias, String tableAlias, String parseType) {
        this.columnName = columnName;
        this.columnAlias = columnAlias;
        this.tableAlias = tableAlias;
        this.parseType = parseType;
    }

    public OncHistoryUploadColumn(String columnName, String columnAlias, String tableAlias, String parseType,
                                  String description) {
        this.columnName = columnName;
        this.columnAlias = columnAlias;
        this.tableAlias = tableAlias;
        this.parseType = parseType;
        this.description = description;
    }

    public static OncHistoryUploadColumn fromJson(String jsonString) {
        JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();
        return new OncHistoryUploadColumn(json.get("columnName").getAsString(),
                    json.get("columnAlias").getAsString(),
                    json.get("columnName").getAsString(),
                    json.get("parseType").getAsString(),
                    json.get("description").getAsString());
    }
}
