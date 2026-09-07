package com.sajo.market_service.market.repository.query;

import com.sajo.market_service.market.domain.MarketStockPrice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.UUID;

public interface MarketDataStatusQueryRepository extends Repository<MarketStockPrice, UUID> {

    @Query(value = """
            select
                (select count(*) from m_market_stocks) as total_stock_count,
                (select count(distinct stock_id)
                   from m_market_stocks_price
                  where source = 'REST'
                    and time is null
                    and close_price is not null) as daily_price_stock_count,
                (select max(date)
                   from m_market_stocks_price
                  where source = 'REST'
                    and time is null
                    and close_price is not null) as latest_daily_price_date,
                (select count(distinct stock_id)
                   from m_market_stocks_indicator
                  where reference_date is not null) as indicator_stock_count,
                (select max(reference_date)
                   from m_market_stocks_indicator
                  where reference_date is not null) as latest_indicator_reference_date
            """, nativeQuery = true)
    MarketDataStatusProjection findStatus();
}
