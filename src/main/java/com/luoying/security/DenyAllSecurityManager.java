package com.luoying.security;

import java.security.Permission;

/**
 * 禁用所有权限的安全管理器
 */
public class DenyAllSecurityManager extends SecurityManager {
    /**
     * 禁用所有的权限
     *
     * @param perm
     */
    @Override
    public void checkPermission(Permission perm) {
        throw new SecurityException("权限不足，"+perm.toString());
    }
}
