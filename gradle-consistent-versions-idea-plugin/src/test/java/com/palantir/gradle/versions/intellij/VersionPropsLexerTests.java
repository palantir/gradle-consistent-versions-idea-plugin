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

import com.google.common.hash.Hashing;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class VersionPropsLexerTests extends LightJavaCodeInsightFixtureTestCase5 {

    private static String basePath = "src/test/resources/lexerTests/";

    @Override
    protected final String getRelativePath() {
        return "";
    }

    @Override
    protected final String getTestDataPath() {
        return "";
    }

    private static Stream<Set<String>> provideTestCases() {
        return Stream.of(Set.of(
                "a.normal:example = 1",
                "random.characters:after = version blah blah",
                "has.end.of:line=comments#here is the comment",
                "odd/.characters@?'.in!:all\\\\/ = the_pl4ces.,?",
                "has.end.of:line=comments # here is the comment",
                " has.extra.space:bad_token = 1",
                "has . space . around:dots = 1",
                "# A comment",
                "has.spaces.around : colon = 1",
                "has.no.spaces.around:equals=1",
                "has.loads.of.spaces:around   =   equals",
                "    # comment after spaces"));
    }

    @ParameterizedTest
    @MethodSource("provideTestCases")
    public void test_psi_tree_structure(Set<String> input) throws Exception {
        JavaCodeInsightTestFixture fixture = getFixture();
        Optional<String> inCi = Optional.ofNullable(System.getenv("CI"));

        // If we are running on remote check all the expected test files are checked in else clean the test directory
        if (inCi.equals(Optional.of("true"))) {
            check_tests_match_input(input);
        } else {
            deleteOldTests(Paths.get(basePath));
        }
        input.forEach(inputItem -> {
            fixture.configureByText("versions.props", inputItem);
            ApplicationManager.getApplication().runReadAction(() -> {
                PsiFile psiFile = fixture.getFile();
                String tokenString = convertPsiToString(psiFile);

                if (inCi.equals(Optional.of("true"))) {
                    String expectedTokens = loadExpectedTokens(inputItem);
                    assertThat(inputItem + "\n" + tokenString).isEqualTo(expectedTokens);
                } else {
                    saveActualTokens(inputItem, tokenString);
                }
            });
        });
    }

    private void check_tests_match_input(Set<String> input) {
        File baseDir = new File(basePath);
        File[] allFiles = baseDir.listFiles();

        if (allFiles == null) {
            throw new RuntimeException("Base directory does not exist or is not a directory.");
        }

        Set<String> inputFileNames = input.stream().map(this::hashToFolderName).collect(Collectors.toSet());

        Set<String> baseDirFileNames =
                Arrays.stream(allFiles).map(File::getName).collect(Collectors.toSet());

        for (String fileName : inputFileNames) {
            assertThat(baseDirFileNames)
                    .describedAs(
                            "Expected file %s does not exist in the base directory. Run tests locally to fix.",
                            fileName)
                    .contains(fileName);
        }

        for (String fileName : baseDirFileNames) {
            assertThat(inputFileNames)
                    .describedAs("Unexpected file %s exists in the base directory. Run tests locally to fix.", fileName)
                    .contains(fileName);
        }
    }

    private static void deleteOldTests(Path path) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    deleteOldTests(entry);
                    Files.delete(entry);
                } else {
                    Files.delete(entry);
                }
            }
        }
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

    private void saveActualTokens(String input, String token) {
        try (BufferedWriter writer = Files.newBufferedWriter(getOutputFilePath(input), StandardCharsets.UTF_8)) {
            writer.write(input + "\n" + token);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Path getOutputFilePath(String input) throws IOException {
        String fileName = hashToFolderName(input);
        Path outputPath = Paths.get(basePath + fileName);
        Files.createDirectories(outputPath.getParent());
        return outputPath;
    }

    private String hashToFolderName(String input) {
        return Hashing.sha256().hashString(input, StandardCharsets.UTF_8).toString();
    }
}
