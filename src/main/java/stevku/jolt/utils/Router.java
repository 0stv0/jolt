package stevku.jolt.utils;

import java.util.HashMap;
import java.util.Map;

public class Router {

    public static boolean matches(String pattern, String path)
    {
        String[] patternParts = pattern.split("/");
        String[] pathParts    = path.split("/");

        if (patternParts.length != pathParts.length)
            return false;

        for (int i = 0; i < patternParts.length; i++)
        {
            if (patternParts[i].startsWith(":"))
                continue;
            if (!patternParts[i].equalsIgnoreCase(pathParts[i]))
                return false;
        }
        return true;
    }
    public static Map<String, String> extract(String pattern, String path)
    {
        Map<String, String> params = new HashMap<>();

        String[] patternParts = pattern.split("/");
        String[] pathParts    = path.split("/");

        for (int i = 0; i < patternParts.length; i++)
            if (patternParts[i].startsWith(":"))
                params.put(patternParts[i].substring(1), pathParts[i]);
        return params;
    }
}
