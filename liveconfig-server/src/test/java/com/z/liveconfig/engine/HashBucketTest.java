package com.z.liveconfig.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分桶算法的黄金向量：把 (flagKey, userId) → 桶号 钉死。
 *
 * 这些期望值不是算出来的，是「锁住」的——一旦有人改了哈希拼法（比如去掉 flagKey 前缀、
 * 换 % 取模方式），这几条用例必红，逼他先回答"已放量用户的命中结果会不会跳变"。
 */
class HashBucketTest {

    @Test
    void 同一主体重复计算结果稳定() {
        int first = HashBucket.of("new_checkout", "2088");
        for (int i = 0; i < 1000; i++) {
            assertThat(HashBucket.of("new_checkout", "2088")).isEqualTo(first);
        }
    }

    @Test
    void 桶号一定落在_0_到_99() {
        for (int i = 0; i < 2000; i++) {
            int bucket = HashBucket.of("new_checkout", "user-" + i);
            assertThat(bucket).isBetween(0, 99);
        }
    }

    @Test
    void 不同开关的同一用户分桶相互独立() {
        // 单比一对 flagKey 会因 1/100 的偶然同桶而假失败，这里统计 500 个用户的分桶分布
        int same = 0;
        for (int i = 0; i < 500; i++) {
            String user = "u-" + i;
            if (HashBucket.of("new_checkout", user) == HashBucket.of("dark_mode", user)) {
                same++;
            }
        }
        // 真正独立时约 5 个相同；若拼法退化成只按 userId 分桶，这里会是 500
        assertThat(same).isBetween(0, 50);
    }

    @Test
    void 黄金向量锁死算法() {
        assertThat(HashBucket.of("new_checkout", "1001")).isEqualTo(GOLD_1001);
        assertThat(HashBucket.of("new_checkout", "2088")).isEqualTo(GOLD_2088);
        assertThat(HashBucket.of("new_checkout", "test_admin")).isEqualTo(GOLD_ADMIN);
        assertThat(HashBucket.of("new_checkout", "9527")).isEqualTo(GOLD_9527);
        assertThat(HashBucket.of("new_checkout", "u-77")).isEqualTo(GOLD_U77);
    }

    // 期望值由当前实现固定；改算法必须同步改这里，且要能解释放量用户为何会跳变
    private static final int GOLD_1001 = 90;
    private static final int GOLD_2088 = 39;
    private static final int GOLD_ADMIN = 15;
    private static final int GOLD_9527 = 12;
    private static final int GOLD_U77 = 79;
}
