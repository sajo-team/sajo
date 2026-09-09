package com.sajo.market_service.market.service.parser;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketStockMasterParserTest {
    private final MarketStockMasterParser parser = new MarketStockMasterParser();

    @Test
    void parsesKoreanNameFromCp949KospiRecord() {
        String tail = tail(227, 3, "0027", 113, "5846278", 212, "15755720");
        byte[] zip = zip("kospi_code.mst", "005930   000000000005삼성전자" + tail + "\n");

        var result = parser.parse(zip, "KOSPI");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).command().stockCode()).isEqualTo("005930");
        assertThat(result.get(0).command().stockName()).isEqualTo("삼성전자");
        assertThat(result.get(0).command().marketType()).isEqualTo("KOSPI");
        assertThat(result.get(0).command().industryCode()).isEqualTo("0027");
        assertThat(result.get(0).command().listedShares()).isEqualTo(5_846_278_000L);
        assertThat(result.get(0).command().marketCap()).isEqualByComparingTo("1575572000000000");
    }

    @Test
    void parsesKosdaqUsingThe222CharacterTail() {
        String tail = tail(221, 3, "1009", 108, "23457", 206, "38212");
        byte[] zip = zip("kosdaq_code.mst", "035720   000000000035카카오" + tail + "\n");

        var command = parser.parse(zip, "KOSDAQ").get(0).command();
        assertThat(command.marketType()).isEqualTo("KOSDAQ");
        assertThat(command.industryCode()).isEqualTo("1009");
        assertThat(command.listedShares()).isEqualTo(23_457_000L);
        assertThat(command.marketCap()).isEqualByComparingTo("3821200000000");
    }

    @Test
    void skipsListedSharesWhenUnitConversionOverflows() {
        assertThatThrownBy(() -> MarketStockMasterParser.convertListedShares("9223372036854776"))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void skipsNonOrdinarySecurityGroup() {
        byte[] zip = zip("kospi_code.mst", "069500   000000000069ETF" + "EF" + " ".repeat(225) + "\n");

        assertThat(parser.parse(zip, "KOSPI")).isEmpty();
    }

    @Test
    void skipsPreferredStockEvenWhenSecurityGroupIsST() {
        String tail = fixedTail(227, 158);
        byte[] zip = zip("kospi_code.mst", "005935   000000000005삼성전자우" + tail + "\n");

        assertThat(parser.parse(zip, "KOSPI")).isEmpty();
    }

    @Test
    void rejectsEmptyAndMalformedZip() {
        assertThatThrownBy(() -> parser.parse(new byte[0], "KOSPI"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse("not-a-zip".getBytes(), "KOSPI"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void countsTruncatedRowsAsSkipped() {
        var result = parser.parseWithStats(zip("kospi_code.mst", "005930 short\n"), "KOSPI");

        assertThat(result.stocks()).isEmpty();
        assertThat(result.skippedCount()).isEqualTo(1);
    }

    @Test
    void rejectsZipSlipEntry() {
        byte[] zip = zip("../kospi_code.mst", "anything");

        assertThatThrownBy(() -> parser.parse(zip, "KOSPI"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] zip(String name, String content) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(content.getBytes(Charset.forName("CP949")));
                zip.closeEntry();
            }
            return output.toByteArray();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static String fixedTail(int length, int preferredOffset) {
        char[] tail = " ".repeat(length).toCharArray();
        tail[preferredOffset] = '1';
        tail[0] = 'S';
        tail[1] = 'T';
        return new String(tail);
    }

    private static String tail(int length, int industryOffset, String industry,
                               int listedSharesOffset, String listedShares,
                               int marketCapOffset, String marketCap) {
        char[] value = " ".repeat(length).toCharArray();
        value[0] = 'S';
        value[1] = 'T';
        put(value, industryOffset, 4, industry);
        put(value, listedSharesOffset, 15, listedShares);
        put(value, marketCapOffset, 9, marketCap);
        return new String(value);
    }

    private static void put(char[] target, int offset, int length, String value) {
        String padded = " ".repeat(Math.max(0, length - value.length())) + value;
        padded.getChars(0, length, target, offset);
    }
}
