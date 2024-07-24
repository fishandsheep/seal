package org.fisheep.common;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.fisheep.bean.Data;

import java.util.List;
import java.util.stream.Collectors;

public class PageResult<T> extends Result{

    private int totalRecords;

    private int totalPages;


    public PageResult(List<T> results, int currentPage, int pageSize) {
        super.setResult(results.stream()
                .skip((long) (currentPage - 1) * pageSize)
                .limit(pageSize)
                .collect(Collectors.toList()));
        this.totalRecords = results.size();
        this.totalPages = (int) Math.ceil((double) totalRecords / pageSize);
    }

//    public void saveResult() {
//        EmbeddedStorageManager storageManager = StorageManagerFactory.getInstance();
//        storageManager.setRoot(new Data());
//        storageManager.setRoot(this);
//        storageManager.storeRoot();
//        storageManager.shutdown();
//    }

//    public PageResult<T> results(int currentPage, int pageSize) {
//        EmbeddedStorageManager storageManager = StorageManagerFactory.getInstance();
//        final PageResult<T> result = (PageResult<T>) storageManager.root();
//        storageManager.shutdown();
//        final List<T> collect = result.getResults().stream()
//                .skip((long) (currentPage - 1) * pageSize)
//                .limit(pageSize)
//                .collect(Collectors.toList());
//        result.setResults(collect);
//        return result;
//    }
}
