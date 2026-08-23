package com.zyibin.app.blackoutradar.domain.address;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class StreetNormalizer {

    private static final Map<String, StreetType> TOKEN_TO_TYPE = Map.ofEntries(
            Map.entry("ул", StreetType.STREET),
            Map.entry("улица", StreetType.STREET),
            Map.entry("у", StreetType.STREET),
            Map.entry("пр", StreetType.PROSPECT),
            Map.entry("проспект", StreetType.PROSPECT),
            Map.entry("пр-т", StreetType.PROSPECT),
            Map.entry("просп", StreetType.PROSPECT),
            Map.entry("б-р", StreetType.BOULEVARD),
            Map.entry("б", StreetType.BOULEVARD),
            Map.entry("бульвар", StreetType.BOULEVARD),
            Map.entry("бул", StreetType.BOULEVARD),
            Map.entry("пер", StreetType.LANE),
            Map.entry("переулок", StreetType.LANE),
            Map.entry("пр-д", StreetType.PASSAGE),
            Map.entry("проезд", StreetType.PASSAGE),
            Map.entry("пл", StreetType.SQUARE),
            Map.entry("площадь", StreetType.SQUARE),
            Map.entry("наб", StreetType.EMBANKMENT),
            Map.entry("набережная", StreetType.EMBANKMENT),
            Map.entry("ш", StreetType.HIGHWAY),
            Map.entry("шоссе", StreetType.HIGHWAY),
            Map.entry("дор", StreetType.ROAD),
            Map.entry("дорога", StreetType.ROAD),
            Map.entry("тракт", StreetType.TRACT),
            Map.entry("ал", StreetType.ALLEY),
            Map.entry("аллея", StreetType.ALLEY),
            Map.entry("туп", StreetType.DEAD_END),
            Map.entry("тупик", StreetType.DEAD_END),
            Map.entry("спуск", StreetType.DESCENT),
            Map.entry("маг", StreetType.MAGISTRAL),
            Map.entry("магистраль", StreetType.MAGISTRAL)
    );

    public NormalizedStreet normalize(String rawStreet) {
        Objects.requireNonNull(rawStreet, "rawStreet must not be null");
        String collapsed = collapseWhitespace(rawStreet);
        if (collapsed.isBlank()) {
            throw new IllegalArgumentException("rawStreet must not be blank");
        }
        // Split tokens preserving original collapsed form for name extraction
        String[] tokens = collapsed.split(" ");
        String firstTokenNormalized = normalizeToken(tokens[0]);
        String lastTokenNormalized = normalizeToken(tokens[tokens.length - 1]);

        StreetType firstType = TOKEN_TO_TYPE.get(firstTokenNormalized);
        StreetType lastType = TOKEN_TO_TYPE.get(lastTokenNormalized);

        StreetType type;
        String namePart;

        boolean firstIsType = firstType != null;
        boolean lastIsType = lastType != null;

        if (firstIsType && lastIsType) {
            // ambiguous: both ends look like type — treat as UNKNOWN to avoid heuristic
            type = StreetType.UNKNOWN;
            namePart = collapsed;
        } else if (firstIsType) {
            type = firstType;
            namePart = collapsed.substring(tokens[0].length()).trim();
        } else if (lastIsType) {
            type = lastType;
            namePart = collapsed.substring(0, collapsed.length() - tokens[tokens.length - 1].length()).trim();
        } else {
            type = StreetType.UNKNOWN;
            namePart = collapsed;
        }

        if (namePart.isBlank()) {
            throw new IllegalArgumentException("street name must not be blank after extracting type");
        }
        String canonicalName = canonicalize(namePart);
        return new NormalizedStreet(type, canonicalName);
    }

    static String collapseWhitespace(String value) {
        String nfc = Normalizer.normalize(value, Normalizer.Form.NFC);
        String trimmed = nfc.trim();
        return trimmed.replaceAll("\\s+", " ");
    }

    static String canonicalize(String value) {
        String collapsed = collapseWhitespace(value);
        if (collapsed.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        return collapsed.toUpperCase(Locale.ROOT);
    }

    private static String normalizeToken(String token) {
        // remove dots, lower case, keep hyphens
        String withoutDots = token.replace(".", "");
        return withoutDots.toLowerCase(Locale.ROOT).trim();
    }
}
