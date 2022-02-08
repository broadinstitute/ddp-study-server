package org.broadinstitute.ddp.model.dsm;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class StudyPdfMapping {

    private long id;
    private PdfMappingType type;
    private long studyId;
    private long pdfConfigurationId;
    private String pdfConfigurationName;
    private String pdfFileName;

    @JdbiConstructor
    public StudyPdfMapping(@ColumnName("study_pdf_mapping_id") long id,
                           @ColumnName("study_pdf_mapping_type") PdfMappingType type,
                           @ColumnName("umbrella_study_id") long studyId,
                           @ColumnName("pdf_document_configuration_id") long pdfConfigurationId,
                           @ColumnName("pdf_configuration_name") String pdfConfigurationName,
                           @ColumnName("pdf_file_name") String pdfFileName) {
        this.id = id;
        this.type = type;
        this.studyId = studyId;
        this.pdfConfigurationId = pdfConfigurationId;
        this.pdfConfigurationName = pdfConfigurationName;
        this.pdfFileName = pdfFileName;
    }

    public long getId() {
        return id;
    }

    public PdfMappingType getType() {
        return type;
    }

    public long getStudyId() {
        return studyId;
    }

    public long getPdfConfigurationId() {
        return pdfConfigurationId;
    }

    public String getPdfConfigurationName() {
        return pdfConfigurationName;
    }

    public String getPdfFileName() {
        return pdfFileName;
    }
}
