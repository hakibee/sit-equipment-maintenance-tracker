package com.sit.tracker.model;

import com.sit.tracker.repository.Identifiable;

/**
 * A tracker user (staff member or lab user).
 *
 * <p>Note: the password is stored in plain text because this application is a
 * teaching/demo build. A production deployment would store a salted hash and
 * authenticate against the institute's directory service.</p>
 */
public class User implements Identifiable<String> {
    private final String id;
    private final String username;
    private String password;
    private final String fullName;
    private Role role;

    public User(String id, String username, String password, String fullName, Role role) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
    }

    @Override
    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    /** Display name used in menus and logs, e.g. "Hanna Tesfaye (user1)". */
    public String getDisplayName() {
        return fullName + " (" + username + ")";
    }

    @Override
    public String toString() {
        return String.format("%-7s %-28s %-18s %-24s", id, fullName, username, role.getLabel());
    }
}
