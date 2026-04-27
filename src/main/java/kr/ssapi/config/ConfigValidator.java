package kr.ssapi.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 설정 파일 스키마 검증기.
 *
 * <p>{@link ConfigSchema} 의 룰 목록을 순회하며 모든 위반사항을 한 번에 수집.
 * 첫 에러에서 멈추지 않음 — 사용자가 한 번에 다 보고 수정할 수 있도록.
 *
 * <p>출력 포맷: "config.yml line ?, key=actions.spawn_mob.difficulty: 99 (허용 범위 1-5)"
 * Bukkit FileConfiguration 은 라인번호 추적 안 하므로 path 만 표시.
 */
public class ConfigValidator {

    public static class Issue {
        public final String file;
        public final String path;
        public final String message;
        public final Object actualValue;

        public Issue(String file, String path, Object actualValue, String message) {
            this.file = file;
            this.path = path;
            this.actualValue = actualValue;
            this.message = message;
        }

        @Override
        public String toString() {
            return String.format("✗ %s key=%s: %s%s", file, path,
                actualValue == null ? "<missing>" : String.valueOf(actualValue),
                message == null ? "" : " (" + message + ")");
        }
    }

    public static class Report {
        public final List<Issue> issues = new ArrayList<>();
        public boolean ok() { return issues.isEmpty(); }
        public void add(Issue i) { issues.add(i); }
        public void merge(Report other) { issues.addAll(other.issues); }
    }

    /** config.yml 검증 */
    public static Report validateConfig(FileConfiguration cfg) {
        Report r = new Report();
        for (ConfigSchema.Rule rule : ConfigSchema.configRules()) {
            validateRule(cfg, rule, "config.yml", r);
        }
        return r;
    }

    // triggers.yml 의 각 scope(connect/donation) 별 트리거 항목 검증
    /** triggers.yml 검증 */
    public static Report validateTriggers(FileConfiguration cfg) {
        Report r = new Report();
        for (String scope : ConfigSchema.TRIGGER_SCOPES) {
            if (!cfg.contains(scope)) continue;
            List<Map<?, ?>> list = cfg.getMapList(scope);
            for (int i = 0; i < list.size(); i++) {
                validateTriggerEntry(list.get(i), "triggers.yml", scope + "[" + i + "]", r);
            }
        }
        return r;
    }

    /** config.yml 의 mission.on_* 트리거 검증 (triggers.yml 와 동일 형식) */
    public static Report validateMissionTriggers(FileConfiguration cfg) {
        Report r = new Report();
        for (String scope : ConfigSchema.MISSION_SCOPES) {
            String path = "mission." + scope;
            if (!cfg.contains(path)) continue;
            List<Map<?, ?>> list = cfg.getMapList(path);
            for (int i = 0; i < list.size(); i++) {
                validateTriggerEntry(list.get(i), "config.yml", path + "[" + i + "]", r);
            }
        }
        return r;
    }

    // 트리거 항목 하나를 검증 (id/match.op/actions[] 필수 필드 확인)
    private static void validateTriggerEntry(Map<?, ?> entry, String file, String path, Report r) {
        if (!entry.containsKey("id")) {
            r.add(new Issue(file, path + ".id", null, "id 필수"));
        }
        Object matchObj = entry.get("match");
        if (!(matchObj instanceof Map)) {
            r.add(new Issue(file, path + ".match", matchObj, "match 객체 필수 (예: { op: any })"));
        } else {
            Map<?, ?> match = (Map<?, ?>) matchObj;
            Object op = match.get("op");
            if (op == null) {
                r.add(new Issue(file, path + ".match.op", null, "op 필수"));
            } else if (!ConfigSchema.allowedOps().contains(String.valueOf(op))) {
                r.add(new Issue(file, path + ".match.op", op,
                    "허용: " + String.join(", ", ConfigSchema.allowedOps())));
            }
        }

        Object actions = entry.get("actions");
        if (!(actions instanceof List)) {
            r.add(new Issue(file, path + ".actions", actions, "actions 배열 필수"));
        } else {
            List<?> arr = (List<?>) actions;
            for (int i = 0; i < arr.size(); i++) {
                Object item = arr.get(i);
                if (!(item instanceof Map)) {
                    r.add(new Issue(file, path + ".actions[" + i + "]", item, "객체 필수"));
                    continue;
                }
                Object type = ((Map<?, ?>) item).get("type");
                if (type == null) {
                    r.add(new Issue(file, path + ".actions[" + i + "].type", null, "type 필수"));
                } else if (!ConfigSchema.allowedActionTypes().contains(String.valueOf(type))) {
                    r.add(new Issue(file, path + ".actions[" + i + "].type", type,
                        "허용: " + String.join(", ", ConfigSchema.allowedActionTypes())));
                }
            }
        }
    }

    // 개별 Rule 을 cfg 에 적용해 타입/범위/enum 위반 여부를 Report 에 추가
    private static void validateRule(FileConfiguration cfg, ConfigSchema.Rule rule, String file, Report r) {
        if (!cfg.contains(rule.path)) {
            if (rule.required) {
                r.add(new Issue(file, rule.path, null, "필수 키 누락"));
            }
            return;
        }

        Object val = cfg.get(rule.path);
        switch (rule.type) {
            case STRING:
                if (!(val instanceof String)) {
                    r.add(new Issue(file, rule.path, val, "문자열 필요"));
                    return;
                }
                if (!rule.allowed.isEmpty() && !rule.allowed.contains(val)) {
                    r.add(new Issue(file, rule.path, val,
                        "허용: " + String.join(", ", rule.allowed)));
                }
                break;
            case INT:
                if (!(val instanceof Number)) {
                    r.add(new Issue(file, rule.path, val, "숫자 필요"));
                    return;
                }
                long iv = ((Number) val).longValue();
                if (rule.min != null && iv < rule.min.longValue()) {
                    r.add(new Issue(file, rule.path, val,
                        "허용 범위 " + rule.min + "-" + rule.max));
                } else if (rule.max != null && iv > rule.max.longValue()) {
                    r.add(new Issue(file, rule.path, val,
                        "허용 범위 " + rule.min + "-" + rule.max));
                }
                break;
            case LONG:
                if (!(val instanceof Number)) {
                    r.add(new Issue(file, rule.path, val, "숫자 필요"));
                    return;
                }
                long lv = ((Number) val).longValue();
                if (rule.min != null && lv < rule.min.longValue()) {
                    r.add(new Issue(file, rule.path, val,
                        "허용 범위 " + rule.min + "-" + rule.max));
                } else if (rule.max != null && lv > rule.max.longValue()) {
                    r.add(new Issue(file, rule.path, val,
                        "허용 범위 " + rule.min + "-" + rule.max));
                }
                break;
            case DOUBLE:
                if (!(val instanceof Number)) {
                    r.add(new Issue(file, rule.path, val, "숫자 필요"));
                    return;
                }
                break;
            case BOOLEAN:
                if (!(val instanceof Boolean)) {
                    r.add(new Issue(file, rule.path, val, "true/false 필요"));
                }
                break;
            case LIST:
                if (!(val instanceof List)) {
                    r.add(new Issue(file, rule.path, val, "리스트 필요"));
                }
                break;
            case MAP:
            case OBJECT:
                if (!(val instanceof ConfigurationSection || val instanceof Map)) {
                    r.add(new Issue(file, rule.path, val, "객체 필요"));
                }
                break;
        }
    }
}
