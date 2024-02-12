package com.luoying.model;

import lombok.Data;

/**
 * @author 落樱的悔恨
 * 执行信息
 */
@Data
public class ExecuteMessage {
    /**
     * 执行代码的状态 0-正常 其他-错误
     */
    private Integer exitValue;

    /**
     * 正常输出
     */
    private String message;

    /**
     * 错误输出
     */
    private String errorMessage;

    /**
     * 消耗时间 ms
     */
    private Long time;

    /**
     * 消耗内存 KB
     */
    private Double memory;
}
