package com.sajo.common.web;


import lombok.NonNull;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Set;

public class CommonPageableArgumentResolver extends PageableHandlerMethodArgumentResolver {

    public static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");
    private static final Set<Integer> ALLOWED_SIZE = Set.of(10, 30, 50);
    private static final int DEFAULT_PAGE_SIZE =10;

    @Override
    @NonNull
    public Pageable resolveArgument(@NonNull MethodParameter methodParameter, ModelAndViewContainer mavContainer,
                                    @NonNull NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {

        Pageable pageable = super.resolveArgument(methodParameter, mavContainer, webRequest, binderFactory);

        int pageSize = pageable.getPageSize();
        int pageNumber = pageable.getPageNumber();
        Sort sort = pageable.getSort();

        if (!ALLOWED_SIZE.contains(pageSize)) {
            pageSize = DEFAULT_PAGE_SIZE;
        }
        if (!sort.isSorted()) {
            sort = DEFAULT_SORT;
        }

        return PageRequest.of(pageNumber, pageSize, sort);
    }
}
