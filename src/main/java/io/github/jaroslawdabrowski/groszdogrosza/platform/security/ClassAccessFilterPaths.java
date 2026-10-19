package io.github.jaroslawdabrowski.groszdogrosza.platform.security;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Which request paths {@link ClassAccessFilter} guards: {@code /api/classes/{classId}[/...]}. */
final class ClassAccessFilterPaths {

    private static final Pattern CLASS_PATH = Pattern.compile("^/?api/classes/([^/]+)(/.*)?$");

    private ClassAccessFilterPaths() {
    }

    /** The class id the path is about, or null when it isn't a class path. */
    static String classIdOf(String path) {
        Matcher matcher = CLASS_PATH.matcher(path == null ? "" : path);
        return matcher.matches() ? matcher.group(1) : null;
    }

    static boolean isClassPath(String path) {
        return classIdOf(path) != null;
    }
}
