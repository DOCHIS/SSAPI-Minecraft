package kr.ssapi.triggers;

/**
 * 트리거 매칭 조건. amount 값과 비교하는 방식.
 *
 * <ul>
 *   <li>{@link Op#EQ}    — amount == value
 *   <li>{@link Op#GTE}   — amount >= value
 *   <li>{@link Op#LTE}   — amount <= value
 *   <li>{@link Op#RANGE} — min <= amount <= max
 *   <li>{@link Op#ANY}   — 모든 amount 매칭
 * </ul>
 */
public class MatchSpec {
    public enum Op { EQ, GTE, LTE, RANGE, ANY }

    public final Op op;
    public final long value;   // EQ / GTE / LTE
    public final long min;     // RANGE
    public final long max;     // RANGE

    private MatchSpec(Op op, long value, long min, long max) {
        this.op = op;
        this.value = value;
        this.min = min;
        this.max = max;
    }

    public static MatchSpec eq(long value) { return new MatchSpec(Op.EQ, value, 0, 0); }
    public static MatchSpec gte(long value) { return new MatchSpec(Op.GTE, value, 0, 0); }
    public static MatchSpec lte(long value) { return new MatchSpec(Op.LTE, value, 0, 0); }
    public static MatchSpec range(long min, long max) { return new MatchSpec(Op.RANGE, 0, min, max); }
    public static MatchSpec any() { return new MatchSpec(Op.ANY, 0, 0, 0); }

    public boolean matches(long amount) {
        switch (op) {
            case EQ:    return amount == value;
            case GTE:   return amount >= value;
            case LTE:   return amount <= value;
            case RANGE: return amount >= min && amount <= max;
            case ANY:   return true;
            default:    return false;
        }
    }

    @Override
    public String toString() {
        switch (op) {
            case EQ:    return "= " + value;
            case GTE:   return ">= " + value;
            case LTE:   return "<= " + value;
            case RANGE: return min + "~" + max;
            case ANY:   return "any";
            default:    return op.name();
        }
    }
}
