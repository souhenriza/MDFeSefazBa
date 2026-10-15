package com.souhenriza.sefazba.infrastructure.lock;

import com.souhenriza.sefazba.domain.exceptions.ExtractionInProgressException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Lets only one process drive the browser at a time. Each command is its own JVM now, so the in-memory
 * batchRunning flag the server used can no longer see a batch started by another invocation (e.g. a scheduled
 * task). The OS releases the lock when the process dies, so a crash never leaves it stuck.
 */
public class ExtractionLock implements AutoCloseable {

    private final FileChannel channel;
    private final FileLock lock;

    private ExtractionLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    public static ExtractionLock acquire(Path file) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                throw new ExtractionInProgressException();
            }
            return new ExtractionLock(channel, lock);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not open lock file " + file, e);
        }
    }

    @Override
    public void close() {
        try {
            lock.release();
            channel.close();
        } catch (IOException ignored) {
            // The lock goes away with the process anyway
        }
    }
}
