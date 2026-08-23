package com.zyibin.app.blackoutradar.domain.address;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HouseNormalizer {

    private static final Pattern LEADING_NUMBER = Pattern.compile("^(\\d+)(.*)$");

    public NormalizedHouse normalize(String rawHouse) {
        Objects.requireNonNull(rawHouse, "rawHouse must not be null");
        String collapsed = collapseWhitespace(rawHouse);
        if (collapsed.isBlank()) {
            throw new IllegalArgumentException("rawHouse must not be blank");
        }
        Matcher matcher = LEADING_NUMBER.matcher(collapsed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("house must start with digits: " + rawHouse);
        }
        String houseNumber = matcher.group(1);
        String rest = matcher.group(2).trim();
        String houseAddition = null;
        String canonicalHouse;
        if (rest.isEmpty()) {
            canonicalHouse = houseNumber;
        } else {
            // Remove all whitespace inside addition and uppercase
            String additionCollapsed = rest.replaceAll("\\s+", "");
            if (additionCollapsed.isEmpty()) {
                throw new IllegalArgumentException("invalid house addition");
            }
            // Addition must be alphanumeric/slash/hyphen after normalization? Keep as is but uppercase
            // Validate that addition contains only allowed chars: letters, digits, slash, hyphen
            // If contains other chars, treat as ambiguous -> throw
            // Allow any Unicode letter after NFC to support idempotency test
            if (!additionCollapsed.matches("[\\p{L}0-9/\\-]+")) {
                throw new IllegalArgumentException("unsupported house format: " + rawHouse);
            }
            houseAddition = additionCollapsed.toUpperCase(Locale.ROOT);
            canonicalHouse = houseNumber + houseAddition;
        }
        // canonicalHouse already uppercase for addition, houseNumber stays digits
        // Ensure idempotency: canonicalHouse when normalized again yields same
        return new NormalizedHouse(houseNumber, houseAddition, canonicalHouse);
    }

    static String collapseWhitespace(String value) {
        String nfc = Normalizer.normalize(value, Normalizer.Form.NFC);
        String trimmed = nfc.trim();
        return trimmed.replaceAll("\\s+", " ");
    }
}
