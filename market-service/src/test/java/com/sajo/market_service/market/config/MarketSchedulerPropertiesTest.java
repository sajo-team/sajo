package com.sajo.market_service.market.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MarketSchedulerPropertiesTest {

    @Test
    void trimsAndDeduplicatesTargetStockCodes() {
        MarketSchedulerProperties properties = properties(List.of(" 005930,000660 ", "005930"));

        assertThat(properties.targetStockCodes()).containsExactly("005930", "000660");
    }

    @Test
    void emptyTargetStockCodesKeepsAllStockKeysetMode() {
        assertThat(properties(List.of()).targetStockCodes()).isEmpty();
    }

    @Test
    void rejectsTargetStockCodeThatIsNotSixDigits() {
        assertThatIllegalArgumentException().isThrownBy(() -> properties(List.of("00593A")));
    }

    private MarketSchedulerProperties properties(List<String> targetStockCodes) {
        return new MarketSchedulerProperties(
                true, "11111111-1111-1111-1111-111111111111", "", 100,
                true, "", Duration.ofMillis(500), targetStockCodes);
    }
}
