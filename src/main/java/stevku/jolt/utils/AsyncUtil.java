package stevku.jolt.utils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class AsyncUtil {

    private static final ExecutorService executor = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors() / 2
    );
    public static void fireForget(Runnable task)
    {
        executor.submit(task);
    }
    public static  <T> void supplyTo(Supplier<T> supplier, Consumer<T> consumer)
    {
        CompletableFuture.supplyAsync(supplier, executor)
            .thenAccept(consumer);
    }
    public static <T> void workOn(Supplier<T> supplier, Consumer<T> consumer)
    {
        CompletableFuture.supplyAsync(supplier, executor)
            .thenAcceptAsync(consumer, executor);
    }
    public static <T> CompletableFuture<T> promise(Supplier<T> supplier)
    {
        return CompletableFuture.supplyAsync(supplier, executor);
    }
}
