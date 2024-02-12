package com.luoying.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author 落樱的悔恨
 * 代码沙箱命令
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeSandBoxCmd {
    /**
     * 编译命令
     */
    private String compileCmd;

    /**
     * 运行命令
     */
    private String runCmd;
}
