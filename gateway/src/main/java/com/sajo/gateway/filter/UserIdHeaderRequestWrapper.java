package com.sajo.gateway.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

// X-User-Id 헤더를 서버가 검증한 값으로 강제 치환 (클라이언트가 보낸 원본 값은 항상 제거 - 스푸핑 방지)
class UserIdHeaderRequestWrapper extends HttpServletRequestWrapper {

    static final String USER_ID_HEADER = "X-User-Id";

    private final String userId;

    UserIdHeaderRequestWrapper(HttpServletRequest request, String userId) {
        super(request);
        this.userId = userId;
    }

    @Override
    public String getHeader(String name) {
        if (USER_ID_HEADER.equalsIgnoreCase(name)) {
            return userId;
        }
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        if (USER_ID_HEADER.equalsIgnoreCase(name)) {
            return userId == null
                    ? Collections.emptyEnumeration()
                    : Collections.enumeration(List.of(userId));
        }
        return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        List<String> names = new ArrayList<>();
        Enumeration<String> originalNames = super.getHeaderNames();
        while (originalNames.hasMoreElements()) {
            String name = originalNames.nextElement();
            if (!USER_ID_HEADER.equalsIgnoreCase(name)) {
                names.add(name);
            }
        }
        if (userId != null) {
            names.add(USER_ID_HEADER);
        }
        return Collections.enumeration(names);
    }
}
