package stevku.jolt.internal;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventBus {

    private static volatile Map<String, List<Runnable>> events =
        new ConcurrentHashMap<>();

    public static boolean emit(String event, Object data)
    {
        List<Runnable> handlers = events.get(event);
        if (handlers == null)
            return false;
        boolean success = true;
        for (Runnable handler : handlers)
            try { handler.run(); }
            catch (Exception _) { success = false; }
        return success;
    }
    public static boolean emit(String event)
    {
        return emit(event, null);
    }
    public static void subscribe(String event, Runnable runnable)
    {
        events.computeIfAbsent(event, _ -> new CopyOnWriteArrayList<>()).add(runnable);
    }
}
