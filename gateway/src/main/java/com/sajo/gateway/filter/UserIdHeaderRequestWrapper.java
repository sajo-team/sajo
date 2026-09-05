package com.sajo.gateway.filter;
 
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
 
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
 
// X-User-Id/X-User-Role 헤더를 서버가 검증한 값으로 강제 치환 (클라이언트가 보낸 원본 값은
// 항상 제거 - 스푸핑 방지)
class UserIdHeaderRequestWrapper extends HttpServletRequestWrapper {
 
    static final String USER_ID_HEADER = "X-User-Id";
    static final String USER_ROLE_HEADER = "X-User-Role";
 
    private final Map<String, String> overriddenHeaders = new LinkedHashMap<>();
 
    UserIdHeaderRequestWrapper(HttpServletRequest request, String userId, String role) {
        super(request);
        overriddenHeaders.put(USER_ID_HEADER, userId);
        overriddenHeaders.put(USER_ROLE_HEADER, role);
    }
 
    @Override
    public String getHeader(String name) {
        for (Map.Entry<String, String> entry : overriddenHeaders.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return super.getHeader(name);
    }
 
    @Override
    public Enumeration<String> getHeaders(String name) {
        for (Map.Entry<String, String> entry : overriddenHeaders.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                String value = entry.getValue();
                return value == null
                        ? Collections.emptyEnumeration()
                        : Collections.enumeration(List.of(value));
            }
        }
        return super.getHeaders(name);
    }
 
    @Override
    public Enumeration<String> getHeaderNames() {
        List<String> names = new ArrayList<>();
        Enumeration<String> originalNames = super.getHeaderNames();
        while (originalNames.hasMoreElements()) {
            String name = originalNames.nextElement();
            boolean isOverridden = overriddenHeaders.keySet().stream()
                    .anyMatch(overriddenName -> overriddenName.equalsIgnoreCase(name));
            if (!isOverridden) {
                names.add(name);
            }
        }
        overriddenHeaders.forEach((headerName, value) -> {
            if (value != null) {
                names.add(headerName);
            }
        });
        return Collections.enumeration(names);
    }
}
