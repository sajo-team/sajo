package com.sajo.operation_service.service.dependency;

// DependencyMappingService가 의존 대상의 기본 상태(up/down)를 조회할 때 쓰는 PromQL 조립만 담당.
// up 메트릭도 application 라벨을 갖고 있어서(실측 확인됨) 서비스/인프라 구분 없이 동일한 쿼리로 조회 가능하다.
final class DependencyMappingQueries {

    private DependencyMappingQueries() {
    }

    static String upStatus(String application) {
        return "up{application=\"%s\"}".formatted(application);
    }
}
