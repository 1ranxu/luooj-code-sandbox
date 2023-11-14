package com.luoying.model;

import lombok.Data;

@Data
public class QuestionSubmitJudgeInfo {
    /**
     * 程序执行信息
     */
    private String message;

    /**
     * 内存消耗 KB
     */
    private Long memory;

    /**
     * 时间消耗 KB
     */
    private Long time;
}
