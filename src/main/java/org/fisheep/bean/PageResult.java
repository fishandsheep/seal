package org.fisheep.bean;

import lombok.Data;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

import java.util.List;
import java.util.stream.Collectors;

@Data
public class PageResult<T> {

//    final EmbeddedStorageManager storageManager;

    private List<T> results;

    private int total;

//    public PageResult(EmbeddedStorageManager storageManager) {
//        this.storageManager = storageManager;
//    }


    public PageResult(List<T> results, int total) {
        this.results = results;
        this.total = total;
    }

    public void saveResult() {
        EmbeddedStorageManager storageManager = EmbeddedStorage.start();
        storageManager.setRoot(this);
        storageManager.storeRoot();
        storageManager.shutdown();
    }

    public PageResult<T> results(int currentPage, int pageSize) {
        EmbeddedStorageManager storageManager = EmbeddedStorage.start();
        final PageResult<T> result = (PageResult<T>) storageManager.root();
        storageManager.shutdown();
        final List<T> collect = result.getResults().stream()
                .skip((currentPage - 1) * pageSize)
                .limit(pageSize)
                .collect(Collectors.toList());
        result.setResults(collect);
        return result;
    }
}
