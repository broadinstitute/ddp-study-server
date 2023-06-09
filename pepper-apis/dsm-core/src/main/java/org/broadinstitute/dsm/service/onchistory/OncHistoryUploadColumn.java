package org.broadinstitute.dsm.service.onchistory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Data;

@Data
public class OncHistoryUploadColumn {

    public String columnName;
    public String columnAlias;
    public String tableAlias;
    public String parseType;

    public OncHistoryUploadColumn(String columnName, String columnAlias, String tableAlias, String parseType) {
        this.columnName = columnName;
        this.columnAlias = columnAlias;
        this.tableAlias = tableAlias;
        this.parseType = parseType;
    }

    public static OncHistoryUploadColumn fromJson(String jsonString) {
            JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();
            return new OncHistoryUploadColumn(json.get("columnName").getAsString(),
                    json.get("columnAlias").getAsString(),
                    json.get("columnName").getAsString(),
                    json.get("parseType").getAsString());
    }
}
