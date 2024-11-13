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

import com.google.common.io.CharStreams;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ContentsUtil {
    private static final Logger log = LoggerFactory.getLogger(ContentsUtil.class);

    public static ContentResults fetchPageContents(String urlString) {
        try {
            URL url = new URL(urlString);
            return ContentsUtil.fetchPageContents(url);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public static ContentResults fetchPageContents(URL pageUrl) {
        try {
            return fetchContentTask(pageUrl).call();
        } catch (Exception e) {
            log.warn("Failed to fetch contents", e);
            return ContentResults.empty();
        }
    }

    private static Callable<ContentResults> fetchContentTask(URL pageUrl) {
        return () -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) pageUrl.openConnection();
                connection.setRequestMethod("GET");

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return ContentResults.error(responseCode);
                }

                InputStreamReader inputStreamReader =
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8);
                return ContentResults.of(CharStreams.toString(inputStreamReader));
            } catch (ConnectException e) {
                log.debug("Connection refused on page {}", pageUrl, e);
                return ContentResults.empty();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        };
    }

    private ContentsUtil() {}

    @Value.Immutable
    public abstract static class ContentResults {

        protected abstract String content();

        protected abstract Integer responseCode();

        public final boolean isEmpty() {
            return (content() == null || content().isEmpty()) && responseCode() == HttpURLConnection.HTTP_OK;
        }

        public final boolean isError() {
            return responseCode() != HttpURLConnection.HTTP_OK;
        }

        public static ContentResults of(String content) {
            return ImmutableContentResults.builder()
                    .content(content)
                    .responseCode(HttpURLConnection.HTTP_OK)
                    .build();
        }

        public static ContentResults error(Integer responseCode) {
            return ImmutableContentResults.builder()
                    .content("")
                    .responseCode(responseCode)
                    .build();
        }

        public static ContentResults empty() {
            return ContentResults.of("");
        }
    }
}
