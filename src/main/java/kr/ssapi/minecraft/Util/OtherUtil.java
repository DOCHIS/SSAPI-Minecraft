package kr.ssapi.minecraft.Util;

import java.util.Locale;

public class OtherUtil {
    public static String numberCom(Integer number) {
        long integerPart = (long) number;
        return String.format(Locale.US, "%,d", integerPart);
    }
    public static boolean StringFormatInteger(String text) {
        try {
            Integer.parseInt(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
