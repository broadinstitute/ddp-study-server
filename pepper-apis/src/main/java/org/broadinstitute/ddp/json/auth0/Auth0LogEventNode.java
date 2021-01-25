package org.broadinstitute.ddp.json.auth0;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.NodeType.DATETIME;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.NodeType.E_MAIL;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.NodeType.JSON_NODE;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.NodeType.STR;

import java.time.Instant;
import java.util.Map;


import com.google.gson.JsonElement;


/**
 * Contains Auth0 Log Event JSON nodes description which needs to read
 * from Auth0 Log event Payload (JSON doc).
 * In this enum defined some logic of JSON nodes parsing (nodes extracted using
 * class {@link org.broadinstitute.ddp.util.GsonRecursiveReader}.
 * This approach is used (instead of deserializing JSON to a POJO class) because of the following reasons:
 * <ul>
 *     <li>format of Auth0 log event is not same for different types: node "details" differs for
 *     some types;</li>
 *     <li>some field which to be defined on top level of node "data" can be defined deeper inside node "details" instead
 *     (therefore it needs recursively find node by name (for example node "user_id");</li>
 *     <li>different nodes can hold email value (it can be "email" or "user_name": if "email" not found - read it from "user_name");</li>
 * </ul>
 */
public enum Auth0LogEventNode {

    LOGS("logs", JSON_NODE),
    LOG_ID("log_id", STR),
    DATE("date", DATETIME),
    TYPE("type", STR),
    CLIENT_ID("client_id", STR),
    CONNECTION_ID("connection_id", STR),
    USER_ID("user_id", STR),
    USER_AGENT("user_agent", STR),
    IP("ip", STR),
    USER_NAME("user_name", E_MAIL),
    EMAIL("email", E_MAIL, USER_NAME),
    DATA("data", JSON_NODE);

    private String nodeName;
    private NodeType nodeType;
    private Auth0LogEventNode secondNode;

    Auth0LogEventNode(String nodeName, NodeType nodeType) {
        this(nodeName, nodeType, null);
    }

    /**
     * Constructor for enum elements
     *
     * @param nodeName   - name of a node in Auth0 Log Events JSON doc
     * @param nodeType   - type of a node (string, datetime, email, etc)
     * @param secondNode - node which value will be fetched in case if this node value is null
     */
    Auth0LogEventNode(String nodeName, NodeType nodeType, Auth0LogEventNode secondNode) {
        this.nodeName = nodeName;
        this.nodeType = nodeType;
        this.secondNode = secondNode;
    }

    public String nodeName() {
        return nodeName;
    }

    public NodeType nodeType() {
        return nodeType;
    }

    public Auth0LogEventNode getSecondNode() {
        return secondNode;
    }

    /**
     * Get from logEvent a string value of a specified node
     */
    public static String resolveStringValue(final Auth0LogEventNode node, final Map<String, JsonElement> logEvent) {
        if (node.nodeType() == DATETIME) {
            throw new IllegalArgumentException("Invalid node type: " + node.nodeType());
        }
        return (String) resolveValue(node, logEvent);
    }

    /**
     * Get from logEvent a datetime value of a specified node
     */
    public static Instant resolveDateTimeValue(final Auth0LogEventNode node, final Map<String, JsonElement> logEvent) {
        if (node.nodeType() != DATETIME) {
            throw new IllegalArgumentException("Invalid node type: " + node.nodeType());
        }
        return (Instant) resolveValue(node, logEvent);
    }

    /**
     * Get from logEvent a value of a specified node
     */
    public static Object resolveValue(final Auth0LogEventNode node, final Map<String, JsonElement> logEvent) {
        var resolvedNode = node;
        var value = logEvent.get(resolvedNode.nodeName());
        if (value == null) {
            if (resolvedNode.getSecondNode() != null) {
                resolvedNode = resolvedNode.getSecondNode();
                value = logEvent.get(resolvedNode.nodeName());
            }
        }
        if (value != null && isNotBlank(value.toString())) {
            switch (resolvedNode.nodeType()) {
                case DATETIME:
                    return Instant.parse(value.getAsString());
                case JSON_NODE:
                    return value.toString();
                case E_MAIL:
                case STR:
                default:
                    return value.getAsString();
            }
        }
        return null;
    }

    /**
     * Types of JSON node
     */
    public enum NodeType {
        STR,
        DATETIME,
        E_MAIL,
        JSON_NODE
    }
}
