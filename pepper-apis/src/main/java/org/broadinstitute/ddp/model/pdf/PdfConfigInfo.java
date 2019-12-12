package org.broadinstitute.ddp.model.pdf;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

/**
 * General metadata info about a pdf configuration.
 */
public class PdfConfigInfo {

    private long id;
    private long studyId;
    private String studyGuid;
    private String configName;
    private String filename;

    @JdbiConstructor
    public PdfConfigInfo(@ColumnName("pdf_document_configuration_id") long id,
                         @ColumnName("study_id") long studyId,
                         @ColumnName("study_guid") String studyGuid,
                         @ColumnName("configuration_name") String configName,
                         @ColumnName("file_name") String filename) {
        this.id = id;
        this.studyId = studyId;
        this.studyGuid = studyGuid;
        this.configName = configName;
        this.filename = filename;
    }

    public PdfConfigInfo(long studyId, String configName, String filename) {
        this.studyId = studyId;
        this.configName = configName;
        this.filename = filename;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getStudyId() {
        return studyId;
    }

    public String getStudyGuid() {
        return studyGuid;
    }

    public String getConfigName() {
        return configName;
    }

    public String getFilename() {
        return filename;
    }
}
