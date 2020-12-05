package org.broadinstitute.ddp.export.collectors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.export.ComponentDataSupplier;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;
import org.broadinstitute.ddp.model.activity.definition.ComponentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.GroupBlockDef;
import org.broadinstitute.ddp.model.activity.definition.MailingAddressComponentDef;
import org.broadinstitute.ddp.model.activity.definition.PhysicianInstitutionComponentDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.question.AgreementQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.NumericQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.ActivityResponse;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.activity.instance.answer.AgreementAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.AnswerRow;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.types.ActivityType;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;

public class ActivityResponseCollector {

    private ActivityDef definition;
    private List<String> responseHeaders = null;
    private List<String> deprecatedHeaders = null;
    private List<String> allHeaders = null;

    private AgreementQuestionFormatStrategy agreementFmt = new AgreementQuestionFormatStrategy();
    private BoolQuestionFormatStrategy boolFmt = new BoolQuestionFormatStrategy();
    private TextQuestionFormatStrategy textFmt = new TextQuestionFormatStrategy();
    private DateQuestionFormatStrategy dateFmt = new DateQuestionFormatStrategy();
    private NumericQuestionFormatStrategy numericFmt = new NumericQuestionFormatStrategy();
    private PicklistQuestionFormatStrategy picklistFmt = new PicklistQuestionFormatStrategy();
    private CompositeQuestionFormatStrategy compositeFmt = new CompositeQuestionFormatStrategy();
    private MailingAddressFormatter addressFmt = new MailingAddressFormatter();
    private MedicalProviderFormatter providerFmt = new MedicalProviderFormatter();

    public ActivityResponseCollector(ActivityDef definition) {
        this.definition = definition;
    }

    public List<String> emptyRow() {
        String[] arr = new String[responseHeaders.size() + deprecatedHeaders.size()];
        Arrays.fill(arr, "");
        return Arrays.asList(arr);
    }

    public Map<String, String> emptyRecord(String defaultRecordValue) {
        return records(null, null, true, defaultRecordValue);
    }

    public Map<String, String> records(ActivityResponse instance, ComponentDataSupplier supplier, String defaultRecordValue) {
        return records(instance, supplier, false, defaultRecordValue);
    }

    /**
     * Returns the record list for a participant and activity in a sorted map.
     *
     * @param instance the participant's activity instance
     * @param supplier the supplier that gives data for embedded components
     * @return
     */
    private Map<String, String> records(ActivityResponse instance, ComponentDataSupplier supplier,
                                        boolean isEmpty, String defaultRecordValue) {
        List<String> headers = getHeaders();
        List<String> values = null;
        if (!isEmpty) {
            values = format(instance, supplier, defaultRecordValue);
        }

        Map<String, String> records = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            records.put(headers.get(i), isEmpty ? null : values.get(i));
        }

        return records;
    }

    public Map<String, Object> mappings() {
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> deprecatedProps = new LinkedHashMap<>();

        if (definition.getActivityType() == ActivityType.FORMS) {
            FormActivityDef def = (FormActivityDef) definition;
            if (def.getIntroduction() != null) {
                def.getIntroduction().getBlocks().forEach(block -> collectBlockMappings(props, deprecatedProps, block));
            }
            for (FormSectionDef sectionDef : def.getSections()) {
                sectionDef.getBlocks().forEach(block -> collectBlockMappings(props, deprecatedProps, block));
            }
            if (def.getClosing() != null) {
                def.getClosing().getBlocks().forEach(block -> collectBlockMappings(props, deprecatedProps, block));
            }
        }

        props.putAll(deprecatedProps);
        return props;
    }

    public Map<String, Object> questionDefinitions() {
        Map<String, Object> props = new LinkedHashMap<>();

        if (definition.getActivityType() == ActivityType.FORMS) {
            FormActivityDef def = (FormActivityDef) definition;
            List<Object> questions = new ArrayList<>();

            if (def.getIntroduction() != null) {
                def.getIntroduction().getBlocks().forEach(block -> collectBlockDefinitions(questions, block));
            }
            for (FormSectionDef sectionDef : def.getSections()) {
                sectionDef.getBlocks().forEach(block -> collectBlockDefinitions(questions, block));
            }
            if (def.getClosing() != null) {
                def.getClosing().getBlocks().forEach(block -> collectBlockDefinitions(questions, block));
            }
            props.put("questions", questions);
        }

        return props;
    }

    private void collectBlockDefinitions(List<Object> props, FormBlockDef blockDef) {
        switch (blockDef.getBlockType()) {
            case CONTENT:
                break;  //nothing to do
            case QUESTION:
                collectQuestionDefinitions(props, ((QuestionBlockDef) blockDef).getQuestion());
                break;
            case COMPONENT:
                collectComponentDefinitions(props, ((ComponentBlockDef) blockDef));
                break;
            case CONDITIONAL:
                ConditionalBlockDef condBlockDef = (ConditionalBlockDef) blockDef;
                collectQuestionDefinitions(props, condBlockDef.getControl());
                condBlockDef.getNested().forEach(nested -> collectBlockDefinitions(props, nested));
                break;
            case GROUP:
                GroupBlockDef groupBlockDef = (GroupBlockDef) blockDef;
                groupBlockDef.getNested().forEach(nested -> collectBlockDefinitions(props, nested));
                break;
            default:
                throw new DDPException("Unhandled block type " + blockDef.getBlockType());
        }
    }

    private void collectBlockMappings(Map<String, Object> props, Map<String, Object> deprecatedProps, FormBlockDef blockDef) {
        switch (blockDef.getBlockType()) {
            case CONTENT:
                break;  // nothing to do
            case QUESTION:
                collectQuestionMappings(props, deprecatedProps, ((QuestionBlockDef) blockDef).getQuestion());
                break;
            case COMPONENT:
                collectComponentMappings(props, (ComponentBlockDef) blockDef);
                break;
            case CONDITIONAL:
                ConditionalBlockDef condBlockDef = (ConditionalBlockDef) blockDef;
                collectQuestionMappings(props, deprecatedProps, condBlockDef.getControl());
                condBlockDef.getNested().forEach(nested -> collectBlockMappings(props, deprecatedProps, nested));
                break;
            case GROUP:
                GroupBlockDef groupBlockDef = (GroupBlockDef) blockDef;
                groupBlockDef.getNested().forEach(nested -> collectBlockMappings(props, deprecatedProps, nested));
                break;
            default:
                throw new DDPException("Unhandled block type " + blockDef.getBlockType());
        }
    }

    private void collectQuestionMappings(Map<String, Object> props, Map<String, Object> deprecatedProps, QuestionDef questionDef) {
        Map<String, Object> currProps = props;
        if (questionDef.isDeprecated()) {
            currProps = deprecatedProps;
        }
        switch (questionDef.getQuestionType()) {
            case AGREEMENT:
                currProps.putAll(agreementFmt.mappings((AgreementQuestionDef) questionDef));
                break;
            case BOOLEAN:
                currProps.putAll(boolFmt.mappings((BoolQuestionDef) questionDef));
                break;
            case TEXT:
                currProps.putAll(textFmt.mappings((TextQuestionDef) questionDef));
                break;
            case DATE:
                currProps.putAll(dateFmt.mappings((DateQuestionDef) questionDef));
                break;
            case NUMERIC:
                currProps.putAll(numericFmt.mappings((NumericQuestionDef) questionDef));
                break;
            case PICKLIST:
                currProps.putAll(picklistFmt.mappings((PicklistQuestionDef) questionDef));
                break;
            case COMPOSITE:
                CompositeQuestionDef composite = (CompositeQuestionDef) questionDef;
                if (composite.shouldUnwrapChildQuestions()) {
                    composite.getChildren().forEach(child -> collectQuestionMappings(props, deprecatedProps, child));
                } else {
                    currProps.putAll(compositeFmt.mappings(composite));
                }
                break;
            default:
                throw new DDPException("Unhandled question type " + questionDef.getQuestionType());
        }
    }

    private void collectQuestionDefinitions(List<Object> questions, QuestionDef questionDef) {
        switch (questionDef.getQuestionType()) {
            case AGREEMENT:
                questions.add(agreementFmt.questionDef((AgreementQuestionDef) questionDef));
                break;
            case BOOLEAN:
                questions.add(boolFmt.questionDef((BoolQuestionDef) questionDef));
                break;
            case TEXT:
                questions.add(textFmt.questionDef((TextQuestionDef) questionDef));
                break;
            case DATE:
                questions.add(dateFmt.questionDef((DateQuestionDef) questionDef));
                break;
            case NUMERIC:
                questions.add(numericFmt.questionDef((NumericQuestionDef) questionDef));
                break;
            case PICKLIST:
                questions.add(picklistFmt.questionDef((PicklistQuestionDef) questionDef));
                break;
            case COMPOSITE:
                CompositeQuestionDef composite = (CompositeQuestionDef) questionDef;
                if (composite.shouldUnwrapChildQuestions()) {
                    composite.getChildren().forEach(child -> collectQuestionDefinitions(questions, child));
                } else {
                    //watchout: both parent and children definitions are included
                    Map<String, Object> compositeDef = compositeFmt.questionDef(composite);
                    List<Object> childQuestions = new ArrayList<>();
                    composite.getChildren().forEach(child -> collectQuestionDefinitions(childQuestions, child));
                    if (!childQuestions.isEmpty()) {
                        compositeDef.put("childQuestions", childQuestions);
                    }
                    questions.add(compositeDef);
                }
                break;
            default:
                throw new DDPException("Unhandled question type " + questionDef.getQuestionType());
        }
    }

    private void collectComponentDefinitions(List<Object> questions, ComponentBlockDef componentBlockDef) {
        String questionText;
        switch (componentBlockDef.getComponentType()) {
            case MAILING_ADDRESS:
                MailingAddressComponentDef mailingAddressComponentDef = (MailingAddressComponentDef) componentBlockDef;
                if (mailingAddressComponentDef.getTitleTemplate() != null) {
                    questionText = mailingAddressComponentDef.getTitleTemplate().render("en");
                } else {
                    questionText = "Your contact information";
                }
                questions.add(addressFmt.definition(questionText));
                break;
            case PHYSICIAN: //fall-through
            case INSTITUTION:
                PhysicianInstitutionComponentDef physicianInstitutionDef = (PhysicianInstitutionComponentDef) componentBlockDef;
                if (physicianInstitutionDef.getTitleTemplate() != null) {
                    questionText = physicianInstitutionDef.getTitleTemplate().render("en");
                } else {
                    questionText = physicianInstitutionDef.getInstitutionType().name();
                }
                questions.add(providerFmt.definition((PhysicianInstitutionComponentDef) componentBlockDef, questionText));
                break;
            default:
                throw new DDPException("Unhandled component type " + componentBlockDef.getComponentType());
        }
    }

    private void collectComponentMappings(Map<String, Object> props, ComponentBlockDef componentBlockDef) {
        switch (componentBlockDef.getComponentType()) {
            case MAILING_ADDRESS:
                props.putAll(addressFmt.mappings());
                break;
            case PHYSICIAN:     // fall-through
            case INSTITUTION:
                props.putAll(providerFmt.mappings((PhysicianInstitutionComponentDef) componentBlockDef));
                break;
            default:
                throw new DDPException("Unhandled component type " + componentBlockDef.getComponentType());
        }
    }

    /**
     * Constructs a row of headers for the activity definition that this formatter is responsible for, and keeps this list around for later
     * use in formatting row data records.
     *
     * @return a list of header strings
     */
    public synchronized List<String> getHeaders() {
        if (responseHeaders == null || deprecatedHeaders == null) {
            responseHeaders = new LinkedList<>();
            deprecatedHeaders = new LinkedList<>();
            if (definition.getActivityType() == ActivityType.FORMS) {
                flattenHeadersByOrderedDepthTraversal((FormActivityDef) definition);
            }
            allHeaders = new LinkedList<>();
            allHeaders.addAll(responseHeaders);
            allHeaders.addAll(deprecatedHeaders);
        }
        return allHeaders;
    }

    private void flattenHeadersByOrderedDepthTraversal(FormActivityDef def) {
        if (def.getIntroduction() != null) {
            def.getIntroduction().getBlocks().forEach(block -> collectBlockIntoHeaders(block));
        }
        for (FormSectionDef sectionDef : def.getSections()) {
            sectionDef.getBlocks().forEach(block -> collectBlockIntoHeaders(block));
        }
        if (def.getClosing() != null) {
            def.getClosing().getBlocks().forEach(block -> collectBlockIntoHeaders(block));
        }
    }

    private void collectBlockIntoHeaders(FormBlockDef blockDef) {
        switch (blockDef.getBlockType()) {
            case CONTENT:
                break;  // nothing to do
            case QUESTION:
                collectQuestionIntoHeaders(((QuestionBlockDef) blockDef).getQuestion());
                break;
            case COMPONENT:
                collectComponentIntoHeaders((ComponentBlockDef) blockDef);
                break;
            case CONDITIONAL:
                ConditionalBlockDef condBlockDef = (ConditionalBlockDef) blockDef;
                collectQuestionIntoHeaders(condBlockDef.getControl());
                condBlockDef.getNested().forEach(nested -> collectBlockIntoHeaders(nested));
                break;
            case GROUP:
                GroupBlockDef groupBlockDef = (GroupBlockDef) blockDef;
                groupBlockDef.getNested().forEach(nested -> collectBlockIntoHeaders(nested));
                break;
            default:
                throw new DDPException("Unhandled block type " + blockDef.getBlockType());
        }
    }

    private void collectQuestionIntoHeaders(QuestionDef questionDef) {
        List<String> headers = responseHeaders;
        if (questionDef.isDeprecated()) {
            headers = deprecatedHeaders;
        }
        switch (questionDef.getQuestionType()) {
            case AGREEMENT:
                headers.addAll(agreementFmt.headers((AgreementQuestionDef) questionDef));
                break;
            case BOOLEAN:
                headers.addAll(boolFmt.headers((BoolQuestionDef) questionDef));
                break;
            case TEXT:
                headers.addAll(textFmt.headers((TextQuestionDef) questionDef));
                break;
            case DATE:
                headers.addAll(dateFmt.headers((DateQuestionDef) questionDef));
                break;
            case NUMERIC:
                headers.addAll(numericFmt.headers((NumericQuestionDef) questionDef));
                break;
            case PICKLIST:
                headers.addAll(picklistFmt.headers((PicklistQuestionDef) questionDef));
                break;
            case COMPOSITE:
                CompositeQuestionDef composite = (CompositeQuestionDef) questionDef;
                //if (composite.shouldUnwrapChildQuestions()) {
                for (int i = 0; i < 5; i++) {
                    composite.getChildren().forEach(this::collectQuestionIntoHeaders);
                }
                //} else {
                //    headers.addAll(compositeFmt.headers(composite));
                //}
                break;
            default:
                throw new DDPException("Unhandled question type " + questionDef.getQuestionType());
        }
    }

    private void collectComponentIntoHeaders(ComponentBlockDef componentBlockDef) {
        switch (componentBlockDef.getComponentType()) {
            case MAILING_ADDRESS:
                responseHeaders.addAll(addressFmt.headers());
                break;
            case PHYSICIAN:     // fall-through
            case INSTITUTION:
                responseHeaders.addAll(providerFmt.headers((PhysicianInstitutionComponentDef) componentBlockDef));
                break;
            default:
                throw new DDPException("Unhandled component type " + componentBlockDef.getComponentType());
        }
    }

    /**
     * Constructs a row for the given data, and ensuring that the row record is sorted in the same order as the headers that should have
     * been computed before this.
     *
     * @param instance the participant's activity instance
     * @param supplier the supplier that gives data for embedded components
     * @return a sorted row data record
     */
    public List<String> format(ActivityResponse instance, ComponentDataSupplier supplier, String defaultRecordValue) {
        Map<String, String> record = new HashMap<>();
        if (definition.getActivityType() == ActivityType.FORMS && instance.getType() == ActivityType.FORMS) {
            FormActivityDef def = (FormActivityDef) definition;
            FormResponse form = (FormResponse) instance;
            for (FormSectionDef section : def.getAllSections()) {
                for (FormBlockDef block : section.getBlocks()) {
                    collectBlockIntoRecord(record, block, form, supplier);
                }
            }
        }
        return alignRecordToHeaders(record, defaultRecordValue);
    }

    private List<String> alignRecordToHeaders(Map<String, String> record, String defaultRecordValue) {
        List<String> row = new LinkedList<>();
        for (String header : responseHeaders) {
            row.add(record.getOrDefault(header, defaultRecordValue));
        }
        for (String header : deprecatedHeaders) {
            row.add(record.getOrDefault(header, defaultRecordValue));
        }
        return row;
    }

    private void collectBlockIntoRecord(Map<String, String> record, FormBlockDef block,
                                        FormResponse instance, ComponentDataSupplier supplier) {
        switch (block.getBlockType()) {
            case CONTENT:
                break;  // nothing to do
            case QUESTION:
                collectQuestionIntoRecord(record, ((QuestionBlockDef) block).getQuestion(), instance);
                break;
            case COMPONENT:
                collectComponentIntoRecord(record, (ComponentBlockDef) block, supplier);
                break;
            case CONDITIONAL:
                ConditionalBlockDef condBlock = (ConditionalBlockDef) block;
                collectQuestionIntoRecord(record, condBlock.getControl(), instance);
                condBlock.getNested().forEach(nested -> collectBlockIntoRecord(record, nested, instance, supplier));
                break;
            case GROUP:
                GroupBlockDef groupBlock = (GroupBlockDef) block;
                groupBlock.getNested().forEach(nested -> collectBlockIntoRecord(record, nested, instance, supplier));
                break;
            default:
                throw new DDPException("Unhandled block type " + block.getBlockType());
        }
    }

    private void collectQuestionIntoRecord(Map<String, String> record, QuestionDef question, FormResponse instance) {
        Answer answer = instance.getAnswer(question.getStableId());
        if (answer == null) {
            return;
        }
        switch (question.getQuestionType()) {
            case AGREEMENT:
                record.putAll(agreementFmt.collect((AgreementQuestionDef) question, (AgreementAnswer) answer));
                break;
            case BOOLEAN:
                record.putAll(boolFmt.collect((BoolQuestionDef) question, (BoolAnswer) answer));
                break;
            case TEXT:
                record.putAll(textFmt.collect((TextQuestionDef) question, (TextAnswer) answer));
                break;
            case DATE:
                record.putAll(dateFmt.collect((DateQuestionDef) question, (DateAnswer) answer));
                break;
            case NUMERIC:
                record.putAll(numericFmt.collect((NumericQuestionDef) question, (NumericAnswer) answer));
                break;
            case PICKLIST:
                record.putAll(picklistFmt.collect((PicklistQuestionDef) question, (PicklistAnswer) answer));
                break;
            case COMPOSITE:
                CompositeQuestionDef composite = (CompositeQuestionDef) question;
                CompositeAnswer compositeAnswer = (CompositeAnswer) answer;
                // There should be one row with one answer per child. Put them into the response object so we can recurse.
                compositeAnswer.getValue().stream()
                        .flatMap(row -> row.getValues().stream())
                        .forEach(instance::putAnswer);
                for (QuestionDef childQuestion : composite.getChildren()) {
                    collectQuestionIntoRecord(record, childQuestion, instance);
                }
                break;
            default:
                throw new DDPException("Unhandled question type " + question.getQuestionType());
        }
    }

    private void collectComponentIntoRecord(Map<String, String> record, ComponentBlockDef component, ComponentDataSupplier supplier) {
        switch (component.getComponentType()) {
            case MAILING_ADDRESS:
                record.putAll(addressFmt.collect(supplier.getAddress()));
                break;
            case PHYSICIAN:     // fall-through
            case INSTITUTION:
                PhysicianInstitutionComponentDef comp = (PhysicianInstitutionComponentDef) component;
                InstitutionType type = comp.getInstitutionType();
                record.putAll(providerFmt.collect(type, supplier.getProviders(type)));
                break;
            default:
                throw new DDPException("Unhandled component type " + component.getComponentType());
        }
    }
}
