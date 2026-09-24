package com.sajo.common.observation;

import java.util.Locale;

/*
     CLIENT span 이름 규칙: "http {method} {client.name} {uri}" (예: http get market-service /internal/v1/strategies/{strategyId})
     Spring 기본값("http get")은 호출 대상이 이름에 없어, 외부 API(KIS 등)처럼 받는 쪽 span이 없는 호출은
     Zipkin 목록에서 구분이 안 된다. Feign/RestClient convention이 같은 규칙을 쓰도록 한 곳에 둔다.
     span 이름에만 쓰이고 메트릭에는 영향이 없다. uri는 템플릿/경로만 들어오므로 실제 값은 포함되지 않는다.
*/
public final class ClientSpanNames {

    private static final String NONE = "none";

    private ClientSpanNames() {
    }

    public static String of(String method, String clientName, String uri) {
        StringBuilder name = new StringBuilder("http ").append(method.toLowerCase(Locale.ROOT));
        if (isPresent(clientName)) {
            name.append(' ').append(clientName);
        }
        if (isPresent(uri)) {
            name.append(' ').append(uri);
        }
        return name.toString();
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank() && !NONE.equals(value);
    }
}
