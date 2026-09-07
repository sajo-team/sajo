package com.sajo.market_service.market.repository.query;

import com.sajo.market_service.market.domain.MarketStockPrice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.UUID;

public interface MarketDataStatusQueryRepository extends Repository<MarketStockPrice, UUID> {

    @Query(value = """
            with price_status as (
                select
                    count(distinct stock_id) as daily_price_stock_count,
                    max(date) as latest_daily_price_date
                from m_market_stocks_price
                where source = 'REST'
                  and time is null
                  and close_price is not null
            ),
            indicator_status as (
                select
                    count(distinct stock_id) as indicator_stock_count,
                    max(reference_date) as latest_indicator_reference_date
                from m_market_stocks_indicator
                where reference_date is not null
            )
            select
                (select count(*) from m_market_stocks) as total_stock_count,
                price_status.daily_price_stock_count,
                price_status.latest_daily_price_date,
                indicator_status.indicator_stock_count,
                indicator_status.latest_indicator_reference_date
            from price_status
            cross join indicator_status
            """, nativeQuery = true)
    MarketDataStatusProjection findStatus();
}
