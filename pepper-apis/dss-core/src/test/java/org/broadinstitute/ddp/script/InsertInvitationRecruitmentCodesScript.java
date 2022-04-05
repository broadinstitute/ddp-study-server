package org.broadinstitute.ddp.script;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.InvitationFactory;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.util.GuidUtils;
import org.jdbi.v3.core.Handle;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Script for generating and inserting invite codes.
 * Instructions:
 * 1) Remove the @Ignore annotation
 * 2) Clone the repository git clone https://github.com/LDNOOBW/List-of-Dirty-Naughty-Obscene-and-Otherwise-Bad-Words.git
 * 3) Create @Test using generateTestBostonInvitationCodes as a model. You will need to specify as method arguments:
 * - The study guid
 * - The prefix to use on all generated codes
 * - The directory path of the cloned bad word repo from step 2
 */

@Ignore
public class InsertInvitationRecruitmentCodesScript extends TxnAwareBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(InsertInvitationRecruitmentCodesScript.class);
    public static final int CODE_LENGTH = 12;

    private String generateRecruitmentInvitationCode(String codePrefix) {
        return GuidUtils.randomWithPrefix(codePrefix, GuidUtils.UPPER_ALPHA_NUMERIC_EXCLUDING_CONFUSING_CHAR, CODE_LENGTH);
    }

    private InvitationDto insertRecruitmentInvitationCode(String invitationGuid, StudyDto study, Handle handle) {
        return handle.attach(InvitationFactory.class).createRecruitmentInvitation(study.getId(), invitationGuid);
    }

    private void insertRecruitmentInvitationCodes(String studyGuid, String codePrefix, final int qty, String badWordDirectoryPath) {
        List<String> normalizedBadWords = readAllPossibleBadWords(badWordDirectoryPath);
        LOG.info("Read a list of bad words of size: {}", normalizedBadWords.size());
        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, handle -> {
            StudyDto study = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
            if (study == null) {
                throw new DDPException("Could not find study with guid: " + studyGuid);
            }
            int insertedCount = 0;
            do {
                String newCode = generateRecruitmentInvitationCode(codePrefix);
                Optional<String> containedBadWord = normalizedBadWords.stream()
                        .filter(badWord -> newCode.contains(badWord)).findAny();
                if (containedBadWord.isEmpty()) {
                    InvitationDto invitation = insertRecruitmentInvitationCode(newCode, study, handle);
                    ++insertedCount;
                    LOG.info("Invitation code: {} inserted with id {}. {} left", newCode, invitation.getInvitationId(),
                            (qty - insertedCount));
                } else {
                    LOG.warn("Skipping inserting code: {} because it contains the bad word: {}", newCode, containedBadWord.get());
                }
            } while (insertedCount < qty);
        });
    }

    private List<String> readAllPossibleBadWords(String directoryPath) {
        List<File> wordFiles;
        try {
            wordFiles = Files.list(Paths.get(directoryPath))
                    .map(path -> path.toFile())
                    .filter(file -> !file.getName().contains("."))
                    .filter(file -> file.getName().toLowerCase().equals(file.getName())) // ignore files with uppercase chars in name
                    .collect(toList());
        } catch (IOException e) {
            throw new DDPException("Could not read the bad word directory at:" + directoryPath, e);
        }
        List<String> badWordList = wordFiles.stream().flatMap(wordFile -> {
            try {
                LOG.info("Reading bad word file: {}", wordFile.getAbsolutePath());
                return ((List<String>) (FileUtils.readLines(wordFile, "UTF-8"))).stream()
                        .filter(word -> StringUtils.isNotBlank(word));
            } catch (IOException e) {
                throw new DDPException("Could not read bad word file:" + wordFile.getAbsolutePath(), e);
            }
        }).collect(toList());

        return badWordList.stream().map(word -> normalizeWord(word)).filter(word -> isWordPossible(word)).collect(toList());
    }

    private String normalizeWord(String word) {
        return word.toUpperCase().replaceAll("\\s", "");
    }

    private boolean isWordPossible(String normalizedWord) {
        if (normalizedWord.length() > CODE_LENGTH) {
            return false;
        }
        Set<Character> wordChars = normalizedWord.codePoints().mapToObj(c -> (char) c).collect(toSet());
        Set<Character> validChars = new String(GuidUtils.UPPER_ALPHA_NUMERIC_EXCLUDING_CONFUSING_CHAR).codePoints()
                .mapToObj(c -> (char) c).collect(toSet());
        return validChars.containsAll(wordChars);
    }


    @Test
    public void generateTestBostonInvitationCodes() {
        insertRecruitmentInvitationCodes("testboston", "TB", 100, "ddp-study-server/pepper-apis/List-of-Dirty"
                + "-Naughty-Obscene-and-Otherwise-Bad-Words");
    }
}
