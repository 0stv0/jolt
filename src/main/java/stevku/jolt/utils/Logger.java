package stevku.jolt.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {

    public enum Color {
        RESET("\u001B[0m"),
        GREEN("\u001B[32m"),
        RED("\u001B[31m"),
        YELLOW("\u001B[33m"),
        CYAN("\u001B[36m");

        private final String code;

        Color(String code)
        {
            this.code = code;
        }
        @Override
        public String toString()
        {
            return this.code;
        }
    }
    private static String getTime()
    {
        return LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
    public static void info(String text)
    {
        IO.println(
            Color.CYAN + "[INFO | " + getTime() + "] " + Color.RESET + text
        );
    }
    public static void warn(String text)
    {
        IO.println(
            Color.YELLOW + "[WARN | " + getTime() + "] " + Color.RESET + text
        );
    }
    public static void error(String text)
    {
        IO.println(
            Color.RED + "[ERROR | " + getTime() + "] " + Color.RESET + text
        );
    }
    public static void success(String text)
    {
        IO.println(
            Color.GREEN + "[OK | " + getTime() + "] " + Color.RESET + text
        );
    }
}
