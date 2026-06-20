package stevku.jolt.internal.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Cron {
    int seconds() default 0;
    int minutes() default 0;
    int hours() default 0;
    int days() default 0;
    int months() default 0;
    boolean async() default false;
}