package com.luoying.security;

import java.security.Permission;

/**
 * 我的安全管理器
 */
public class MySecurityManager extends SecurityManager {
    // 默认放开所有的权限
    @Override
    public void checkPermission(Permission perm) {
    }

    // 检查程序是否允许执行文件
    @Override
    public void checkExec(String cmd) {
        throw new SecurityException("checkExec 权限不足，" + cmd);
    }

    // 检查程序是否允许读文件
    @Override
    public void checkRead(String file) {
        System.out.println(file);
        if (file.contains("hutool")) {
            return;
        }
        throw new SecurityException("checkRead 权限不足，" + file);
    }

    // 检查程序是否允许写文件
    @Override
    public void checkWrite(String file) {
        System.out.println(file);
        throw new SecurityException("checkWrite 权限不足，" + file);
    }

    // 检查程序是否允许删除文件
    @Override
    public void checkDelete(String file) {
        System.out.println(file);
        throw new SecurityException("checkDelete 权限不足，" + file);
    }

    // // 检查程序是否允许连接网络
    @Override
    public void checkConnect(String host, int port) {
        throw new SecurityException("checkConnect 权限不足，" + host + ":" + port);
    }
}
