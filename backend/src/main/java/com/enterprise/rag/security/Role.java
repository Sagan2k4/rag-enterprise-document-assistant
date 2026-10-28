package com.enterprise.rag.security;

/**
 * Organisational roles. A document lists the roles allowed to read it;
 * ADMIN can read everything and manage the corpus.
 */
public enum Role {
    ADMIN,
    HR,
    FINANCE,
    ENGINEERING,
    EMPLOYEE
}
