package stevku.jolt.domain;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("all")
public class JSON {

    private volatile Map<String, Object> data = new ConcurrentHashMap<>();

    public static JSON fromMap(Map<String, Object> map)
    {
        JSON json = new JSON();
        map.forEach((k,v) -> json.set(k, v));
        return json;
    }
    public synchronized JSON set(String key, Object val)
    {
        data.put(key, val);
        return this;
    }
    public synchronized <T> T get(String key)
    {
        return (T) data.get(key);
    }
    public synchronized Map<String, Object> getMap()
    {
        return this.data;
    }
    private synchronized String serializeValue(Object v)
    {
        if (v instanceof Number || v instanceof Boolean)
            return v.toString();
        else if (v instanceof Map<?, ?> map)
        {
            StringBuilder sb = new StringBuilder("{");
            map.forEach((k, val) -> {
                sb.append("\"").append(k).append("\":");
                sb.append(this.serializeValue(val));
                sb.append(",");
            });
            if (sb.length() > 1)
            sb.deleteCharAt(sb.length() - 1);
            sb.append("}");
            return sb.toString();
        }
        else if (v instanceof JSON json)
        {
            return json.stringify();
        }
        return "\"" + v + "\"";
    }
    public synchronized String stringify()
    {
        StringBuilder sb = new StringBuilder("{");
        this.data.forEach((k, v) -> {
            sb.append("\"").append(k).append("\":");
            sb.append(this.serializeValue(v));
            sb.append(",");
        });
        if (sb.length() > 1)
            sb.deleteCharAt(sb.length() - 1);
        sb.append("}");
        return sb.toString();
    }
}
