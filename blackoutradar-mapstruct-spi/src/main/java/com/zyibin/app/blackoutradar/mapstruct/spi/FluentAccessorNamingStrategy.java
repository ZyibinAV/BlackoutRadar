package com.zyibin.app.blackoutradar.mapstruct.spi;

import java.util.Set;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.mapstruct.ap.spi.DefaultAccessorNamingStrategy;
import org.mapstruct.ap.spi.MethodType;

public class FluentAccessorNamingStrategy extends DefaultAccessorNamingStrategy {

    private static final Set<String> JAVA_BEAN_PREFIXES = Set.of("get", "is", "set", "has", "with", "add");

    private static final Set<String> OBJECT_METHODS =
            Set.of("equals", "hashCode", "toString", "getClass", "clone", "finalize", "notify", "notifyAll", "wait");

    @Override
    public MethodType getMethodType(ExecutableElement method) {
        if (isObjectMethod(method)) {
            return MethodType.OTHER;
        }
        MethodType methodType = super.getMethodType(method);
        if (method.getParameters().isEmpty()) {
            if (methodType == MethodType.ADDER || methodType == MethodType.SETTER) {
                methodType = MethodType.OTHER;
            } else if (methodType == MethodType.PRESENCE_CHECKER && !isBooleanType(method.getReturnType())) {
                methodType = MethodType.OTHER;
            }
        }
        if (methodType != MethodType.OTHER) {
            return methodType;
        }
        if (isFluentGetter(method)) {
            return MethodType.GETTER;
        }
        return methodType;
    }

    @Override
    public String getPropertyName(ExecutableElement getterOrSetterMethod) {
        MethodType methodType = getMethodType(getterOrSetterMethod);
        if (methodType != MethodType.GETTER && methodType != MethodType.SETTER && methodType != MethodType.ADDER) {
            return null;
        }
        String methodName = getterOrSetterMethod.getSimpleName().toString();
        for (String prefix : JAVA_BEAN_PREFIXES) {
            if (methodName.startsWith(prefix)
                    && methodName.length() > prefix.length()
                    && Character.isUpperCase(methodName.charAt(prefix.length()))) {
                return decapitalize(methodName.substring(prefix.length()));
            }
        }
        return methodName;
    }

    private boolean isFluentGetter(ExecutableElement method) {
        if (!method.getParameters().isEmpty()) {
            return false;
        }
        if (method.getReturnType().getKind() == TypeKind.VOID) {
            return false;
        }
        if (method.getModifiers().contains(Modifier.STATIC)) {
            return false;
        }
        return !isJavaBeanPrefixed(method.getSimpleName().toString());
    }

    private boolean isJavaBeanPrefixed(String name) {
        for (String prefix : JAVA_BEAN_PREFIXES) {
            if (name.startsWith(prefix)
                    && name.length() > prefix.length()
                    && Character.isUpperCase(name.charAt(prefix.length()))) {
                return true;
            }
        }
        return false;
    }

    private boolean isObjectMethod(ExecutableElement method) {
        return OBJECT_METHODS.contains(method.getSimpleName().toString());
    }

    private boolean isBooleanType(TypeMirror type) {
        return type.getKind() == TypeKind.BOOLEAN || "java.lang.Boolean".equals(getQualifiedName(type));
    }

    private static String decapitalize(String name) {
        if (name.isEmpty()) {
            return name;
        }
        if (name.length() > 1 && Character.isUpperCase(name.charAt(0)) && Character.isUpperCase(name.charAt(1))) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}