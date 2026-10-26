package com.z.liveconfig.security;

import com.z.liveconfig.entity.PlatformUser;

/** 登录用户上下文，由 AuthInterceptor 写入、afterCompletion 清理 */
public final class CurrentUser {

    private static final ThreadLocal<PlatformUser> HOLDER = new ThreadLocal<>();

    private CurrentUser() {
    }

    public static void set(PlatformUser user) {
        HOLDER.set(user);
    }

    public static PlatformUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
