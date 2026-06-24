package stevku.jolt.internal;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.cdimascio.dotenv.Dotenv;
import org.reflections.Reflections;
import org.reflections.scanners.FieldAnnotationsScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;
import stevku.jolt.domain.JSON;
import stevku.jolt.internal.annotations.*;
import stevku.jolt.internal.enums.TextType;
import stevku.jolt.utils.AsyncUtil;
import stevku.jolt.utils.Logger;
import stevku.jolt.utils.Router;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("all")
public class Server {

    @FunctionalInterface
    public interface Middleware {
        Object[] handle(Request request);
    }

    private static final Dotenv dotenv                      = Dotenv.configure().ignoreIfMissing().load();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private final int port;
    private final HttpServer server;

    private Runnable onStart;
    private Runnable onStop;
    private Runnable onStartAsync;

    private final Map<String, String> routePatterns    = new HashMap<>();
    private final Map<String, Method> routeMethods     = new HashMap<>();
    private final Map<String, Object> routeInstances   = new HashMap<>();
    private final Map<String, Endpoint> routeEndpoints = new HashMap<>();
    private final Map<String, String> routeCors        = new HashMap<>();

    private final Map<Class<?>, Method> errorMethods   = new HashMap<>();
    private final Map<Class<?>, Object> errorInstances = new HashMap<>();

    public Server(int port) throws Exception
    {
        this.port   = port;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
    }
    public Server listen() throws Exception
    {
        this.env();
        this.scan();
        if (this.onStop != null)
            Runtime.getRuntime().addShutdownHook(new Thread(this.onStop));

        this.workers();
        this.listeners();
        this.errors();

        this.server.setExecutor(null);
        this.server.start();
        Logger.info("Server listening on: " + Logger.Color.CYAN + "http://localhost:" + this.port);

        if (this.onStart != null)
            this.onStart.run();
        if (this.onStartAsync != null)
            AsyncUtil.fireForget(this.onStartAsync);

        return this;
    }
    private void workers() throws Exception
    {
        Reflections reflections = new Reflections(
            new ConfigurationBuilder()
                .forPackage("")
                .filterInputsBy(new FilterBuilder().includePattern(".*"))
        );

        Set<Class<?>> workers = reflections.getTypesAnnotatedWith(Worker.class);
        for (Class<?> worker : workers)
        {
            Object instance = worker.getDeclaredConstructor().newInstance();
            for (Method method : worker.getDeclaredMethods())
            {
                if (!method.isAnnotationPresent(Cron.class))
                    continue;
                Cron cron     = method.getAnnotation(Cron.class);
                long interval = cron.seconds()
                    + cron.minutes() * 60L
                    + cron.hours() * 3600L
                    + cron.days() * 86400L
                    + cron.months() * 2592000L;
                if (interval == 0)
                    continue;
                Runnable task = () -> {
                    try { method.invoke(instance); }
                    catch (Exception e) { Logger.error(e.getMessage()); }
                };
                if (cron.async())
                    scheduler.scheduleAtFixedRate(
                        () -> AsyncUtil.fireForget(task),
                        0, interval, TimeUnit.SECONDS
                    );
                else
                    scheduler.scheduleAtFixedRate(task, 0, interval, TimeUnit.SECONDS);
            }
        }
    }
    private void listeners() throws Exception
    {
        Reflections reflections = new Reflections(
            new ConfigurationBuilder()
                .forPackage("")
                .filterInputsBy(new FilterBuilder().includePattern(".*"))
        );

        Set<Class<?>> listeners = reflections.getTypesAnnotatedWith(EventListener.class);
        for (Class<?> listener : listeners)
        {
            Object instance = listener.getDeclaredConstructor().newInstance();
            for (Method method : listener.getDeclaredMethods())
            {
                if (!method.isAnnotationPresent(Subscribe.class))
                    continue;
                Subscribe subscribe = method.getAnnotation(Subscribe.class);

                Runnable handler = () -> {
                    try { method.invoke(instance); }
                    catch (Exception _) {}
                };
                EventBus.subscribe(subscribe.value(), handler);
            }
        }
    }
    private void errors() throws Exception
    {
        Reflections reflections = new Reflections(
            new ConfigurationBuilder()
                .forPackage("")
                .filterInputsBy(new FilterBuilder().includePattern(".*"))
        );

        Set<Class<?>> handlers = reflections.getTypesAnnotatedWith(ErrorListener.class);
        for (Class<?> handler : handlers)
        {
            Object instance = handler.getDeclaredConstructor().newInstance();
            for (Method method : handler.getDeclaredMethods())
            {
                if (!method.isAnnotationPresent(Fetch.class))
                    continue;
                Class<? extends Exception> ex = method.getAnnotation(Fetch.class).value();
                this.errorMethods.put(ex, method);
                this.errorInstances.put(ex, instance);
            }
        }
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
                this.routePatterns.put(key, fullPath);
                this.routeMethods.put(key, method);
                this.routeInstances.put(key, instance);
                this.routeEndpoints.put(key, endpoint);
                this.routeCors.put(key, cors);
            }
        }
        this.server.createContext("/", (ex) -> {
            try { this.handle(ex); }
            catch (Exception _) {}
        });
    }
    private void env() throws Exception
    {
        String cp      = System.getProperty("java.class.path");
        String[] parts = cp.split(File.pathSeparator);

        ConfigurationBuilder config = new ConfigurationBuilder()
            .filterInputsBy(new FilterBuilder().includePattern(".*"))
            .addClassLoaders(Thread.currentThread().getContextClassLoader())
            .addClassLoaders(ClassLoader.getSystemClassLoader())
            .setScanners(new SubTypesScanner(false), new FieldAnnotationsScanner());
        for (String path : parts)
            config.addUrls(new File(path).toURI().toURL());

        Reflections reflections  = new Reflections(config);
        Set<Class<?>> allClasses = reflections.getSubTypesOf(Object.class);
        for (Class<?> clazz : allClasses)
            try
            {
                for (Field field : clazz.getDeclaredFields())
                {
                    if (!field.isAnnotationPresent(Env.class) || !Modifier.isStatic(field.getModifiers()))
                        continue;
                    String envKey = field.getAnnotation(Env.class).value();
                    String envVal = dotenv.get(envKey);
                    if (envVal == null)
                        continue;

                    field.setAccessible(true);
                    Object val;
                    if (field.getType() == int.class)
                        val = Integer.parseInt(envVal);
                    else if (field.getType() == boolean.class)
                        val = Boolean.parseBoolean(envVal);
                    else
                        val = envVal;
                    field.set(null, val);
                }
            }
            catch (Throwable _) {}
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
                if (method.getParameterCount() == 1)
                    result = method.invoke(instance, request);
                else
                    result = method.invoke(instance);
                for (Map.Entry<String, String> headerEntry : request.getNHeaders().entrySet())
                    ex.getResponseHeaders().set(headerEntry.getKey(), headerEntry.getValue());
                this.sendResponse(ex, result, endpoint.textType(), 200);

                long ms = System.currentTimeMillis() - start;
                Logger.success(endpoint.method().name() + " " + fullPath + " in " + ms + "ms");
            }
            catch (Exception e)
            {
                long ms = System.currentTimeMillis() - start;

                Throwable cause = e.getCause() != null ? e.getCause() : e;
                if (cause.getCause() != null)
                    cause = cause.getCause();
                Method errorMethod = this.errorMethods.get(cause.getClass());
                if (errorMethod != null)
                {
                    try
                    {
                        Object result;
                        if (errorMethod.getParameterCount() == 1)
                            result = errorMethod.invoke(this.errorInstances.get(cause.getClass()), cause);
                        else
                            result = errorMethod.invoke(this.errorInstances.get(cause.getClass()));

                        String str    = result.toString();
                        TextType type = str.trim().startsWith("<") ? TextType.HTML : TextType.Basic;
                        this.sendResponse(ex, result, type, 500);
                    }
                    catch (Exception _)
                    {
                        try { ex.sendResponseHeaders(500, -1); }
                        catch (Exception _) {}
                    }
                }
                else
                    try { ex.sendResponseHeaders(500, -1); }
                    catch (Exception _) {};
                Logger.error(endpoint.method().name() + " " + fullPath + " ERROR in " + ms + "ms");
            }
        };

        if (endpoint.async())
            AsyncUtil.fireForget(handler);
        else
            handler.run();
    }
    private void sendResponse(HttpExchange ex, Object result, TextType textType, int code) throws Exception
    {
        byte[] response;
        String contentType;

        if (result instanceof JSON json)
        {
            contentType = "application/json";
            response    = json.stringify().getBytes();
        }
        else
        {
            contentType = textType == TextType.HTML ?
                "text/html" :
                "text/plain";
            response = result.toString().getBytes();
        }
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(code, response.length);
        ex.getResponseBody().write(response);
        ex.getResponseBody().close();
    }
    public Server onStart(Runnable onStart)
    {
        this.onStart = onStart;
        return this;
    }
    public Server onStop(Runnable onStop)
    {
        this.onStop = onStop;
        return this;
    }
    public Server onStartAsync(Runnable onStartAsync)
    {
        this.onStartAsync = onStartAsync;
        return this;
    }
}
