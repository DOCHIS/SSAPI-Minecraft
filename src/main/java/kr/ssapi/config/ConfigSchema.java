package kr.ssapi.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v2 설정 파일 스키마 명세 — ConfigValidator 가 사용.
 *
 * <p>각 SchemaRule 은 키 경로 + 타입 + 제약(범위 / enum)을 정의.
 * 파일별 (config.yml / triggers.yml / kits.yml / messages.yml) 로 분리.
 */
public final class ConfigSchema {

    public enum Type { STRING, INT, LONG, DOUBLE, BOOLEAN, LIST, MAP, OBJECT }

    public static class Rule {
        public final String path;
        public final Type type;
        public final boolean required;
        public final Number min;
        public final Number max;
        public final List<String> allowed;       // enum 제약
        public final String description;

        public Rule(String path, Type type, boolean required, Number min, Number max,
                    List<String> allowed, String description) {
            this.path = path;
            this.type = type;
            this.required = required;
            this.min = min;
            this.max = max;
            this.allowed = allowed == null ? Collections.emptyList() : allowed;
            this.description = description;
        }

        public static Rule req(String path, Type type, String desc) {
            return new Rule(path, type, true, null, null, null, desc);
        }
        public static Rule opt(String path, Type type, String desc) {
            return new Rule(path, type, false, null, null, null, desc);
        }
        public static Rule range(String path, Type type, Number min, Number max, String desc) {
            return new Rule(path, type, false, min, max, null, desc);
        }
        public static Rule oneOf(String path, Type type, List<String> allowed, String desc) {
            return new Rule(path, type, false, null, null, allowed, desc);
        }
    }

    private static final List<Rule> CONFIG_RULES = Arrays.asList(
        Rule.req("config-version", Type.INT, "설정 파일 버전"),
        Rule.req("api.key", Type.STRING, "SSAPI API 키"),
        Rule.opt("api.prefix", Type.STRING, "메시지 prefix"),
        Rule.opt("api.servers.socket", Type.STRING, "Socket.IO URL"),
        Rule.opt("api.servers.api", Type.STRING, "REST API URL"),
        Rule.range("api.socket.timeout_ms", Type.INT, 500, 60000, "소켓 연결 timeout (ms)"),
        Rule.opt("api.socket.reconnect", Type.BOOLEAN, "재연결 시도 여부"),
        Rule.range("api.socket.reconnect_delay_ms", Type.INT, 100, 60000, "재연결 시도 지연"),
        Rule.range("api.socket.reconnect_max_delay_ms", Type.INT, 100, 600000, "재연결 최대 지연"),
        Rule.opt("api.socket.reconnect_max_attempts", Type.INT, "재연결 최대 시도 (-1 = 무한)"),

        Rule.oneOf("storage.type", Type.STRING, Arrays.asList("yml", "mysql"), "저장 방식"),
        Rule.opt("storage.mysql.host", Type.STRING, "MySQL host"),
        Rule.range("storage.mysql.port", Type.INT, 1, 65535, "MySQL port"),
        Rule.opt("storage.mysql.database", Type.STRING, "DB 이름"),
        Rule.opt("storage.mysql.username", Type.STRING, "DB 사용자"),
        Rule.opt("storage.mysql.password", Type.STRING, "DB 비밀번호"),
        Rule.range("storage.mysql.pool.maximum_pool_size", Type.INT, 1, 100, "Pool 최대 크기"),
        Rule.range("storage.mysql.pool.minimum_idle", Type.INT, 0, 100, "Pool 최소 유휴"),
        Rule.range("storage.mysql.pool.idle_timeout_ms", Type.LONG, 1000L, 3_600_000L, "유휴 timeout"),
        Rule.range("storage.mysql.pool.max_lifetime_ms", Type.LONG, 1000L, 7_200_000L, "최대 수명"),
        Rule.range("storage.mysql.pool.connection_timeout_ms", Type.LONG, 1000L, 600_000L, "연결 timeout"),

        Rule.oneOf("mission.settle_payout", Type.STRING,
            Arrays.asList("combined", "individual"), "정산 처리 방식"),
        Rule.range("mission.payout_safety.max_donors_processed", Type.INT, 1, 5000,
            "individual 모드에서 처리할 최대 후원자 수"),
        Rule.range("mission.payout_safety.tick_spacing", Type.INT, 0, 200,
            "후원자 간 tick 간격"),

        Rule.opt("reward.execute_when_offline", Type.BOOLEAN, "오프라인 시에도 즉시 실행"),

        Rule.opt("actions.give_kit.drop_overflow", Type.BOOLEAN, "인벤토리 가득 시 바닥 드랍"),
        Rule.opt("actions.spawn_mob.enabled.passive", Type.BOOLEAN, "평화 몹 허용"),
        Rule.opt("actions.spawn_mob.enabled.neutral", Type.BOOLEAN, "중립 몹 허용"),
        Rule.opt("actions.spawn_mob.enabled.hostile", Type.BOOLEAN, "적대 몹 허용"),
        Rule.opt("actions.spawn_mob.enabled.boss", Type.BOOLEAN, "보스 몹 허용"),
        Rule.range("actions.spawn_mob.difficulty", Type.INT, 1, 5, "몹 난이도 1~5"),

        Rule.opt("actions.random_effect.positive_effects", Type.BOOLEAN, "이로운 효과 허용"),
        Rule.opt("actions.random_effect.negative_effects", Type.BOOLEAN, "해로운 효과 허용"),
        Rule.range("actions.random_effect.duration_ticks", Type.INT, 1, 72000, "효과 지속시간 ticks"),
        Rule.range("actions.random_effect.amplifier", Type.INT, 0, 4, "효과 증폭 0~4"),

        Rule.range("actions.random_teleport.range.x.distance_min", Type.INT, 1, 1_000_000, "X 최소 거리"),
        Rule.range("actions.random_teleport.range.x.distance_max", Type.INT, 1, 1_000_000, "X 최대 거리"),
        Rule.range("actions.random_teleport.range.x.world_min", Type.INT, -30_000_000, 30_000_000, "X 월드 최소"),
        Rule.range("actions.random_teleport.range.x.world_max", Type.INT, -30_000_000, 30_000_000, "X 월드 최대"),
        Rule.range("actions.random_teleport.range.z.distance_min", Type.INT, 1, 1_000_000, "Z 최소 거리"),
        Rule.range("actions.random_teleport.range.z.distance_max", Type.INT, 1, 1_000_000, "Z 최대 거리"),
        Rule.range("actions.random_teleport.range.z.world_min", Type.INT, -30_000_000, 30_000_000, "Z 월드 최소"),
        Rule.range("actions.random_teleport.range.z.world_max", Type.INT, -30_000_000, 30_000_000, "Z 월드 최대"),
        Rule.oneOf("actions.random_teleport.range.y.search_direction", Type.STRING,
            Arrays.asList("BOTTOM_TO_TOP", "TOP_TO_BOTTOM"), "Y 검색 방향"),
        Rule.range("actions.random_teleport.range.y.world_min", Type.INT, -64, 1024, "Y 월드 최소"),
        Rule.range("actions.random_teleport.range.y.world_max", Type.INT, -64, 1024, "Y 월드 최대"),
        Rule.opt("actions.random_teleport.safe_zone.allow_water", Type.BOOLEAN, "물 위 허용"),
        Rule.opt("actions.random_teleport.safe_zone.allow_lava", Type.BOOLEAN, "용암 위 허용"),
        Rule.opt("actions.random_teleport.safe_zone.allow_solid", Type.BOOLEAN, "고체 블록 안 허용"),
        Rule.range("actions.random_teleport.search.max_attempts", Type.INT, 1, 100, "최대 시도 횟수"),
        Rule.range("actions.random_teleport.search.retry_delay_ms", Type.INT, 0, 60000, "재시도 간격"),

        Rule.opt("actions.instant_death.protect_inventory", Type.BOOLEAN, "인벤토리 보호"),

        Rule.opt("sounds.donation", Type.BOOLEAN, "후원 사운드"),
        Rule.opt("sounds.mission", Type.BOOLEAN, "미션 사운드"),
        Rule.opt("sounds.give_kit", Type.BOOLEAN, "킷 사운드"),
        Rule.opt("sounds.spawn_mob", Type.BOOLEAN, "몹 사운드"),
        Rule.opt("sounds.random_effect", Type.BOOLEAN, "효과 사운드"),
        Rule.opt("sounds.random_teleport", Type.BOOLEAN, "텔포 사운드"),
        Rule.opt("sounds.instant_death", Type.BOOLEAN, "즉사 사운드"),

        Rule.opt("logging.enabled", Type.BOOLEAN, "로그 활성화"),
        Rule.opt("logging.save.donation", Type.BOOLEAN, "후원 로그 저장"),
        Rule.opt("logging.save.mission", Type.BOOLEAN, "미션 로그 저장"),
        Rule.opt("logging.save.failure", Type.BOOLEAN, "실패 로그 저장"),
        Rule.opt("logging.debug", Type.BOOLEAN, "디버그 모드"),

        Rule.opt("metrics.enabled", Type.BOOLEAN, "bStats 통계"),
        Rule.opt("update_check.enabled", Type.BOOLEAN, "업데이트 알림")
    );

    private static final List<String> ALLOWED_OPS = Arrays.asList(
        "eq", "gte", "lte", "range", "any"
    );

    private static final List<String> ALLOWED_ACTION_TYPES = Arrays.asList(
        "command", "give_kit", "spawn_mob", "random_effect",
        "random_teleport", "instant_death"
    );

    // config.yml 에 적용되는 불변 룰 목록 반환
    public static List<Rule> configRules() { return Collections.unmodifiableList(CONFIG_RULES); }
    public static List<String> allowedOps() { return Collections.unmodifiableList(ALLOWED_OPS); }
    public static List<String> allowedActionTypes() { return Collections.unmodifiableList(ALLOWED_ACTION_TYPES); }

    /** 지원되는 hook_timings */
    public static final List<String> ALLOWED_HOOK_TIMINGS =
        Collections.unmodifiableList(Arrays.asList("receive", "settle", "result"));

    /** 지원되는 mission scope (config.yml 의 mission.* 키) */
    public static final List<String> MISSION_SCOPES =
        Collections.unmodifiableList(Arrays.asList("on_receive", "on_settle", "on_result"));

    /** 지원되는 trigger scope (triggers.yml 의 최상위 키) */
    public static final List<String> TRIGGER_SCOPES =
        Collections.unmodifiableList(Arrays.asList("connect", "donation"));

    private ConfigSchema() {}

    // 빠른 조회를 위해 path → Rule 로 인덱싱한 맵 반환
    /** 키 path → Rule 인덱스 */
    public static Map<String, Rule> indexedRules() {
        Map<String, Rule> map = new LinkedHashMap<>();
        for (Rule r : CONFIG_RULES) map.put(r.path, r);
        return map;
    }
}
