package com.z.liveconfig.engine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** 一致性分桶：同一个 (flagKey, 主体) 永远落在同一个桶 */
public final class HashBucket {

    private HashBucket() {
    }

    /**
     * 掺 flagKey 是为了让不同开关的分桶相互独立：
     * 只按 userId 分桶的话，放量时永远是同一批人先命中，新功能之间还会互相污染。
     */
    /** 单独抽出：测试代码在 test classpath 上够不到 main 的包私有方法，所以放这里 */
    static int bucketOf(int h) {
        return Math.floorMod(h, 100);
    }

    public static int of(String flagKey, String subject) {
        try {
            byte[] md5 = MessageDigest.getInstance("MD5")
                    .digest((flagKey + ":" + subject).getBytes(StandardCharsets.UTF_8));
            int h = ((md5[0] & 0xFF) << 24)
                    | ((md5[1] & 0xFF) << 16)
                    | ((md5[2] & 0xFF) << 8)
                    | (md5[3] & 0xFF);
            return bucketOf(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 不可用", e);
        }
    }
}
