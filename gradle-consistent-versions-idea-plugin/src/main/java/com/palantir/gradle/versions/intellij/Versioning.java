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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = ImmutableVersioning.class)
@JsonSerialize(as = ImmutableVersioning.class)
@JsonIgnoreProperties(ignoreUnknown = true)
interface Versioning {
    @Value.Parameter
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "latest")
    String latest();

    @Value.Parameter
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "release")
    String release();

    @Value.Parameter
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "lastUpdated")
    String lastUpdated();

    @Value.Parameter
    @JacksonXmlElementWrapper(localName = "versions")
    @JacksonXmlProperty(localName = "version")
    String[] versions();
}
