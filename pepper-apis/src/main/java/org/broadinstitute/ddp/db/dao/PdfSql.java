package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.broadinstitute.ddp.model.pdf.MailingAddressTemplate;
import org.broadinstitute.ddp.model.pdf.PdfConfigInfo;
import org.broadinstitute.ddp.model.pdf.PdfDataSourceType;
import org.broadinstitute.ddp.model.pdf.PdfTemplateType;
import org.broadinstitute.ddp.model.pdf.PdfVersion;
import org.broadinstitute.ddp.model.pdf.PhysicianInstitutionTemplate;
import org.broadinstitute.ddp.model.pdf.SubstitutionType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface PdfSql extends SqlObject {

    @GetGeneratedKeys
    @SqlUpdate("insert into pdf_document_configuration (umbrella_study_id, configuration_name, file_name, display_name)"
            + " values (:studyId, :configName, :filename, :displayName)")
    long insertConfigInfo(
            @Bind("studyId") long studyId,
            @Bind("configName") String configurationName,
            @Bind("filename") String filename,
            @Bind("displayName") String displayName);

    default long insertConfigInfo(PdfConfigInfo info) {
        return insertConfigInfo(info.getStudyId(), info.getConfigName(), info.getFilename(), info.getDisplayName());
    }

    @GetGeneratedKeys
    @SqlUpdate("insert into pdf_document_version (pdf_document_configuration_id, version_tag, revision_id)"
            + " values (:configId, :versionTag, :revId)")
    long insertConfigVersion(
            @Bind("configId") long configId,
            @Bind("versionTag") String versionTag,
            @Bind("revId") long revisionId);

    default long insertConfigVersion(PdfVersion version) {
        return insertConfigVersion(version.getConfigId(), version.getVersionTag(), version.getRevId());
    }

    @GetGeneratedKeys
    @SqlUpdate("insert into pdf_data_source (pdf_document_version_id, pdf_data_source_type_id)"
            + "(select :versionId, pdf_data_source_type_id from pdf_data_source_type where pdf_data_source_type_code = :type)")
    long insertBaseDataSource(@Bind("versionId") long versionId, @Bind("type") PdfDataSourceType type);

    @SqlUpdate("insert into pdf_activity_data_source (pdf_data_source_id, activity_id, activity_version_id)"
            + " values (:srcId, :actId, :actVerId)")
    int insertActivityDataSource(@Bind("srcId") long sourceId, @Bind("actId") long activityId, @Bind("actVerId") long activityVersionId);

    @GetGeneratedKeys
    @SqlUpdate("insert into pdf_base_template (template, pdf_template_type_id)"
            + " values (:blob, (select pdf_template_type_id from pdf_template_type where pdf_template_type_code = :type))")
    long insertBaseTemplate(@Bind("blob") byte[] blob, @Bind("type") PdfTemplateType type);

    @SqlUpdate("insert into pdf_mailing_address_template ("
            + "        pdf_base_template_id, first_name_placeholder, last_name_placeholder,"
            + "        proxy_first_name_placeholder, proxy_last_name_placeholder,"
            + "        street_placeholder, city_placeholder, state_placeholder,"
            + "        zip_placeholder, country_placeholder, phone_placeholder)"
            + " values (:templateId, :firstNamePlaceholder, :lastNamePlaceholder,"
            + "        :proxyFirstNamePlaceholder, :proxyLastNamePlaceholder,"
            + "        :streetPlaceholder, :cityPlaceholder, :statePlaceholder,"
            + "        :zipPlaceholder, :countryPlaceholder, :phonePlaceholder)")
    int insertMailingAddressTemplate(
            @Bind("templateId") long templateId,
            @Bind("firstNamePlaceholder") String firstNamePlaceholder,
            @Bind("lastNamePlaceholder") String lastNamePlaceholder,
            @Bind("proxyFirstNamePlaceholder") String proxyFirstNamePlaceholder,
            @Bind("proxyLastNamePlaceholder") String proxyLastNamePlaceholder,
            @Bind("streetPlaceholder") String streetPlaceholder,
            @Bind("cityPlaceholder") String cityPlaceholder,
            @Bind("statePlaceholder") String statePlaceholder,
            @Bind("zipPlaceholder") String zipPlaceholder,
            @Bind("countryPlaceholder") String countryPlaceholder,
            @Bind("phonePlaceholder") String phonePlaceholder);

    default int insertMailingAddressTemplate(MailingAddressTemplate template) {
        return insertMailingAddressTemplate(
                template.getId(),
                template.getFirstNamePlaceholder(),
                template.getLastNamePlaceholder(),
                template.getProxyFirstNamePlaceholder(),
                template.getProxyLastNamePlaceholder(),
                template.getStreetPlaceholder(),
                template.getCityPlaceholder(),
                template.getStatePlaceholder(),
                template.getZipPlaceholder(),
                template.getCountryPlaceholder(),
                template.getPhonePlaceholder());
    }

    @SqlUpdate("insert into pdf_physician_institution_template ("
            + "        pdf_base_template_id, physician_name_placeholder, institution_name_placeholder,"
            + "        city_placeholder, state_placeholder, institution_type_id,"
            + "        street_placeholder, zip_placeholder, phone_placeholder)"
            + " values (:templateId, :physicianNamePlaceholder, :institutionNamePlaceholder,"
            + "        :cityPlaceholder, :statePlaceholder,"
            + "        (select institution_type_id from institution_type where institution_type_code = :institutionType),"
            + "        :streetPlaceholder, :zipPlaceholder, :phonePlaceholder)")
    int insertPhysicianInstitutionTemplate(
            @Bind("templateId") long templateId,
            @Bind("physicianNamePlaceholder") String physicianNamePlaceholder,
            @Bind("institutionNamePlaceholder") String institutionNamePlaceholder,
            @Bind("cityPlaceholder") String cityPlaceholder,
            @Bind("statePlaceholder") String statePlaceholder,
            @Bind("institutionType") InstitutionType institutionType,
            @Bind("streetPlaceholder") String streetPlaceholder,
            @Bind("zipPlaceholder") String zipPlaceholder,
            @Bind("phonePlaceholder") String phonePlaceholder);

    default int insertPhysicianInstitutionTemplate(PhysicianInstitutionTemplate template) {
        return insertPhysicianInstitutionTemplate(
                template.getId(),
                template.getPhysicianNamePlaceholder(),
                template.getInstitutionNamePlaceholder(),
                template.getCityPlaceholder(),
                template.getStatePlaceholder(),
                template.getInstitutionType(),
                template.getStreetPlaceholder(),
                template.getZipPlaceholder(),
                template.getPhonePlaceholder());
    }

    @SqlUpdate("insert into pdf_custom_template (pdf_base_template_id) values (:templateId)")
    int insertBaseCustomTemplate(@Bind("templateId") long templateId);

    @GetGeneratedKeys
    @SqlUpdate("insert into pdf_substitution (placeholder, pdf_substitution_type_id)"
            + "(select :placeholder, pdf_substitution_type_id from pdf_substitution_type where pdf_substitution_type_code = :type)")
    long insertBaseSubstitution(@Bind("placeholder") String placeholder, @Bind("type") SubstitutionType type);

    @SqlUpdate("insert into pdf_profile_substitution (pdf_substitution_id, profile_field_name) values (:subId, :profileFieldName)")
    int insertProfileSubstitution(@Bind("subId") long substitutionId, @Bind("profileFieldName") String profileFieldName);

    @SqlUpdate("insert into pdf_activity_date_substitution (pdf_substitution_id, study_activity_id) values (:subId, :activityId)")
    int insertActivityDateSubstitution(@Bind("subId") long substitutionId, @Bind("activityId") long activityId);

    @SqlUpdate("insert into pdf_answer_substitution (pdf_substitution_id, question_stable_code_id)"
            + "(select :subId, question_stable_code_id"
            + "   from question_stable_code as qsc"
            + "  where qsc.stable_id = :stableId"
            + "    and qsc.umbrella_study_id = (select study_id from study_activity where study_activity_id = :activityId))")
    int insertBaseAnswerSubstitution(
            @Bind("subId") long substitutionId,
            @Bind("activityId") long activityId,
            @Bind("stableId") String questionStableId);

    @SqlUpdate("insert into pdf_boolean_answer_substitution (pdf_answer_substitution_id, check_if_false) values (:subId, :checkIfFalse)")
    int insertBooleanAnswerSubstitution(@Bind("subId") long substitutionId, @Bind("checkIfFalse") boolean checkIfFalse);

    @GetGeneratedKeys
    @SqlUpdate("insert into pdf_version_template (pdf_document_version_id, pdf_base_template_id, template_order)"
            + " values (:versionId, :templateId, :order)")
    long assignTemplateToVersion(@Bind("versionId") long versionId, @Bind("templateId") long templateId, int order);

    @GetGeneratedKeys
    @SqlUpdate("insert into pdf_template_substitution_set (pdf_custom_template_id, pdf_substitution_id) values (:templateId, :subId)")
    long assignSubstitutionToCustomTemplate(@Bind("templateId") long customTemplateId, @Bind("subId") long substitutionId);


    @SqlUpdate("update pdf_activity_date_substitution set study_activity_id = :activityId where pdf_substitution_id = :subId")
    int updateActivityDateSubstitution(@Bind("subId") long substitutionId, @Bind("activityId") long activityId);


    @SqlUpdate("delete from pdf_document_configuration where pdf_document_configuration_id = :configId")
    int deleteConfigInfoById(@Bind("configId") long configId);

    @SqlUpdate("delete from pdf_document_version where pdf_document_version_id = :versionId")
    int deleteConfigVersionById(@Bind("versionId") long versionId);

    @SqlUpdate("delete from pdf_data_source where pdf_data_source_id = :sourceId")
    int deleteBaseDataSourceById(@Bind("sourceId") long sourceId);

    @SqlUpdate("delete from pdf_activity_data_source where pdf_data_source_id = :sourceId")
    int deleteActivityDataSourceBySourceId(@Bind("sourceId") long sourceId);

    @SqlUpdate("delete from pdf_base_template where pdf_base_template_id = :templateId")
    int deleteBaseTemplateById(@Bind("templateId") long templateId);

    @SqlUpdate("delete from pdf_mailing_address_template where pdf_base_template_id = :templateId")
    int deleteMailingAddressTemplateByTemplateId(@Bind("templateId") long templateId);

    @SqlUpdate("delete from pdf_physician_institution_template where pdf_base_template_id = :templateId")
    int deletePhysicianInstitutionTemplateByTemplateId(@Bind("templateId") long templateId);

    @SqlUpdate("delete from pdf_custom_template where pdf_base_template_id = :templateId")
    int deleteBaseCustomTemplateByTemplateId(@Bind("templateId") long templateId);

    @SqlUpdate("delete from pdf_substitution where pdf_substitution_id = :subId")
    int deleteBaseSubstitutionById(@Bind("subId") long substitutionId);

    @SqlUpdate("delete from pdf_profile_substitution where pdf_substitution_id = :subId")
    int deleteProfileSubstitutionBySubId(@Bind("subId") long substitutionId);

    @SqlUpdate("delete from pdf_activity_date_substitution where pdf_substitution_id = :subId")
    int deleteActivityDateSubstitutionBySubId(@Bind("subId") long substitutionId);

    @SqlUpdate("delete from pdf_answer_substitution where pdf_substitution_id = :subId")
    int deleteBaseAnswerSubstitutionBySubId(@Bind("subId") long substitutionId);

    @SqlUpdate("delete from pdf_boolean_answer_substitution where pdf_answer_substitution_id = :subId")
    int deleteBooleanAnswerSubstitutionBySubId(@Bind("subId") long substitutionId);

    @SqlUpdate("delete from pdf_version_template where pdf_document_version_id = :versionId")
    int unassignTemplatesFromVersion(@Bind("versionId") long versionId);

    @SqlUpdate("delete from pdf_template_substitution_set where pdf_custom_template_id = :templateId")
    int unassignSubstitutionsFromCustomTemplate(@Bind("templateId") long customTemplateId);
}
