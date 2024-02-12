package com.luoying.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author 落樱的悔恨
 * 执行代码响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteCodeResponse {
    /**
     * 输出结果
     */
    private List<String> outputList;

    /**
     * 执行信息
     */
    private String message;

    /**
     * 判题状态
     */
    private Integer status;

    /**
     * 判题信息
     */
    private QuestionSubmitJudgeInfo judgeInfo;
}
