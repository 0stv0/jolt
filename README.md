# Jolt

Lightweight REST API framework for Java. No Spring, no boilerplate.

## Installation

```xml
<dependency>
    <groupId>stevku.jolt</groupId>
    <artifactId>jolt</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Quick Start

```java
public class Main {
    public static void main(String[] args) throws Exception {
        new Server(4000).listen();
    }
}
```

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
        String name = req.body("name");
        return new JSON().set("message", "created").set("name", name);
    }
}
```

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

## JSON

```java
return new JSON()
    .set("message", "ok")
    .set("code", 200)
    .set("active", true)
    .set("user", Map.of("name", "John", "age", 30));
```

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
@UseMiddle(AuthMiddleware.class)
public JSON secure(Request req) {
    return new JSON().set("message", "ok");
}
```

## Async

```java
@Endpoint(path = "/heavy", method = Method.GET, async = true)
public JSON heavy(Request req) {
    // runs on thread pool (availableProcessors / 2)
    return new JSON().set("message", "done");
}
```

## Environment Variables

```java
@Controller(path = "/user")
public class UserController {

    @Env("JWT_SECRET")
    private String secret;

    @Env("DB_PORT")
    private int dbPort;
}
```

Reads from `.env` file in project root or system environment variables.

## AsyncUtil

```java
AsyncUtil.fireForget(() -> doSomething());

AsyncUtil.promise(() -> fetchData())
    .thenAccept(data -> process(data));

AsyncUtil.supplyTo(() -> fetchData(), data -> save(data));
```

## License

MIT