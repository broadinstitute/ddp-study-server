package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.ComponentDto;
import org.broadinstitute.ddp.db.dto.InstitutionPhysicianComponentDto;
import org.broadinstitute.ddp.db.dto.MailingAddressComponentDto;
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


    default FormComponent findByBlockId(String activityInstanceGuid, long blockId) {
        // query for component type by block id
        JdbiComponent jdbiComponent = getJdbiComponent();

        // query must pay attention to revisions
        ComponentDto componentDto = jdbiComponent.findByBlockId(activityInstanceGuid, blockId);
        long componentId = componentDto.getComponentId();
        FormComponent formComponent = null;

        boolean isInstitution = componentDto.getComponentType() == ComponentType.INSTITUTION;
        boolean isPhysician = componentDto.getComponentType() == ComponentType.PHYSICIAN;
        if (isInstitution || isPhysician) {
            InstitutionPhysicianComponentDto institutionDto = getJdbiInstitutionPhysicianComponent().findById(componentId);
            if (isInstitution) {
                formComponent = new InstitutionComponent(institutionDto, componentDto.shouldHideNumber());
            } else if (isPhysician) {
                formComponent = new PhysicianComponent(institutionDto, componentDto.shouldHideNumber());
            } else {
                throw new DaoException("Unknown component type " + componentDto.getComponentType());
            }
        } else if (componentDto.getComponentType() == ComponentType.MAILING_ADDRESS) {
            MailingAddressComponentDto mailingAddressComponentDto = getJdbiComponent()
                    .findMailingAddressComponentDtoById(componentId)
                    .orElseThrow(() -> new DaoException("Could not find mailing address component with id " + componentId));
            formComponent = new MailingAddressComponent(
                    mailingAddressComponentDto.getTitleTemplateId(),
                    mailingAddressComponentDto.getSubtitleTemplateId(),
                    componentDto.shouldHideNumber(),
                    mailingAddressComponentDto.shouldRequireVerified(),
                    mailingAddressComponentDto.shouldRequirePhone());
        } else {
            throw new DaoException("Cannot process component type " + componentDto.getComponentType());
        }
        return formComponent;
    }

    private long insertComponentForBlock(long blockId, ComponentBlockDef compDef, long revisionId) {
        JdbiComponent jdbiComponent = getJdbiComponent();
        JdbiBlockComponent jdbiBlockComponent = getJdbiBlockComponent();

        long componentId = jdbiComponent.insert(compDef.getComponentType(), compDef.shouldHideNumber(), revisionId);
        long componentBlockId = jdbiBlockComponent.insert(blockId, componentId);

        LOG.info("inserted component block {} for component {} and block {}.", componentBlockId, componentId, blockId);

        return componentId;
    }

    default long insertComponentDef(long blockId, PhysicianInstitutionComponentDef compDef, long revisionId) {
        JdbiInstitutionType jdbiInstitutionType = getJdbiInstitutionType();
        JdbiInstitutionPhysicianComponent jdbiInstitutionPhysicianComponent = getJdbiInstitutionPhysicianComponent();
        TemplateDao templateDao = getTemplateDao();

        long componentId = insertComponentForBlock(blockId, compDef, revisionId);
        long institutionTypeId = jdbiInstitutionType.findByType(compDef.getInstitutionType());

        Long addButtonTemplateId = null;
        if (compDef.getAddButtonTemplate() != null) {
            addButtonTemplateId = templateDao.insertTemplate(compDef.getAddButtonTemplate(), revisionId);
        }
        Long titleTemplateId = null;
        if (compDef.getTitleTemplate() != null) {
            titleTemplateId = templateDao.insertTemplate(compDef.getTitleTemplate(), revisionId);
        }
        Long subtitleTemplateId = null;
        if (compDef.getSubtitleTemplate() != null) {
            subtitleTemplateId = templateDao.insertTemplate(compDef.getSubtitleTemplate(), revisionId);
        }

        int numRows = jdbiInstitutionPhysicianComponent.insert(componentId,
                compDef.allowMultiple(),
                addButtonTemplateId,
                titleTemplateId,
                subtitleTemplateId,
                institutionTypeId,
                compDef.showFields(),
                compDef.isRequired());
        if (numRows != 1) {
            throw new DaoException("Inserted " + numRows + " rows for institution/physician component " + componentId
                    + " in block " + blockId);
        }

        LOG.info("Inserted institution component {}", componentId);
        return componentId;
    }

    default long insertComponentDef(long blockId, MailingAddressComponentDef mailAddressCompDef, long revisionId) {
        long componentId = insertComponentForBlock(blockId, mailAddressCompDef, revisionId);

        TemplateDao templateDao = getTemplateDao();
        Long titleTemplateId = null;
        if (mailAddressCompDef.getTitleTemplate() != null) {
            titleTemplateId = templateDao.insertTemplate(mailAddressCompDef.getTitleTemplate(), revisionId);
        }
        Long subtitleTemplateId = null;
        if (mailAddressCompDef.getSubtitleTemplate() != null) {
            subtitleTemplateId = templateDao.insertTemplate(mailAddressCompDef.getSubtitleTemplate(), revisionId);
        }

        DBUtils.checkInsert(1, getJdbiComponent().insertMailingAddressComponent(
                componentId, titleTemplateId, subtitleTemplateId,
                mailAddressCompDef.shouldRequireVerified(),
                mailAddressCompDef.shouldRequirePhone()));

        LOG.info("Inserted mailing address component {}", componentId);
        return componentId;
    }

    default ComponentBlockDef findDefByBlockIdAndTimestamp(long blockId, long timestamp) {
        ComponentDto componentDto = getJdbiComponent()
                .findDtoByBlockIdAndTimestamp(blockId, timestamp)
                .orElseThrow(() -> new DaoException(String.format(
                        "Could not find component block definition for id %d and timestamp %d", blockId, timestamp)));

        long componentId = componentDto.getComponentId();
        if (componentDto.getComponentType() == ComponentType.MAILING_ADDRESS) {
            MailingAddressComponentDto mailingAddressComponentDto = getJdbiComponent()
                    .findMailingAddressComponentDtoById(componentId)
                    .orElseThrow(() -> new DaoException("Could not find mailing address component with id " + componentId));
            Template titleTmpl = null;
            if (mailingAddressComponentDto.getTitleTemplateId() != null) {
                titleTmpl = getTemplateDao().loadTemplateById(mailingAddressComponentDto.getTitleTemplateId());
            }
            Template subtitleTmpl = null;
            if (mailingAddressComponentDto.getSubtitleTemplateId() != null) {
                subtitleTmpl = Template.text("");
            }
            MailingAddressComponentDef comp = new MailingAddressComponentDef(titleTmpl, subtitleTmpl);
            comp.setHideNumber(componentDto.shouldHideNumber());
            comp.setRequireVerified(mailingAddressComponentDto.shouldRequireVerified());
            comp.setRequirePhone(mailingAddressComponentDto.shouldRequirePhone());
            return comp;
        }

        InstitutionPhysicianComponentDto compDto = getJdbiInstitutionPhysicianComponent().findById(componentId);

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

        PhysicianInstitutionComponentDef comp;
        if (compDto.getInstitutionType() == InstitutionType.PHYSICIAN) {
            comp = new PhysicianComponentDef(compDto.getAllowMultiple(), buttonTmpl, titleTmpl, subtitleTmpl,
                    compDto.getInstitutionType(), compDto.showFields(), compDto.isRequired());
        } else {
            comp = new InstitutionComponentDef(compDto.getAllowMultiple(), buttonTmpl, titleTmpl, subtitleTmpl,
                    compDto.getInstitutionType(), compDto.showFields(), compDto.isRequired());
        }
        comp.setHideNumber(componentDto.shouldHideNumber());
        return comp;
    }
}
