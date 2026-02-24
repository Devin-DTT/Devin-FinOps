package com.devin.finops.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Model representing a single consumption/session record from the Devin API.
 * Maps to the raw_usage_data.json structure used by the Python pipeline.
 *
 * Fields: session_id, user_id, organization_id, project_id, pull_request_id,
 *         timestamp, acu_consumed, business_unit, task_type, is_out_of_hours,
 *         is_merged, session_outcome
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConsumptionData {

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("organization_id")
    private String organizationId;

    @JsonProperty("project_id")
    private String projectId;

    @JsonProperty("pull_request_id")
    private String pullRequestId;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("acu_consumed")
    private double acuConsumed;

    @JsonProperty("business_unit")
    private String businessUnit;

    @JsonProperty("task_type")
    private String taskType;

    @JsonProperty("is_out_of_hours")
    private boolean outOfHours;

    @JsonProperty("is_merged")
    private boolean merged;

    @JsonProperty("session_outcome")
    private String sessionOutcome;

    public ConsumptionData() {
    }

    public ConsumptionData(String sessionId, String userId, String organizationId,
                           String projectId, String pullRequestId, String timestamp,
                           double acuConsumed, String businessUnit, String taskType,
                           boolean outOfHours, boolean merged, String sessionOutcome) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.pullRequestId = pullRequestId;
        this.timestamp = timestamp;
        this.acuConsumed = acuConsumed;
        this.businessUnit = businessUnit;
        this.taskType = taskType;
        this.outOfHours = outOfHours;
        this.merged = merged;
        this.sessionOutcome = sessionOutcome;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getPullRequestId() {
        return pullRequestId;
    }

    public void setPullRequestId(String pullRequestId) {
        this.pullRequestId = pullRequestId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public double getAcuConsumed() {
        return acuConsumed;
    }

    public void setAcuConsumed(double acuConsumed) {
        this.acuConsumed = acuConsumed;
    }

    public String getBusinessUnit() {
        return businessUnit;
    }

    public void setBusinessUnit(String businessUnit) {
        this.businessUnit = businessUnit;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public boolean isOutOfHours() {
        return outOfHours;
    }

    public void setOutOfHours(boolean outOfHours) {
        this.outOfHours = outOfHours;
    }

    public boolean isMerged() {
        return merged;
    }

    public void setMerged(boolean merged) {
        this.merged = merged;
    }

    public String getSessionOutcome() {
        return sessionOutcome;
    }

    public void setSessionOutcome(String sessionOutcome) {
        this.sessionOutcome = sessionOutcome;
    }

    @Override
    public String toString() {
        return "ConsumptionData{" +
                "sessionId='" + sessionId + '\'' +
                ", userId='" + userId + '\'' +
                ", organizationId='" + organizationId + '\'' +
                ", acuConsumed=" + acuConsumed +
                ", taskType='" + taskType + '\'' +
                ", sessionOutcome='" + sessionOutcome + '\'' +
                '}';
    }
}
