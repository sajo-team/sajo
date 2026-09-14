package com.sajo.trading_service.trading.repository.query.specification;

import com.sajo.trading_service.trading.controller.dto.request.OrderSearchCondition;
import com.sajo.trading_service.trading.domain.Order;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OrderSpecifications {

    public static Specification<Order> withCondition(
            UUID userId,
            OrderSearchCondition condition
    ) {
        return (root, query, criteriaBuilder) -> {

            List<Predicate> predicates = new ArrayList<>();

            predicates.add(
                    criteriaBuilder.equal(
                            root.get("userId"),
                            userId
                    )
            );

            predicates.add(
                    criteriaBuilder.isNull(
                            root.get("deletedAt")
                    )
            );

            if (condition.autoTradingId() != null) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.get("autoTradingId"),
                                condition.autoTradingId()
                        )
                );
            }

            if (condition.strategyId() != null) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.get("strategyId"),
                                condition.strategyId()
                                )
                );
            }

            if (condition.status() != null) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.get("status"),
                                condition.status()
                                )
                );
            }

            if (condition.stockCode() != null) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.get("stockCode"),
                                condition.stockCode()
                        )
                );
            }

            if (condition.orderType() != null) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.get("orderType"),
                                condition.orderType()
                        )
                );
            }

            return criteriaBuilder.and(
                    predicates.toArray(new Predicate[0])
            );
        };
    }
}
