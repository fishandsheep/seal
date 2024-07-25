package org.fisheep.common;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.fisheep.bean.Data;

public class StorageManagerFactory {

    private static EmbeddedStorageManager storageManager;

    public static EmbeddedStorageManager getInstance() {
        if (storageManager == null) {
            storageManager = EmbeddedStorage.start();
        }
        if (storageManager.root() == null) {
            createStorage();
        }
        return storageManager;
    }

    public static void createStorage() {
        storageManager.setRoot(new Data());
        storageManager.storeRoot();
    }


    public static void shutdown() {
        if (storageManager != null) {
            storageManager.shutdown();
            storageManager = null;
        }
    }

}
