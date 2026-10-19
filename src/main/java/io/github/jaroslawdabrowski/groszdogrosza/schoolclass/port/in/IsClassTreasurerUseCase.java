package io.github.jaroslawdabrowski.groszdogrosza.schoolclass.port.in;


public interface IsClassTreasurerUseCase {

    boolean isTreasurer(String classId, String email);
}
