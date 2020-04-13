package org.broadinstitute.ddp.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;
import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.PdfPageFormCopier;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfDocumentInfo;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.ParticipantDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;
import org.broadinstitute.ddp.model.activity.instance.ActivityResponse;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.governance.Governance;
import org.broadinstitute.ddp.model.pdf.ActivityDateSubstitution;
import org.broadinstitute.ddp.model.pdf.AnswerSubstitution;
import org.broadinstitute.ddp.model.pdf.BooleanAnswerSubstitution;
import org.broadinstitute.ddp.model.pdf.CustomTemplate;
import org.broadinstitute.ddp.model.pdf.MailingAddressTemplate;
import org.broadinstitute.ddp.model.pdf.PdfConfiguration;
import org.broadinstitute.ddp.model.pdf.PdfDataSourceType;
import org.broadinstitute.ddp.model.pdf.PdfSubstitution;
import org.broadinstitute.ddp.model.pdf.PdfTemplate;
import org.broadinstitute.ddp.model.pdf.PdfVersion;
import org.broadinstitute.ddp.model.pdf.PhysicianInstitutionTemplate;
import org.broadinstitute.ddp.model.pdf.ProfileSubstitution;
import org.broadinstitute.ddp.model.pdf.SubstitutionType;
import org.broadinstitute.ddp.model.study.Participant;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.transformers.DateTimeFormatUtils;
import org.broadinstitute.ddp.util.Auth0Util;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PdfGenerationService {

    // Do not change these Off/Yes values as they are defined by iText internals
    public static final String UNCHECKED_VALUE = "Off";
    public static final String CHECKED_VALUE = "Yes";

    public static final int COPY_START_PAGE = 1;

    private static final Logger LOG = LoggerFactory.getLogger(PdfGenerationService.class);
    private static final String FONT_PATH = "fonts/FreeSans.ttf";

    // canEncode() changes state of the encoder, so we need one per thread
    private static final ThreadLocal<CharsetEncoder> ISO88591 = ThreadLocal.withInitial(() -> Charset.forName("ISO-8859-1").newEncoder());

    /**
     * Sets the value of a checkbox to boolean submitted
     *
     * @param field     the pdf field
     * @param isChecked whether or not to check it
     */
    private static void setIsChecked(PdfFormField field, boolean isChecked) {
        String value = isChecked ? CHECKED_VALUE : UNCHECKED_VALUE;
        // need to set the style of the check here again, it appears that the check type is lost in the template.
        field.setCheckType(PdfFormField.TYPE_CHECK);
        field.setValue(value);
    }

    public byte[] generateFlattenedPdfForConfiguration(PdfConfiguration configuration, String userGuid,
                                                       Handle handle) throws IOException {
        byte[] unflattenedPdf = generatePdfForConfiguration(configuration, userGuid, handle);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 PdfWriter tempWriter = new PdfWriter(baos);
                 PdfDocument flattenedDoc = new PdfDocument(new PdfReader(new ByteArrayInputStream(unflattenedPdf)), tempWriter)) {

            PdfAcroForm form = PdfAcroForm.getAcroForm(flattenedDoc, false);
            form.flattenFields();

            LOG.info("Flattened {}", configuration.getConfigName());
            flattenedDoc.close();
            return baos.toByteArray();
        }
    }

    /**
     * Generate single PDF based on a user's physician institution information,
     * profile information, and/ or how they responded to questions in studies.
     *
     * @param configuration The configuration for the PDF
     * @param userGuid      the user guid
     * @param handle        the jdbi handle
     * @return an array of bytes representing the rendered PDF
     */
    byte[] generatePdfForConfiguration(PdfConfiguration configuration,
                                       String userGuid,
                                       Handle handle) throws IOException {
        Participant participant = loadParticipantData(handle, configuration, userGuid);
        Map<Long, ActivityResponse> instances = loadActivityInstanceData(handle, configuration, participant);

        // for each template, get the rendered doc
        List<PdfTemplate> templates = configuration.getTemplates();

        List<String> errors = new ArrayList<>();
        List<byte[]> individualPdfs = new ArrayList<>();
        byte[] pdf = null;
        for (int pdfOrderIndex = 0; pdfOrderIndex < templates.size(); pdfOrderIndex++) {
            PdfTemplate template = templates.get(pdfOrderIndex);
            switch (template.getType()) {
                case PHYSICIAN_INSTITUTION:
                    pdf = generatePhysicianInstitutionPdf(configuration.getStudyGuid(),
                            (PhysicianInstitutionTemplate) template, pdfOrderIndex, configuration.getConfigName(), participant, errors);
                    break;
                case CUSTOM:
                    pdf = generateCustomPdf((CustomTemplate) template,
                            pdfOrderIndex, configuration.getConfigName(), participant, instances, errors);
                    break;
                case MAILING_ADDRESS:
                    pdf = generateMailingAddressPdf((MailingAddressTemplate) template, participant.getUser(), errors,
                            configuration.getStudyGuid(), handle);
                    break;
                default:
                    errors.add("Tried to use a template type (" + template.getType() + ") that is unsupported.");
                    pdf = null;
                    break;
            }
            if (pdf != null && pdf.length != 0) {
                individualPdfs.add(pdf);
            }
        }
        checkForErrors(errors, userGuid);

        try (ByteArrayOutputStream renderedStream = new ByteArrayOutputStream();
                 PdfWriter pdfWriter = new PdfWriter(renderedStream);
                 PdfDocument mergedDoc = new PdfDocument(pdfWriter)) {
            // concatenate each rendered doc to a master doc

            int counter = 0;
            for (byte[] pdfStream : individualPdfs) {
                copyPdfToMasterDoc(pdfStream, counter, mergedDoc);
                counter++;
            }

            setOutputPdfMetadata(configuration, mergedDoc, instances);

            pdfWriter.flush();
            renderedStream.flush();
            mergedDoc.close();
            return renderedStream.toByteArray();
        }
    }

    private void setOutputPdfMetadata(PdfConfiguration config, PdfDocument pdfDoc, Map<Long, ActivityResponse> instances) {
        PdfDocumentInfo docInfo = pdfDoc.getDocumentInfo();
        docInfo.setTitle(config.getFilename());
        docInfo.setMoreInfo("studyGuid", config.getStudyGuid());
        docInfo.setMoreInfo("pdfConfigurationName", config.getConfigName());
        docInfo.setMoreInfo("pdfVersionTag", config.getVersion().getVersionTag());
        if (!instances.isEmpty()) {
            List<String> activityTags = instances.values()
                    .stream()
                    .map(ActivityResponse::getActivityTag)
                    .collect(Collectors.toList());
            docInfo.setMoreInfo("activityTags", String.join(",", activityTags));
        }
    }

    /**
     * Fetch participant data needed to generate the given pdf configuration. Attempts to query the minimum set of data that will satisfy
     * the pdf version data sources.
     *
     * @param handle   the database handle
     * @param config   the pdf configuration
     * @param userGuid the user guid
     * @return participant data to satisfy pdf generation
     */
    Participant loadParticipantData(Handle handle, PdfConfiguration config, String userGuid) {
        PdfVersion version = config.getVersion();
        boolean hasEmailSource = version.hasDataSource(PdfDataSourceType.EMAIL);
        boolean hasParticipantSource = version.hasDataSource(PdfDataSourceType.PARTICIPANT);

        Participant participant;
        if (hasParticipantSource) {
            participant = handle.attach(ParticipantDao.class)
                    .findParticipantsWithUserDataByUserGuids(config.getStudyId(), Sets.newHashSet(userGuid))
                    .findFirst()
                    .orElseThrow(() -> new DDPException("Could not find participant data for pdf generation with guid=" + userGuid));
        } else {
            participant = new Participant(null, handle.attach(UserDao.class)
                    .findUserByGuid(userGuid)
                    .orElseThrow(() -> new DDPException("Could not find participant user data for pdf generation with guid=" + userGuid)));
        }

        if (hasEmailSource) {
            var mgmtClient = Auth0Util.getManagementClientForStudy(handle, config.getStudyGuid());
            String auth0UserId = participant.getUser().getAuth0UserId();
            Map<String, String> emailResults = new Auth0Util(mgmtClient.getDomain())
                    .getUserPassConnEmailsByAuth0UserIds(Sets.newHashSet(auth0UserId), mgmtClient.getToken());
            participant.getUser().setEmail(emailResults.get(auth0UserId));
        }

        return participant;
    }

    /**
     * Fetch participant activity instance data needed to generate the given pdf configuration. Attempts to query the minimum set of data
     * that will satisfy the pdf version activity sources. The activity instances will be matched up and "assigned" to the allowed activity
     * sources of the pdf. Fetched instances will be stored on the given participant. If pdf version has no activity sources, no instances
     * will be fetched/assigned and given participant will be cleared of all instances.
     *
     * @param handle      the database handle
     * @param config      the pdf configuration
     * @param participant the participant
     * @return mapping of allowed activity id to user's activity instance
     */
    Map<Long, ActivityResponse> loadActivityInstanceData(Handle handle, PdfConfiguration config, Participant participant) {
        Map<Long, ActivityResponse> assignments = new HashMap<>();
        participant.clearAllResponses();

        Map<String, Set<String>> acceptedActivityVersions = config.getVersion().getAcceptedActivityVersions();
        if (!acceptedActivityVersions.isEmpty()) {
            Set<String> userGuids = Sets.newHashSet(participant.getUser().getGuid());
            Set<String> activityCodes = acceptedActivityVersions.keySet();
            handle.attach(ActivityInstanceDao.class)
                    .findFormResponsesSubsetWithAnswersByUserGuids(config.getStudyId(), userGuids, activityCodes)
                    .peek(FormResponse::unwrapComposites)
                    .forEach(participant::addResponse);

            for (Map.Entry<String, Set<String>> entry : acceptedActivityVersions.entrySet()) {
                String activityCode = entry.getKey();
                for (String versionTag : entry.getValue()) {
                    String activityTag = ActivityDef.getTag(activityCode, versionTag);
                    List<ActivityResponse> instances = participant.getResponses(activityTag);
                    if (!instances.isEmpty()) {
                        ActivityResponse instance = instances.stream()
                                .max(Comparator.comparing(inst -> inst.getLatestStatus().getUpdatedAt())).get();
                        assignments.put(instance.getActivityId(), instance);
                    }
                }
            }
        }

        return assignments;
    }

    private void checkForErrors(List<String> errors, String userGuid) {
        if (!errors.isEmpty()) {
            for (String error : errors) {
                LOG.error("PDF configuration error: " + error);
            }
            throw new DDPException("Errors while generating PDF for user: " + userGuid
                    + ". See logged PDF configuration errors for exact failures.");
        }
    }

    /**
     * Given a PdfDocument, rename each field to be previous name with an additional counter appended to
     * make each field unique. Solves issue where when merging docs, fields with matching names caused form
     * to not be filled out properly since new fields became children of other matching fields.
     *
     * @param doc     the PdfDocument
     * @param counter the place in the order of concatenated docs the pdf is
     */
    private void renameFields(PdfDocument doc, int counter) {
        PdfAcroForm form = PdfAcroForm.getAcroForm(doc, true);
        form.setGenerateAppearance(true);

        Set<String> keySet = form.getFormFields().keySet();
        String[] array = new String[keySet.size()];
        String[] fields = keySet.toArray(array);

        for (int i = 0; i < fields.length; i++) {
            String fieldName = fields[i];
            form.renameField(fieldName, fieldName + "_" + counter);
        }
    }

    /**
     * Given a pdf that has been rendered and the order in the pdfs it is, add it to the given doc.
     *
     * @param pdfStream rendered pdf
     * @param counter   order in pdfs this pdf is, used to renamed fields to maintain uniqueness
     * @param mergedDoc doc adding pdf to end of
     */
    private void copyPdfToMasterDoc(byte[] pdfStream, int counter, PdfDocument mergedDoc) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (PdfDocument sourceDoc = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdfStream)), new PdfWriter(baos))) {
            renameFields(sourceDoc, counter);
        }

        try (PdfDocument sourceDoc = new PdfDocument(new PdfReader(new ByteArrayInputStream(baos.toByteArray())))) {
            sourceDoc.copyPagesTo(COPY_START_PAGE, sourceDoc.getNumberOfPages(), mergedDoc, new PdfPageFormCopier());
        }
    }

    /**
     * For a given study and user, fill in the physician institution template for all relevant
     * physician institution information. When there are no physician/institution data, the INITIAL_BIOPSY type will
     * return a blank page, while the other types will return null.
     *
     * @param studyGuid the study guid
     * @param template  the template to be filled out n times depending on how many dtos are found.
     * @return byte array of rendered physician institution pdf with correctly filled in fields
     */
    byte[] generatePhysicianInstitutionPdf(String studyGuid,
                                           PhysicianInstitutionTemplate template,
                                           int pdfOrderIndex,
                                           String pdfConfigurationName,
                                           Participant participant,
                                           List<String> errors) throws IOException {
        List<MedicalProviderDto> dtos = participant.getProviders().stream()
                .filter(provider -> provider.getInstitutionType() == template.getInstitutionType())
                .collect(Collectors.toList());

        String userGuid = participant.getUser().getGuid();
        if (template.getInstitutionType() == InstitutionType.INITIAL_BIOPSY) {
            return generateBiopsyPdf(userGuid, studyGuid, dtos, template, pdfOrderIndex, pdfConfigurationName, errors);
        }

        if (dtos.isEmpty()) {
            return null;
        }

        try (ByteArrayOutputStream rendered = new ByteArrayOutputStream()) {
            int pagesWritten = mergeAllProviderPdfsIntoOutput(rendered, dtos, template, pdfOrderIndex, pdfConfigurationName, errors);
            checkForErrors(errors, userGuid);

            return (pagesWritten > 0 ? rendered.toByteArray() : null);
        }
    }

    /**
     * Helper to generate pdf page for biopsy institution. Will always return a pdf page, although it might be blank.
     */
    private byte[] generateBiopsyPdf(String userGuid, String studyGuid, List<MedicalProviderDto> providerDtos,
                                     PhysicianInstitutionTemplate template, int pdfOrderIndex, String pdfConfigurationName,
                                     List<String> errors) throws IOException {
        if (providerDtos == null || providerDtos.isEmpty()) {
            return template.getRawBytes();
        }

        if (providerDtos.size() > 1) {
            // This is an erroneous scenario but pdf generation doesn't need to halt.
            LOG.error("User {} in study {} has {} biopsy institutions", userGuid, studyGuid, providerDtos.size());
        }

        try (ByteArrayOutputStream rendered = new ByteArrayOutputStream()) {
            int pagesWritten = mergeAllProviderPdfsIntoOutput(rendered, providerDtos, template, pdfOrderIndex,
                                                              pdfConfigurationName, errors);
            checkForErrors(errors, userGuid);

            return (pagesWritten > 0 ? rendered.toByteArray() : template.getRawBytes());
        }
    }

    /**
     * Helper to convert given medical providers into pdfs and write them into given output stream.
     *
     * @return number of pages actually written to output
     */
    private int mergeAllProviderPdfsIntoOutput(ByteArrayOutputStream output, List<MedicalProviderDto> providerDtos,
                                               PhysicianInstitutionTemplate template, int pdfOrderIndex, String pdfConfigurationName,
                                               List<String> errors) throws IOException {
        PdfDocument master = new PdfDocument(new PdfWriter(output));
        int pagesWritten = 0;

        try {
            int currentDocumentIndex = 0;
            for (MedicalProviderDto dto : providerDtos) {
                if (dto.isBlank()) {
                    continue;
                }
                byte[] rendered = convertMedicalProviderDtoToPdf(dto, template.asByteStream(),
                        template, pdfOrderIndex, pdfConfigurationName, errors);
                if (rendered == null) {
                    break;
                }
                copyPdfToMasterDoc(rendered, currentDocumentIndex, master);
                currentDocumentIndex += 1;
            }
        } finally {
            pagesWritten = master.getNumberOfPages();
            if (pagesWritten > 0) {
                master.close();
            }
        }

        return pagesWritten;
    }

    /**
     * Given the information in a dto, insert it into an existing template for physician institution information
     * and create a PDF.
     *
     * @param medicalProviderDto the medical provider dto
     * @param inputStream        the existing template
     * @param template           the physician institution template it is
     * @return stream of the rendered pdf
     */
    private byte[] convertMedicalProviderDtoToPdf(MedicalProviderDto medicalProviderDto, InputStream inputStream,
                                                  PhysicianInstitutionTemplate template, int pdfOrderIndex,
                                                  String pdfConfigurationName,
                                                  List<String> errors) throws IOException {

        try (ByteArrayOutputStream renderedStream = new ByteArrayOutputStream();
                PdfDocument renderedPdf = new PdfDocument(new PdfReader(inputStream), new PdfWriter(renderedStream))) {

            PdfAcroForm form = PdfAcroForm.getAcroForm(renderedPdf, true);
            form.setGenerateAppearance(true);

            Map<String, PdfFormField> fields = form.getFormFields();

            if (!fields.containsKey(template.getInstitutionNamePlaceholder())
                    || !fields.containsKey(template.getCityPlaceholder())
                    || !fields.containsKey(template.getStatePlaceholder())
                    || (
                    template.getInstitutionType() == InstitutionType.PHYSICIAN
                            && !fields.containsKey(template.getPhysicianNamePlaceholder()))) {
                errors.add("template " + pdfOrderIndex + " for configuration " + pdfConfigurationName
                                   + " is missing necessary fields");
                return null;
            }

            setTextFieldValue(fields.get(template.getInstitutionNamePlaceholder()), medicalProviderDto.getInstitutionName());
            setTextFieldValue(fields.get(template.getCityPlaceholder()), medicalProviderDto.getCity());
            setTextFieldValue(fields.get(template.getStatePlaceholder()), medicalProviderDto.getState());

            if (template.getInstitutionType() == InstitutionType.PHYSICIAN) {
                PdfFormField nameField = fields.get(template.getPhysicianNamePlaceholder());
                setTextFieldValue(nameField, medicalProviderDto.getPhysicianName());
            }

            Map<String, String> optionalPlaceholders = new HashMap<>();
            optionalPlaceholders.put(template.getStreetPlaceholder(), medicalProviderDto.getStreet());
            optionalPlaceholders.put(template.getZipPlaceholder(), medicalProviderDto.getPostalCode());
            optionalPlaceholders.put(template.getPhonePlaceholder(), medicalProviderDto.getPhone());

            for (Map.Entry<String, String> entry : optionalPlaceholders.entrySet()) {
                String placeholder = entry.getKey();
                if (placeholder != null) {
                    if (fields.containsKey(placeholder)) {
                        setTextFieldValue(fields.get(placeholder), entry.getValue());
                    } else {
                        errors.add(String.format("template %d for configuration %s is missing field '%s'",
                                pdfOrderIndex, pdfConfigurationName, placeholder));
                        return null;
                    }
                }
            }

            renderedPdf.close();
            return renderedStream.toByteArray();
        }
    }

    /**
     * Loads a font that is generally useful for writing out non-latin symbols
     */
    private PdfFont loadInternationalFont() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        byte[] freeSans;
        try {
            freeSans = IOUtils.toByteArray(classLoader.getResourceAsStream(FONT_PATH));
            return PdfFontFactory.createFont(freeSans, PdfEncodings.IDENTITY_H, true);
        } catch (IOException e) {
            throw new RuntimeException("Could not load font " + FONT_PATH, e);
        }

    }

    /**
     * Sets the text field's value, checking first to see if value is non-null and if we should use
     * a more i18n-friendly font and setting it as necessary.
     */
    private void setTextFieldValue(PdfFormField field, String value) throws IOException {
        if (value == null) {
            return;
        }

        if (!ISO88591.get().canEncode(value)) {
            PdfFont internationalFont = loadInternationalFont();
            field.setFont(internationalFont);
        } else {
            field.setFont(PdfFontFactory.createFont());
        }

        field.setValue(value);
    }

    /**
     * For a given user, fill in all relevant information for custom substitutions, which can be of two types,
     * answer (which is the user's answer to a given question for an activity instance) or profile
     * (which is information regarding the user stored in the database such as first name). Custom substitutions
     * hold the placeholder in the form that will be replaced with the value determined by the query.
     *
     * @param template the blank template to be rendered into a completed PDF
     * @return byte array of rendered pdf for custom substitutions
     */
    byte[] generateCustomPdf(CustomTemplate template,
                             int pdfOrderIndex,
                             String pdfConfigurationName,
                             Participant participant, Map<Long, ActivityResponse> instances, List<String> errors) throws IOException {
        InputStream templateStream = template.asByteStream();
        try (ByteArrayOutputStream renderedStream = new ByteArrayOutputStream();
                PdfDocument renderedPdf = new PdfDocument(new PdfReader(templateStream), new PdfWriter(renderedStream))) {

            PdfAcroForm form = PdfAcroForm.getAcroForm(renderedPdf, true);
            form.setGenerateAppearance(true);

            for (PdfSubstitution substitution : template.getSubstitutions()) {
                SubstitutionType type = substitution.getType();
                switch (type) {
                    case PROFILE:
                        convertSubstitutionToPdf((ProfileSubstitution) substitution, form,
                                                 pdfOrderIndex, pdfConfigurationName, participant.getUser(), errors);
                        break;
                    case ANSWER:
                        convertSubstitutionToPdf((AnswerSubstitution) substitution, form,
                                                 participant, instances,
                                                 errors);
                        break;
                    case ACTIVITY_DATE:
                        convertSubstitutionToPdf((ActivityDateSubstitution) substitution, form,
                                                 participant, instances, errors);
                        break;
                    default:
                        errors.add("Tried to use unsupported custom substitution type: " + type.toString());
                        break;
                }
            }
            renderedStream.flush();
            renderedPdf.close();

            return renderedStream.toByteArray();
        }
    }

    /**
     * Apply date substitution into pdf. Will use latest UTC date activity instance status became "COMPLETE"
     *
     * @param substitution the substitution
     * @param form         pdf form
     */
    private void convertSubstitutionToPdf(ActivityDateSubstitution substitution, PdfAcroForm form,
                                          Participant participant, Map<Long, ActivityResponse> instances, List<String> errors) {
        ActivityResponse instance = instances.get(substitution.getActivityId());
        if (instance == null) {
            errors.add(String.format("Did not find activity instance for user guid %s and activity id %d."
                    + " Required for PDF ACTIVITY_DATE substitutions", participant.getUser().getGuid(), substitution.getActivityId()));
            return;
        }

        if (instance.getLatestStatus().getType() == InstanceStatusType.COMPLETE) {
            PdfFormField pdfField = form.getField(substitution.getPlaceholder());
            if (pdfField == null) {
                String msg = String.format("Could not find field with name: %s in body of PDF template", substitution.getPlaceholder());
                errors.add(msg);
            } else {
                try {
                    setTextFieldValue(pdfField,
                            DateTimeFormatUtils.convertUtcMillisToDefaultDateString(instance.getLatestStatus().getUpdatedAt()));
                } catch (IOException e) {
                    errors.add("Error setting text value during PDF substitution." + e.getMessage());
                }
            }
        } else {
            String msg = String.format("Did not find activity status COMPLETE for activity instance with guid=%s", instance.getGuid());
            errors.add(msg);
        }
    }

    /**
     * Given a ProfileSubstitution to make in an existing form for a specific user, query the database for the
     * required information and insert it into the PDF.
     *
     * @param substitution the given profile substitution
     * @param form         the PDF that is being rendered
     */
    private void convertSubstitutionToPdf(ProfileSubstitution substitution, PdfAcroForm form,
                                          int pdfOrderIndex, String pdfConfigurationName, User user, List<String> errors) {
        String fieldName = substitution.getFieldName();
        if (!ProfileSubstitution.SUPPORTED_PROFILE_FIELDS.contains(fieldName)) {
            errors.add("Unsupported pdf substitution profile field name '" + fieldName + "'");
            return;
        }

        String value = null;
        if ("hruid".equals(fieldName)) {
            value = user.getHruid();
        } else if ("email".equals(fieldName)) {
            value = user.getEmail();
        } else if ("first_name".equals(fieldName)) {
            value = user.hasProfile() ? user.getProfile().getFirstName() : null;
        } else if ("last_name".equals(fieldName)) {
            value = user.hasProfile() ? user.getProfile().getLastName() : null;
        }

        if (value != null) {
            String placeholder = substitution.getPlaceholder();
            if (form.getField(placeholder) == null) {
                errors.add("template " + pdfOrderIndex + " for configuration " + pdfConfigurationName
                        + " is missing necessary field: " + placeholder);
            } else {
                try {
                    setTextFieldValue(form.getField(placeholder), value);
                } catch (IOException e) {
                    errors.add("Error setting text field " + e.getMessage());
                }
            }
        } else {
            errors.add("User " + user.getGuid() + " did not have profile value for " + fieldName);
        }
    }

    /**
     * Given an answer substitution to make in an existing form for a specific user, query the database for the
     * user's response to a question in the activity instance and insert it into the PDF.
     *
     * @param substitution the given answer substitution
     * @param form         the PDF that is being rendered
     */
    private void convertSubstitutionToPdf(AnswerSubstitution substitution,
                                          PdfAcroForm form, Participant participant, Map<Long, ActivityResponse> instances,
                                          List<String> errors) throws IOException {
        if (!instances.containsKey(substitution.getActivityId())) {
            errors.add(String.format("Did not find activity instance for user guid %s and activityId=%d questionStableId=%s."
                    + " Required for PDF ANSWER substitutions", participant.getUser().getGuid(),
                    substitution.getActivityId(), substitution.getQuestionStableId()));
            return;
        }

        // Note: currently only form activities have answers.
        FormResponse instance = (FormResponse) instances.get(substitution.getActivityId());

        String placeholder = substitution.getPlaceholder();
        PdfFormField field = form.getField(placeholder);
        if (field == null) {
            errors.add(String.format("Could not find PDFFormField field with name: %s", placeholder));
            return;
        }

        field.setFont(PdfFontFactory.createFont());

        Answer answer = instance.getAnswer(substitution.getQuestionStableId());
        switch (substitution.getQuestionType()) {
            case BOOLEAN:
                BooleanAnswerSubstitution booleanSubstitution = (BooleanAnswerSubstitution) substitution;
                Boolean boolValue = answer == null ? null : ((BoolAnswer) answer).getValue();
                if (boolValue != null) {
                    boolean shouldCheck;
                    if (booleanSubstitution.checkIfFalse()) {
                        shouldCheck = !boolValue;
                    } else {
                        shouldCheck = boolValue;
                    }
                    setIsChecked(field, shouldCheck);
                }
                break;
            case TEXT:
                String textValue = answer == null ? null : ((TextAnswer) answer).getValue();
                if (textValue != null) {
                    field.setValue(textValue);
                }
                break;
            case DATE:
                DateValue dateValue = answer == null ? null : ((DateAnswer) answer).getValue();
                if (dateValue != null) {
                    field.setValue(dateValue.toDefaultDateFormat());
                }
                break;
            case PICKLIST:
                //sets selected option stableIds.
                List<String> selectedOptions = new ArrayList<>();
                for (SelectedPicklistOption option : ((PicklistAnswer) answer).getValue()) {
                    selectedOptions.add(option.getStableId());
                }
                if (CollectionUtils.isNotEmpty(selectedOptions)) {
                    field.setValue(String.join(", ", selectedOptions));
                }
                break;

            default:
                errors.add("tried to use an unsupported answer type " + substitution.getQuestionType());
                return;
        }
    }


    public byte[] generateMailingAddressPdf(MailingAddressTemplate template, User user,
                                            List<String> errors, String studyGuid, Handle handle) throws IOException {

        InputStream templateStream = template.asByteStream();
        try (ByteArrayOutputStream renderedStream = new ByteArrayOutputStream();
                PdfDocument renderedPdf = new PdfDocument(new PdfReader(templateStream), new PdfWriter(renderedStream))) {

            PdfAcroForm form = PdfAcroForm.getAcroForm(renderedPdf, true);
            form.setGenerateAppearance(true);

            List<String> placeholders = template.getRequiredPlaceholders();
            if (template.getCountryPlaceholder() != null) {
                placeholders.add(template.getCountryPlaceholder());
            }
            if (template.getFirstNamePlaceholder() != null) {
                placeholders.add(template.getFirstNamePlaceholder());
            }
            if (template.getLastNamePlaceholder() != null) {
                placeholders.add(template.getLastNamePlaceholder());
            }
            if (template.getProxyFirstNamePlaceholder() != null) {
                placeholders.add(template.getProxyFirstNamePlaceholder());
            }
            if (template.getProxyLastNamePlaceholder() != null) {
                placeholders.add(template.getProxyLastNamePlaceholder());
            }

            Optional<String> fieldNotFound = placeholders.stream()
                    .filter(placeholder -> form.getField(placeholder) == null)
                    .findAny();

            if (fieldNotFound.isPresent()) {
                errors.add("Could not find PDFFormField field with name: " + fieldNotFound.get());
                return null;
            }

            MailAddress address = user.getAddress();
            if (address == null) {
                LOG.error("Could not find default address for user {}, continuing with empty address", user.getGuid());
                address = new MailAddress("", "", "", "", "", "", "", "", "", "",
                        DsmAddressValidationStatus.DSM_INVALID_ADDRESS_STATUS, false);
            }

            UserProfile userProfile = user.getProfile();
            Map<String, String> placeholderToValue = new HashMap<>();

            //check if we need proxy name
            if (StringUtils.isNotBlank(template.getProxyFirstNamePlaceholder())
                    || StringUtils.isNotBlank(template.getProxyLastNamePlaceholder())) {
                Governance governance = null;
                UserGovernanceDao userGovernanceDao = handle.attach(UserGovernanceDao.class);
                List<Governance> governances = userGovernanceDao.findActiveGovernancesByParticipantAndStudyGuids(user.getGuid(), studyGuid)
                        .collect(Collectors.toList());
                if (governances.isEmpty()) {
                    String errorMessage = String.format("No proxy found for participant %s in study %s to substitute proxy name ",
                            user.getGuid(), studyGuid);
                    LOG.error(errorMessage);
                    errors.add(errorMessage);
                } else {
                    if (governances.size() > 1) {
                        LOG.warn("Multiple proxies found for participant {} in study {} , using first one ", user.getGuid(), studyGuid);
                    }
                    governance = governances.get(0);
                    //get proxy userProfile to substitute first & last names
                    String proxyUserGuid = governance.getProxyUserGuid();
                    userProfile = handle.attach(UserProfileDao.class).findProfileByUserGuid(proxyUserGuid)
                            .orElseThrow(() -> new DDPException("Could not find profile for proxy user with guid " + proxyUserGuid));
                    if (StringUtils.isNotBlank(template.getProxyFirstNamePlaceholder())) {
                        placeholderToValue.put(template.getProxyFirstNamePlaceholder(), userProfile.getFirstName());
                    }
                    if (StringUtils.isNotBlank(template.getProxyLastNamePlaceholder())) {
                        placeholderToValue.put(template.getProxyLastNamePlaceholder(), userProfile.getLastName());
                    }
                }
            }

            if (template.getFirstNamePlaceholder() != null) {
                placeholderToValue.put(template.getFirstNamePlaceholder(), userProfile.getFirstName());
            }
            if (template.getLastNamePlaceholder() != null) {
                placeholderToValue.put(template.getLastNamePlaceholder(), userProfile.getLastName());
            }
            placeholderToValue.put(template.getStreetPlaceholder(), address.getCombinedStreet());
            placeholderToValue.put(template.getCityPlaceholder(), address.getCity());
            placeholderToValue.put(template.getStatePlaceholder(), address.getState());
            placeholderToValue.put(template.getPhonePlaceholder(), address.getPhone());
            placeholderToValue.put(template.getZipPlaceholder(), address.getZip());
            if (template.getCountryPlaceholder() != null) {
                placeholderToValue.put(template.getCountryPlaceholder(), address.getCountry());
            }

            for (Map.Entry<String, String> placeholderValue : placeholderToValue.entrySet()) {
                // set fields only if they are non-null
                String value = placeholderValue.getValue();
                if (value != null) {
                    form.getField(placeholderValue.getKey()).setValue(placeholderValue.getValue());
                }
            }

            renderedStream.flush();
            renderedPdf.close();
            return renderedStream.toByteArray();
        }
    }
}
