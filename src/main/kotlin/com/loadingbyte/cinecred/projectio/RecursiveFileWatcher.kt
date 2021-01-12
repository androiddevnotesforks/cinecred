package com.loadingbyte.cinecred.projectio

import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.*


abstract class RecursiveFileWatcher {

    private val watcher = FileSystems.getDefault().newWatchService()
    private val watchKeys = mutableMapOf<Path, WatchKey>()
    private val lock = Any()

    fun watch(watchedDir: Path) {
        synchronized(lock) {
            for (file in Files.walk(watchedDir))
                if (Files.isDirectory(file))
                    watchKeys[file] = file.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
        }
    }

    fun clear() {
        synchronized(lock) {
            for (watchKey in watchKeys.values)
                watchKey.cancel()
            watchKeys.clear()
        }
    }

    init {
        Thread({
            while (true) {
                val key = watcher.take()
                synchronized(lock) {
                    for (event in key.pollEvents())
                        if (event.kind() != OVERFLOW) {
                            val file = (key.watchable() as Path).resolve(event.context() as Path)

                            // If the file is a directory, we have to (de)register a watching instruction for it.
                            if (Files.isDirectory(file))
                                if (event.kind() == ENTRY_CREATE)
                                    watchKeys[file] = file.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
                                else if (event.kind() == ENTRY_DELETE)
                                    watchKeys.remove(file)?.cancel()

                            onEvent(file, event.kind())
                        }
                    key.reset()
                }
            }
        }, "DirectoryWatcherThread").apply { isDaemon = true }.start()
    }

    protected abstract fun onEvent(file: Path, kind: WatchEvent.Kind<*>)

}
