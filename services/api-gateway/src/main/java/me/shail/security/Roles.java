package me.shail.security;

/** The realm roles from Keycloak. */
public final class Roles {

    /** Sees everything */
    public static final String ADMIN = "admin";
    /** Sees scheduling tasks: patients who need appointments booked directly */
    public static final String SCHEDULER = "scheduler";
    /** Sees scheduling and referral tasks, and reviews whether referrals are appropriate */
    public static final String CLINICAL_TEAM = "clinical-team";

    private Roles() {
    }
}
