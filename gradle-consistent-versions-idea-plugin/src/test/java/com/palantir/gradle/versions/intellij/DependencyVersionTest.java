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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// These tests may appear odd as v2.compareTo(v1) results in v2 < 0 for v2 > v1, this is due to the fact that we are
// sorting in descending order so larger versions are sorted to the top/start
public class DependencyVersionTest {

    @Test
    public void numeric_comparison() {
        DependencyVersion v1 = DependencyVersion.of("1.2.3", false);
        DependencyVersion v2 = DependencyVersion.of("1.2.4", false);
        assertThat(v2.compareTo(v1))
                .as("v2 should appear before v1 based on numeric comparison")
                .isLessThan(0);
    }

    @Test
    public void pre_release_comparison() {
        DependencyVersion v1 = DependencyVersion.of("1.2.3-alpha", false);
        DependencyVersion v2 = DependencyVersion.of("1.2.3-beta", false);
        assertThat(v2.compareTo(v1))
                .as("v2 (beta) should appear before v1 (alpha) in pre-release comparison")
                .isLessThan(0);
    }

    @Test
    public void release_vs_pre_release() {
        DependencyVersion v1 = DependencyVersion.of("1.2.3", false);
        DependencyVersion v2 = DependencyVersion.of("1.2.3-rc", false);
        assertThat(v1.compareTo(v2))
                .as("v1 (release) should appear before v2 (pre-release)")
                .isLessThan(0);
    }

    @Test
    public void date_suffix_comparison() {
        DependencyVersion v1 = DependencyVersion.of("6.7.0.202309050840-alpha", false);
        DependencyVersion v2 = DependencyVersion.of("6.7.0.202309050840-r", false);
        assertThat(v2.compareTo(v1))
                .as("v2 with date suffix 'r' should appear before v1 with 'alpha'")
                .isLessThan(0);
    }

    @Test
    public void is_laflag() {
        DependencyVersion v1 = DependencyVersion.of("1.2.3", false);
        DependencyVersion v2 = DependencyVersion.of("1.2.3", true);
        assertThat(v2.compareTo(v1))
                .as("v2 (latest flag) should appear before v1")
                .isLessThan(0);
    }

    @Test
    public void equal_versions() {
        DependencyVersion v1 = DependencyVersion.of("2.0.0", false);
        DependencyVersion v2 = DependencyVersion.of("2.0.0", false);
        assertThat(v1.compareTo(v2)).as("v1 and v2 should be equal").isEqualTo(0);
    }

    @Test
    public void unknown_qualifier() {
        DependencyVersion v1 = DependencyVersion.of("1.0.0-unknown", false);
        DependencyVersion v2 = DependencyVersion.of("1.0.0-alpha", false);
        assertThat(v2.compareTo(v1))
                .as("v1 with unknown qualifier should appear after v2 with alpha")
                .isLessThan(0);
    }

    @Test
    public void rc_qualifier_comparison() {
        DependencyVersion v1 = DependencyVersion.of("1.0.0-rc1", false);
        DependencyVersion v2 = DependencyVersion.of("1.0.0-rc2", false);
        assertThat(v2.compareTo(v1))
                .as("v2 (rc2) should appear before v1 (rc1)")
                .isLessThan(0);
    }

    @Test
    public void rc_and_beta_comparison() {
        DependencyVersion v1 = DependencyVersion.of("1.0.0-beta2", false);
        DependencyVersion v2 = DependencyVersion.of("1.0.0-rc1", false);
        assertThat(v2.compareTo(v1))
                .as("v2 (rc1) should appear before v1 (beta2)")
                .isLessThan(0);
    }

    @Test
    public void multiple_qualifiers() {
        DependencyVersion v1 = DependencyVersion.of("1.0.0-alpha1.20230905", false);
        DependencyVersion v2 = DependencyVersion.of("1.0.0-alpha1.20230906", false);
        assertThat(v2.compareTo(v1))
                .as("v2 should appear before v1 based on later numerical suffix")
                .isLessThan(0);
    }

    @Test
    public void qualifier_with_no_number() {
        DependencyVersion v1 = DependencyVersion.of("1.0.0-rc", false);
        DependencyVersion v2 = DependencyVersion.of("1.0.0-rc1", false);
        assertThat(v2.compareTo(v1)).as("v2 (rc1) should appear before v1 (rc)").isLessThan(0);
    }

    @Test
    public void empty_version_string() {
        DependencyVersion v1 = DependencyVersion.of("", false);
        DependencyVersion v2 = DependencyVersion.of("1.0.0", false);
        assertThat(v2.compareTo(v1))
                .as("Valid version should appear before an empty version string")
                .isLessThan(0);
    }

    @Test
    public void version_with_non_standard_separator() {
        DependencyVersion v1 = DependencyVersion.of("1_0_0", false);
        DependencyVersion v2 = DependencyVersion.of("1.0.0", false);
        assertThat(v2.compareTo(v1))
                .as("Version with dots should appear before one with underscores")
                .isLessThan(0);
    }

    @Test
    public void version_with_invalid_characters() {
        DependencyVersion v1 = DependencyVersion.of("1.0.0!beta", false);
        DependencyVersion v2 = DependencyVersion.of("1.0.0-beta", false);
        assertThat(v2.compareTo(v1))
                .as("Valid qualifier should appear before version with invalid characters")
                .isLessThan(0);
    }

    @Test
    public void version_with_leading_separator() {
        DependencyVersion v1 = DependencyVersion.of(".1.0.0", false);
        DependencyVersion v2 = DependencyVersion.of("1.0.0", false);
        assertThat(v2.compareTo(v1))
                .as("Valid version should appear before one with a leading separator")
                .isLessThan(0);
    }

    @Test
    public void version_with_consecutive_separators() {
        DependencyVersion v1 = DependencyVersion.of("1..0.0", false);
        DependencyVersion v2 = DependencyVersion.of("1.0.0", false);
        assertThat(v2.compareTo(v1))
                .as("Valid version should appear before one with consecutive separators")
                .isLessThan(0);
    }

    @Test
    public void version_with_prefix_v() {
        DependencyVersion v1 = DependencyVersion.of("v1.0.0", false);
        DependencyVersion v2 = DependencyVersion.of("1.0.0", false);
        assertThat(v2.compareTo(v1))
                .as("Version without prefix 'v' should appear before one with the prefix")
                .isLessThan(0);
    }

    @Test
    public void version_with_uppercase_qualifiers() {
        DependencyVersion v1 = DependencyVersion.of("1.0.0-ALPHA", false);
        DependencyVersion v2 = DependencyVersion.of("1.0.0-alpha", false);
        assertThat(v1.compareTo(v2))
                .as("Qualifiers should be case-insensitive and versions should be equal")
                .isEqualTo(0);
    }

    @Test
    public void version_with_long_qualifier() {
        DependencyVersion v1 = DependencyVersion.of("1.0.0-alphaedition", false);
        DependencyVersion v2 = DependencyVersion.of("1.0.0-alpha", false);
        assertThat(v2.compareTo(v1))
                .as("Shorter, recognized qualifier should appear before longer, unrecognized qualifier")
                .isLessThan(0);
    }

    @Test
    public void version_with_numeric_only_qualifiers() {
        DependencyVersion v1 = DependencyVersion.of("1.0.0-123", false);
        DependencyVersion v2 = DependencyVersion.of("1.0.0-124", false);
        assertThat(v2.compareTo(v1))
                .as("Higher numeric qualifier should appear before lower numeric qualifier")
                .isLessThan(0);
    }

    @Test
    public void random_strings_for_versions_match() {
        DependencyVersion v1 = DependencyVersion.of("asdas", false);
        DependencyVersion v2 = DependencyVersion.of("jlkjhk", false);
        assertThat(v2.compareTo(v1))
                .as("Neither can be parsed so treated as equal")
                .isEqualTo(0);
    }

    @Test
    public void real_version_higher_than_random() {
        DependencyVersion v1 = DependencyVersion.of("asdas", false);
        DependencyVersion v2 = DependencyVersion.of("1.0.0", false);
        assertThat(v2.compareTo(v1))
                .as("v2 appears before than the random string of v1")
                .isLessThan(0);
    }
}