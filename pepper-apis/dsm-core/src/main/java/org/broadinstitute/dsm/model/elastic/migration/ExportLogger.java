package org.broadinstitute.dsm.model.elastic.migration;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import org.broadinstitute.dsm.service.adminoperation.ExportLog;

public class ExportLogger {
    @Getter
    private final List<ExportLog> exportLogs;
    @Getter
    private final String entity;
    private final boolean recordSuccessIds;
    private final boolean useExportLog;

    public ExportLogger(String entity) {
        this(new ArrayList<>(), entity, false, false);
    }

    public ExportLogger(List<ExportLog> exportLogs, String entity, boolean recordSuccessIds, boolean useExportLog) {
        this.exportLogs = exportLogs;
        this.entity = entity;
        this.recordSuccessIds = recordSuccessIds;
        this.useExportLog = useExportLog;
    }

    public ExportLogger(List<ExportLog> exportLogs, String entity, boolean recordSuccessIds) {
        this(exportLogs, entity, recordSuccessIds, true);
    }

    public ExportLogger(List<ExportLog> exportLogs, String entity) {
        this(exportLogs, entity, false, true);
    }

    public void addLog(ExportLog log) {
        exportLogs.add(log);
    }

    public boolean recordSuccessIds() {
        return recordSuccessIds;
    }

    public boolean useExportLog() {
        return useExportLog;
    }

    public void setEntityStatus(ExportLog.Status status) {
        ExportLog exportLog = new ExportLog(entity);
        exportLog.setStatus(status);
        addLog(exportLog);
    }

    public void setEntityError(String message) {
        ExportLog exportLog = new ExportLog(entity);
        exportLog.setError(message);
        addLog(exportLog);
    }
}
