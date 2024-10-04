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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class VersionPropsLexerTests extends LightJavaCodeInsightFixtureTestCase5 {

    @Override
    protected final String getRelativePath() {
        return "";
    }

    @Override
    protected final String getTestDataPath() {
        return "";
    }

    @BeforeAll
    public static void setup() throws IOException {
        Optional<String> inCi = Optional.ofNullable(System.getenv("CI"));

        if (!inCi.equals(Optional.of("true"))) {
            Path directoryToDelete = Paths.get("src/test/resources/lexerTests");
            if (Files.exists(directoryToDelete)) {
                deleteDirectoryRecursively(directoryToDelete);
            }
        }
    }

    private static void deleteDirectoryRecursively(Path path) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    deleteDirectoryRecursively(entry);
                } else {
                    Files.delete(entry);
                }
            }
        }
        Files.delete(path);
    }

    @ValueSource(
            strings = {
                "a.normal:example = 1",
                "random.characters:after = version blah blah",
                "a.normal:example = 1",
                "has.end.of:line=comments#here is the comment",
                "odd/.characters@?'.in!:all\\\\/ = the_pl4ces.,?",
                "has.end.of:line=comments # here is the comment",
                " has.extra.space:bad_token = 1",
                "has . space . around:dots = 1",
                "# A comment",
                "has.spaces.around : colon = 1",
                "has.no.spaces.around:equals=1",
                "has.loads.of.spaces:around   =   equals",
                "    # comment after spaces"
            })
    @ParameterizedTest
    public void test_psi_tree_structure(String input) throws Exception {
        JavaCodeInsightTestFixture fixture = getFixture();
        fixture.configureByText("versions.props", input);

        Optional<String> inCi = Optional.ofNullable(System.getenv("CI"));
        ApplicationManager.getApplication().runReadAction(() -> {
            PsiFile psiFile = fixture.getFile();
            String tokenString = convertPsiToString(psiFile);

            if (inCi.equals(Optional.of("true"))) {
                String expectedTokens = loadExpectedTokens(input);
                assertThat(tokenString).isEqualTo(expectedTokens);
            } else {
                saveActualTokens(input, tokenString);
            }
        });
    }

    private String convertPsiToString(PsiFile psiFile) {
        StringBuilder tokenStringBuilder = new StringBuilder();
        psiFile.accept(new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(PsiElement element) {
                super.visitElement(element);
                tokenStringBuilder
                        .append(element.getNode().getElementType())
                        .append(" ")
                        .append(element.getNode().getText())
                        .append("\n");
            }
        });
        return tokenStringBuilder.toString().trim();
    }

    private String loadExpectedTokens(String fileName) {
        try (BufferedReader reader = Files.newBufferedReader(getOutputFilePath(fileName), StandardCharsets.UTF_8)) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void saveActualTokens(String fileName, String token) {
        try (BufferedWriter writer = Files.newBufferedWriter(getOutputFilePath(fileName), StandardCharsets.UTF_8)) {
            writer.write(token);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Path getOutputFilePath(String input) throws IOException {
        String fileName = hashToFolderName(input);
        Path outputPath = Paths.get("src/test/resources/lexerTests/" + fileName);
        Files.createDirectories(outputPath.getParent());
        return outputPath;
    }

    private static String hashToFolderName(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
