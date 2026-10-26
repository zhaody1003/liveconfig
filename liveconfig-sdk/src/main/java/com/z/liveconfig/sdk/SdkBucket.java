package com.z.liveconfig.sdk;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 与服务端完全一致的分桶算法。
 *
 * 只在降级路径用到，但必须逐字节相同 —— 否则「服务端算出来 true、断网后本地算出 false」，
 * 灰度会在故障那一刻发生跳变，这是最不能接受的行为。
 */
final class SdkBucket {

    private SdkBucket() {
    }

    static int of(String flagKey, String subject) {
        try {
            byte[] md5 = MessageDigest.getInstance("MD5")
                    .digest((flagKey + ":" + subject).getBytes(StandardCharsets.UTF_8));
            int h = ((md5[0] & 0xFF) << 24)
                    | ((md5[1] & 0xFF) << 16)
                    | ((md5[2] & 0xFF) << 8)
                    | (md5[3] & 0xFF);
            return Math.floorMod(h, 100);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 不可用", e);
        }
    }
}
