# Jolt

Lightweight REST API framework for Java. No Spring, no boilerplate — just clean code.

> Download the latest release at [GitHub Releases](https://github.com/0stv0/jolt/releases)

---

## Quick Start

```java
public class Main {
    public static void main(String[] args) throws Exception {
        new Server(4000)
            .onStart(() -> Logger.info("Ready"))
            .onStop(() -> Logger.warn("Shutting down"))
            .listen();
    }
}
```

---

## Controller

```java
@Controller(path = "/user", cors = "*")
public class UserController {

    @Endpoint(path = "/list", method = Method.GET)
    public JSON list() {
        return new JSON().set("message", "ok");
    }

    @Endpoint(path = "/:id", method = Method.GET)
    public JSON get(Request req) {
        int id = req.paramInt("id");
        return new JSON().set("id", id);
    }

    @Endpoint(path = "/create", method = Method.POST)
    public JSON create(Request req) {
        if (!req.checkBodyKeys("name", "email"))
            return new JSON().set("error", "missing fields");
        return new JSON().set("name", req.body("name"));
    }

    @Endpoint(path = "/page", method = Method.GET, textType = TextType.HTML)
    public String page() {
        return "<h1>Hello</h1>";
    }

    @Endpoint(path = "/heavy", method = Method.GET, async = true)
    public JSON heavy() {
        // runs on thread pool (availableProcessors / 2)
        return new JSON().set("message", "done");
    }
}
```

---

## Request

| Method | Description |
|---|---|
| `param(key)` | Path param as String |
| `paramInt(key)` | Path param as int |
| `query(key)` | Query param as String |
| `queryInt(key)` | Query param as int |
| `queryBool(key)` | Query param as boolean |
| `body(key)` | Body field as String |
| `bodyInt(key)` | Body field as int |
| `bodyBool(key)` | Body field as boolean |
| `bodyMap(key)` | Body field as Map |
| `header(key)` | Request header |
| `setHeader(key, value)` | Set response header |
| `checkBodyKeys(keys...)` | Validate body fields exist |
| `checkQueryKeys(keys...)` | Validate query params exist |
| `checkParams(keys...)` | Validate path params exist |

---

## JSON

```java
return new JSON()
    .set("message", "ok")
    .set("code", 200)
    .set("active", true)
    .set("user", Map.of("name", "John", "age", 30));
```

```java
// from map
Map<String, Object> data = req.bodyMap("user");
JSON json = JSON.fromMap(data);
```

---

## Middleware

```java
public class AuthMiddleware implements Server.Middleware {
    @Override
    public Object[] handle(Request req) {
        String token = req.header("Authorization");
        if (token == null)
            return new Object[]{false, new JSON().set("error", "unauthorized")};
        return new Object[]{true, null};
    }
}
```

```java
@Endpoint(path = "/secure", method = Method.GET)
@UseMiddle({AuthMiddleware.class, RateLimitMiddleware.class})
public JSON secure(Request req) {
    return new JSON().set("message", "ok");
}
```

Returns `403` with error JSON if middleware blocks.

---

## Workers & Cron

```java
@Worker
public class CleanupWorker {

    @Cron(seconds = 30)
    public void cleanup() {
        Logger.info("Running cleanup...");
    }

    @Cron(hours = 1, async = true)
    public void report() {
        Logger.info("Generating report...");
    }

    @Cron(minutes = 5, seconds = 30) // 5min 30s = 330s
    public void sync() {
        Logger.info("Syncing...");
    }
}
```

Cron intervals are summed: `seconds + minutes*60 + hours*3600 + days*86400 + months*2592000`.

---

## Event Bus

```java
// emit
EventBus.emit("user.created", data);
boolean ok = EventBus.emit("user.created", data); // returns false if any listener threw
```

```java
@EventListener
public class UserListener {

    @Subscribe("user.created")
    public void onUserCreated() {
        Logger.info("User created");
    }
}
```

---

## Error Handling

```java
@ErrorListener
public class AppErrorListener {

    @Fetch(RuntimeException.class)
    public JSON onRuntime(Exception e) {
        return new JSON().set("error", e.getMessage());
    }

    @Fetch(IllegalArgumentException.class)
    public JSON onIllegal(Exception e) {
        return new JSON().set("error", "bad input");
    }
}
```

Returns `500` with the handler's response. Handler can optionally accept `Exception` as parameter. Return type is auto-detected:

- `JSON` → `application/json`
- `String` starting with `<` → `text/html`
- `String` → `text/plain`

```java
@Fetch(RuntimeException.class)
public JSON onRuntime(Exception e) {
    return new JSON().set("error", e.getMessage()); // application/json
}

@Fetch(IllegalArgumentException.class)
public String onIllegal(Exception e) {
    return "Bad input: " + e.getMessage(); // text/plain
}

@Fetch(NotFoundException.class)
public String onNotFound(Exception e) {
    return "<h1>Not Found</h1>"; // text/html — auto-detected
}
```

---

## Environment Variables

```java
public class Postgres {

    @Env("PG_HOST")
    private static String host;

    @Env("PG_PORT")
    private static int port;
}
```

Reads from `.env` in project root, falls back to system environment variables.

```
JWT_SECRET=mysecret
DB_PORT=5432
```

---

## Lifecycle Hooks

```java
new Server(4000)
    .onStart(() -> Logger.info("Connected to DB"))
    .onStartAsync(() -> warmupCache())
    .onStop(() -> Logger.warn("Cleanup done"))
    .listen();
```

---

## Manager

Generic in-memory store with iteration support.

```java
public class Main {
    public static final Manager<String, User> users = new Manager<>();

    public static void main(String[] args) throws Exception {
        new Server(4000).listen();
    }
}
```

```java
// in controller
Main.users.set("u1", new User("John"));
User u = Main.users.get("u1");

for (User user : Main.users) {
    Logger.info(user.getName());
}
```

---

## AsyncUtil

```java
AsyncUtil.fireForget(() -> doSomething());

AsyncUtil.promise(() -> fetchData())
    .thenAccept(data -> process(data));

AsyncUtil.supplyTo(() -> fetchData(), data -> save(data));

AsyncUtil.workOn(() -> fetchData(), data -> save(data)); // both on thread pool
```

---

## Logger

```java
Logger.info("Server ready");
Logger.success("Request handled");
Logger.warn("Rate limit hit");
Logger.error("Something failed");
```

Output format: `[LEVEL | yyyy-MM-dd HH:mm:ss] message`

---

## Spring vs Jolt

Same result — POST endpoint with body validation, auth middleware and JSON response.

```java
// Spring
@Component
public class AuthInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        if (req.getHeader("Authorization") == null) {
            res.setStatus(401);
            res.getWriter().write("{\"error\":\"unauthorized\"}");
            return false;
        }
        return true;
    }
}

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Autowired AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor);
    }
}

@RestController
@RequestMapping("/api/user")
public class UserController {

    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        if (!body.containsKey("name") || !body.containsKey("email"))
            return ResponseEntity.badRequest().body(Map.of("error", "missing fields"));
        return ResponseEntity.ok(Map.of("name", body.get("name")));
    }
}

// Jolt
public class AuthMiddleware implements Server.Middleware {
    @Override
    public Object[] handle(Request req) {
        if (req.header("Authorization") == null)
            return new Object[]{false, new JSON().set("error", "unauthorized")};
        return new Object[]{true, null};
    }
}

@Controller(path = "/api/user")
public class UserController {

    @Endpoint(path = "/create", method = Method.POST)
    @UseMiddle(AuthMiddleware.class)
    public JSON create(Request req) {
        if (!req.checkBodyKeys("name", "email"))
            return new JSON().set("error", "missing fields");
        return new JSON().set("name", req.body("name"));
    }
}
```

---

## License

MIT