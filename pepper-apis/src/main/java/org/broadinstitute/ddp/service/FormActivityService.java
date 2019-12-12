package org.broadinstitute.ddp.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.db.dao.JdbiBlockNesting;
import org.broadinstitute.ddp.db.dao.JdbiFormActivityFormSection;
import org.broadinstitute.ddp.db.dao.JdbiFormActivitySetting;
import org.broadinstitute.ddp.db.dao.JdbiFormSectionBlock;
import org.broadinstitute.ddp.db.dto.FormActivitySettingDto;
import org.broadinstitute.ddp.db.dto.FormBlockDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.form.BlockVisibility;
import org.broadinstitute.ddp.pex.PexException;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FormActivityService {

    private static final Logger LOG = LoggerFactory.getLogger(FormActivityService.class);

    private PexInterpreter interpreter;

    public FormActivityService(PexInterpreter interpreter) {
        this.interpreter = interpreter;
    }

    /**
     * Get and evaluate visibility only for blocks that has an associated conditional expression.
     *
     * @param handle       the jdbi handle
     * @param userGuid     the user guid
     * @param instanceGuid the form instance guid
     * @return list of block visibilities
     * @throws DDPException if pex evaluation error
     */
    public List<BlockVisibility> getBlockVisibilities(Handle handle, String userGuid, String instanceGuid) {
        List<Long> sectionIds = handle.attach(JdbiFormActivityFormSection.class).getOrderedBodySectionIds(instanceGuid);
        Optional<FormActivitySettingDto> res = handle.attach(JdbiFormActivitySetting.class).findSettingDtoByInstanceGuid(instanceGuid);
        if (res.isPresent()) {
            FormActivitySettingDto settingDto = res.get();
            if (settingDto.getIntroductionSectionId() != null) {
                sectionIds.add(settingDto.getIntroductionSectionId());
            }
            if (settingDto.getClosingSectionId() != null) {
                sectionIds.add(settingDto.getClosingSectionId());
            }
        }

        List<FormBlockDto> blocks = handle.attach(JdbiFormSectionBlock.class)
                .findOrderedFormBlockDtosForSections(sectionIds, instanceGuid)
                .values()
                .stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        List<BlockVisibility> visibilities = new ArrayList<>();
        for (FormBlockDto dto : blocks) {
            BlockVisibility vis = evaluateVisibility(handle, dto, userGuid, instanceGuid);
            if (vis != null) {
                visibilities.add(vis);
            }
            if (dto.getType().isContainerBlock()) {
                handle.attach(JdbiBlockNesting.class)
                        .findOrderedNestedFormBlockDtos(dto.getId(), instanceGuid)
                        .stream()
                        .map(nestedDto -> evaluateVisibility(handle, nestedDto, userGuid, instanceGuid))
                        .filter(Objects::nonNull)
                        .forEach(visibilities::add);
            }
        }
        return visibilities;
    }

    private BlockVisibility evaluateVisibility(Handle handle, FormBlockDto dto, String userGuid, String instanceGuid) {
        BlockVisibility vis = null;
        String expr = dto.getShownExpr();
        if (expr != null) {
            try {
                boolean shown = interpreter.eval(expr, handle, userGuid, instanceGuid);
                vis = new BlockVisibility(dto.getGuid(), shown);
            } catch (PexException e) {
                String msg = String.format("Error evaluating pex expression for form activity instance %s and block %s: `%s`",
                        instanceGuid, dto.getGuid(), expr);
                throw new DDPException(msg, e);
            }
        }
        return vis;
    }
}
