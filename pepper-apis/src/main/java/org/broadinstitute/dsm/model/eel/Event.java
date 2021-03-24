package org.broadinstitute.dsm.model.eel;

public class Event {

    private String event;
    private long timestamp;
    private String url;

    public Event(String event, long timestamp, String url) {
        this.event = event;
        this.timestamp = timestamp;
        this.url = url;
    }

    public String getEvent() {
        return event;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getUrl() {
        return url;
    }
}
