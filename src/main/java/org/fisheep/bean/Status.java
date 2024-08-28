package org.fisheep.bean;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Status {

    /**
     * 状态 0-进行中，1-完成，2-出错
     */
    private int status;

    /**
     * 耗时/s
     */
    private long processTime;
}
