package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.model.dsm.PdfMappingType;
import org.broadinstitute.ddp.model.dsm.StudyPdfMapping;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiStudyPdfMapping extends SqlObject {

    @SqlUpdate("insert into study_pdf_mapping (umbrella_study_id,study_pdf_mapping_type_id,pdf_document_configuration_id)"
            + " values (:studyId, (select study_pdf_mapping_type_id from study_pdf_mapping_type where study_pdf_mapping_type_code = :type),"
            + " :pdfConfigId)")
    @GetGeneratedKeys
    long insert(@Bind("studyId") long studyId, @Bind("type") PdfMappingType type,
                @Bind("pdfConfigId") long pdfConfigurationId);

    @SqlQuery("select mapping.study_pdf_mapping_id,"
            + "mtype.study_pdf_mapping_type_code as study_pdf_mapping_type,"
            + "mapping.umbrella_study_id,"
            + "mapping.pdf_document_configuration_id,"
            + "conf.configuration_name as pdf_configuration_name,"
            + "conf.file_name as pdf_file_name "
            + "from study_pdf_mapping as mapping "
            + "join study_pdf_mapping_type as mtype on mtype.study_pdf_mapping_type_id = mapping.study_pdf_mapping_type_id "
            + "join pdf_document_configuration as conf on mapping.pdf_document_configuration_id = conf.pdf_document_configuration_id "
            + "where mapping.umbrella_study_id = :studyId "
            + "and mtype.study_pdf_mapping_type_code = :type")
    @RegisterConstructorMapper(StudyPdfMapping.class)
    Optional<StudyPdfMapping> findByStudyIdAndMappingType(@Bind("studyId") long studyId, @Bind("type") PdfMappingType type);

    @SqlUpdate("delete from study_pdf_mapping where study_pdf_mapping_id = :id")
    int deleteById(@Bind("id") long id);
}
