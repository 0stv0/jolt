package stevku.jolt.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("all")
public class Request {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, String> query;
    private final Map<String, String> headers;
    private final Map<String, Object> body;
    private final Map<String, String> nHeaders;

    private Map<String, String> params = new HashMap<>();

    public Request(HttpExchange ex)
    {
        this.query    = this.parseQuery(ex.getRequestURI().getQuery());
        this.headers  = this.parseHeaders(ex);
        this.body     = this.parseBody(ex);
        this.nHeaders = new HashMap<>();
    }

    // Params Getters/Setter
    public void setParams(Map<String, String> params)
    {
        this.params = params;
    }
    public String param(String key)
    {
        return this.params.get(key);
    }
    public int paramInt(String key)
    {
        return Integer.parseInt(this.param(key));
    }

    // Query Getters
    public String query(String key)
    {
        return this.query.get(key);
    }
    public int queryInt(String key)
    {
        return Integer.parseInt(this.query(key));
    }
    public boolean queryBool(String key)
    {
        return Boolean.parseBoolean(this.query(key));
    }

    // Header Getter/Setter
    public String header(String key)
    {
        return this.headers.get(key);
    }
    public void setHeader(String header, String value)
    {
        this.nHeaders.put(header, value);
    }
    public Map<String, String> getNHeaders()
    {
        return this.nHeaders;
    }

    // Body Getters
    public String body(String key)
    {
        Object val = this.body.get(key);
        return val == null ? null : val.toString();
    }
    public int bodyInt(String key)
    {
        return Integer.parseInt(this.body(key));
    }
    public boolean bodyBool(String key)
    {
        return Boolean.parseBoolean(this.body(key));
    }
    public Map<String, Object> bodyMap(String key)
    {
        return (Map<String, Object>) this.body.get(key);
    }

    // Parsers
    private Map<String, String> parseQuery(String raw)
    {
        Map<String, String> query = new HashMap<>();
        if (raw == null)
            return query;
        for (String entry : raw.split("&"))
        {
            String[] parts = entry.split("=");
            if (parts.length == 2)
                query.put(parts[0], parts[1]);
        }
        return query;
    }
    private Map<String, String> parseHeaders(HttpExchange ex)
    {
        Map<String, String> headers = new HashMap<>();
        ex.getRequestHeaders().forEach((k, v) ->
            headers.put(k, v.getFirst())
        );
        return headers;
    }
    private Map<String, Object> parseBody(HttpExchange ex)
    {
        try
        {
            String raw = new String(ex.getRequestBody().readAllBytes());
            if (raw.isBlank())
                return new HashMap<>();
            return mapper.readValue(raw, new TypeReference<>() {});
        }
        catch (Exception _) { return new HashMap<>(); }
    }

    // Validators
    public boolean checkBodyKeys(String... keys)
    {
        for (String entry : keys)
            if (!this.body.containsKey(entry))
                return false;
        return true;
    }
    public boolean checkParams(String... params)
    {
        for (String entry : params)
            if (!this.params.containsKey(entry))
                return false;
        return true;
    }
    public boolean checkQueryKeys(String... keys)
    {
        for (String entry : keys)
            if (!this.query.containsKey(entry))
                return false;
        return true;
    }
}
