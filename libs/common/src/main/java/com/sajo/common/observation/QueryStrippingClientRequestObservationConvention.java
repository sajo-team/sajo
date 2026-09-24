package com.sajo.common.observation;

import io.micrometer.common.KeyValue;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention;

/*
     RestClient의 기본 convention은 .uri(String)로 넘긴 문자열을 그대로 URI 템플릿으로 간주해 uri 태그에 넣는다.
     http.url(high cardinality, Zipkin span 태그 전용)에는 템플릿 사용 여부와 무관하게 실제 값이 담긴 전체 URL이 들어간다.

     호출 코드 작성 방식에 기대지 않고 한 곳에서 막기 위해, 두 태그 모두 쿼리스트링을 잘라내고 경로까지만 남긴다.
     KIS API는 경로에 식별자가 없어 경로만으로 API 구분이 충분하다.
*/
public class QueryStrippingClientRequestObservationConvention extends DefaultClientRequestObservationConvention {

    @Override
    public String getContextualName(ClientRequestObservationContext context) {
        return ClientSpanNames.of(
                method(context).getValue(),
                clientName(context).getValue(),
                uri(context).getValue()
        );
    }

    @Override
    protected KeyValue uri(ClientRequestObservationContext context) {
        KeyValue uri = super.uri(context);
        return KeyValue.of(uri.getKey(), stripQuery(uri.getValue()));
    }

    @Override
    protected KeyValue requestUri(ClientRequestObservationContext context) {
        KeyValue requestUri = super.requestUri(context);
        return KeyValue.of(requestUri.getKey(), stripQuery(requestUri.getValue()));
    }

    static String stripQuery(String value) {
        int queryStart = value.indexOf('?');
        return queryStart >= 0 ? value.substring(0, queryStart) : value;
    }
}
