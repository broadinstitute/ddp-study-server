package org.broadinstitute.dsm.model.eel;

import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.ddp.email.SendGridEvent;
import org.broadinstitute.dsm.db.EELSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Data
public class Template {

    private static final Logger logger = LoggerFactory.getLogger(Template.class);

    private String templateId;
    private String name;
    private List<Email> emails;
    private int workflowId;
    private boolean followUp;
    private long lastEventTimestamp;
    private String followUpDateString;
    private List<Event> events;
    private String sgeId;

    public Template(String templateId, String name, List<Email> emails, int workflowId) {
        this.templateId = templateId;
        this.name = name;
        this.emails = emails;
        this.workflowId = workflowId;
    }

    public Template(String templateId, String name, int workflowId, boolean followUp, long lastEventTimestamp,
                    String sgeId, List<Event> events, String followUpDateString) {
        this.templateId = templateId;
        this.name = name;
        this.workflowId = workflowId;
        this.followUp = followUp;
        this.lastEventTimestamp = lastEventTimestamp;
        this.sgeId = sgeId;
        this.events = events;
        this.followUpDateString = followUpDateString;
    }

    public static Collection<Template> getEELDataByTemplate(@NonNull List<SendGridEvent> eelData, @NonNull Map<String, EELSettings> eelSettings) {
        Map<String, Template> templates = new HashMap<>();
        for(SendGridEvent eelEvent : eelData) {
            String eventTemplate = eelEvent.getSGE_DATA().getDdp_email_template();
            String eventEmail = eelEvent.getSGE_DATA().getEmail();
            String eventEvent = eelEvent.getSGE_DATA().getEvent();
            long timestamp = eelEvent.getSGE_DATA().getTimestamp();
            String url = eelEvent.getSGE_DATA().getUrl();

            if (templates.get(eventTemplate) != null) {
                Template template = templates.get(eventTemplate);
                if (template.getEmails() != null) {
                    List<Email> emails = template.getEmails();

                    int found = -1;
                    for (int i = 0; i < emails.size(); i++) {
                        if (emails.get(i).getEmail().equals(eventEmail)) {
                            found = i;
                            break;
                        }
                    }
                    if (found != -1) {
                        Email email = emails.get(found);
                        if (email.getEvents() != null) {
                            List<Event> events = email.getEvents();
                            Event ev = new Event(eventEvent, timestamp, url);
                            events.add(ev);
                        }
                    }
                    else {
                        List<Event> events = new ArrayList<>();
                        Event ev = new Event(eventEvent, timestamp, url);
                        events.add(ev);
                        Email email = new Email(eventEmail, events);
                        emails.add(email);
                    }
                }
            }
            else {
                List<Event> events = new ArrayList<>();
                Event ev = new Event(eventEvent, timestamp, url);
                events.add(ev);
                Email email = new Email(eventEmail, events);
                List<Email> emails = new ArrayList<>();
                emails.add(email);
                String name = null;
                int workflowId = -1;
                EELSettings setting = eelSettings.get(eventTemplate);
                if (setting != null) {
                    name = setting.getName();
                    if (!EELSettings.EMPTY_WORKFLOW.equals(setting.getWorkflowId())){
                        workflowId = Integer.parseInt(setting.getWorkflowId());
                    }
                }
                Template template = new Template(eventTemplate, name, emails, workflowId);
                templates.put(eventTemplate, template);
            }
        }
        logger.info("Found " + templates.values().size() + " Templates ");
        return templates.values();
    }
}
