package krispasi.omGames.hallsofcarnage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

final class HallsResourceManager {
    private static final String PREFIX = "hallsOfCarnage/";
    private static final String CONFIG = "halls-of-carnage.yml";
    private static final Set<String> EXCLUDED = Set.of("level-maker.jar", "run.bat", "run.vbs");
    // Only game content is disposable; never infer reset targets from server directories.
    private static final java.util.List<String> RESET_FOLDERS = java.util.List.of(
            "scenarios", "level", "level_type", "modifiers", "breakables", "breakable_loot_pools",
            "vegetation", "traps", "bosses", "monsters", "items", "buildings");

    private HallsResourceManager() {
    }

    static void copyMissing(File folder) throws IOException {
        copyMissing(folder.toPath().toAbsolutePath().normalize(), readBundle());
    }

    static void reset(File folder) throws IOException {
        // Read every source before deleting anything, including in exploded development builds.
        Map<String, byte[]> bundle = readBundle();
        Path root = folder.toPath().toAbsolutePath().normalize();
        for (String name : bundle.keySet()) {
            safeTarget(root, name);
        }
        for (String name : RESET_FOLDERS) {
            safeTarget(root, name);
        }
        for (String name : RESET_FOLDERS) {
            Path target = safeTarget(root, name);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                try (var paths = Files.walk(target)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                        Files.delete(path);
                    }
                }
            }
        }
        copyMissing(root, bundle);
    }

    private static Map<String, byte[]> readBundle() throws IOException {
        Map<String, byte[]> bundle = new LinkedHashMap<>();
        Path source;
        try {
            var codeSource = HallsResourceManager.class.getProtectionDomain().getCodeSource();
            if (codeSource == null) {
                throw new IOException("Halls plugin code source is unavailable.");
            }
            source = Path.of(codeSource.getLocation().toURI());
        } catch (URISyntaxException | RuntimeException ex) {
            throw new IOException("Cannot resolve Halls plugin code source.", ex);
        }
        if (Files.isDirectory(source)) {
            Path resources = source.resolve("hallsOfCarnage");
            if (!Files.isDirectory(resources, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Bundled Halls resource directory is missing: " + resources);
            }
            try (var paths = Files.walk(resources)) {
                for (Path path : paths.sorted().toList()) {
                    if (Files.isSymbolicLink(path)) {
                        throw new IOException("Symbolic link in bundled Halls resources: " + path);
                    }
                    if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                        String name = resources.relativize(path).toString().replace(File.separatorChar, '/');
                        validateName(name);
                        if (!excluded(name)) {
                            bundle.put(name, Files.readAllBytes(path));
                        }
                    }
                }
            }
            Path config = source.resolve(CONFIG);
            if (Files.isSymbolicLink(config)) {
                throw new IOException("Symbolic link in bundled Halls config: " + config);
            }
            bundle.put(CONFIG, Files.readAllBytes(config));
        } else {
            try (JarFile jar = new JarFile(source.toFile())) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    if (entry.isDirectory() || !entry.getName().startsWith(PREFIX)) {
                        continue;
                    }
                    String name = entry.getName().substring(PREFIX.length());
                    validateName(name);
                    if (!excluded(name)) {
                        try (InputStream input = jar.getInputStream(entry)) {
                            if (bundle.putIfAbsent(name, input.readAllBytes()) != null) {
                                throw new IOException("Duplicate bundled Halls resource: " + name);
                            }
                        }
                    }
                }
                var config = jar.getJarEntry(CONFIG);
                if (config == null) {
                    throw new IOException("Bundled Halls config is missing.");
                }
                try (InputStream input = jar.getInputStream(config)) {
                    bundle.put(CONFIG, input.readAllBytes());
                }
            }
        }
        if (bundle.size() <= 1) {
            throw new IOException("No bundled Halls game resources found.");
        }
        return bundle;
    }

    private static boolean excluded(String name) {
        return EXCLUDED.contains(name.substring(name.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT));
    }

    private static void validateName(String name) throws IOException {
        if (name.isEmpty() || name.contains("\\") || name.contains(":")) {
            throw new IOException("Unsafe bundled Halls resource path: " + name);
        }
        for (String part : name.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IOException("Unsafe bundled Halls resource path: " + name);
            }
        }
    }

    private static Path safeTarget(Path root, String name) throws IOException {
        validateName(name);
        Path target = root.resolve(name).normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IOException("Halls resource path escapes the data folder: " + name);
        }
        for (Path path = target; path != null; path = path.getParent()) {
            if (Files.isSymbolicLink(path)) {
                throw new IOException("Symbolic link in Halls resource destination: " + path);
            }
        }
        return target;
    }

    private static void copyMissing(Path root, Map<String, byte[]> bundle) throws IOException {
        for (var resource : bundle.entrySet()) {
            Path target = safeTarget(root, resource.getKey());
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            Files.createDirectories(target.getParent());
            try (InputStream input = new ByteArrayInputStream(resource.getValue())) {
                Files.copy(input, target);
            }
        }
    }
}
