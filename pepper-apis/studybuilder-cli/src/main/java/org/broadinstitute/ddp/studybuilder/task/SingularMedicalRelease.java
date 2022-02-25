package org.broadinstitute.ddp.studybuilder.task;


import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.exception.DDPException;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class SingularMedicalRelease implements CustomTask {
    private static final Logger log = LoggerFactory.getLogger(SingularMedicalRelease.class);
    private static final String DATA_FILE = "patches/update-medical-release.conf";
    private static final String UPDATE_LINKS = "$ddp.checkAnswer(" +
            "\"MRR_WHO_ENROLLING_COPY\"," +
            "\"MYSELF\"," +
            " \"Participants with questions about this form may contact a member of the study staff at " +
            "<a href=\"tel:(650) 761-6486\" class=\"Link\">(650) 761-6486</a> Monday " +
            "- Friday 9 a.m. - 5 p.m. or email at " +
            "<a href=\"mailto:contact@projectsingular.org\" class=\"Link\">contact@projectsingular.org</a>." +
            "\"," +
            " \"Parents or legal guardians with questions about this form may contact a member of the study staff at" +
            " <a href=\"tel:(650) 761-6486\" class=\"Link\">(650) 761-6486</a>Monday - Friday 9 a.m. - 5 p.m. or email at " +
            "<a href=\"mailto:contact@projectsingular.org\" class=\"Link\">contact@projectsingular.org</a>." +
            "\")";


    private Config cfg;
    private Config dataCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfg = studyCfg;
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
    }

    @Override
    public void run(Handle handle) {
        Config sections = dataCfg.getConfig("toUpdateVariables");
        updateMedicalRecordReleaseQuestionli2(handle, sections);
        updates3p1(handle, sections);
//        updateIntroli3(handle, sections);
    }

    private int updateMedicalRecordReleaseQuestionli2(Handle handle, Config section){
        String name = section.getString("nameli2");
        long variableId = getVariableId(name, handle);
        List<? extends Config> update = dataCfg.getConfigList("updates");
        String string = update.get(0).getConfig("upload").getString("li2");
        log.info("trying to update variable {}", string);
        return udateTemplate(variableId, string, handle);
    }

    private int updates3p1(Handle handle, Config section){
        String name = section.getString("namep1");
        long variableId = getVariableId(name, handle);
        List<? extends Config> updates = dataCfg.getConfigList("updates");
        String s3 = updates.get(1).getConfig("s3").getString("p1");
        log.info("trying to update variable {}", s3);
        return udateTemplate(variableId, s3, handle);
    }

//    private int updateIntroli3(Handle handle, Config section){
//        String name = section.getString("nameli3");
//        long variableId = getVariableId(name, handle);
//        log.info("trying to update variable {}", UPDATE_LINKS);
//        return udateTemplate(variableId, UPDATE_LINKS, handle);
//    }

    private long getVariableId(String variableName, Handle handle){
        return handle.attach(sqlHelper.class).getVariableIdbyName(variableName);
    }

    private int udateTemplate(long variable_id, String substitution, Handle handle){
        return handle.attach(sqlHelper.class).updateTemplate(substitution, variable_id);
    }


    private interface sqlHelper extends SqlObject {

        @SqlUpdate("update i18n_template_substitution\n" +
                "set substitution_value = :substitution_value\n" +
                "where template_variable_id = :template_variable_id")
        int updateTemplate(@Bind("substitution_value") String substitutionValue, @Bind("template_variable_id") long variableId);

        @SqlQuery("select template_variable_id from template_variable where variable_name = :variable_name")
        long getVariableIdbyName(@Bind("variable_name") String variableName);

    }
}