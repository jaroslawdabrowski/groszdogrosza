package io.github.jaroslawdabrowski.groszdogrosza.schoolclass.domain;

/** What a logged-in user may do within ONE class - roles never carry over between classes. */
public enum ClassRole {
    /** Manages the class: students, parents, collections, payments, settlement. */
    TREASURER,
    /** A guardian of at least one student in the class: sees class totals and only their own
     *  children's details. */
    PARENT
}
