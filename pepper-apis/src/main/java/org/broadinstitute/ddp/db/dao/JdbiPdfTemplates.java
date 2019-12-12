package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.db.dto.pdf.PdfTemplateDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiPdfTemplates extends SqlObject {

    // study-builder
    @SqlUpdate("update pdf_base_template set template = :template where pdf_base_template_id = :id")
    int updatePdfBaseTemplate(@Bind("id") long id, @Bind("template") byte[] template);

    // study-builder
    @SqlQuery("select pbt.pdf_base_template_id, pbt.template, ptt.pdf_template_type_code"
            + " from pdf_base_template as pbt"
            + " join pdf_template_type as ptt on ptt.pdf_template_type_id = pbt.pdf_template_type_id"
            + " join pdf_physician_institution_template as ppit on ppit.pdf_base_template_id = pbt.pdf_base_template_id"
            + " join institution_type as it on it.institution_type_id = ppit.institution_type_id"
            + " join pdf_document_template_set as dts on dts.pdf_base_template_id = pbt.pdf_base_template_id"
            + " join pdf_document_configuration as c on c.pdf_document_configuration_id = dts.pdf_document_configuration_id"
            + " join umbrella_study as s on s.umbrella_study_id = c.umbrella_study_id"
            + " where s.guid = :studyGuid and it.institution_type_code = :instType")
    @RegisterConstructorMapper(PdfTemplateDto.class)
    Optional<PdfTemplateDto> getPhysicianInstitutionPdfTemplateByTypeAndStudy(
            @Bind("studyGuid") String studyGuid, @Bind("instType") String instType);

    // study-builder
    @SqlQuery("select pbt.pdf_base_template_id, pbt.template, ptt.pdf_template_type_code"
            + " from pdf_base_template as pbt"
            + " join pdf_template_type as ptt on ptt.pdf_template_type_id = pbt.pdf_template_type_id"
            + " join pdf_custom_template as pct on pct.pdf_base_template_id = pbt.pdf_base_template_id"
            + " join pdf_document_template_set as dts on dts.pdf_base_template_id = pbt.pdf_base_template_id"
            + " join pdf_document_configuration as c on c.pdf_document_configuration_id = dts.pdf_document_configuration_id"
            + " join umbrella_study as s on s.umbrella_study_id = c.umbrella_study_id"
            + " join study_pdf_mapping as m on m.pdf_document_configuration_id = c.pdf_document_configuration_id"
            + " join study_pdf_mapping_type as mt on mt.study_pdf_mapping_type_id = m.study_pdf_mapping_type_id"
            + " where s.guid = :studyGuid and mt.study_pdf_mapping_type_code = :mapType")
    @RegisterConstructorMapper(PdfTemplateDto.class)
    Optional<PdfTemplateDto> getCustomPdfTemplateByStudyAndType(@Bind("studyGuid") String studyGuid, @Bind("mapType") String mapType);

    // study-builder
    @SqlQuery("select pbt.pdf_base_template_id, pbt.template, ptt.pdf_template_type_code"
            + " from pdf_base_template as pbt"
            + " join pdf_template_type as ptt on ptt.pdf_template_type_id = pbt.pdf_template_type_id"
            + " join pdf_mailing_address_template as pat on pat.pdf_base_template_id = pbt.pdf_base_template_id"
            + " join pdf_document_template_set as dts on dts.pdf_base_template_id = pbt.pdf_base_template_id"
            + " join pdf_document_configuration as c on c.pdf_document_configuration_id = dts.pdf_document_configuration_id"
            + " join umbrella_study as s on s.umbrella_study_id = c.umbrella_study_id"
            + " where s.guid = :studyGuid")
    @RegisterConstructorMapper(PdfTemplateDto.class)
    Optional<PdfTemplateDto> getMailingAddressPdfTemplateByStudy(@Bind("studyGuid") String studyGuid);

}
