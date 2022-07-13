package org.broadinstitute.dsm.model.elastic.export.tabular;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.export.WorkflowAndFamilyIdExporter;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.ParticipantColumn;
import org.broadinstitute.dsm.model.elastic.export.tabular.renderer.TextValueProvider;
import org.broadinstitute.dsm.model.elastic.export.tabular.renderer.ValueProviderFactory;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperDto;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses a list of ParticipantDtos from elasticSearch into a map of column names to string values.
 * Uses a DDP instance to query the question schema to determine which questions are multi-select,
 * and therefore may be spit into multiple columns
 * Usage:
 * p = new ParticipantDtoParser([[list of columns from UI]], DDPInstance)
 * List configs = p.generateExportConfigs();
 * List participantMaps = p.parse(listOfParticipantsFromES);
 */
public class TabularParticipantParser {
    private static final Logger logger = LoggerFactory.getLogger(TabularParticipantParser.class);
    private static final String COLUMN_SELECTED = "1";
    private static final String COLUMN_UNSELECTED = "0";
    private static final String DATA_AS_MAP = "dataAsMap";
    private final List<Filter> filters;
    private final DDPInstance ddpInstance;
    private final boolean splitOptions;
    private final boolean onlyMostRecent;
    private final ValueProviderFactory valueProviderFactory = new ValueProviderFactory();

    public TabularParticipantParser(List<Filter> filters, DDPInstance ddpInstance, boolean splitOptions, boolean onlyMostRecent) {
        this.filters = filters;
        this.ddpInstance = ddpInstance;
        this.splitOptions = splitOptions;
        this.onlyMostRecent = onlyMostRecent;
    }

    /**
     * generates the config objects that will be used during parsing to put values into columns
     * this makes a call to ElasticSearch to load question schemas, used to determine whether a given question
     * is single or multiselect.
     *
     * @return the configs
     */
    public List<ModuleExportConfig> generateExportConfigs() {
        List<ModuleExportConfig> configs = new ArrayList<>();
        // map of table name => module export config
        Map<String, ModuleExportConfig> exportInfoMap = new HashMap<>();
        Map<String, Map<String, Object>> activityDefs = ElasticSearchUtil.getActivityDefinitions(ddpInstance);

        // iterate over each filter, to generate a corresponding ItemExportConfig
        for (Filter filter : filters) {
            try {
                ParticipantColumn participantColumn = filter.getParticipantColumn();
                // 'Modules' are combinations of tableAlias + object -- it's a discrete set of data
                // within the ES data for a participant
                String moduleConfigKey = participantColumn.getTableAlias() + participantColumn.getObject();
                ModuleExportConfig moduleExport = exportInfoMap.get(moduleConfigKey);
                if (moduleExport == null) {
                    moduleExport = new ModuleExportConfig(participantColumn);
                    configs.add(moduleExport);
                    exportInfoMap.put(moduleConfigKey, moduleExport);
                }
                boolean splitChoicesIntoColumns = false;
                List<Map<String, Object>> options = null;
                if (ESObjectConstants.OPTIONS_TYPE.equals(filter.getType())) {
                    Map<String, Object> questionDef = getDefForQuestion(participantColumn, activityDefs);
                    if (questionDef != null) {
                        // create a column for each option if it's a multiselect
                        splitChoicesIntoColumns = splitOptions &&
                                ESObjectConstants.MULTIPLE.equals(questionDef.get(ESObjectConstants.SELECT_MODE));
                        // save the options so we can translate from stableIds if needed
                        options = (List<Map<String, Object>>) questionDef.get(ESObjectConstants.OPTIONS);
                    }
                }
                FilterExportConfig colConfig = new FilterExportConfig(moduleExport, filter, splitChoicesIntoColumns, options);
                moduleExport.getQuestions().add(colConfig);
            } catch (Exception e) {
                logger.error("Export column could not be generated for filter", e);
            }
        }
        configs.sort(Comparator.comparing(ModuleExportConfig::isCollection).thenComparing(ModuleExportConfig::getAliasValue));
        return configs;
    }

    /**
     * Gets the definition for a given question based on a filter column
     *
     * @param column       the column from the Filter
     * @param activityDefs the schema loaded from ElasticSearch
     * @return the question schema corresponding to the given column, or null if it does not correspond to a question (e.g.
     * it is a 'data' attribute)
     */
    private Map<String, Object> getDefForQuestion(ParticipantColumn column, Map<String, Map<String, Object>> activityDefs) {
        String activityName = column.getTableAlias();
        Map<String, Object> activityDef = activityDefs.values().stream().filter(d ->
                        d.get(ESObjectConstants.ACTIVITY_CODE).equals(activityName))
                .findFirst().orElse(null);
        if (activityDef == null) {
            return null;
        }
        // find the question with the matching stableId
        Map<String, Object> matchingDef = ((List<Map<String, Object>>) activityDef.get(ESObjectConstants.QUESTIONS))
                .stream().filter(q -> q.get(ESObjectConstants.STABLE_ID).equals(column.getName()))
                .findFirst().orElse(null);
        return matchingDef;
    }

    /**
     * map each participant's data into a map of columnName => string value, based on the passed-in configs
     */
    public List<Map<String, String>> parse(List<ModuleExportConfig> moduleConfigs, List<ParticipantWrapperDto> participantDtos) {
        List<Map<String, String>> allParticipantMaps = new ArrayList<>(participantDtos.size());
        for (ParticipantWrapperDto participant : participantDtos) {
            allParticipantMaps.addAll(generateParticipantTabularMaps(moduleConfigs, participant));
        }
        return allParticipantMaps;
    }

    /**
     * Convert the participant dtos into hashmaps with a value for each column
     *
     * @param moduleConfigs the moduleExportConfigs, such as returned from generateExportConfigs
     * @param participant   the participantDto, as fetched from elasticSearch
     * @return a list of hashmaps suitable for turning into a table. The list will be of length 1, unless this is a study (RGP)
     * with family members.  In that case, the list will have one map for each member.
     */
    private List<Map<String, String>> generateParticipantTabularMaps(List<ModuleExportConfig> moduleConfigs,
                                                                     ParticipantWrapperDto participant) {
        List<Map<String, String>> participantMaps = new ArrayList<>();
        Map<String, Object> esDataAsMap = participant.getEsDataAsMap();
        mapParticipantDataJson(esDataAsMap);
        // get the 'subParticipants' a.k.a RGP family members
        // note that getSubParticipants will always return at least one entry, (for non-RGP studies, it will just return a single empty map)
        List<Map<String, Object>> participantDataList = getSubParticipants(esDataAsMap);
        for (Map<String, Object> subParticipant : participantDataList) {
            Map<String, String> participantMap = new HashMap();
            participantMaps.add(participantMap);
            for (ModuleExportConfig moduleConfig : moduleConfigs) {
                // get the data corresponding to each time this module was completed
                List<Map<String, Object>> esModuleMaps =
                        getModuleCompletions(esDataAsMap, moduleConfig, subParticipant, this.onlyMostRecent);
                if (esModuleMaps.size() > moduleConfig.getNumMaxRepeats()) {
                    moduleConfig.setNumMaxRepeats(esModuleMaps.size());
                }
                // for each time the module was completed, loop over the data and add it to the map
                for (int moduleIndex = 0; moduleIndex < esModuleMaps.size(); moduleIndex++) {
                    Map<String, Object> esModuleMap = esModuleMaps.get(moduleIndex);
                    addModuleDataToParticipantMap(moduleConfig, participantMap, esModuleMap, moduleIndex);
                }
            }
        }
        return participantMaps;
    }

    /**
     * Iterate through the questions in the module, adding answer values to the participant mop
     *
     * @param moduleConfig   the module config
     * @param esModuleMap    A map corresponding to where the data for the module answers can be found
     *                       such as one from the list returned by getModuleCompletions
     * @param participantMap a map to store columnName -> answerString
     */
    private void addModuleDataToParticipantMap(ModuleExportConfig moduleConfig, Map<String, String> participantMap,
                                               Map<String, Object> esModuleMap, int moduleRepeatNum) {
        for (FilterExportConfig filterConfig : moduleConfig.getQuestions()) {
            TextValueProvider valueProvider =
                    valueProviderFactory.getValueProvider(filterConfig.getColumn().getName(), filterConfig.getType());

            Collection<String> formattedValues = valueProvider.getFormattedValues(filterConfig, esModuleMap);

            if (filterConfig.isSplitOptionsIntoColumns()) {
                for (int optIndex = 0; optIndex < filterConfig.getOptions().size(); optIndex++) {
                    Map<String, Object> opt = filterConfig.getOptions().get(optIndex);

                    String colName = TabularParticipantExporter.getColumnName(
                            filterConfig,
                            moduleRepeatNum + 1,
                            1,
                            opt
                    );

                    String exportValue = formattedValues.contains(opt.get(ESObjectConstants.OPTION_STABLE_ID)) ?
                            COLUMN_SELECTED : COLUMN_UNSELECTED;
                    participantMap.put(colName, exportValue);
                }

            } else {
                String colName = TabularParticipantExporter.getColumnName(
                        filterConfig,
                        moduleRepeatNum + 1,
                        1,
                        null);
                String exportValue = formattedValues.stream().collect(Collectors.joining(", "));
                participantMap.put(colName, exportValue);

            }
        }
    }

    /**
     * Get subparticipants (aka RGP family members)
     *
     * @param esDataAsMap elastic search data for a ParticipantDto, in map form.
     * @return a list of sub-participant data (a.k.a. RGP family members) associated with the given participant
     * If none exist, a list with a single empty map will be returned
     */
    List<Map<String, Object>> getSubParticipants(Map<String, Object> esDataAsMap) {
        List<Map<String, Object>> participantDataList = (List<Map<String, Object>>) ((Map<String, Object>) esDataAsMap
                .get(ESObjectConstants.DSM)).get(ESObjectConstants.PARTICIPANT_DATA);
        List<Map<String, Object>> subParticipants = participantDataList.stream()
                // do a case insensitive comparison as some data has "rgp_PARTICIPANTS" as fieldIds
                .filter(item -> WorkflowAndFamilyIdExporter.RGP_PARTICIPANTS
                        .equalsIgnoreCase((String) item.get(ESObjectConstants.FIELD_TYPE_ID)))
                .collect(Collectors.toList());
        if (subParticipants.size() > 0) {
            return subParticipants;
        } else {
            return Collections.singletonList(Collections.emptyMap());
        }
    }

    /**
     * pre-parses all the json fields into maps for easier access during the main parse
     * For now, this assumes 'dsm.participantData.data' is the only source of json that needs to be parsed
     */
    private void mapParticipantDataJson(Map<String, Object> esDataAsMap) {
        try {
            List<Map<String, Object>> participantDataList = (List<Map<String, Object>>) ((Map<String, Object>) esDataAsMap
                    .get(ESObjectConstants.DSM)).get(ESObjectConstants.PARTICIPANT_DATA);
            for (Map<String, Object> dataItem : participantDataList) {
                try {
                    JsonNode jsonNode = ObjectMapperSingleton.instance()
                            .readTree(dataItem.get(ESObjectConstants.DATA).toString());
                    Map<String, Object> mappedNode = ObjectMapperSingleton.instance()
                            .convertValue(jsonNode, new TypeReference<Map<String, Object>>() {
                            });
                    dataItem.put(DATA_AS_MAP, mappedNode);
                } catch (Exception e) {
                    dataItem.put(DATA_AS_MAP, Collections.emptyMap());
                }
            }
        } catch (Exception e) {
            // do nothing, the parser will just leave empty fields where the json couldn't be parsed
        }
    }

    /**
     * Returns the maps correspond to the participants completions of a given activity, e.g. their completions of the MEDICAL_HISTORY
     *
     * @param esDataAsMap  the participant's ES data
     * @param moduleConfig the config for the given activity
     * @return the maps
     */
    private static List<Map<String, Object>> getModuleCompletions(Map<String, Object> esDataAsMap,
                                                                 ModuleExportConfig moduleConfig,
                                                                 Map<String, Object> subParticipant,
                                                                 boolean onlyMostRecent) {
        if (moduleConfig.isActivity()) {
            return getActivityCompletions(esDataAsMap, moduleConfig, subParticipant, onlyMostRecent);
        } else if (moduleConfig.getFilterKey().isJson() && moduleConfig.getName().startsWith(ESObjectConstants.DSM_PARTICIPANT_DATA)) {
            return getNestedJsonCompletions(esDataAsMap, moduleConfig, subParticipant, onlyMostRecent);
        } else {
            return getOtherCompletions(esDataAsMap, moduleConfig, subParticipant, onlyMostRecent);
        }
    }

    private static List<Map<String, Object>> getActivityCompletions(Map<String, Object> esDataAsMap,
                                          ModuleExportConfig moduleConfig,
                                          Map<String, Object> subParticipant,
                                          boolean onlyMostRecent) {
        List<Map<String, Object>> activityList = (List<Map<String, Object>>) esDataAsMap.get(ESObjectConstants.ACTIVITIES);
        List<Map<String, Object>> matchingActivities = activityList.stream().filter(activity ->
                        moduleConfig.getName().equals(activity.get(ESObjectConstants.ACTIVITY_CODE)))
                .collect(Collectors.toList()
                );
        matchingActivities.sort((a1, a2) ->
                Long.compare((long) a2.get(ESObjectConstants.LAST_UPDATED_AT),
                        (long) a1.get(ESObjectConstants.LAST_UPDATED_AT)));
        if (onlyMostRecent && matchingActivities.size() > 1) {
            return matchingActivities.subList(0, 1);
        }
        return matchingActivities;
    }

    private static List<Map<String, Object>> getNestedJsonCompletions(Map<String, Object> esDataAsMap,
                                                                      ModuleExportConfig moduleConfig,
                                                                      Map<String, Object> subParticipant,
                                                                      boolean onlyMostRecent) {
        // get the module name from the first question -- this assumes all questions
        // in the module get stored in the same object.
        String objectName = moduleConfig.getQuestions().get(0).getColumn().getObject();
        if (objectName == null) {
            // this handles some derived RGP columns like "#FIRSTNAME #LASTNAME.." that do not need to be exported
            return Collections.singletonList(Collections.emptyMap());
        }
        // figure out whether we're dealing with subparticipants (RGP) or just data
        if (objectName != null && objectName.startsWith("RGP") && objectName.endsWith("GROUP")) {
            if (subParticipant != null && subParticipant.get(DATA_AS_MAP) != null) {
                return Collections.singletonList((Map<String, Object>) subParticipant.get(DATA_AS_MAP));
            } else {
                return Collections.singletonList(Collections.emptyMap());
            }
        } else {
            List<Map<String, Object>> participantDataList = (List<Map<String, Object>>) ((Map<String, Object>) esDataAsMap
                    .get(ESObjectConstants.DSM)).get(ESObjectConstants.PARTICIPANT_DATA);
            List<Map<String, Object>> matchingModules = participantDataList.stream()
                    .filter(item -> objectName.equals(item.get(ESObjectConstants.FIELD_TYPE_ID)))
                    .collect(Collectors.toList());

            List<Map<String, Object>> moduleData = matchingModules.stream().map(module -> {
                return (Map<String, Object>) module.get(DATA_AS_MAP);
            }).collect(Collectors.toList());
            return moduleData;
        }

    }

    private static List<Map<String, Object>> getOtherCompletions(Map<String, Object> esDataAsMap,
                                                                 ModuleExportConfig moduleConfig,
                                                                 Map<String, Object> subParticipant,
                                                                 boolean onlyMostRecen) {
        // this module is based off the root of the map, like 'dsm' or 'invitations'
        String mapPath = moduleConfig.getFilterKey().getValue();
        Object rootObject = esDataAsMap.get(mapPath);
        if (rootObject == null && mapPath.contains(".")) {
            // we need to traverse a nested path like "dsm.participant"
            String[] pathSegments = mapPath.split("\\.");
            rootObject = esDataAsMap;
            for(String segment : pathSegments) {
                if (rootObject != null && rootObject instanceof Map) {
                    rootObject = ((Map<String, Object>) rootObject).get(segment);
                }
            }
        }
        if (rootObject instanceof List) {
            // it's a list, like 'invitations'
            return (List<Map<String, Object>>) rootObject;
        } else if (rootObject instanceof Map) {
            // it's a Map, like 'dsm'
            return Collections.singletonList((Map<String, Object>) rootObject);
        }

        // we are either pulling data from the root "data" level, or
        // we don't know what the module/question will be getting at, so return the entire map
        // in case the question config has the logic to handle it.
        return Collections.singletonList(esDataAsMap);
    }
}
