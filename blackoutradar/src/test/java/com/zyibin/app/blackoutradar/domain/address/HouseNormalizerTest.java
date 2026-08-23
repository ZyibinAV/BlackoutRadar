package com.zyibin.app.blackoutradar.domain.address;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.text.Normalizer;
import org.junit.jupiter.api.Test;

class HouseNormalizerTest {

    private final HouseNormalizer normalizer = new HouseNormalizer();

    @Test
    void pureNumber() {
        NormalizedHouse result = normalizer.normalize("15");
        assertEquals("15", result.houseNumber());
        assertNull(result.houseAddition());
        assertEquals("15", result.canonicalHouse());
    }

    @Test
    void numberWithLetterAddition() {
        NormalizedHouse result = normalizer.normalize("15а");
        assertEquals("15", result.houseNumber());
        assertEquals("А", result.houseAddition());
        assertEquals("15А", result.canonicalHouse());
    }

    @Test
    void numberWithLetterAndDigitsAddition() {
        NormalizedHouse result = normalizer.normalize("15к1");
        assertEquals("15", result.houseNumber());
        assertEquals("К1", result.houseAddition());
        assertEquals("15К1", result.canonicalHouse());
    }

    @Test
    void whitespaceIsNormalized() {
        NormalizedHouse result = normalizer.normalize("  15  к1  ");
        assertEquals("15", result.houseNumber());
        assertEquals("К1", result.houseAddition());
        assertEquals("15К1", result.canonicalHouse());
    }

    @Test
    void additionIsUppercasedWithRootLocale() {
        NormalizedHouse result = normalizer.normalize("15а");
        assertEquals("А", result.houseAddition());
        NormalizedHouse upper = normalizer.normalize("15А");
        assertEquals(result.canonicalHouse(), upper.canonicalHouse());
    }

    @Test
    void slashFormatSupported() {
        NormalizedHouse result = normalizer.normalize("15/2");
        assertEquals("15", result.houseNumber());
        assertEquals("/2", result.houseAddition());
        assertEquals("15/2", result.canonicalHouse());
    }

    @Test
    void hyphenFormatSupported() {
        NormalizedHouse result = normalizer.normalize("15-1");
        assertEquals("15", result.houseNumber());
        assertEquals("-1", result.houseAddition());
        assertEquals("15-1", result.canonicalHouse());
    }

    @Test
    void idempotency() {
        NormalizedHouse first = normalizer.normalize("15к1");
        NormalizedHouse second = normalizer.normalize(first.canonicalHouse());
        assertEquals(first.canonicalHouse(), second.canonicalHouse());
        assertEquals(first.houseNumber(), second.houseNumber());
        assertEquals(first.houseAddition(), second.houseAddition());
    }

    @Test
    void blankInputRejected() {
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalize("   "));
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalize(""));
        assertThrows(NullPointerException.class, () -> normalizer.normalize(null));
    }

    @Test
    void mustStartWithDigits() {
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalize("а15"));
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalize("квартира"));
    }

    @Test
    void unsupportedCharsRejected() {
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalize("15@1"));
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalize("15#"));
    }

    @Test
    void unicodeNfcHandling() {
        String decomposed = "15\u0061\u0301"; // a + accent
        String nfc = Normalizer.normalize(decomposed, Normalizer.Form.NFC);
        NormalizedHouse r1 = normalizer.normalize(decomposed);
        NormalizedHouse r2 = normalizer.normalize(nfc);
        assertEquals(r1.canonicalHouse(), r2.canonicalHouse());
    }

    @Test
    void collapseWhitespaceInsideAddition() {
        NormalizedHouse result = normalizer.normalize("15 к 1");
        assertEquals("15", result.houseNumber());
        assertEquals("К1", result.houseAddition());
        assertEquals("15К1", result.canonicalHouse());
    }
}
