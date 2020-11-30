package org.broadinstitute.ddp.db.dao;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.ComponentDto;
import org.broadinstitute.ddp.db.dto.FormBlockDto;
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
        long componentId = jdbiComponent.findComponentIdByBlockIdAndInstanceGuid(blockId, activityInstanceGuid);
        ComponentDto componentDto;
        try (var stream = jdbiComponent.findComponentDtosByIds(Set.of(componentId))) {
            componentDto = stream.findFirst().get();
        }
        FormComponent formComponent = null;

        boolean isInstitution = componentDto.getComponentType() == ComponentType.INSTITUTION;
        boolean isPhysician = componentDto.getComponentType() == ComponentType.PHYSICIAN;
        if (isInstitution || isPhysician) {
            var institutionDto = (InstitutionPhysicianComponentDto) componentDto;
            if (isInstitution) {
                formComponent = new InstitutionComponent(institutionDto, componentDto.shouldHideNumber());
            } else if (isPhysician) {
                formComponent = new PhysicianComponent(institutionDto, componentDto.shouldHideNumber());
            } else {
                throw new DaoException("Unknown component type " + componentDto.getComponentType());
            }
        } else if (componentDto.getComponentType() == ComponentType.MAILING_ADDRESS) {
            var mailingAddressComponentDto = (MailingAddressComponentDto) componentDto;
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

    default Map<Long, ComponentBlockDef> collectBlockDefs(Collection<FormBlockDto> blockDtos, long timestamp) {
        if (blockDtos == null || blockDtos.isEmpty()) {
            return new HashMap<>();
        }

        Set<Long> blockIds = new HashSet<>();
        for (var blockDto : blockDtos) {
            blockIds.add(blockDto.getId());
        }

        Map<Long, Long> blockIdToComponentId = getJdbiComponent()
                .findComponentIdsByBlockIdsAndTimestamp(blockIds, timestamp);
        Map<Long, ComponentDto> componentDtos;
        try (var stream = getJdbiComponent().findComponentDtosByIds(blockIdToComponentId.values())) {
            componentDtos = stream.collect(Collectors.toMap(ComponentDto::getComponentId, Function.identity()));
        }

        Set<Long> templateIds = new HashSet<>();
        for (var componentDto : componentDtos.values()) {
            templateIds.addAll(componentDto.getTemplateIds());
        }
        Map<Long, Template> templates = getTemplateDao().collectTemplatesByIds(templateIds);

        Map<Long, ComponentBlockDef> blockDefs = new HashMap<>();
        for (var blockDto : blockDtos) {
            long componentId = blockIdToComponentId.get(blockDto.getId());
            ComponentDto componentDto = componentDtos.get(componentId);

            ComponentBlockDef blockDef;
            if (componentDto.getComponentType() == ComponentType.MAILING_ADDRESS) {
                blockDef = buildAddressBlockDef((MailingAddressComponentDto) componentDto, templates);
            } else {
                blockDef = buildInstitutionBlockDef((InstitutionPhysicianComponentDto) componentDto, templates);
            }

            blockDef.setHideNumber(componentDto.shouldHideNumber());
            blockDef.setBlockId(blockDto.getId());
            blockDef.setBlockGuid(blockDto.getGuid());
            blockDef.setShownExpr(blockDto.getShownExpr());

            blockDefs.put(blockDto.getId(), blockDef);
        }

        return blockDefs;
    }

    private ComponentBlockDef buildAddressBlockDef(MailingAddressComponentDto addressComponentDto,
                                                   Map<Long, Template> templates) {
        Template titleTmpl = templates.getOrDefault(addressComponentDto.getTitleTemplateId(), null);
        Template subtitleTmpl = templates.getOrDefault(addressComponentDto.getSubtitleTemplateId(), null);
        var blockDef = new MailingAddressComponentDef(titleTmpl, subtitleTmpl);
        blockDef.setRequireVerified(addressComponentDto.shouldRequireVerified());
        blockDef.setRequirePhone(addressComponentDto.shouldRequirePhone());
        return blockDef;
    }

    private ComponentBlockDef buildInstitutionBlockDef(InstitutionPhysicianComponentDto institutionComponentDto,
                                                       Map<Long, Template> templates) {
        Template titleTmpl = templates.getOrDefault(institutionComponentDto.getTitleTemplateId(), null);
        Template subtitleTmpl = templates.getOrDefault(institutionComponentDto.getSubtitleTemplateId(), null);
        Template buttonTmpl = templates.getOrDefault(institutionComponentDto.getButtonTemplateId(), null);
        if (institutionComponentDto.getInstitutionType() == InstitutionType.PHYSICIAN) {
            return new PhysicianComponentDef(
                    institutionComponentDto.getAllowMultiple(),
                    buttonTmpl, titleTmpl, subtitleTmpl,
                    institutionComponentDto.getInstitutionType(),
                    institutionComponentDto.showFields(),
                    institutionComponentDto.isRequired());
        } else {
            return new InstitutionComponentDef(
                    institutionComponentDto.getAllowMultiple(),
                    buttonTmpl, titleTmpl, subtitleTmpl,
                    institutionComponentDto.getInstitutionType(),
                    institutionComponentDto.showFields(),
                    institutionComponentDto.isRequired());
        }
    }
}
