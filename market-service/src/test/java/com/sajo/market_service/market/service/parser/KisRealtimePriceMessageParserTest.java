package com.sajo.market_service.market.service.parser;

import com.sajo.market_service.market.dto.kis.KisRealtimePriceMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실측 payload(2026-09-14, 로컬 실행 중 캡처한 005930/000660 체결가)를 그대로 사용한 테스트다.
 * H0STCNT0의 레코드 구조(필드 순서/개수)는 KIS 공식 문서로 직접 검증하지 못했으므로, 이 테스트가
 * "실제로 들어온 원문을 우리가 해석한 대로 파싱하는지"를 지켜주는 유일한 안전장치에 가깝다.
 */
class KisRealtimePriceMessageParserTest {

    private final KisRealtimePriceMessageParser parser = new KisRealtimePriceMessageParser();

    @Test
    void parsesSingleRecordFrame() {
        String raw = "0|H0STCNT0|001|005930^150746^249250^5^-10250^-3.95^250742.47^249500^254500^248500^249500"
                + "^249000^1^14871538^3728926343750^206240^176545^-29695^81.34^7912656^6435830^5^0.44^106.69"
                + "^090011^5^-250^113225^5^-5250^145726^2^750^20260914^20^N^85729^141952^343655^1371668^0.25"
                + "^12052370^123.39^0^^249500^2";

        List<KisRealtimePriceMessage> messages = parser.parse(raw);

        assertThat(messages).hasSize(1);
        KisRealtimePriceMessage message = messages.get(0);
        assertThat(message.stockCode()).isEqualTo("005930");
        assertThat(message.tradeTime()).isEqualTo("150746");
        assertThat(message.currentPrice()).isEqualTo("249250");
        assertThat(message.changeSign()).isEqualTo("5");
        assertThat(message.changePrice()).isEqualTo("-10250");
        assertThat(message.changeRate()).isEqualTo("-3.95");
        assertThat(message.openPrice()).isEqualTo("249500");
        assertThat(message.highPrice()).isEqualTo("254500");
        assertThat(message.lowPrice()).isEqualTo("248500");
        assertThat(message.tradeVolume()).isEqualTo("1");
        assertThat(message.accumulatedVolume()).isEqualTo("14871538");
        assertThat(message.accumulatedTradeAmount()).isEqualTo("3728926343750");
        assertThat(message.businessDate()).isEqualTo("20260914");
    }

    @Test
    void parsesMultiRecordFrameIntoSeparateMessagesInOrder() {
        String raw = "0|H0STCNT0|003|"
                + "000660^150746^1697500^5^-114500^-6.32^1713280.13^1707000^1740000^1690000^1698000^1697000^8"
                + "^3348315^5736602835000^153164^118444^-34720^82.14^1752489^1439449^5^0.43^127.55^090007^5"
                + "^-9500^110819^5^-42500^150626^2^7500^20260914^20^N^1069^574^11250^27434^0.46^2305122^145.26"
                + "^0^^1707000^2"
                + "^000660^150746^1697000^5^-115000^-6.35^1713280.12^1707000^1740000^1690000^1698000^1697000^1"
                + "^3348316^5736604532000^153165^118444^-34721^82.14^1752490^1439449^5^0.43^127.55^090007^5"
                + "^-10000^110819^5^-43000^150626^2^7000^20260914^20^N^1069^574^11250^27434^0.46^2305122^145.26"
                + "^0^^1707000^2"
                + "^000660^150746^1697000^5^-115000^-6.35^1713280.12^1707000^1740000^1690000^1698000^1697000^1"
                + "^3348317^5736606229000^153166^118444^-34722^82.14^1752491^1439449^5^0.43^127.55^090007^5"
                + "^-10000^110819^5^-43000^150626^2^7000^20260914^20^N^1570^575^11751^27446^0.46^2305122^145.26"
                + "^0^^1707000^2";

        List<KisRealtimePriceMessage> messages = parser.parse(raw);

        assertThat(messages).hasSize(3);
        assertThat(messages).allMatch(message -> message.stockCode().equals("000660"));
        assertThat(messages.get(0).currentPrice()).isEqualTo("1697500");
        assertThat(messages.get(0).accumulatedVolume()).isEqualTo("3348315");
        assertThat(messages.get(1).currentPrice()).isEqualTo("1697000");
        assertThat(messages.get(1).accumulatedVolume()).isEqualTo("3348316");
        assertThat(messages.get(2).accumulatedVolume()).isEqualTo("3348317");
    }

    @Test
    void ignoresJsonSubscriptionAckFrame() {
        String raw = "{\"header\":{\"tr_id\":\"H0STCNT0\",\"tr_key\":\"000660\",\"encrypt\":\"N\"},"
                + "\"body\":{\"rt_cd\":\"0\",\"msg_cd\":\"OPSP0000\",\"msg1\":\"SUBSCRIBE SUCCESS\"}}";

        assertThat(parser.parse(raw)).isEmpty();
    }

    @Test
    void ignoresBlankOrNullPayload() {
        assertThat(parser.parse(null)).isEmpty();
        assertThat(parser.parse("")).isEmpty();
        assertThat(parser.parse("   ")).isEmpty();
    }

    @Test
    void skipsFrameWhoseFieldCountIsNotAMultipleOfFieldCountPerRecord() {
        String raw = "0|H0STCNT0|001|005930^150746^249250";

        assertThat(parser.parse(raw)).isEmpty();
    }
}
