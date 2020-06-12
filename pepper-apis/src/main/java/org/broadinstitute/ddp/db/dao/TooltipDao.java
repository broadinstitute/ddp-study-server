package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.model.activity.definition.question.TooltipDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.question.Tooltip;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;

public interface TooltipDao extends SqlObject {

    @CreateSqlObject
    TemplateDao getTemplateDao();

    @CreateSqlObject
    TooltipSql getTooltipSql();

    default long insertDef(TooltipDef tooltipDef, long revisionId) {
        if (tooltipDef.getTextTemplate().getTemplateType() != TemplateType.TEXT) {
            throw new DaoException("Only TEXT template type is supported for tooltips");
        }
        long textTemplateId = getTemplateDao().insertTemplate(tooltipDef.getTextTemplate(), revisionId);
        long tooltipId = getTooltipSql().insert(textTemplateId);
        tooltipDef.setTooltipId(tooltipId);
        return tooltipId;
    }

    default void disableTooltip(Tooltip tooltip, RevisionMetadata meta) {
        getTemplateDao().disableTemplate(tooltip.getTextTemplateId(), meta);
    }

    default TooltipDef findDef(Tooltip tooltip) {
        Template textTemplate = getTemplateDao().loadTemplateById(tooltip.getTextTemplateId());
        return new TooltipDef(tooltip.getTooltipId(), textTemplate);
    }
}
