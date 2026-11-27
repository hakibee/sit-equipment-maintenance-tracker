package com.sit.tracker.service;

import com.sit.tracker.auth.AccessControl;
import com.sit.tracker.auth.AccessDeniedException;
import com.sit.tracker.auth.Session;
import com.sit.tracker.model.Role;
import com.sit.tracker.model.User;
import com.sit.tracker.repository.DataStore;
import com.sit.tracker.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Authentication and user administration: login/logout, listing and creating
 * users, changing roles. All actions are audited.
 */
public class AuthService {

    private final DataStore store;
    private final AuditService audit;
    private final Repository<String, User> users;

    public AuthService(DataStore store, AuditService audit) {
        this.store = store;
        this.audit = audit;
        this.users = store.users();
    }

    /**
     * Verify credentials and log the user in.
     *
     * @throws AccessDeniedException on unknown username or wrong password
     */
    public Session login(String username, String password) throws AccessDeniedException {
        Optional<User> found = users.findAll().stream()
                .filter(u -> u.getUsername().equalsIgnoreCase(username))
                .findFirst();

        if (found.isEmpty() || !found.get().getPassword().equals(password)) {
            audit.log(username, "UNKNOWN", "LOGIN_FAILED", "USER", username,
                    "Invalid username or password");
            throw new AccessDeniedException("Invalid username or password.");
        }

        User user = found.get();
        Session session = new Session();
        session.login(user);
        audit.log(session, "LOGIN_SUCCESS", "USER", user.getId(),
                user.getFullName() + " logged in as " + user.getRole().getLabel());
        return session;
    }

    /** Log the session out (audit + clear). Safe to call when already logged out. */
    public void logout(Session session) {
        User user = session.currentUser();
        if (user != null) {
            audit.log(session, "LOGOUT", "USER", user.getId(),
                    user.getFullName() + " logged out");
        }
        session.logout();
    }

    /** All users, for the administrator's "manage users" screen. */
    public List<User> listUsers(Session session) throws AccessDeniedException {
        AccessControl.check(session, com.sit.tracker.model.Permission.MANAGE_USERS);
        return users.findAll();
    }

    /**
     * Technicians only, used to populate the ticket-assignment list.
     * Read-only and available to anyone holding ASSIGN_TICKETS.
     */
    public List<User> listTechnicians() {
        return users.findAll().stream()
                .filter(u -> u.getRole() == Role.TECHNICIAN)
                .toList();
    }

    public Optional<User> findById(String userId) {
        return users.findById(userId);
    }

    /** Create a user account (administrator only). */
    public User createUser(Session session, String username, String password,
                           String fullName, Role role) throws AccessDeniedException {
        AccessControl.check(session, com.sit.tracker.model.Permission.MANAGE_USERS);

        boolean usernameTaken = users.findAll().stream()
                .anyMatch(u -> u.getUsername().equalsIgnoreCase(username));
        if (usernameTaken) {
            throw new IllegalArgumentException("Username '" + username + "' is already taken.");
        }

        User user = new User(store.nextId("USR"), username, password, fullName, role);
        users.save(user);
        audit.log(session, "USER_CREATED", "USER", user.getId(),
                user.getDisplayName() + " created with role " + role.getLabel());
        return user;
    }

    /** Change a user's role (administrator only). */
    public User changeRole(Session session, String userId, Role newRole) throws AccessDeniedException {
        AccessControl.check(session, com.sit.tracker.model.Permission.MANAGE_USERS);

        User user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("No user with id " + userId));

        if (user.getRole() == newRole) {
            throw new IllegalArgumentException(user.getDisplayName() + " already has role " + newRole.getLabel());
        }

        Role oldRole = user.getRole();
        user.setRole(newRole);
        audit.log(session, "USER_ROLE_CHANGED", "USER", user.getId(),
                user.getDisplayName() + ": " + oldRole.getLabel() + " -> " + newRole.getLabel());
        return user;
    }
}
