package com.sajo.operation_service.service;

// rules.yml의 alert: 필드와 정확히 일치해야 하는 alertname 리터럴을 한 곳에 모은다 -
// Alertmanager webhook이 이 문자열 그대로 보내오므로 완전히 없앨 수는 없지만,
// 여러 파일에 따로 적혀서 오타/드리프트가 생기는 걸 막기 위해 상수로만 관리한다.
public final class AlertNames {

    private AlertNames() {
    }

    public static final String HIGH_ERROR_RATE = "HighErrorRate";
    public static final String HIGH_LATENCY = "HighLatency";
    public static final String HIGH_CPU_USAGE = "HighCpuUsage";
    public static final String HIGH_MEMORY_USAGE = "HighMemoryUsage";
    public static final String HIGH_GC_OVERHEAD = "HighGcOverhead";
    public static final String HIKARI_POOL_PENDING = "HikariPoolPending";

    public static final String HIGH_NODE_CPU_USAGE = "HighNodeCpuUsage";
    public static final String HIGH_NODE_MEMORY_USAGE = "HighNodeMemoryUsage";
    public static final String NODE_DISK_LOW = "NodeDiskLow";
    public static final String NODE_DISK_WILL_FILL_IN_24H = "NodeDiskWillFillIn24h";

    public static final String REDIS_MEMORY_HIGH = "RedisMemoryHigh";
}
