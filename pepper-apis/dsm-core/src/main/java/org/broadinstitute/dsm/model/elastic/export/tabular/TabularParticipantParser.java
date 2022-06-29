package org.broadinstitute.dsm.model.elastic.export.tabular;

import java.util.*;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.ParticipantColumn;
import org.broadinstitute.dsm.model.elastic.export.tabular.renderer.ValueProvider;
import org.broadinstitute.dsm.model.elastic.export.tabular.renderer.ValueProviderFactory;
import org.broadinstitute.dsm.model.elastic.sort.Alias;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperDto;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

/**
 * Parses a list of ParticipantDtos from elasticSearch into a map of column names to string values.
 * Uses a DDP instance to query the question schema to determine which questions are multi-select, and therefore may be spit into multiple columns
 * Usage:
 *  p = new ParticipantDtoParser(<list of columns from UI>, DDPInstance)
 *  List<ExportFormConfigs> configs = p.generateExportConfigs();
 *  List<Map<String, String>> participantMaps = p.parse(listOfParticipantsFromES);
 */
public class TabularParticipantParser {
    private final List<Filter> filters;
    private final DDPInstance ddpInstance;

    private ValueProviderFactory valueProviderFactory = new ValueProviderFactory();

    public TabularParticipantParser(List<Filter> filters, DDPInstance ddpInstance) {
        this.filters = filters;
        this.ddpInstance = ddpInstance;
    }

    /**
     * generates the config objects that will be used during parsing to put values into columns
     * this makes a call to ElasticSearch to load question schemas, used to determine whether a given question
     * is single or multiselect.
     * @return the configs
     */
    public List<ModuleExportConfig> generateExportConfigs() {
        List<ModuleExportConfig> configs = new ArrayList<>();
        // map of table name => module export config
        Map<String, ModuleExportConfig> exportInfoMap = new HashMap<>();
        Map<String, Map<String, Object>> activityDefs = ElasticSearchUtil.getActivityDefinitions(ddpInstance);

        // iterate over each filter, to generate a corresponding ItemExportConfig
        for (Filter filter : filters) {
            ParticipantColumn participantColumn = filter.getParticipantColumn();
            ModuleExportConfig moduleExport = exportInfoMap.get(participantColumn.getTableAlias());
            if (moduleExport == null) {
                moduleExport = new ModuleExportConfig(participantColumn);
                configs.add(moduleExport);
                exportInfoMap.put(participantColumn.getTableAlias(), moduleExport);
            }
            boolean splitChoicesIntoColumns = false;
            List<Map<String, Object>> options = null;
            if ("OPTIONS".equals(filter.getType())) {
                Map<String, Object> questionDef = getDefForQuestion(participantColumn, activityDefs);
                if (questionDef != null) {
                    // create a column for each option if it's a multiselect
                    splitChoicesIntoColumns = "MULTIPLE".equals(questionDef.get("selectMode"));
                    options = (List<Map<String, Object>>) questionDef.get("options");
                }
            }
            FilterExportConfig colConfig = new FilterExportConfig(moduleExport, filter, splitChoicesIntoColumns, options);
            moduleExport.getQuestions().add(colConfig);

        }
        configs.sort(Comparator.comparing(ModuleExportConfig::isCollection).thenComparing(ModuleExportConfig::getAliasValue));
        return configs;
    }

    /**
     *
     * @param column the column from the Filter
     * @param activityDefs the schema loaded from ElasticSearch
     * @return the question schema corresponding to the given column, or null if it does not correspond to a question (e.g.
     * it is a 'data' attribute)
     */
    private Map<String, Object> getDefForQuestion(ParticipantColumn column, Map<String, Map<String, Object>> activityDefs) {
        String activityName = column.getTableAlias();
        Map<String, Object> activityDef = activityDefs.values().stream().filter( d -> d.get("activityCode").equals(activityName))
                .findFirst().orElse(null);
        if (activityDef == null) {
            return null;
        }
        // find the question with the matching stableId
        Map<String, Object> matchingDef = ((List<Map<String, Object>>) activityDef.get("questions"))
                .stream().filter( q -> q.get("stableId").equals(column.getName()))
                .findFirst().orElse(null);
        return matchingDef;
    }
    /** map each participant's data into a map of columnName => string value, based on the passed-in configs */
    public List<Map<String, String>> parse(List<ModuleExportConfig> moduleConfigs, List<ParticipantWrapperDto> participantDtos) {
        List<Map<String, String>> participantMaps = new ArrayList<>(participantDtos.size());
        for (int participantIndex = 0; participantIndex < participantDtos.size(); participantIndex++) {
            Map<String, String> participantMap = new HashMap();
            participantMaps.add(participantMap);
            ParticipantWrapperDto participant = participantDtos.get(participantIndex);
            Map<String, Object> esDataAsMap = participant.getEsDataAsMap();
            for (ModuleExportConfig moduleConfig : moduleConfigs) {
                List<Map<String, Object>> esModuleMaps = getModuleCompletions(esDataAsMap, moduleConfig);
                if (esModuleMaps.size() > moduleConfig.getNumMaxRepeats()) {
                    moduleConfig.setNumMaxRepeats(esModuleMaps.size());
                }

                for (int moduleIndex = 0; moduleIndex < esModuleMaps.size(); moduleIndex++) {
                    Map<String, Object> esFormMap = esModuleMaps.get(moduleIndex);
                    for (FilterExportConfig fConfig : moduleConfig.getQuestions()) {

                        ValueProvider valueProvider = valueProviderFactory.getFormValueProvider(fConfig.getColumn().getName(), fConfig.getType());

                        Collection<String> formattedValues = valueProvider.getFormattedValues(fConfig, esFormMap);

                        if (fConfig.isSplitOptionsIntoColumns()) {
                            for (int optIndex = 0; optIndex < fConfig.getOptions().size(); optIndex++) {
                                Map<String, Object> opt = fConfig.getOptions().get(optIndex);

                                String colName = TabularParticipantExporter.getColumnName(
                                        fConfig,
                                         moduleIndex + 1,
                                        1,
                                        opt
                                        );

                                String exportValue = formattedValues.contains(opt.get("optionStableId")) ? "1" : "0";
                                participantMap.put(colName, exportValue);
                            }

                        } else {
                            String colName = TabularParticipantExporter.getColumnName(
                                    fConfig,
                                     moduleIndex + 1,
                                    1,
                                    null);
                            String exportValue = formattedValues.stream().collect(Collectors.joining(", "));
                            participantMap.put(colName, exportValue);

                        }

                    }
                }
            }
        }
        return participantMaps;
    }

    /**
     * Returns the maps correspond to the participants completions of a given activity, e.g. their completions of the MEDICAL_HISTORY
     * @param esDataAsMap the participant's ES data
     * @param formInfo the config for the given activity
     * @return the maps
     */
    private List<Map<String, Object>> getModuleCompletions(Map<String, Object> esDataAsMap, ModuleExportConfig formInfo) {
        if (formInfo.isActivity()) {
            List<Map<String, Object>> activityList = (List<Map<String, Object>>) esDataAsMap.get("activities");
            return activityList.stream().filter(activity -> formInfo.getName().equals(activity.get("activityCode"))).collect(Collectors.toList());
        } else {
            return Collections.singletonList(esDataAsMap);
        }
    }
}
