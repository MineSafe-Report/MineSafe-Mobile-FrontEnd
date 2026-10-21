package com.example.javareportingsystem.data.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The identity and session fields expected from a successful login response.
 */
public final class LoggedInUser {
    private final int accountId;
    private final String workerId;
    private final String displayName;
    private final String role;
    private final String accessToken;
    private final long accessExpiresAtMillis;
    private final List<String> assignedLocations;

    public LoggedInUser(
            int accountId,
            String workerId,
            String displayName,
            String role,
            String accessToken,
            long accessExpiresAtMillis,
            List<String> assignedLocations) {
        this.accountId = accountId;
        this.workerId = workerId;
        this.displayName = displayName;
        this.role = role;
        this.accessToken = accessToken;
        this.accessExpiresAtMillis = accessExpiresAtMillis;
        this.assignedLocations = Collections.unmodifiableList(new ArrayList<>(assignedLocations));
    }

    public int getAccountId() {
        return accountId;
    }

    public String getWorkerId() {
        return workerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRole() {
        return role;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public long getAccessExpiresAtMillis() {
        return accessExpiresAtMillis;
    }

    public List<String> getAssignedLocations() {
        return assignedLocations;
    }
}
