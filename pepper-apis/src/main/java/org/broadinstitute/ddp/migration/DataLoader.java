package org.broadinstitute.ddp.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.typesafe.config.Config;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstanceStatus;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.db.dao.JdbiMailingList;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.ClientDto;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DataLoader {

    private static final Logger LOG = LoggerFactory.getLogger(DataLoader.class);

    private final Config cfg;
    private final String studyGuid;
    private final SourceFileReader fileReader;
    private final Gson gson;
    private Mapping mapping;
    private ClientDto clientDto;
    private Map<String, Long> activityCodeToId = new HashMap<>();

    DataLoader(Config cfg, SourceFileReader fileReader) {
        this.cfg = cfg;
        this.studyGuid = cfg.getString(LoaderConfigFile.STUDY_GUID);
        this.fileReader = fileReader;
        this.gson = new Gson();
    }

    public String getStudyGuid() {
        return studyGuid;
    }

    public void processMailingListFiles() {
        Set<String> filenames = fileReader.listMailingListFiles();
        LOG.info("Found {} mailing list files", filenames.size());

        for (var filename : filenames) {
            LOG.info("Working on mailing list file: {}", filename);
            var data = gson.fromJson(fileReader.readContent(filename), MailingListFile.class);
            TransactionWrapper.useTxn(TransactionWrapper.DB.APIS,
                    handle -> loadMailingListContacts(handle, data.getContacts()));
        }
    }

    private void loadMailingListContacts(Handle handle, List<MailingListContact> contacts) {
        String padding = "  ";
        LOG.info(padding + "Mailing list: loading {} contacts", contacts.size());

        List<JdbiMailingList.MailingListEntryDto> entryDtos = new ArrayList<>();
        for (var contact : contacts) {
            String firstName = StringUtils.defaultIfBlank(contact.getFirstName(), "");
            String lastName = StringUtils.defaultIfBlank(contact.getLastName(), "");
            entryDtos.add(new JdbiMailingList.MailingListEntryDto(
                    firstName, lastName, contact.getEmail(),
                    studyGuid, null, contact.getInfo(),
                    contact.getDateCreatedMillis()));
        }

        int[] counts = handle.attach(JdbiMailingList.class).bulkInsertIfNotStoredAlready(entryDtos);
        DBUtils.checkInsert(contacts.size(), counts.length);

        for (int i = 0; i < counts.length; i++) {
            int numInserted = counts[i];
            String email = contacts.get(i).getEmail();
            if (numInserted == 0) {
                LOG.warn(padding + "Mailing list: already contains contact with email '{}'", email);
            } else if (numInserted > 1) {
                LOG.error(padding + "Mailing list: expected to insert 1 row but did {} for email '{}'", numInserted, email);
            }
        }

        LOG.info(padding + "Mailing list: finished loading contacts");
    }

    private void initMappingFile() {
        Path path = Path.of(cfg.getString(LoaderConfigFile.MAPPING_FILE));
        try {
            var reader = Files.newBufferedReader(path);
            mapping = gson.fromJson(reader, Mapping.class);
        } catch (IOException e) {
            throw new LoaderException(e);
        }
        if (!studyGuid.equals(mapping.getStudyGuid())) {
            throw new LoaderException("Mapping file study guid does not match!");
        }
        LOG.info("Using mapping file: {}", path);
    }

    private void initAuth0Client() {
        String auth0Domain = cfg.getString(LoaderConfigFile.AUTH0_DOMAIN);
        String auth0ClientId = cfg.getString(LoaderConfigFile.AUTH0_CLIENT_ID);
        clientDto = TransactionWrapper.withTxn(handle -> handle
                .attach(JdbiClient.class)
                .getClientByAuth0ClientAndDomain(auth0ClientId, auth0Domain)
                .orElseThrow(() -> new LoaderException("Could not load client " + auth0ClientId)));
        LOG.info("Using auth0 client: id={}, tenantId={}, domain={}, clientId={}",
                clientDto.getId(), clientDto.getAuth0TenantId(), clientDto.getAuth0Domain(), clientDto.getAuth0ClientId());
    }

    public void processParticipantFiles() {
        initMappingFile();
        initAuth0Client();

        Set<String> filenames = fileReader.listParticipantFiles();
        LOG.info("Found {} participant files", filenames.size());

        int count = 1;
        int total = filenames.size();
        for (var filename : filenames) {
            LOG.info("({}/{}) Working on participant file: {}", count, total, filename);
            var data = gson.fromJson(fileReader.readContent(filename), ParticipantFile.class);
            TransactionWrapper.useTxn(handle -> processParticipant(handle, data));
            count++;
        }
    }

    private void processParticipant(Handle handle, ParticipantFile data) {
        String padding = "  ";

        var participant = data.getParticipantWrapper();
        LOG.info(padding + "[user] altpid={}, shortid={}, email={}, surveys={}",
                participant.getAltPid(), participant.getShortId(),
                participant.getEmail(), data.getNumSurveys());

        // todo: deal with auth0 account
        User user = createLegacyUser(handle, participant);
        LOG.info(padding + "- Created migration user: id={}, guid={}, hruid={}",
                user.getId(), user.getGuid(), user.getHruid());

        long registeredAt = participant.getCreated().toEpochMilli();
        handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(
                user.getGuid(), studyGuid, EnrollmentStatusType.REGISTERED, registeredAt);
        LOG.info(padding + "- Registered user {} in study {}", user.getGuid(), studyGuid);

        for (var activityMapping : mapping.getActivities()) {
            processActivity(handle, user, activityMapping, data);
        }
    }

    private User createLegacyUser(Handle handle, ParticipantWrapper participant) {
        String auth0UserId = "fake|" + System.currentTimeMillis();

        String legacyShortId = participant.getShortId();
        String legacyAltPid = participant.getAltPid();
        Instant createdAt = participant.getCreated();
        long createdAtMillis = createdAt.toEpochMilli();

        var userDao = handle.attach(UserDao.class);
        String userGuid = DBUtils.uniqueUserGuid(handle);
        String userHruid = DBUtils.uniqueUserHruid(handle);
        long userId = userDao.getUserSql().insertByClientIdOrAuth0Ids(
                true, clientDto.getId(), null, null, auth0UserId,
                userGuid, userHruid, legacyAltPid, legacyShortId, false,
                createdAtMillis, createdAtMillis, null);

        var participantMapping = mapping.getParticipant();
        var langDto = LanguageStore.get(participantMapping.getDefaultLanguage());
        var profile = new UserProfile.Builder(userId)
                .setFirstName(participant.getFirstName())
                .setLastName(participant.getLastName())
                .setPreferredLangId(langDto.getId())
                .build();
        handle.attach(UserProfileDao.class).createProfile(profile);

        return userDao.findUserById(userId).orElseThrow(() ->
                new LoaderException("Could not find newly created user with altpid" + legacyAltPid));
    }

    private void processActivity(Handle handle, User user, MappingActivity activity, ParticipantFile data) {
        String padding = "  ";

        String sourceSurveyName = activity.getSource();
        var survey = data.getSurveyWrapper(sourceSurveyName);
        if (survey == null) {
            LOG.info(padding + "Participant does not have survey {}, continuing", sourceSurveyName);
            return;
        }

        LOG.info(padding + "[{}] created={}, completed={}, updated={}", sourceSurveyName,
                survey.getCreated(), survey.getFirstCompleted(), survey.getLastUpdated());
        var instanceDto = createActivityInstance(handle, user, activity, survey, null);
        LOG.info(padding + "- Created activity instance with id={}, guid={}", instanceDto.getId(), instanceDto.getGuid());
        loadAnswers(handle, user, instanceDto, activity, survey);

        for (var nestedMapping : activity.getNestedActivities()) {
            switch (nestedMapping.getNestedType()) {
                case KEYS:
                    processNestedActivityKeys(handle, user, instanceDto, nestedMapping, survey);
                    break;
                case LIST:
                    processNestedActivityList(handle, user, instanceDto, nestedMapping, survey);
                    break;
                default:
                    throw new LoaderException("Unhandled nested activity type: " + nestedMapping.getNestedType());
            }
        }
    }

    private void processNestedActivityKeys(Handle handle, User user, ActivityInstanceDto parentInstanceDto,
                                           MappingActivity nestedActivity, SurveyWrapper parentSurvey) {
        String padding = "  ";

        // KEYS means data is in the parent survey itself, so we use the same object for data, including timestamps.
        LOG.info(padding + "[{}] parent={}", nestedActivity.getActivityCode(), parentInstanceDto.getActivityCode());

        var instanceDto = createActivityInstance(handle, user, nestedActivity, parentSurvey, parentInstanceDto.getId());
        LOG.info(padding + "- Created nested activity instance with id={}, guid={}", instanceDto.getId(), instanceDto.getGuid());

        loadAnswers(handle, user, instanceDto, nestedActivity, parentSurvey);
    }

    private void processNestedActivityList(Handle handle, User user, ActivityInstanceDto parentInstanceDto,
                                           MappingActivity nestedActivity, SurveyWrapper parentSurvey) {
        String padding = "  ";

        List<ObjectWrapper> nestedList = parentSurvey.getObjectList(nestedActivity.getNestedList());
        if (nestedList == null || nestedList.isEmpty()) {
            if (nestedActivity.isNestedCreateIfEmptyList()) {
                // We should create an empty nested instance. Since it's empty, we should only have a created timestamp,
                // so we create a new survey wrapper here. For the actual timestamp, let's use the parent's one.
                var object = new JsonObject();
                object.add("ddp_created", new JsonPrimitive(parentSurvey.getCreated().toString()));
                var survey = new SurveyWrapper(object);
                var instanceDto = createActivityInstance(handle, user, nestedActivity, survey, parentInstanceDto.getId());
                LOG.info(padding + "[{}] parent={}", nestedActivity.getActivityCode(), parentInstanceDto.getActivityCode());
                LOG.info(padding + "- Created empty nested activity instance with id={}, guid={}",
                        instanceDto.getId(), instanceDto.getGuid());
            }
            return;
        }

        int number = 1;
        for (var nested : nestedList) {
            // Each nested object becomes the survey object to use for pulling out data. Use same timestamps as parent.
            LOG.info(padding + "[{}] parent={} ({})", nestedActivity.getActivityCode(), parentInstanceDto.getActivityCode(), number);
            var instanceDto = createActivityInstance(handle, user, nestedActivity, parentSurvey, parentInstanceDto.getId());
            LOG.info(padding + "- Created nested activity instance with id={}, guid={}", instanceDto.getId(), instanceDto.getGuid());
            loadAnswers(handle, user, instanceDto, nestedActivity, nested);
            number++;
        }
    }

    private ActivityInstanceDto createActivityInstance(Handle handle, User user, MappingActivity activity,
                                                       SurveyWrapper survey, Long parentInstanceId) {
        if (!activityCodeToId.containsKey(activity.getActivityCode())) {
            ActivityDto activityDto = handle.attach(JdbiActivity.class)
                    .findActivityByStudyGuidAndCode(studyGuid, activity.getActivityCode())
                    .orElseThrow(() -> new LoaderException("Could not find activity " + activity.getActivityCode()));
            activityCodeToId.put(activity.getActivityCode(), activityDto.getActivityId());
        }

        long activityId = activityCodeToId.get(activity.getActivityCode());
        long createdAtMillis = survey.getCreated().toEpochMilli();
        Instant completedAt = survey.getFirstCompleted();
        Instant updatedAt = survey.getLastUpdated();

        // Use low-level interface so we don't trigger any side-effects like events or nested activities.
        var jdbiInstance = handle.attach(JdbiActivityInstance.class);
        var jdbiInstanceStatus = handle.attach(JdbiActivityInstanceStatus.class);

        long instanceId = jdbiInstance.insert(
                activityId, user.getId(), jdbiInstance.generateUniqueGuid(),
                null, createdAtMillis, null, parentInstanceId,
                survey.getSubmissionId(), survey.getSessionId(), survey.getVersion());
        long statusId = jdbiInstanceStatus.insert(instanceId, InstanceStatusType.CREATED, createdAtMillis, user.getId());

        if (completedAt != null) {
            boolean addedDelta = false;
            long completedAtMillis = completedAt.toEpochMilli();
            if (completedAtMillis < createdAtMillis) {
                throw new LoaderException("ddp_firstcompleted is less than ddp_created");
            } else if (completedAtMillis == createdAtMillis) {
                // Pepper doesn't allow statuses to have same timestamp, so we add a small delta here.
                completedAtMillis += 1;
                addedDelta = true;
            }
            statusId = jdbiInstanceStatus.insert(instanceId, InstanceStatusType.COMPLETE, completedAtMillis, user.getId());
            DBUtils.checkUpdate(1, jdbiInstance.updateFirstCompletedAtIfNotSet(instanceId, completedAtMillis));
            if (updatedAt != null) {
                // Shift this one too if we shifted the above one, so we don't get into situation where updated < completed.
                long updatedAtMillis = updatedAt.toEpochMilli() + (addedDelta ? 1 : 0);
                if (updatedAtMillis < completedAtMillis) {
                    throw new LoaderException("ddp_lastupdated is less than ddp_firstcompleted");
                } else if (updatedAtMillis > completedAtMillis) {
                    DBUtils.checkUpdate(1, jdbiInstanceStatus.updateTimestampByStatusId(statusId, completedAtMillis));
                }
            }
        } else if (updatedAt != null) {
            long updatedAtMillis = updatedAt.toEpochMilli();
            if (updatedAtMillis < createdAtMillis) {
                throw new LoaderException("ddp_lastupdated is less than ddp_created");
            } else if (updatedAtMillis == createdAtMillis) {
                updatedAtMillis += 1;
            }
            jdbiInstanceStatus.insert(instanceId, InstanceStatusType.IN_PROGRESS, updatedAtMillis, user.getId());
        }

        return jdbiInstance.getByActivityInstanceId(instanceId).orElseThrow(() ->
                new LoaderException("Could not find newly created activity instance with id " + instanceId));
    }

    private void loadAnswers(Handle handle, User user, ActivityInstanceDto instanceDto,
                             MappingActivity activity, ObjectWrapper survey) {
        String padding = "  - ";
        var answerDao = handle.attach(AnswerDao.class);
        for (var question : activity.getQuestions()) {
            Answer answer = question.extractAnswer(survey);
            if (answer != null) {
                answerDao.createAnswer(user.getId(), instanceDto.getId(), answer);
                LOG.info(padding + "[{}] type={}, source={}, answerId={}, answerGuid={}",
                        question.getTarget(), question.getType(), question.getSource(),
                        answer.getAnswerId(), answer.getAnswerGuid());
            } else {
                LOG.info(padding + "[{}] type: {}, no source",
                        question.getTarget(), question.getType());
            }
        }
    }
}
