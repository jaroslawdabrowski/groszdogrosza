package io.github.jaroslawdabrowski.groszdogrosza.parent.port.in;

/** Treasurer-only: create a login account for an existing {@code Parent} record. */
public interface CreateCognitoAccountUseCase {

    void createCognitoAccount(String parentId);
}
