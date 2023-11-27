package org.broadinstitute.ddp.studybuilder.task;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.KitConfigurationDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.jdbi.v3.core.Handle;

public abstract class AbstractAddUsTerritoriesToRules implements CustomTask {
    public static List<String> US_TERRITORY_COUNTRY_CODES = List.of("pr", "gu", "vi", "mp", "as");

    Map<String, String> oldExpressionToNewExpression;

    public abstract String getDataFileName();

    public abstract String getStudyGuid();

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        oldExpressionToNewExpression = readOldExpressionToNewExpressionMap(cfgPath);
    }

    private Map<String, String> readOldExpressionToNewExpressionMap(Path cfgPath) {
        File file = cfgPath.getParent().resolve(getDataFileName()).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        Config dataCfg = ConfigFactory.parseFile(file);
        List<? extends ConfigObject> expressionPairs = dataCfg.getObjectList("expressions");
        Map<String, String> oldToNewMap = expressionPairs.stream()
                .collect(toMap(configObject -> (String) configObject.get("old").unwrapped(),
                        configObject -> (String) configObject.get("new").unwrapped()));
        if (oldToNewMap.isEmpty()) {
            throw new DDPException("We could not read the expressions!");
        }
        Function<String, Boolean> isEmpty = (String val) -> val == null || val.trim().length() == 0;
        for (Map.Entry<String, String> entry : oldToNewMap.entrySet()) {
            if (isEmpty.apply(entry.getKey()) || isEmpty.apply(entry.getValue())) {
                throw new DDPException("We could not read at least one of the pairs of expressions from: " + getDataFileName());
            }
        }
        return oldToNewMap;
    }

    @Override
    public void run(Handle handle) {
        updateExpressions(handle);
        addKitRulesForUsTerritories(handle);

    }

    protected void addKitRulesForUsTerritories(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(getStudyGuid());
        KitConfigurationDao kitDao = handle.attach(KitConfigurationDao.class);
        List<Long> kitConfigIds = kitDao.getKitConfigurationDtos().stream()
                .filter(configDto -> configDto.getStudyId() == studyDto.getId())
                .map(configDto -> configDto.getId())
                .collect(toList());
        if (kitConfigIds.isEmpty()) {
            throw new DDPException("Could not find kit configs for study with guid:" + getStudyGuid());
        }
        for (long kitConfigId : kitConfigIds) {
            for (String countryCode : US_TERRITORY_COUNTRY_CODES) {
                kitDao.addCountryRule(kitConfigId, countryCode);
            }
        }
    }

    protected void updateExpressions(Handle handle) {
        for (Map.Entry<String, String> oldToNewExpr : oldExpressionToNewExpression.entrySet()) {
            int rowCount = handle.createUpdate("UPDATE expression SET expression_text=:new WHERE expression_text=:old")
                    .bind("old", oldToNewExpr.getKey())
                    .bind("new", oldToNewExpr.getValue())
                    .execute();
            // some of these expressions can be duplicated. This would be OK.
            if (rowCount < 1) {
                throw new DDPException("Expected to at least 1 row, but updated: " + rowCount);
            }
        }
    }
}
