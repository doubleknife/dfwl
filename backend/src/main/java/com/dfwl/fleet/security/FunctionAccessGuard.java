package com.dfwl.fleet.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.HashMap;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Component
public final class FunctionAccessGuard implements HandlerInterceptor {
    public record ApiBinding(String method, String path, List<String> anyOf, boolean self) { }
    private record Access(Set<String> anyOf, boolean self) { }

    private final ObjectProvider<RequestMappingHandlerMapping> mappings;
    private final FunctionRegistry registry;
    private volatile Map<Method, Access> compiled = Map.of();
    private volatile List<ApiBinding> apiBindings = List.of();

    public FunctionAccessGuard(@Qualifier("requestMappingHandlerMapping") ObjectProvider<RequestMappingHandlerMapping> mappings,
                               FunctionRegistry registry) {
        this.mappings = mappings;
        this.registry = registry;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void compileMappings() {
        Map<Method, Access> next = new HashMap<>();
        java.util.ArrayList<ApiBinding> bindings = new java.util.ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mappings.getObject().getHandlerMethods().entrySet()) {
            HandlerMethod method = entry.getValue();
            if (!method.getBeanType().getPackageName().startsWith("com.dfwl.fleet")) continue;
            FunctionAccess annotation = method.getMethodAnnotation(FunctionAccess.class);
            boolean publicLogin = method.getMethod().getName().equals("login")
                    && method.getBeanType().getSimpleName().equals("AuthController");
            if (publicLogin) continue;
            if (annotation == null || annotation.anyOf().length == 0 && !annotation.self())
                throw new IllegalStateException("Protected API has no function binding: " + method);
            Set<String> codes = Set.copyOf(Arrays.asList(annotation.anyOf()));
            for (String code : codes) {
                if (registry.find(code) == null)
                    throw new IllegalStateException("API uses unregistered function " + code + ": " + method);
            }
            boolean writes = entry.getKey().getMethodsCondition().getMethods().stream()
                    .anyMatch(http -> !"GET".equals(http.name()) && !"HEAD".equals(http.name()));
            if (writes && !annotation.self() && codes.stream().noneMatch(code -> "BUTTON".equals(registry.find(code).type())))
                throw new IllegalStateException("Write API must use button function: " + method);
            next.put(method.getMethod(), new Access(codes, annotation.self()));
            List<String> paths = entry.getKey().getPatternValues().stream().sorted().toList();
            List<String> httpMethods = entry.getKey().getMethodsCondition().getMethods().stream()
                    .map(Enum::name).sorted().toList();
            for (String path : paths) for (String http : httpMethods)
                bindings.add(new ApiBinding(http, path, codes.stream().sorted().toList(), annotation.self()));
        }
        compiled = Map.copyOf(next);
        apiBindings = List.copyOf(bindings);
    }

    public List<ApiBinding> apiBindings() { return apiBindings; }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) return true;
        if (!method.getBeanType().getPackageName().startsWith("com.dfwl.fleet")) return true;
        if (method.getMethod().getName().equals("login")
                && method.getBeanType().getSimpleName().equals("AuthController")) return true;
        Access access = compiled.get(method.getMethod());
        if (access == null) throw new AccessDeniedException("接口未关联合法页面或按钮功能");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user))
            throw new AccessDeniedException("请先登录");
        if (access.self()) return true;
        Set<String> granted = user.permissions();
        boolean allowed = access.anyOf().stream().anyMatch(code -> {
            if (!granted.contains(code)) return false;
            FunctionRegistry.Function function = registry.find(code);
            return "PAGE".equals(function.type()) || granted.contains(function.parentCode());
        });
        if (!allowed) throw new AccessDeniedException("当前账号没有此页面或操作权限");
        return true;
    }
}
