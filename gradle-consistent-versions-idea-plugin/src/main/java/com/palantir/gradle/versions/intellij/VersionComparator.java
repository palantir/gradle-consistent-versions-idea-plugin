/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.versions.intellij;

import com.google.common.base.Splitter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import one.util.streamex.StreamEx;

public record VersionComparator(boolean latest, List<Integer> numericParts, List<Qualifier> qualifiers) {

    private static final List<String> PRE_RELEASE_ORDER =
            List.of("final", "release", "r", "rc", "dev", "snapshot", "beta", "alpha");

    private static final Pattern Q_PATTERN = Pattern.compile("([a-zA-Z]+)(\\d*)");
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("\\d+");
    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+(?:\\.\\d+)*)?(?:[-.](.*))?$");

    /**
     * Compares two {@code DependencyVersion} instances.
     *
     * @param v1 the first version
     * @param v2 the second version
     * @return a negative integer, zero, or a positive integer as the first version is newer than,
     * equal to, or older than the second version
     */
    public static int compare(DependencyVersion v1, DependencyVersion v2) {
        VersionComparator vc1 = parseVersion(v1);
        VersionComparator vc2 = parseVersion(v2);

        Comparator<VersionComparator> comparator = Comparator.comparing(VersionComparator::isLatest)
                .reversed()
                .thenComparing(VersionComparator::getNumericParts, VersionComparator::compareNumericParts)
                .thenComparing(VersionComparator::getQualifiers, VersionComparator::compareQualifiers);

        return comparator.compare(vc1, vc2);
    }

    private static int compareNumericParts(List<Integer> nums1, List<Integer> nums2) {
        int maxLength = Math.max(nums1.size(), nums2.size());

        return IntStream.range(0, maxLength)
                .map(i -> {
                    int num1 = i < nums1.size() ? nums1.get(i) : 0;
                    int num2 = i < nums2.size() ? nums2.get(i) : 0;
                    return Integer.compare(num2, num1); // Compare num2 to num1 for descending order
                })
                .filter(comparisonResult -> comparisonResult != 0) // Find the first non-zero comparison
                .findFirst()
                .orElse(0); // Return 0 if all parts are equal
    }

    private static int compareQualifiers(List<Qualifier> qualifiers1, List<Qualifier> qualifiers2) {
        if (qualifiers1.isEmpty() && qualifiers2.isEmpty()) {
            return 0;
        }
        if (qualifiers1.isEmpty()) {
            return -1;
        }
        if (qualifiers2.isEmpty()) {
            return 1;
        }

        int minSize = Math.min(qualifiers1.size(), qualifiers2.size());

        List<Qualifier> subList1 = qualifiers1.subList(0, minSize);
        List<Qualifier> subList2 = qualifiers2.subList(0, minSize);

        // Zip the two sublists together and compare pair-wise
        return StreamEx.zip(subList1, subList2, (q1, q2) -> {
                    int index1 = PRE_RELEASE_ORDER.indexOf(q1.type());
                    int index2 = PRE_RELEASE_ORDER.indexOf(q2.type());

                    if (index1 == index2) {
                        return Integer.compare(q2.number(), q1.number()); // Higher number = newer
                    }

                    if (index1 == -1) {
                        return 1; // q1 > q2
                    }
                    if (index2 == -1) {
                        return -1; // q1 < q2
                    }

                    return Integer.compare(index1, index2); // Lower index = higher priority
                })
                .findFirst(result -> result != 0)
                .orElseGet(() -> Integer.compare(qualifiers2.size(), qualifiers1.size()));
    }

    private static Qualifier parseQualifier(String raw) {
        String lowered = raw.toLowerCase(Locale.ROOT);
        Matcher matcher = Q_PATTERN.matcher(lowered);

        if (matcher.matches()) {
            return new Qualifier(matcher.group(1), parseNumeric(matcher.group(2)));
        }

        if (NUMERIC_PATTERN.matcher(raw).matches()) {
            return new Qualifier("numeric", parseNumeric(raw));
        }

        return new Qualifier(lowered, 0);
    }

    private static int parseNumeric(String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static VersionComparator parseVersion(DependencyVersion version) {
        return parseVersion(version.version(), version.isLatest());
    }

    private static VersionComparator parseVersion(String ver, boolean latest) {
        Matcher matcher = VERSION_PATTERN.matcher(ver);
        if (!matcher.matches()) {
            return new VersionComparator(latest, List.of(), List.of());
        }

        String numericPart = matcher.group(1);
        String qualifierPart = matcher.group(2);

        List<Integer> numericParts = (numericPart != null && !numericPart.isEmpty())
                ? Splitter.on('.').splitToList(numericPart).stream()
                        .map(VersionComparator::parseNumeric)
                        .toList()
                : List.of();

        List<Qualifier> qualifiers = (qualifierPart != null && !qualifierPart.isEmpty())
                ? Splitter.onPattern("[^a-zA-Z0-9]+")
                        .omitEmptyStrings()
                        .trimResults()
                        .splitToList(qualifierPart)
                        .stream()
                        .map(VersionComparator::parseQualifier)
                        .toList()
                : List.of();

        return new VersionComparator(latest, numericParts, qualifiers);
    }

    private boolean isLatest() {
        return latest;
    }

    private List<Integer> getNumericParts() {
        return numericParts;
    }

    private List<Qualifier> getQualifiers() {
        return qualifiers;
    }

    private record Qualifier(String type, int number) {}
}
