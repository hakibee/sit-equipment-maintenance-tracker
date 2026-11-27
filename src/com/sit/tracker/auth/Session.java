package com.sit.tracker.auth;

import com.sit.tracker.model.User;

/**
 * Holds the currently logged-in user. Created once per application run and
 * passed to every service call; services read the user from here to apply
 * RBAC and to attribute audit entries.
 */
public final class Session {
    private User currentUser;

    public void login(User user) {
        this.currentUser = user;
    }

    public void logout() {
        this.currentUser = null;
    }

    /** @return the logged-in user, or {@code null} when nobody is logged in. */
    public User currentUser() {
        return currentUser;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }
}
