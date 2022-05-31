package com.boydti.discord.util.task;
import com.boydti.discord.util.scheduler.ThrowingConsumer;
import com.boydti.discord.util.AlertUtil;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

import static java.nio.file.StandardWatchEventKinds.*;

public abstract class FileListenerTask implements Runnable, ThrowingConsumer<Path> {
    private final Path path;
    private final WatchKey key;
    private final WatchService watcher;

    public FileListenerTask(Path path) throws IOException {
        this.path = path;
        this.watcher = FileSystems.getDefault().newWatchService();
        this.key = path.register(watcher,
                ENTRY_CREATE,
                ENTRY_DELETE,
                ENTRY_MODIFY);
    }

    public Path getPath() {
        return path;
    }

    @Override
    public final void run() {
        for (;;) {
            WatchKey currentKey;
            try {
                currentKey = watcher.take();
            } catch (InterruptedException x) {
                x.printStackTrace();
                return;
            }
            for (WatchEvent<?> event : currentKey.pollEvents()) {
                try {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == OVERFLOW) {
                        continue;
                    }

                    // The filename is the
                    // context of the event.
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();
                    accept(filename);

                } catch (Throwable e) {
                    AlertUtil.error("File listener task 2", e);
                    try {
                        Thread.sleep(60000);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
            boolean valid = currentKey.reset();
            if (!valid) {
                break;
            }
        }
    }
}
