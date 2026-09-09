package com.sajo.market_service.market.service.parser;

import com.sajo.market_service.market.dto.command.MarketStockMasterCommand;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

@Component
public class MarketStockMasterParser {
    private static final long MAX_UNCOMPRESSED_BYTES = 50L * 1024 * 1024;
    private static final Charset CP949 = Charset.forName("CP949");
    private static final String ORDINARY = "ST";
    private static final Layout KOSPI = new Layout(
            new String[]{"group", "capScale", "industryLarge", "industryMedium", "industrySmall",
                    "manufacturing", "lowLiquidity", "governance", "sector", "kospi100", "kospi50", "krx",
                    "etp", "elw", "krx100", "auto", "semi", "bio", "bank", "spac", "energy", "steel",
                    "overheat", "media", "construction", "non1", "securities", "ship", "insurance", "transport",
                    "sri", "basePrice", "regularUnit", "afterUnit", "halt", "liquidation", "management",
                    "warning", "warningNotice", "unfairDisclosure", "reverseListing", "lock", "faceChange",
                    "capitalIncrease", "margin", "credit", "creditDays", "volume", "faceValue", "listingDate",
                    "listedShares", "capital", "closingMonth", "ipoPrice", "preferred", "shortSelling", "unusual",
                    "krx300", "kospi", "sales", "operatingProfit", "ordinaryProfit", "netProfit", "roe", "referenceMonth",
                    "marketCap", "groupCode", "creditLimit", "collateral", "lending"},
            new int[]{2, 1, 4, 4, 4, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 9, 5, 5, 1, 1, 1, 2, 1, 1,
                    1, 2, 2, 2, 3, 1, 3, 12, 12, 8, 15, 21, 2, 7, 1, 1, 1, 1, 1, 9,
                    9, 9, 5, 9, 8, 9, 3, 1, 1, 1});

    private static final Layout KOSDAQ = new Layout(
            new String[]{"group", "capScale", "industryLarge", "industryMedium", "industrySmall", "venture", "lowLiquidity",
                    "krx", "etp", "krx100", "auto", "semi", "bio", "bank", "spac", "energy", "steel", "overheat",
                    "media", "construction", "investmentCaution", "securities", "ship", "insurance", "transport", "kosdaq150",
                    "basePrice", "regularUnit", "afterUnit", "halt", "liquidation", "management", "warning", "warningNotice",
                    "unfairDisclosure", "reverseListing", "lock", "faceChange", "capitalIncrease", "margin", "credit", "creditDays",
                    "volume", "faceValue", "listingDate", "listedShares", "capital", "closingMonth", "ipoPrice", "preferred",
                    "shortSelling", "unusual", "krx300", "sales", "operatingProfit", "ordinaryProfit", "netProfit", "roe",
                    "referenceMonth", "marketCap", "groupCode", "creditLimit", "collateral", "lending"},
            new int[]{2, 1, 4, 4, 4, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 9,
                    5, 5, 1, 1, 1, 2, 1, 1, 1, 2, 2, 2, 3, 1, 3, 12, 12, 8, 15, 21, 2, 7, 1, 1, 1, 1,
                    9, 9, 9, 5, 9, 8, 9, 3, 1, 1, 1});

    public List<ParsedStock> parse(byte[] zipBytes, String marketType) {
        return parseWithStats(zipBytes, marketType).stocks();
    }

    public ParseResult parseWithStats(byte[] zipBytes, String marketType) {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new IllegalArgumentException("종목 마스터 ZIP이 비어 있습니다.");
        }
        Layout layout = "KOSPI".equals(marketType) ? KOSPI : KOSDAQ;
        // 공식 228/222자 영역에는 줄바꿈이 포함된다. BufferedReader.readLine()이
        // 줄바꿈을 제거하므로 Java에서 읽는 고정영역은 field_specs 합계만 사용한다.
        int tailLength = layout.totalWidth();
        List<ParsedStock> result = new ArrayList<>();
        int skipped = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            int fileCount = 0;
            boolean found = false;
            long uncompressedBytes = 0;
            while ((entry = zip.getNextEntry()) != null) {
                validateEntry(entry);
                if (entry.isDirectory()) continue;
                fileCount++;
                if (fileCount > 1) throw new IllegalArgumentException("종목 마스터 ZIP에 파일이 여러 개 있습니다.");
                String expectedName = "KOSPI".equals(marketType) ? "kospi_code.mst" : "kosdaq_code.mst";
                if (!entry.getName().equals(expectedName)) {
                    throw new IllegalArgumentException("예상하지 않은 종목 마스터 파일입니다.");
                }
                found = true;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(zip, CP949))) {
                    String row;
                    while ((row = reader.readLine()) != null) {
                        uncompressedBytes += row.getBytes(CP949).length + 1;
                        if (uncompressedBytes > MAX_UNCOMPRESSED_BYTES) {
                            throw new IllegalArgumentException("종목 마스터 압축 해제 크기가 제한을 초과했습니다.");
                        }
                        ParsedStock parsed;
                        try {
                            parsed = parseRow(row, tailLength, layout, marketType);
                        } catch (ArithmeticException exception) {
                            parsed = null;
                        }
                        if (parsed != null) result.add(parsed);
                        else if (!row.isBlank()) skipped++;
                    }
                }
                break;
            }
            if (!found) throw new IllegalArgumentException("종목 마스터 ZIP에 파일이 없습니다.");
            return new ParseResult(result, skipped);
        } catch (IOException exception) {
            if (exception instanceof ZipException) {
                throw new IllegalArgumentException("유효하지 않은 종목 마스터 ZIP입니다.", exception);
            }
            throw new IllegalArgumentException("종목 마스터 ZIP을 읽을 수 없습니다.", exception);
        }
    }

    private ParsedStock parseRow(String row, int tailLength, Layout layout, String marketType) {
        if (row == null || row.length() <= tailLength + 21) return null;
        String head = row.substring(0, row.length() - tailLength);
        String tail = row.substring(row.length() - tailLength);
        String code = slice(head, 0, 9).trim();
        String name = head.substring(21).trim();
        if (!code.matches("\\d{6}") || name.isBlank()) return null;
        String group = layout.value(tail, "group");
        String preferred = layout.value(tail, "preferred");
        if (!ORDINARY.equals(group) || !(preferred.isBlank() || "0".equals(preferred))) return null;
        String industry = layout.value(tail, "industryLarge");
        Long listedShares = convertListedShares(layout.value(tail, "listedShares"));
        BigDecimal marketCap = decimal(layout.value(tail, "marketCap"));
        if (marketCap != null) marketCap = marketCap.multiply(BigDecimal.valueOf(100_000_000L));
        return new ParsedStock(new MarketStockMasterCommand(code, name, marketType, blankToNull(industry), listedShares, marketCap));
    }

    private static String slice(String value, int start, int end) { return value.substring(start, Math.min(end, value.length())); }
    private static Long number(String value) { try { return value.isBlank() ? null : Long.parseLong(value); } catch (RuntimeException e) { return null; } }
    static Long convertListedShares(String value) {
        Long raw = number(value);
        return raw == null ? null : Math.multiplyExact(raw, 1_000L);
    }
    private static BigDecimal decimal(String value) { try { return value.isBlank() ? null : new BigDecimal(value); } catch (RuntimeException e) { return null; } }
    private static String blankToNull(String value) { return value.isBlank() ? null : value; }
    private static void validateEntry(ZipEntry entry) {
        String name = entry.getName();
        if (name.startsWith("/") || name.contains("..") || name.contains("\\")) throw new IllegalArgumentException("비정상 ZIP 경로입니다.");
    }
    public record ParsedStock(MarketStockMasterCommand command) { }
    public record ParseResult(List<ParsedStock> stocks, int skippedCount) { }

    private record Layout(String[] names, int[] widths) {
        private Layout {
            if (names.length != widths.length) throw new IllegalArgumentException("공식 레이아웃 컬럼과 폭이 일치하지 않습니다.");
        }
        int totalWidth() { return java.util.Arrays.stream(widths).sum(); }
        String value(String row, String name) {
            int offset = 0;
            for (int i = 0; i < names.length; i++) {
                if (names[i].equals(name)) return row.substring(offset, offset + widths[i]).trim();
                offset += widths[i];
            }
            throw new IllegalArgumentException("알 수 없는 종목 마스터 필드입니다: " + name);
        }
    }
}
