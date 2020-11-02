package org.broadinstitute.ddp.service;

import java.util.Optional;
import java.util.function.Function;

import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.db.ActivityInstanceDao;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.model.activity.types.ActivityType;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActivityInstanceService {

    private static final Logger LOG = LoggerFactory.getLogger(ActivityInstanceService.class);

    private final ActivityInstanceDao actInstanceDao;
    private final PexInterpreter interpreter;

    public ActivityInstanceService(ActivityInstanceDao actInstanceDao, PexInterpreter interpreter) {
        this.actInstanceDao = actInstanceDao;
        this.interpreter = interpreter;
    }

    /**
     * Get an activity instance, translated to given language. If activity is a form, visibility of
     * blocks will be resolved as well.
     *
     * @param handle          the jdbi handle
     * @param userGuid        the user guid
     * @param actType         the activity type
     * @param actInstanceGuid the activity instance guid
     * @param isoLangCode     the iso language code
     * @param style           the content style to use for converting content
     * @return activity instance, if found
     * @throws DDPException if pex evaluation error
     */
    public Optional<ActivityInstance> getTranslatedActivity(Handle handle, String userGuid, String operatorGuid, ActivityType actType,
                                                            String actInstanceGuid, String isoLangCode, ContentStyle style) {
        ActivityInstance inst = actInstanceDao.getTranslatedActivityByTypeAndGuid(handle, actType, actInstanceGuid, isoLangCode, style);
        if (inst == null) {
            return Optional.empty();
        }

        if (ActivityType.FORMS.equals(inst.getActivityType())) {
            ((FormInstance) inst).updateBlockStatuses(handle, interpreter, userGuid, operatorGuid, actInstanceGuid);
        }

        return Optional.of(inst);
    }

    /**
     * Get a form instance, translated to given language and with visibility of blocks resolved.
     *
     * @param handle           the jdbi handle
     * @param userGuid         the user guid
     * @param formInstanceGuid the form instance guid
     * @param isoLangCode      the iso language code
     * @return form instance, if found
     * @throws DDPException if pex evaluation error
     */
    public Optional<FormInstance> getTranslatedForm(Handle handle, String userGuid, String operatorGuid, String formInstanceGuid,
                                                    String isoLangCode, ContentStyle style) {
        Function<ActivityInstance, FormInstance> typeChecker = (inst) -> {
            if (ActivityType.FORMS.equals(inst.getActivityType())) {
                return (FormInstance) inst;
            } else {
                LOG.warn("Expected a form instance but got type {} for guid {} lang code {}",
                        inst.getActivityType(), formInstanceGuid, isoLangCode);
                return null;
            }
        };
        return getTranslatedActivity(handle, userGuid, operatorGuid, ActivityType.FORMS,
                formInstanceGuid, isoLangCode, style).map(typeChecker);
    }
}
