package io.github.jaroslawdabrowski.groszdogrosza.platform.persistence;

import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.s;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ClassScopedReadsTest {

    @Test
    void anItemBelongsOnlyToTheClassItWasWrittenFor() {
        var item = Map.of("pk", s("STUDENT#1"), "classId", s("class-a"));
        assertTrue(ClassScopedReads.belongsTo(item, "class-a"));
        assertFalse(ClassScopedReads.belongsTo(item, "class-b"));
    }

    @Test
    void anItemWithoutAClassBelongsToNoClass() {
        // Written before the migration and not migrated yet: invisible, never "everyone's".
        assertFalse(ClassScopedReads.belongsTo(Map.of("pk", s("STUDENT#1")), "class-a"));
    }

    @Test
    void aMissingItemOrMissingClassNeverMatches() {
        assertFalse(ClassScopedReads.belongsTo(Map.of(), "class-a"));
        assertFalse(ClassScopedReads.belongsTo(null, "class-a"));
        assertFalse(ClassScopedReads.belongsTo(Map.of("classId", s("class-a")), null));
    }
}
