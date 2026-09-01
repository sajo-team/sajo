package com.other;

import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PageableTestController {

    @GetMapping("/pageable")
    public String pageable(Pageable pageable) {
        return pageable.getPageNumber() + "," + pageable.getPageSize() + "," + pageable.getSort();
    }
}
