package org.broadinstitute.ddp.db.dao;

import static org.broadinstitute.ddp.db.dao.JdbiComponent.ComponentDto;

import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.model.activity.definition.ComponentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.InstitutionComponentDef;
import org.broadinstitute.ddp.model.activity.definition.MailingAddressComponentDef;
import org.broadinstitute.ddp.model.activity.definition.PhysicianComponentDef;
import org.broadinstitute.ddp.model.activity.definition.PhysicianInstitutionComponentDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.FormComponent;
import org.broadinstitute.ddp.model.activity.instance.InstitutionComponent;
import org.broadinstitute.ddp.model.activity.instance.MailingAddressComponent;
import org.broadinstitute.ddp.model.activity.instance.PhysicianComponent;
import org.broadinstitute.ddp.model.activity.types.ComponentType;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface ComponentDao extends SqlObject {

    Logger LOG = LoggerFactory.getLogger(ComponentDao.class);

    @CreateSqlObject
    JdbiComponent getJdbiComponent();

    @CreateSqlObject
    JdbiBlockComponent getJdbiBlockComponent();

    @CreateSqlObject
    TemplateDao getTemplateDao();

    @CreateSqlObject
    JdbiInstitutionType getJdbiInstitutionType();

    @CreateSqlObject
    JdbiInstitutionPhysicianComponent getJdbiInstitutionPhysicianComponent();


    default FormComponent findByBlockId(String activityInstanceGuid, long blockId, I18nContentRenderer i18nRenderer,
                                        long languageCodeId) {
        // query for component type by block id
        JdbiComponent jdbiComponent = getJdbiComponent();

        // query must pay attention to revisions
        ComponentDto componentDto = jdbiComponent.findByBlockId(activityInstanceGuid, blockId);
        FormComponent formComponent = null;

        boolean isInstitution = componentDto.getComponentType() == ComponentType.INSTITUTION;
        boolean isPhysician = componentDto.getComponentType() == ComponentType.PHYSICIAN;
        if (isInstitution || isPhysician) {
            JdbiInstitutionPhysicianComponent.InstitutionPhysicianComponentDto institutionDto = getJdbiInstitutionPhysicianComponent()
                    .findById(componentDto.getComponentId());

            String buttonText = null;
            if (institutionDto.getButtonTemplateId() != null) {
                buttonText = i18nRenderer.renderContent(getHandle(), institutionDto.getButtonTemplateId(),
                                                        languageCodeId);
            }
            String titleText = null;
            if (institutionDto.getTitleTemplateId() != null) {
                titleText = i18nRenderer.renderContent(getHandle(), institutionDto.getTitleTemplateId(),
                                                        languageCodeId);
            }

            String subtitleText = null;
            if (institutionDto.getSubtitleTemplateId() != null) {
                subtitleText = i18nRenderer.renderContent(getHandle(), institutionDto.getSubtitleTemplateId(),
                                                       languageCodeId);
            }

            if (isInstitution) {
                formComponent = new InstitutionComponent(institutionDto.getAllowMultiple(),
                                                         buttonText,
                                                         titleText,
                                                         subtitleText,
                                                         institutionDto.getInstitutionType(),
                                                         institutionDto.showFields(),
                                                         institutionDto.shouldHideNumber());
            } else if (isPhysician) {
                formComponent = new PhysicianComponent(institutionDto.getAllowMultiple(),
                                                       buttonText,
                                                       titleText,
                                                       subtitleText,
                                                       institutionDto.getInstitutionType(),
                                                       institutionDto.showFields(),
                                                       institutionDto.shouldHideNumber());
            } else {
                throw new DaoException("Unknown component type " + componentDto.getComponentType());
            }
        } else if (componentDto.getComponentType() == ComponentType.MAILING_ADDRESS) {
            formComponent = new MailingAddressComponent();
        } else {
            throw new DaoException("Cannot process component type " + componentDto.getComponentType());
        }
        return formComponent;
    }

    default long insertComponentForBlock(long blockId, ComponentType componentType, long revisionId) {
        JdbiComponent jdbiComponent = getJdbiComponent();
        JdbiBlockComponent jdbiBlockComponent = getJdbiBlockComponent();

        long componentId = jdbiComponent.insert(componentType, revisionId);

        long componentBlockId = jdbiBlockComponent.insert(blockId, componentId);

        LOG.info("inserted component block {} for component {} and block {}.", componentBlockId, componentId, blockId);

        return componentId;
    }

    default void insertTemplatesForComponent(PhysicianInstitutionComponentDef compDef, long revisionId) {
        TemplateDao templateDao = getTemplateDao();
        if (compDef.getAddButtonTemplate() != null) {
            templateDao.insertTemplate(compDef.getAddButtonTemplate(), revisionId);
        }
        if (compDef.getTitleTemplate() != null) {
            templateDao.insertTemplate(compDef.getTitleTemplate(), revisionId);
        }
        if (compDef.getSubtitleTemplate() != null) {
            templateDao.insertTemplate(compDef.getSubtitleTemplate(), revisionId);
        }
    }

    default long insertComponentDef(long blockId, PhysicianInstitutionComponentDef compDef, long revisionId) {
        ComponentType componentType = compDef.getComponentType();
        long componentId = insertComponentForBlock(blockId, componentType, revisionId);
        insertTemplatesForComponent(compDef, revisionId);
        JdbiInstitutionType jdbiInstitutionType = getJdbiInstitutionType();
        JdbiInstitutionPhysicianComponent jdbiInstitutionPhysicianComponent = getJdbiInstitutionPhysicianComponent();

        long institutionTypeId = jdbiInstitutionType.findByType(compDef.getInstitutionType());
        Long addButtonTemplateId = null;
        if (compDef.getAddButtonTemplate() != null) {
            addButtonTemplateId = compDef.getAddButtonTemplate().getTemplateId();
        }
        Long titleTemplateId = null;
        if (compDef.getTitleTemplate() != null) {
            titleTemplateId = compDef.getTitleTemplate().getTemplateId();
        }
        Long subtitleTemplateId = null;
        if (compDef.getSubtitleTemplate() != null) {
            subtitleTemplateId = compDef.getSubtitleTemplate().getTemplateId();
        }
        int numRows = jdbiInstitutionPhysicianComponent.insert(componentId,
                                                               compDef.allowMultiple(),
                                                               addButtonTemplateId,
                                                               titleTemplateId,
                                                               subtitleTemplateId,
                                                               institutionTypeId,
                                                               compDef.showFields());
        if (numRows != 1) {
            throw new DaoException("Inserted " + numRows + " rows for institution/physician component " + componentId
                                           + " in block " + blockId);
        }
        LOG.info("Inserted institution component {}", componentId);
        return componentId;

    }

    default long insertComponentDef(long blockId, MailingAddressComponentDef mailAddressCompDef, long revisionId) {
        return insertComponentForBlock(blockId, ComponentType.MAILING_ADDRESS, revisionId);
    }

    default ComponentBlockDef findDefByBlockIdAndTimestamp(long blockId, long timestamp) {
        ComponentDto componentDto = getJdbiComponent()
                .findDtoByBlockIdAndTimestamp(blockId, timestamp)
                .orElseThrow(() -> new DaoException(String.format(
                        "Could not find component block definition for id %d and timestamp %d", blockId, timestamp)));

        if (componentDto.getComponentType() == ComponentType.MAILING_ADDRESS) {
            return new MailingAddressComponentDef();
        }

        JdbiInstitutionPhysicianComponent.InstitutionPhysicianComponentDto compDto = getJdbiInstitutionPhysicianComponent()
                .findById(componentDto.getComponentId());

        // todo: query templates
        Template buttonTmpl = null;
        if (compDto.getButtonTemplateId() != null) {
            buttonTmpl = Template.text("");
        }

        Template titleTmpl = null;
        if (compDto.getTitleTemplateId() != null) {
            titleTmpl = getTemplateDao().loadTemplateById(compDto.getTitleTemplateId());
        }

        Template subtitleTmpl = null;
        if (compDto.getSubtitleTemplateId() != null) {
            subtitleTmpl = Template.text("");
        }

        if (compDto.getInstitutionType() == InstitutionType.PHYSICIAN) {
            return new PhysicianComponentDef(compDto.getAllowMultiple(), buttonTmpl, titleTmpl, subtitleTmpl,
                    compDto.getInstitutionType(), compDto.showFields());
        } else {
            return new InstitutionComponentDef(compDto.getAllowMultiple(), buttonTmpl, titleTmpl, subtitleTmpl,
                    compDto.getInstitutionType(), compDto.showFields());
        }
    }
}
