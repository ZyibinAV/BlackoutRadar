package com.zyibin.app.blackoutradar.domain.address;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.text.Normalizer;
import org.junit.jupiter.api.Test;

class StreetNormalizerTest {

    private final StreetNormalizer normalizer = new StreetNormalizer();

    @Test
    void trimmingAndWhitespaceNormalization() {
        NormalizedStreet result = normalizer.normalize("  ул.   Ленина   ");
        assertEquals(StreetType.STREET, result.type());
        assertEquals("ЛЕНИНА", result.canonicalName());
    }

    @Test
    void collapseConsecutiveWhitespace() {
        NormalizedStreet result = normalizer.normalize("ул.\t\n Ленина  \t  ");
        assertEquals("ЛЕНИНА", result.canonicalName());
    }

    @Test
    void unicodeNfcNormalization() {
        // e + combining accent vs single char
        String decomposed = "Ленина\u0301";
        String nfc = Normalizer.normalize(decomposed, Normalizer.Form.NFC);
        NormalizedStreet r1 = normalizer.normalize(decomposed);
        NormalizedStreet r2 = normalizer.normalize(nfc);
        assertEquals(r1.canonicalName(), r2.canonicalName());
    }

    @Test
    void canonicalUppercaseWithLocaleRoot() {
        NormalizedStreet result = normalizer.normalize("ул. ленина");
        assertEquals("ЛЕНИНА", result.canonicalName());
    }

    @Test
    void idempotencyOnCanonicalValues() {
        // canonicalize steps are idempotent: applying twice via StreetNormalizer's internal canonicalize is not directly,
        // but textual canonicalization is idempotent
        String raw = "  Ул.  Ленина  ";
        NormalizedStreet first = normalizer.normalize(raw);
        // normalize canonicalName alone as UNKNOWN street should preserve it uppercased
        NormalizedStreet second = normalizer.normalize(first.canonicalName());
        assertEquals(first.canonicalName(), second.canonicalName());
    }

    @Test
    void knownStreetTypes() {
        assertEquals(StreetType.STREET, normalizer.normalize("ул Ленина").type());
        assertEquals(StreetType.PROSPECT, normalizer.normalize("пр Победы").type());
        assertEquals(StreetType.BOULEVARD, normalizer.normalize("б-р Мира").type());
        assertEquals(StreetType.LANE, normalizer.normalize("пер Гоголя").type());
        assertEquals(StreetType.PASSAGE, normalizer.normalize("пр-д Заводской").type());
        assertEquals(StreetType.SQUARE, normalizer.normalize("пл Ленина").type());
        assertEquals(StreetType.EMBANKMENT, normalizer.normalize("наб Речная").type());
        assertEquals(StreetType.HIGHWAY, normalizer.normalize("ш Энтузиастов").type());
        assertEquals(StreetType.ROAD, normalizer.normalize("дор Садовая").type());
        assertEquals(StreetType.TRACT, normalizer.normalize("тракт Московский").type());
        assertEquals(StreetType.ALLEY, normalizer.normalize("ал Лесная").type());
        assertEquals(StreetType.DEAD_END, normalizer.normalize("туп Глухой").type());
        assertEquals(StreetType.DESCENT, normalizer.normalize("спуск Крутой").type());
        assertEquals(StreetType.MAGISTRAL, normalizer.normalize("магистраль Главная").type());
    }

    @Test
    void unknownForUnrecognizedType() {
        NormalizedStreet result = normalizer.normalize("Ленина");
        assertEquals(StreetType.UNKNOWN, result.type());
        assertEquals("ЛЕНИНА", result.canonicalName());
    }

    @Test
    void unknownIsNotWildcard() {
        NormalizedStreet unknown = normalizer.normalize("Ленина");
        NormalizedStreet street = normalizer.normalize("ул Ленина");
        assertNotEquals(unknown.type(), street.type());
        assertEquals(StreetType.UNKNOWN, unknown.type());
        assertEquals(StreetType.STREET, street.type());
    }

    @Test
    void blankInputRejected() {
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalize("   "));
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalize(""));
        assertThrows(NullPointerException.class, () -> normalizer.normalize(null));
    }

    @Test
    void ambiguousBothEndsTypeIsUnknown() {
        // Both first and last token look like types -> ambiguous
        NormalizedStreet result = normalizer.normalize("ул Ленина пер");
        assertEquals(StreetType.UNKNOWN, result.type());
    }

    @Test
    void typeAtEndIsRecognized() {
        NormalizedStreet result = normalizer.normalize("Ленина ул");
        assertEquals(StreetType.STREET, result.type());
        assertEquals("ЛЕНИНА", result.canonicalName());
    }

    @Test
    void dotVariationsAreHandled() {
        assertEquals(StreetType.STREET, normalizer.normalize("ул. Ленина").type());
        assertEquals(StreetType.STREET, normalizer.normalize("ул Ленина").type());
        assertEquals(StreetType.PROSPECT, normalizer.normalize("пр. Победы").type());
        assertEquals(StreetType.PROSPECT, normalizer.normalize("пр Победы").type());
    }

    @Test
    void caseInsensitiveTypeDetection() {
        assertEquals(StreetType.STREET, normalizer.normalize("УЛ Ленина").type());
        assertEquals(StreetType.STREET, normalizer.normalize("Ул Ленина").type());
        assertEquals(StreetType.STREET, normalizer.normalize("ул ленина").type());
    }
}
