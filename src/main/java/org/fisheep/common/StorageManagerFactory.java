package org.fisheep.common;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

public class StorageManagerFactory {

    private static EmbeddedStorageManager storageManager;

    public static EmbeddedStorageManager getInstance() {
        if (storageManager == null) {
            storageManager = EmbeddedStorage.start();
        }
        return storageManager;
    }

    public static void shutdown() {
        if (storageManager != null) {
            storageManager.shutdown();
            storageManager = null;
        }
    }

}
