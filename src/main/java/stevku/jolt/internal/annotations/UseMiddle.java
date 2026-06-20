package stevku.jolt.internal.annotations;

import stevku.jolt.internal.Server;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface UseMiddle {
    Class<? extends Server.Middleware>[] value();
}
