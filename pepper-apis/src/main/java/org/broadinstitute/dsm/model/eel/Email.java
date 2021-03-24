package org.broadinstitute.dsm.model.eel;

import java.util.List;

public class Email {

    private String email;
    private List<Event> events;

    public Email(String email, List<Event> events) {
        this.email = email;
        this.events = events;
    }

    public String getEmail() {
        return email;
    }

    public List<Event> getEvents() {
        return events;
    }
}
