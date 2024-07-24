package org.fisheep.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@AllArgsConstructor
public class PageResult<T> extends Result{

    private final int totalRecords;

    private final int totalPages;


    public PageResult(List<T> results, int currentPage, int pageSize) {
        this.setResult(results.stream()
                .skip((long) (currentPage - 1) * pageSize)
                .limit(pageSize)
                .collect(Collectors.toList()));
        this.totalRecords = results.size();
        this.totalPages = (int) Math.ceil((double) totalRecords / pageSize);
    }
}
