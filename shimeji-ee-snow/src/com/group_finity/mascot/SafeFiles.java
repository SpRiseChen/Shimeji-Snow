package com.group_finity.mascot;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Fail closed on existing symlinks/junctions before application-owned writes. */
public final class SafeFiles {
    private SafeFiles() { }

    public static OutputStream output(Path path) throws IOException {
        check(path);
        return Files.newOutputStream(path, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
    }

    /** Only a concrete regular file may be removed; never a directory or link. */
    public static void deleteFile(Path path) throws IOException {
        check(path);
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Refusing to delete a non-regular file: " + path);
        }
        Files.deleteIfExists(path);
    }

    public static void check(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        for (Path current = absolute; current != null; current = current.getParent()) {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                    && (Files.isSymbolicLink(current) || !current.toRealPath().equals(current))) {
                throw new IOException("Refusing linked filesystem path: " + current);
            }
        }
    }
}
