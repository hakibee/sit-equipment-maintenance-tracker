package com.sit.tracker.auth;

import com.sit.tracker.model.Permission;
import com.sit.tracker.model.Role;
import com.sit.tracker.model.User;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The RBAC policy: one static matrix mapping each {@link Role} to the set of
 * {@link Permission}s it holds. Services call {@link #check(Session,
 * Permission)} before every protected operation; the UI calls {@link
 * #can(Session, Permission)} to hide menu entries a role cannot use.
 *
 * <p>Matrix summary:</p>
 * <pre>
 * General User          : view assets, report faults, view own tickets
 * Technician            : + update asset status, work tickets (view all,
 *                         assign, advance status, notes), record maintenance,
 *                         view maintenance schedule and summary reports
 * Facility Administrator : everything (user management, asset registration,
 *                         retirement, maintenance planning, audit log, reports)
 * </pre>
 */
public final class AccessControl {

    private static final Map<Role, Set<Permission>> MATRIX = buildMatrix();

    private AccessControl() {
        // static policy holder, not instantiable
    }

    private static Map<Role, Set<Permission>> buildMatrix() {
        Map<Role, Set<Permission>> m = new EnumMap<>(Role.class);

        m.put(Role.GENERAL_USER, EnumSet.of(
                Permission.VIEW_ASSETS,
                Permission.REPORT_FAULT,
                Permission.VIEW_OWN_TICKETS));

        m.put(Role.TECHNICIAN, EnumSet.of(
                Permission.VIEW_ASSETS,
                Permission.UPDATE_ASSET_STATUS,
                Permission.REPORT_FAULT,
                Permission.VIEW_OWN_TICKETS,
                Permission.VIEW_ALL_TICKETS,
                Permission.ASSIGN_TICKETS,
                Permission.UPDATE_TICKET_STATUS,
                Permission.ADD_TICKET_NOTE,
                Permission.VIEW_MAINTENANCE_SCHEDULE,
                Permission.RECORD_MAINTENANCE,
                Permission.VIEW_REPORTS));

        // Administrators hold every permission.
        m.put(Role.FACILITY_ADMIN, EnumSet.allOf(Permission.class));

        return Collections.unmodifiableMap(m);
    }

    /** @return the (read-only) permission set granted to the given role. */
    public static Set<Permission> permissionsOf(Role role) {
        return MATRIX.getOrDefault(role, Collections.emptySet());
    }

    /** @return true when the session's user holds the given permission. */
    public static boolean can(Session session, Permission permission) {
        User user = session == null ? null : session.currentUser();
        return user != null && permissionsOf(user.getRole()).contains(permission);
    }

    /**
     * Guard method used by the service layer.
     *
     * @throws AccessDeniedException when nobody is logged in or the user's
     *         role does not include {@code permission}.
     */
    public static void check(Session session, Permission permission) throws AccessDeniedException {
        if (!can(session, permission)) {
            String who = session == null || session.currentUser() == null
                    ? "not logged in" : "role " + session.currentUser().getRole().getLabel();
            throw new AccessDeniedException(permission, String.format(
                    "Access denied: '%s' requires more privileges (%s).",
                    permission, who));
        }
    }
}
