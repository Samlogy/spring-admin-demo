package com.example.admin.security;

/** Admin Server roles (without the {@code ROLE_} prefix). */
public final class Roles {

    /** Read-only access to the UI and to GET Actuator calls. */
    public static final String VIEWER = "VIEWER";
    /** Everything VIEWER can do, plus every write/sensitive action (loggers, heapdump, env, caches...). */
    public static final String ADMIN = "ADMIN";
    /** Technical account of the monolith: may only register/deregister, never open the UI. */
    public static final String CLIENT = "CLIENT";

    private Roles() {
    }
}
