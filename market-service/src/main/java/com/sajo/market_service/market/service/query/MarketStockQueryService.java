package com.sajo.market_service.market.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.common.response.PageResponse;
import com.sajo.market_service.market.domain.MarketStock;
import com.sajo.market_service.market.dto.response.MarketStockResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import com.sajo.market_service.market.repository.query.MarketStockQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketStockQueryService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final java.util.Set<String> ALLOWED_SORT_PROPERTIES =
            java.util.Set.of("stockCode", "stockName", "marketType");
    private static final java.util.Set<String> ALLOWED_SORT_DIRECTIONS = java.util.Set.of("asc", "desc");

    private final MarketStockQueryRepository marketStockQueryRepository;

    public PageResponse<MarketStockResponse> getStocks(String marketType, int page, int size, String sort) {
        Pageable pageable = createPageable(page, size, sort);
        Page<MarketStock> stocks = marketType == null || marketType.isBlank()
                ? marketStockQueryRepository.findAll(pageable)
                : marketStockQueryRepository.findByMarketType(MarketStock.normalizeMarketType(marketType), pageable);
        return PageResponse.from(stocks.map(MarketStockResponse::from));
    }

    public PageResponse<MarketStockResponse> searchStocks(String keyword, int page, int size, String sort) {
        String normalizedKeyword = normalizeKeyword(keyword);
        Pageable pageable = createPageable(page, size, sort);
        return PageResponse.from(marketStockQueryRepository
                .searchByStockNameOrStockCode(escapeLikeKeyword(normalizedKeyword), pageable)
                .map(MarketStockResponse::from));
    }

    public MarketStockResponse getStock(String stockCode) {
        MarketStock stock = marketStockQueryRepository.findByStockCode(MarketStock.normalizeStockCode(stockCode))
                .orElseThrow(() -> new BusinessException(MarketErrorCode.MARKET_STOCK_NOT_FOUND)); //6자리 숫자라 형식은 정상, 하지만 DB에는 없음 > 404
        return MarketStockResponse.from(stock);
    }

    private Pageable createPageable(int page, int size, String sort) {
        //테스트나 다른 클래스에서 직접 호출될 수 있기 때문에 Controller에서도 @Min, @Max로 검사했지만 Service에서도 다시 확인
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(MarketErrorCode.INVALID_MARKET_STOCK, "페이지 요청값이 유효하지 않습니다.");
        }
        Sort requestedSort = parseSort(sort);
        if (requestedSort.getOrderFor("stockCode") == null) {
            requestedSort = requestedSort.and(Sort.by(Sort.Direction.ASC, "stockCode"));
        }
        return PageRequest.of(page, size, requestedSort);
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.ASC, "stockCode");
        }
        String[] tokens = sort.split(",", -1);
        String property = tokens[0];
        if (tokens.length > 2 || property.isBlank() || !ALLOWED_SORT_PROPERTIES.contains(property)) {
            throw new BusinessException(MarketErrorCode.INVALID_MARKET_STOCK, "정렬 기준이 유효하지 않습니다.");
        }
        if (tokens.length == 2 && !ALLOWED_SORT_DIRECTIONS.contains(tokens[1])) {
            throw new BusinessException(MarketErrorCode.INVALID_MARKET_STOCK, "정렬 방향이 유효하지 않습니다.");
        }
        Sort.Direction direction = tokens.length == 1 || "asc".equals(tokens[1])
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, property);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new BusinessException(MarketErrorCode.INVALID_MARKET_STOCK, "검색어는 필수입니다.");
        }
        String normalized = keyword.trim();
        if (normalized.length() > 100) {
            throw new BusinessException(MarketErrorCode.INVALID_MARKET_STOCK, "검색어는 100자 이하여야 합니다.");
        }
        return normalized;
    }

    private String escapeLikeKeyword(String keyword) {
        return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
