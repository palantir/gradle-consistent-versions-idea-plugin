package com.palantir.gradle.versions.intellij;

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

    // Compare in descending order e.g. higher versions are lower so they appear at the top.
    @Override
    public final int compareTo(DependencyVersion other) {
        return VersionComparator.compare(this, other);
    }
}