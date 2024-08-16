package org.broadinstitute.dsm.model.elastic.export.tabular;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.export.WorkflowAndFamilyIdExporter;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.ParticipantColumn;
import org.broadinstitute.dsm.model.elastic.export.tabular.renderer.TextValueProvider;
import org.broadinstitute.dsm.model.elastic.export.tabular.renderer.ValueProviderFactory;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
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
    private final List<Filter> filters;
    private final boolean humanReadable;
    private final boolean onlyMostRecent;
    private final List<String> nestedArrayObjects = Arrays.asList(ESObjectConstants.KIT_TEST_RESULT);
    private Map<String, Map<String, Object>> activityDefs;

    public TabularParticipantParser(List<Filter> filters, DDPInstanceDto ddpInstanceDto, boolean humanReadable, boolean onlyMostRecent,
                                    Map<String, Map<String, Object>> activityDefs) {
        this.filters = filters;
        this.humanReadable = humanReadable;
        this.onlyMostRecent = onlyMostRecent;
        if (activityDefs == null) {
            activityDefs = ElasticSearchUtil.getActivityDefinitions(ddpInstanceDto);
        }
        this.activityDefs = activityDefs;
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
        Map<String, FilterExportConfig> collationColumnMap = new HashMap<>();
        // iterate over each filter, to generate a corresponding FilterExportConfig
        for (Filter filter : filters) {
            try {
                ParticipantColumn participantColumn = filter.getParticipantColumn();

                // 'Modules' are combinations of tableAlias + object -- it's a discrete set of data
                // within the ES data for a participant
                String moduleConfigKey = participantColumn.getTableAlias() + participantColumn.getObject();
                if (nestedArrayObjects.contains(participantColumn.getObject())) {
                    // for nested array objects, we want them to appear inside their associated module (e.g. test results
                    // should appear inside the corresponding kiRequest object
                    moduleConfigKey = participantColumn.getTableAlias() + "null";
                }
                ModuleExportConfig moduleExport = exportInfoMap.get(moduleConfigKey);
                if (moduleExport == null) {
                    moduleExport = new ModuleExportConfig(participantColumn);
                    configs.add(moduleExport);
                    exportInfoMap.put(moduleConfigKey, moduleExport);
                }

                Map<String, Object> questionDef = null;
                if (moduleExport.isActivity() && ElasticSearchUtil.QUESTIONS_ANSWER.equals(participantColumn.getObject())) {
                    questionDef = getDefForQuestion(participantColumn, activityDefs);
                }
                boolean splitChoicesIntoColumns = false;
                if (ESObjectConstants.OPTIONS_TYPE.equals(filter.getType())) {
                    if (questionDef != null) {
                        // create a column for each option if it's a multiselect
                        splitChoicesIntoColumns =
                                !humanReadable && ESObjectConstants.MULTIPLE.equals(questionDef.get(ESObjectConstants.SELECT_MODE));
                    }
                }
                // columns for 'meta' properties (completion date, etc.) and other non-questions will come first
                int questionIndex = questionDef != null ? (int) questionDef.get("index") : -1;

                String collationSuffix = ValueProviderFactory.COLLATED_SUFFIXES.stream()
                        .filter(suffix -> StringUtils.endsWith(participantColumn.getName(), suffix)).findFirst().orElse(null);

                FilterExportConfig colConfig =
                        new FilterExportConfig(moduleExport, filter, splitChoicesIntoColumns, !humanReadable, collationSuffix, questionDef,
                                questionIndex);
                if (collationSuffix != null) {
                    if (collationColumnMap.containsKey(collationSuffix)) {
                        if (colConfig.getOptions() != null) {
                            collationColumnMap.get(collationSuffix).getOptions().addAll(colConfig.getOptions());
                        }
                        // we only want one column config for collated questions, so don't add this to the module
                        continue;
                    } else {
                        collationColumnMap.put(collationSuffix, colConfig);
                    }
                }

                moduleExport.getQuestions().add(colConfig);
            } catch (Exception e) {
                logger.error("Export column could not be generated for filter", e);
            }
        }
        configs.sort(Comparator.comparing(ModuleExportConfig::isCollection).thenComparing(ModuleExportConfig::getAliasValue));
        // sort the questions inside each config
        configs.forEach(config -> {
            config.getQuestions().sort(Comparator.comparing(FilterExportConfig::getQuestionIndex));
        });
        return configs;
    }

    /**
     * Gets the definition for a given question based on a filter column, and adds the index so the question sort order can be preserved
     *
     * @param column       the column from the Filter
     * @param activityDefs the schema loaded from ElasticSearch
     * @return the question schema corresponding to the given column, or null if it does not correspond to a question (e.g.
     *       it is a 'data' attribute)
     */
    private Map<String, Object> getDefForQuestion(ParticipantColumn column, Map<String, Map<String, Object>> activityDefs) {
        String activityName = column.getTableAlias();
        Map<String, Object> activityDef =
                activityDefs.values().stream().filter(d -> d.get(ESObjectConstants.ACTIVITY_CODE).equals(activityName)).findFirst()
                        .orElse(null);
        if (activityDef == null) {
            return null;
        }
        // find the question with the matching stableId, and get the index
        List<Map<String, Object>> questionList = ((List<Map<String, Object>>) activityDef.get(ESObjectConstants.QUESTIONS));
        for (int i = 0; i < questionList.size(); i++) {
            Map<String, Object> question = questionList.get(i);
            if (question.get(ESObjectConstants.STABLE_ID).equals((column.getName()))) {
                question.put("index", i);
                return question;
            }
        }
        return null;
    }

    /**
     * map each participant's data into a map of columnName => string value, based on the passed-in configs
     */
    public List<Map<String, String>> parse(List<ModuleExportConfig> moduleConfigs, List<Map<String, Object>> participantEsDataMaps) {
        List<Map<String, String>> allParticipantMaps = new ArrayList<>(participantEsDataMaps.size());
        for (Map<String, Object> participant : participantEsDataMaps) {
            allParticipantMaps.addAll(generateParticipantTabularMaps(moduleConfigs, participant));
        }
        return allParticipantMaps;
    }

    /**
     * Convert the participant dtos into hashmaps with a value for each column
     *
     * @param moduleConfigs        the moduleExportConfigs, such as returned from generateExportConfigs
     * @param participantEsDataMap the participantDto dataAsMap, as fetched from elasticSearch
     * @return a list of hashmaps suitable for turning into a table. The list will be of length 1, unless this is a study (RGP)
     *       with family members.  In that case, the list will have one map for each member.
     */
    private List<Map<String, String>> generateParticipantTabularMaps(List<ModuleExportConfig> moduleConfigs,
                                                                     Map<String, Object> participantEsDataMap) {
        List<Map<String, String>> participantMaps = new ArrayList<>();

        // get the 'subParticipants' a.k.a RGP family members
        // note that getSubParticipants will always return at least one entry, (for non-RGP studies, it will just return a single empty map)
        List<Map<String, Object>> participantDataList = getSubParticipants(participantEsDataMap);
        for (Map<String, Object> subParticipant : participantDataList) {
            participantMaps.add(parseSingleParticipant(participantEsDataMap, moduleConfigs, subParticipant));
        }
        return participantMaps;
    }

    /**
     * parses a single participant record into a string-string map.  This method is public because
     * it is far easier to write tests with Map data than full-fledged ParticipantDTOWrappers
     */
    public Map<String, String> parseSingleParticipant(Map<String, Object> participantEsMap, List<ModuleExportConfig> moduleConfigs,
                                                      Map<String, Object> subParticipant) {
        Map<String, String> participantMap = new HashMap();
        for (ModuleExportConfig moduleConfig : moduleConfigs) {
            // get the data corresponding to each time this module was completed
            List<Map<String, Object>> esModuleMaps =
                    getModuleCompletions(participantEsMap, moduleConfig, subParticipant, this.onlyMostRecent);
            if (esModuleMaps.size() > moduleConfig.getNumMaxRepeats()) {
                moduleConfig.setNumMaxRepeats(esModuleMaps.size());
            }
            // for each time the module was completed, loop over the data and add it to the map
            for (int moduleIndex = 0; moduleIndex < esModuleMaps.size(); moduleIndex++) {
                Map<String, Object> esModuleMap = esModuleMaps.get(moduleIndex);
                addModuleDataToParticipantMap(moduleConfig, participantMap, esModuleMap, moduleIndex);
            }
        }
        return participantMap;
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
            List<Map<String, Object>> options = Collections.singletonList(null);
            List<Map<String, Object>> nestedOptions = new ArrayList<>();
            if (filterConfig.getOptions() != null && filterConfig.isSplitOptionsIntoColumns()) {
                options = filterConfig.getOptions();
                //check for nestedOptions and add as options
                options.stream().filter(option -> option.get(ESObjectConstants.NESTED_OPTIONS) != null).forEach(option -> {
                    nestedOptions.addAll((List<Map<String, Object>>) option.get(ESObjectConstants.NESTED_OPTIONS));
                });
                if (!nestedOptions.isEmpty()) {
                    options.addAll(nestedOptions);
                }
            }
            TextValueProvider valueProvider =
                    ValueProviderFactory.getValueProvider(filterConfig.getColumn().getName(), filterConfig.getType());

            List<List<String>> formattedValues = valueProvider.getFormattedValues(filterConfig, esModuleMap);
            if (filterConfig.isAllowMultiple() && formattedValues.size() > filterConfig.getMaxRepeats()) {
                filterConfig.setMaxRepeats(formattedValues.size());
            }
            // iterate through each response to the question, adding the values to the map
            for (int responseNum = 0; responseNum < formattedValues.size(); responseNum++) {
                List<String> responseValues = formattedValues.get(responseNum);

                if (filterConfig.getChildConfigs() != null) {
                    // if this is a composite question, we need a separate column for each child question
                    for (int childIndex = 0; childIndex < filterConfig.getChildConfigs().size(); childIndex++) {
                        List<String> childResponse = new ArrayList<>();
                        if (childIndex < responseValues.size()) {
                            childResponse = Collections.singletonList(responseValues.get(childIndex));
                        }
                        addSingleResponseToMap(childResponse,
                                filterConfig.getChildConfigs().get(childIndex), options, moduleRepeatNum, responseNum, esModuleMap,
                                valueProvider, participantMap, filterConfig);
                    }
                } else {
                    addSingleResponseToMap(responseValues, filterConfig, options, moduleRepeatNum, responseNum, esModuleMap, valueProvider,
                            participantMap, null);
                }

            }
        }
    }

    // adds a single question response to the map.  Also handles adding any additional details associated with the answer
    private void addSingleResponseToMap(List<String> responseValues, FilterExportConfig filterConfig, List<Map<String, Object>> options,
                                        int moduleRepeatNum, int responseNum, Map<String, Object> esModuleMap,
                                        TextValueProvider valueProvider, Map<String, String> participantMap,
                                        FilterExportConfig parentConfig) {
        for (Map<String, Object> option : options) {
            String colName = TabularParticipantExporter.getColumnName(filterConfig, moduleRepeatNum + 1, responseNum + 1, option, null,
                    parentConfig);
            String exportValue = StringUtils.EMPTY;
            String optionStableId = null;
            if (option != null) {
                optionStableId = (String) option.get(ESObjectConstants.OPTION_STABLE_ID);
                exportValue = responseValues.contains(optionStableId) ? COLUMN_SELECTED : COLUMN_UNSELECTED;
            } else {
                exportValue = responseValues.stream().collect(Collectors.joining(", "));
            }
            participantMap.put(colName, exportValue);
            if (filterConfig.hasDetailsForOption(optionStableId)) {
                String detailValue = valueProvider.getOptionDetails(filterConfig, esModuleMap, optionStableId, responseNum);
                String detailColName =
                        TabularParticipantExporter.getColumnName(filterConfig, moduleRepeatNum + 1, responseNum + 1, option, "DETAIL",
                                parentConfig);
                participantMap.put(detailColName, detailValue);
            }

        }
    }

    /**
     * Get subparticipants (aka RGP family members)
     *
     * @param esDataAsMap elastic search data for a ParticipantDto, in map form.
     * @return a list of sub-participant data (a.k.a. RGP family members) associated with the given participant
     *       If none exist, a list with a single empty map will be returned
     */
    List<Map<String, Object>> getSubParticipants(Map<String, Object> esDataAsMap) {
        List<String> pathNames = Arrays.asList(ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT_DATA);
        List<Map<String, Object>> participantDataList = (List<Map<String, Object>>) nullSafeGet(pathNames, esDataAsMap);
        if (participantDataList == null) {
            return Collections.singletonList(Collections.emptyMap());
        }
        List<Map<String, Object>> subParticipants = participantDataList.stream()
                // do a case insensitive comparison as some data has "rgp_PARTICIPANTS" as fieldIds
                .filter(item -> WorkflowAndFamilyIdExporter.RGP_PARTICIPANTS.equalsIgnoreCase(
                        (String) item.get(ESObjectConstants.FIELD_TYPE_ID))).collect(Collectors.toList());
        if (subParticipants.size() > 0) {
            return subParticipants;
        } else {
            return Collections.singletonList(Collections.emptyMap());
        }
    }

    private static Object nullSafeGet(List<String> pathNames, Map<String, Object> map) {
        Object finalObj = null;
        if (map == null) {
            return finalObj;
        }
        Map<String, Object> currentMap = map;
        for (String pathName : pathNames) {
            finalObj = currentMap.get(pathName);
            if (finalObj instanceof Map) {
                currentMap = (Map<String, Object>) finalObj;
            } else {
                return finalObj;
            }
        }
        return finalObj;
    }


    /**
     * Returns the maps correspond to the participants completions of a given activity, e.g. their completions of the MEDICAL_HISTORY
     *
     * @param esDataAsMap  the participant's ES data
     * @param moduleConfig the config for the given activity
     * @return the maps
     */
    private static List<Map<String, Object>> getModuleCompletions(Map<String, Object> esDataAsMap, ModuleExportConfig moduleConfig,
                                                                  Map<String, Object> subParticipant, boolean onlyMostRecent) {
        if (moduleConfig.isActivity()) {
            return getActivityCompletions(esDataAsMap, moduleConfig, onlyMostRecent);
        } else if (moduleConfig.getFilterKey().isJson() && moduleConfig.getName().startsWith(ESObjectConstants.DSM_PARTICIPANT_DATA)) {
            return getNestedCompletions(esDataAsMap, moduleConfig, subParticipant, onlyMostRecent);
        } else if ("proxy".equals(moduleConfig.getTableAlias())) {
            return getProxyCompletions(esDataAsMap, moduleConfig);
        } else {
            return getOtherCompletions(esDataAsMap, moduleConfig, subParticipant, onlyMostRecent);
        }
    }

    private static List<Map<String, Object>> getProxyCompletions(Map<String, Object> esDataAsMap, ModuleExportConfig moduleConfig) {
        List<Map<String, Object>> proxyData = (List<Map<String, Object>>) esDataAsMap.get(ESObjectConstants.PROXY_DATA);
        if (proxyData != null) {
            return proxyData.stream().map(proxy -> (Map<String, Object>) proxy.get(moduleConfig.getName())).collect(Collectors.toList());
        }
        return Collections.singletonList(Collections.emptyMap());
    }

    private static List<Map<String, Object>> getActivityCompletions(Map<String, Object> esDataAsMap, ModuleExportConfig moduleConfig,
                                                                    boolean onlyMostRecent) {
        List<Map<String, Object>> activityList = (List<Map<String, Object>>) esDataAsMap.get(ESObjectConstants.ACTIVITIES);
        if (activityList == null) {
            return Collections.singletonList(Collections.emptyMap());
        }
        List<Map<String, Object>> matchingActivities =
                activityList.stream().filter(activity -> moduleConfig.getName().equals(activity.get(ESObjectConstants.ACTIVITY_CODE)))
                        .collect(Collectors.toList());
        matchingActivities.sort((a1, a2) -> Long.compare((long) a2.get(ESObjectConstants.LAST_UPDATED_AT),
                (long) a1.get(ESObjectConstants.LAST_UPDATED_AT)));
        if (onlyMostRecent && matchingActivities.size() > 1) {
            return matchingActivities.subList(0, 1);
        }
        return matchingActivities;
    }

    // handles getting response objects from the dsm.participantData object
    private static List<Map<String, Object>> getNestedCompletions(Map<String, Object> esDataAsMap, ModuleExportConfig moduleConfig,
                                                                  Map<String, Object> subParticipant, boolean onlyMostRecent) {
        // get the module name from the first question -- this assumes all questions
        // in the module get stored in the same object.
        String objectName = moduleConfig.getQuestions().get(0).getColumn().getObject();
        if (objectName == null) {
            // this handles some derived RGP columns like "#FIRSTNAME #LASTNAME..." that do not need to be exported
            return Collections.singletonList(Collections.emptyMap());
        }
        // figure out whether we're dealing with subparticipants (RGP) or just data
        if (objectName.startsWith("RGP") && objectName.endsWith("GROUP")) {
            if (subParticipant != null) {
                return Collections.singletonList(subParticipant);
            } else {
                return Collections.singletonList(Collections.emptyMap());
            }
        } else {
            List<String> pathNames = Arrays.asList(ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT_DATA);
            List<Map<String, Object>> participantDataList = (List<Map<String, Object>>) nullSafeGet(pathNames, esDataAsMap);
            if (participantDataList == null) {
                return Collections.singletonList(Collections.emptyMap());
            }
            List<Map<String, Object>> matchingModules =
                    participantDataList.stream().filter(item -> objectName.equals(item.get(ESObjectConstants.FIELD_TYPE_ID)))
                            .collect(Collectors.toList());
            return matchingModules;
        }

    }

    private static List<Map<String, Object>> getOtherCompletions(Map<String, Object> esDataAsMap, ModuleExportConfig moduleConfig,
                                                                 Map<String, Object> subParticipant, boolean onlyMostRecen) {
        // this module is based off the root of the map, like 'dsm' or 'invitations'
        String mapPath = moduleConfig.getFilterKey().getValue();
        Object rootObject = esDataAsMap.get(mapPath);
        if (rootObject == null && mapPath.contains(".")) {
            // we need to traverse a nested path like "dsm.participant"
            String[] pathSegments = mapPath.split("\\.");
            rootObject = esDataAsMap;
            for (String segment : pathSegments) {
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
