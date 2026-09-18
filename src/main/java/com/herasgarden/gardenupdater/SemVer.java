package com.herasgarden.gardenupdater;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SemVer implements Comparable<SemVer> {
    private final List<Integer> numbers;
    private final String prerelease;

    private SemVer(List<Integer> numbers, String prerelease) {
        this.numbers = numbers;
        this.prerelease = prerelease;
    }

    public static SemVer parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Version is blank.");
        }

        String value = raw.trim();
        if (value.startsWith("v") || value.startsWith("V")) {
            value = value.substring(1);
        }

        int plus = value.indexOf('+');
        if (plus >= 0) {
            value = value.substring(0, plus);
        }

        String prerelease = "";
        int dash = value.indexOf('-');
        if (dash >= 0) {
            prerelease = value.substring(dash + 1).toLowerCase(Locale.ROOT);
            value = value.substring(0, dash);
        }

        List<Integer> numbers = new ArrayList<>();
        for (String part : value.split("\\.")) {
            if (part.isBlank()) {
                numbers.add(0);
                continue;
            }
            int end = 0;
            while (end < part.length() && Character.isDigit(part.charAt(end))) {
                end++;
            }
            if (end == 0) {
                throw new IllegalArgumentException("Invalid version: " + raw);
            }
            numbers.add(Integer.parseInt(part.substring(0, end)));
        }

        while (numbers.size() < 3) {
            numbers.add(0);
        }
        return new SemVer(List.copyOf(numbers), prerelease);
    }

    @Override
    public int compareTo(SemVer other) {
        int length = Math.max(numbers.size(), other.numbers.size());
        for (int i = 0; i < length; i++) {
            int left = i < numbers.size() ? numbers.get(i) : 0;
            int right = i < other.numbers.size() ? other.numbers.get(i) : 0;
            int compared = Integer.compare(left, right);
            if (compared != 0) {
                return compared;
            }
        }

        if (prerelease.isBlank() && !other.prerelease.isBlank()) {
            return 1;
        }
        if (!prerelease.isBlank() && other.prerelease.isBlank()) {
            return -1;
        }
        return prerelease.compareTo(other.prerelease);
    }
}
