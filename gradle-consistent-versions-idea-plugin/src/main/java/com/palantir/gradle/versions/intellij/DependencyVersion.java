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
import java.util.List;
import org.immutables.value.Value;

@Value.Immutable
public abstract class DependencyVersion implements Comparable<DependencyVersion> {
    protected abstract String version();

    protected abstract Boolean isLatest();

    public static ImmutableDependencyVersion of(String version, Boolean isLatest) {
        return ImmutableDependencyVersion.builder()
                .version(version)
                .isLatest(isLatest)
                .build();
    }

    @Override
    public final String toString() {
        return version();
    }

    @Override
    public final int compareTo(DependencyVersion other) {
        return VersionComparator.compare(this, other);
    }

    // Nested static class for comparison logic
    private record VersionComparator(Boolean latest, int major, int minor, int patch) {

        public static int compare(DependencyVersion v1, DependencyVersion v2) {
            VersionComparator vc1 = parseVersion(v1);
            VersionComparator vc2 = parseVersion(v2);

            // Compare the 'isLatest' flag
            if (vc1.latest && !vc2.latest) {
                return -1;
            }
            if (!vc1.latest && vc2.latest) {
                return 1;
            }

            // Compare major version
            if (vc1.major != vc2.major) {
                return Integer.compare(vc2.major, vc1.major);
            }

            // Compare minor version
            if (vc1.minor != vc2.minor) {
                return Integer.compare(vc2.minor, vc1.minor);
            }

            // Compare patch version
            return Integer.compare(vc2.patch, vc1.patch);
        }

        private static VersionComparator parseVersion(DependencyVersion version) {
            List<String> parts = Splitter.on(".").splitToList(version.version());
            int major = !parts.isEmpty() ? parsePart(parts.get(0)) : 0;
            int minor = parts.size() > 1 ? parsePart(parts.get(1)) : 0;
            int patch = parts.size() > 2 ? parsePart(parts.get(2)) : 0;
            return new VersionComparator(version.isLatest(), major, minor, patch);
        }

        private static int parsePart(String part) {
            try {
                return Integer.parseInt(part);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }
}
