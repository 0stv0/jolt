package stevku.jolt.internal.annotations;

import stevku.jolt.internal.enums.Method;
import stevku.jolt.internal.enums.TextType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Endpoint {
    String path();
    Method method();
    boolean async() default false;
    TextType textType() default TextType.Basic;
}