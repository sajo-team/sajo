package com.sajo.market_service.market.service.parser;

import com.sajo.market_service.market.dto.kis.KisRealtimePriceMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * KIS WebSocket이 보내는 원문 텍스트 프레임을 {@link KisRealtimePriceMessage} 목록으로 변환한다.
 *
 * <p>원문 프레임은 두 종류가 섞여서 들어온다 — 구독 등록 성공/실패 등의 응답은 JSON
 * ({@code {"header":...,"body":...}})이고, 실시간 체결가 자체는
 * {@code 암호화구분|tr_id|데이터건수|필드...} 형태의 파이프/캐럿 구분 텍스트다. 이 파서는 후자만 다루고,
 * JSON 프레임은 빈 목록을 반환해 조용히 무시한다.</p>
 *
 * <p>한 프레임에 여러 종목/여러 체결 건이 이어붙어 오는 경우가 있어({@code 데이터건수} > 1), 본문을
 * {@code ^}로 나눈 뒤 {@link KisRealtimePriceMessage#FIELD_COUNT_PER_RECORD}개씩 잘라 레코드 단위로
 * 복원한다. 레코드 하나를 파싱하다 실패해도 나머지 레코드 처리는 계속한다 — 실시간 시세는 다음 tick이
 * 금방 다시 오므로, 한 건 때문에 전체 프레임을 버릴 이유가 없다.</p>
 */
@Slf4j
@Component
public class KisRealtimePriceMessageParser {

    private static final String TRADE_CONDITION_TR_ID = "H0STCNT0";
    // WebSocket 수신 스레드의 핫 패스이므로 String.split()이 매번 컴파일하지 않도록 미리 컴파일해둔다(코드 리뷰 반영).
    private static final Pattern FRAME_DELIMITER = Pattern.compile("\\|");
    private static final Pattern FIELD_DELIMITER = Pattern.compile("\\^");

    public List<KisRealtimePriceMessage> parse(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            return List.of();
        }
        // JSON 프레임(구독 응답 등)은 실시간 체결가 프레임이 아니므로 조용히 건너뛴다.
        if (!rawPayload.startsWith("0|") && !rawPayload.startsWith("1|")) {
            return List.of();
        }

        String[] frameParts = FRAME_DELIMITER.split(rawPayload, 4);
        if (frameParts.length < 4 || !TRADE_CONDITION_TR_ID.equals(frameParts[1])) {
            log.debug("H0STCNT0가 아닌 프레임이라 건너뜁니다. trId={}", frameParts.length > 1 ? frameParts[1] : null);
            return List.of();
        }

        String[] allFields = FIELD_DELIMITER.split(frameParts[3], -1);
        int recordCount = allFields.length / KisRealtimePriceMessage.FIELD_COUNT_PER_RECORD;
        if (recordCount == 0 || allFields.length % KisRealtimePriceMessage.FIELD_COUNT_PER_RECORD != 0) {
            log.warn("KIS 실시간 체결가 프레임의 필드 개수가 예상과 달라 건너뜁니다. fieldCount={}, expectedPerRecord={}",
                    allFields.length, KisRealtimePriceMessage.FIELD_COUNT_PER_RECORD);
            return List.of();
        }

        List<KisRealtimePriceMessage> messages = new ArrayList<>(recordCount);
        for (int recordIndex = 0; recordIndex < recordCount; recordIndex++) {
            int start = recordIndex * KisRealtimePriceMessage.FIELD_COUNT_PER_RECORD;
            String[] recordFields = java.util.Arrays.copyOfRange(
                    allFields, start, start + KisRealtimePriceMessage.FIELD_COUNT_PER_RECORD);
            try {
                messages.add(KisRealtimePriceMessage.fromFields(recordFields));
            } catch (Exception exception) {
                // 레코드 하나가 손상돼도 나머지 레코드는 계속 처리한다.
                log.warn("KIS 실시간 체결가 레코드 파싱에 실패해 건너뜁니다. recordIndex={}, exceptionType={}",
                        recordIndex, exception.getClass().getSimpleName());
            }
        }
        return messages;
    }
}
