package io.github.jaroslawdabrowski.groszdogrosza.schoolclass.port.in;

import java.util.List;

public interface ListTreasurerClassesUseCase {

    List<String> classIdsWhereTreasurer(String email);
}
