package org.broadinstitute.dsm.model.eel;

import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.ddp.email.SendGridEvent;
import org.broadinstitute.dsm.db.EELFollowUp;
import org.broadinstitute.dsm.db.EELSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Data
public class FollowUpEmail {

    private static final Logger logger = LoggerFactory.getLogger(FollowUpEmail.class);

    private static final String OPEN = "open";
    private static final String CLICK = "click";

    private String email;
    private int lastWorkflowId;
    private List<Template> templates;
    private boolean needsAttention;

    public FollowUpEmail(String email, int lastWorkflowId, List<Template> templates) {
        this.email = email;
        this.lastWorkflowId = lastWorkflowId;
        this.templates = templates;
    }

    public static Collection<FollowUpEmail> getWorkflowEventsByEmail(String source, @NonNull List<SendGridEvent> eelData,
                                                                     @NonNull Map<String, EELSettings> eelSettings) {
        Map<String, EELFollowUp> eelFollowUpMap = EELFollowUp.getFollowUpDate(source); //from eel_settings table

        Map<String, FollowUpEmail> emailMap = new HashMap<>();
        for(SendGridEvent eelEvent : eelData) {
            String eventTemplate = eelEvent.getSGE_DATA().getDdp_email_template();
            String eventEmail = eelEvent.getSGE_DATA().getEmail();
            String eventEvent = eelEvent.getSGE_DATA().getEvent();
            String sgeId = eelEvent.getSGE_ID();
            long timestamp = eelEvent.getSGE_DATA().getTimestamp();
            String url = eelEvent.getSGE_DATA().getUrl();

            EELSettings setting = eelSettings.get(eventTemplate);
            if (setting != null) {
                String templateName = setting.getName();
                if (!EELSettings.EMPTY_WORKFLOW.equals(setting.getWorkflowId())) {// only templates with workflowId are needed for follow-up
                    try {
                        int workflowId = Integer.parseInt(setting.getWorkflowId());
                        if (emailMap.get(eventEmail) != null) {
                            FollowUpEmail followUpEmail = emailMap.get(eventEmail);
                            if (followUpEmail.getTemplates() != null) {
                                List<Template> templateList = followUpEmail.getTemplates();

                                int found = -1;
                                for (int i = 0; i < templateList.size(); i++) {
                                    if (templateList.get(i).getTemplateId() != null &&
                                            templateList.get(i).getTemplateId().equals(eventTemplate)) {
                                        found = i;
                                        break;
                                    }
                                }

                                if (found != -1) {
                                    Template template = templateList.get(found);
                                    if (template.getEvents() != null) {
                                        List<Event> events = template.getEvents();
                                        Event ev = new Event(eventEvent, timestamp, url);
                                        events.add(ev);
                                    }
                                    //latest timestamp for that template
                                    template.setLastEventTimestamp(Math.max(template.getLastEventTimestamp(), timestamp));
                                    if (eelFollowUpMap.get(sgeId) != null) {
                                        EELFollowUp eelFollowUp = eelFollowUpMap.get(sgeId);
                                        template.setFollowUpDateString(eelFollowUp.getFollowUp());
                                    }
                                    if (OPEN.equals(eventEvent) || CLICK.equals(eventEvent)) {
                                        template.setFollowUp(false);
                                    }
                                }
                                else {
                                    List<Event> events = new ArrayList<>();
                                    Event ev = new Event(eventEvent, timestamp, url);
                                    events.add(ev);
                                    //follow up = if email wasn't opened or clicked
                                    boolean followUp = true;
                                    if (OPEN.equals(eventEvent) || CLICK.equals(eventEvent)) {
                                        followUp = false;
                                    }
                                    String followUpDateString = null;
                                    if (eelFollowUpMap.get(sgeId) != null) {
                                        EELFollowUp eelFollowUp = eelFollowUpMap.get(sgeId);
                                        followUpDateString = eelFollowUp.getFollowUp();
                                    }
                                    Template template = new Template(eventTemplate, templateName, workflowId, followUp,
                                            timestamp, sgeId, events, followUpDateString);
                                    templateList.add(template);
                                }
                                //last workflow mail got sent to participant
                                followUpEmail.setLastWorkflowId(Math.max(followUpEmail.getLastWorkflowId(), workflowId));
                            }
                        }
                        else {
                            List<Event> events = new ArrayList<>();
                            Event ev = new Event(eventEvent, timestamp, url);
                            events.add(ev);
                            //follow up = if email wasn't opened or clicked
                            boolean followUp = true;
                            if (OPEN.equals(eventEvent) || CLICK.equals(eventEvent)) {
                                followUp = false;
                            }
                            String followUpDateString = null;
                            if (eelFollowUpMap.get(sgeId) != null) {
                                EELFollowUp eelFollowUp = eelFollowUpMap.get(sgeId);
                                followUpDateString = eelFollowUp.getFollowUp();
                            }
                            Template template = new Template(eventTemplate, templateName, workflowId, followUp,
                                    timestamp, sgeId, events, followUpDateString);
                            List<Template> templateList = new ArrayList<>();
                            templateList.add(template);
                            FollowUpEmail email = new FollowUpEmail(eventEmail, workflowId, templateList);
                            emailMap.put(eventEmail, email);
                        }
                    }
                    catch (NumberFormatException e) {
                        logger.error("Couldn't parse workflowId " + setting.getWorkflowId() + " of template "
                                + eventTemplate + "to int ", e);
                    }
                }
            }
        }
        logger.info("Found " + emailMap.values().size() + " FollowUpEmails");
        return emailMap.values();
    }
}
