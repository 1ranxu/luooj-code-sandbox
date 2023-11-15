package com.luoying.security;

import java.security.Permission;

/**
 * 默认安全管理器
 */
public class DefaultSecurityManager extends SecurityManager {
    /**
     * 检查所有的权限
     *
     * @param perm
     */
    @Override
    public void checkPermission(Permission perm) {
        System.out.println("默认不做任何权限限制");
        // super.checkPermission(perm);
    }
}
