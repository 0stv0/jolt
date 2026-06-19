package stevku.jolt.internal;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.cdimascio.dotenv.Dotenv;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;
import stevku.jolt.domain.JSON;
import stevku.jolt.utils.AsyncUtil;
import stevku.jolt.utils.Logger;
import stevku.jolt.utils.Router;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("all")
public class Server {

    @FunctionalInterface
    public interface Middleware {
        Object[] handle(Request request);
    }

    private static final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

    private final int port;
    private final HttpServer server;

    private final Map<String, String> routePatterns    = new HashMap<>();
    private final Map<String, Method> routeMethods     = new HashMap<>();
    private final Map<String, Object> routeInstances   = new HashMap<>();
    private final Map<String, Endpoint> routeEndpoints = new HashMap<>();
    private final Map<String, String> routeCors        = new HashMap<>();

    public Server(int port) throws Exception
    {
        this.port   = port;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
    }
    public void listen() throws Exception
    {
        this.scan();
        this.server.setExecutor(null);
        this.server.start();
        Logger.info("Server listening on: " + Logger.Color.CYAN + "http://localhost:" + this.port);
    }
    private void scan() throws Exception
    {
        Reflections reflections = new Reflections(
            new ConfigurationBuilder()
                .forPackage("")
                .filterInputsBy(new FilterBuilder().includePattern(".*"))
        );

        Set<Class<?>> controllers = reflections.getTypesAnnotatedWith(Controller.class);
        for (Class<?> controller : controllers)
        {
            Object instance = controller.getDeclaredConstructor().newInstance();
            for (Field field : controller.getDeclaredFields())
            {
                if (!field.isAnnotationPresent(Env.class))
                    continue;
                String envKey = field.getAnnotation(Env.class).value();
                String envVal = dotenv.get(envKey);
                if (envVal == null)
                    continue;
                field.setAccessible(true);
                if (field.getType() == int.class)
                    field.set(instance, Integer.parseInt(envVal));
                else if (field.getType() == boolean.class)
                    field.set(instance, Boolean.parseBoolean(envVal));
                else
                    field.set(instance, envVal);
            }

            Controller meta = controller.getAnnotation(Controller.class);
            String basePath = meta.path();
            String cors     = meta.cors();

            for (Method method : controller.getDeclaredMethods())
            {
                if (!method.isAnnotationPresent(Endpoint.class))
                    continue;
                Endpoint endpoint = method.getAnnotation(Endpoint.class);
                String fullPath = basePath + endpoint.path();

                String key = endpoint.method().name() + ":" + fullPath;
                routePatterns.put(key, fullPath);
                routeMethods.put(key, method);
                routeInstances.put(key, instance);
                routeEndpoints.put(key, endpoint);
                routeCors.put(key, cors);
            }
        }
        this.server.createContext("/", (ex) -> {
            try { this.handle(ex); }
            catch (Exception _) {}
        });
    }
    private void handle(HttpExchange ex) throws Exception
    {
        String requestPath   = ex.getRequestURI().getPath();
        String requestMethod = ex.getRequestMethod();

        for (Map.Entry<String, String> entry : routePatterns.entrySet())
        {
            String method  = entry.getKey().split(":")[0];
            String pattern = entry.getValue();

            if (!method.equalsIgnoreCase(requestMethod) || !Router.matches(pattern, requestPath))
                continue;

            Map<String, String> params = Router.extract(pattern, requestPath);
            this.dispatch(ex, entry.getKey(), params);
            return;
        }
        ex.sendResponseHeaders(404, -1);
    }
    private void dispatch(HttpExchange ex, String key, Map<String, String> params)
    {
        Endpoint endpoint = routeEndpoints.get(key);
        Method method     = routeMethods.get(key);
        Object instance   = routeInstances.get(key);
        String cors       = routeCors.get(key);
        String fullPath   = routePatterns.get(key);

        Runnable handler = () -> {
            long start = System.currentTimeMillis();
            try
            {
                if (!cors.isEmpty())
                    ex.getResponseHeaders().set("Access-Control-Allow-Origin", cors);

                Request request = new Request(ex);
                request.setParams(params);

                if (method.isAnnotationPresent(UseMiddle.class))
                    for (Class<? extends Middleware> mClass : method.getAnnotation(UseMiddle.class).value())
                    {
                        Middleware middleware = mClass.getDeclaredConstructor().newInstance();
                        Object[] result       = middleware.handle(request);

                        if (!(boolean) result[0])
                        {
                            JSON error = (JSON) result[1];
                            byte[] err = error.stringify().getBytes();

                            ex.getResponseHeaders().set("Content-Type", "application/json");
                            ex.sendResponseHeaders(403, err.length);
                            ex.getResponseBody().write(err);
                            ex.getResponseBody().close();

                            long ms = System.currentTimeMillis() - start;
                            Logger.warn(endpoint.method().name() + " " + fullPath + " 403 in " + ms + "ms (" + mClass.getSimpleName() + ")");
                            return;
                        }
                    }

                Object result;
                byte[] response;
                String contentType;

                if (method.getParameterCount() == 1)
                    result = method.invoke(instance, request);
                else
                    result = method.invoke(instance);

                if (result instanceof JSON json)
                {
                    contentType = "application/json";
                    response    = json.stringify().getBytes();
                }
                else
                {
                    contentType = endpoint.textType() == TextType.HTML ?
                        "text/html" :
                        "text/plain";
                    response = result.toString().getBytes();
                }
                for (Map.Entry<String, String> headerEntry : request.getNHeaders().entrySet())
                    ex.getResponseHeaders().set(headerEntry.getKey(), headerEntry.getValue());
                ex.getResponseHeaders().set("Content-Type", contentType);
                ex.sendResponseHeaders(200, response.length);
                ex.getResponseBody().write(response);
                ex.getResponseBody().close();

                long ms = System.currentTimeMillis() - start;
                Logger.success(endpoint.method().name() + " " + fullPath + " in " + ms + "ms");
            }
            catch (Exception _)
            {
                long ms = System.currentTimeMillis() - start;
                Logger.error(endpoint.method().name() + " " + fullPath + " ERROR in " + ms + "ms");
                try { ex.sendResponseHeaders(500, -1); } catch (Exception _) {}
            }
        };

        if (endpoint.async())
            AsyncUtil.fireForget(handler);
        else
            handler.run();
    }
}
