package io.github.jaroslawdabrowski.groszdogrosza.platform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Fails the build if an endpoint could expose class data without going through
 * {@link ClassAccessFilter}. Every endpoint under {@code /api} must either sit under
 * {@code /api/classes/{classId}/...} (where the filter checks membership before anything runs)
 * or be on the short, explicit list of deliberately global endpoints below. Adding a new
 * global endpoint means adding it here on purpose - and asking whether it can leak data
 * from one class to another.
 */
class ClassEndpointsGuardTest {

    private static final List<String> GLOBAL_ENDPOINTS = List.of(
            "/api/auth-config",          // public: tells the frontend where to log in
            "/api/me",                   // the caller's own identity and classes only
            "/api/client-log",           // writes a line to the server log, reads nothing
            "/api/public/overview",      // public: the default class's totals (no per-student data)
            "/api/public/classes/{token}/overview"); // public: a class's totals by its secret link

    @Test
    void everyApiEndpointIsClassScopedOrExplicitlyGlobal() throws Exception {
        List<String> endpoints = allEndpointPaths();
        assertFalse(endpoints.isEmpty(), "found no endpoints at all - the scan is broken");
        List<String> offending = endpoints.stream()
                .filter(path -> path.startsWith("/api"))
                .filter(path -> !path.equals("/api/classes/{classId}") && !path.startsWith("/api/classes/{classId}/"))
                .filter(path -> !GLOBAL_ENDPOINTS.contains(path))
                .toList();
        assertEquals(List.of(), offending,
                "These endpoints are neither under /api/classes/{classId} nor on the explicit global list");
    }

    @Test
    void theClassFilterMatchesExactlyTheClassScopedPaths() {
        assertTrue(ClassAccessFilterPaths.isClassPath("/api/classes/abc/students"));
        assertTrue(ClassAccessFilterPaths.isClassPath("api/classes/abc"));
        assertFalse(ClassAccessFilterPaths.isClassPath("/api/classesX/abc"));
        assertFalse(ClassAccessFilterPaths.isClassPath("/api/public/classes/abc/overview"));
    }

    private static List<String> allEndpointPaths() throws IOException, URISyntaxException, ClassNotFoundException {
        java.nio.file.Path root = Paths.get(ClassAccessFilter.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<String> classNames;
        try (Stream<java.nio.file.Path> files = Files.walk(root)) {
            classNames = files.filter(file -> file.toString().endsWith(".class") && !file.toString().contains("$"))
                    .map(file -> root.relativize(file).toString().replace('/', '.').replaceAll("\\.class$", ""))
                    .toList();
        }
        List<String> paths = new ArrayList<>();
        for (String name : classNames) {
            Class<?> type = Class.forName(name, false, ClassEndpointsGuardTest.class.getClassLoader());
            Path classPath = type.getAnnotation(Path.class);
            if (classPath == null) {
                continue;
            }
            for (Method method : type.getDeclaredMethods()) {
                if (!isEndpoint(method)) {
                    continue;
                }
                Path methodPath = method.getAnnotation(Path.class);
                paths.add(join(classPath.value(), methodPath == null ? "" : methodPath.value()));
            }
        }
        return paths;
    }

    private static boolean isEndpoint(Method method) {
        return method.isAnnotationPresent(GET.class) || method.isAnnotationPresent(POST.class)
                || method.isAnnotationPresent(PUT.class) || method.isAnnotationPresent(DELETE.class);
    }

    private static String join(String a, String b) {
        String joined = ("/" + a + "/" + b).replaceAll("/+", "/");
        return joined.length() > 1 && joined.endsWith("/") ? joined.substring(0, joined.length() - 1) : joined;
    }
}
