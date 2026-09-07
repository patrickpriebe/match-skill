package com.matchskill.backend.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SlugsTest {

    @ParameterizedTest(name = "Slugify \"{0}\" should be \"{1}\"")
    @CsvSource({
        "Java, java",
        "JavaScript, javascript",
        "Java Script, java-script",
        "Spring Boot, spring-boot",
        "C++, c~2b~2b",
        "C#, c~23",
        "F#, f~23",
        "Notepad++, notepad~2b~2b",
        "C plus plus, c-plus-plus",
        "C sharp, c-sharp",
        "ABC++ course, abc~2b~2b-course",
        "C ++, c-~2b~2b",
        "Node.js, node-js",
        "Ruby on Rails, ruby-on-rails",
        "Développement Web, developpement-web",
        "São Paulo, sao-paulo",
        "  Trim Spaces  , trim-spaces",
        "Multiple---Hyphens, multiple-hyphens",
        "Special @#$% Chars, special-~23-chars",
        "日本語, 日本語",
        "Программирование, программирование",
        "--Leading-And-Trailing--, leading-and-trailing"
    })
    void shouldSlugifyVariousInputs(String input, String expected) {
        assertThat(Slugs.slugify(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Single character input")
    void shouldHandleSingleCharacter() {
        assertThat(Slugs.slugify("A")).isEqualTo("a");
        assertThat(Slugs.slugify("9")).isEqualTo("9");
    }

    @ParameterizedTest
    @ValueSource(strings = {"---", "   ", "@@@", "++", "#", "🎨", "\u0301"})
    @DisplayName("Input with only special characters or separators yields empty string")
    void shouldHandleOnlySpecialCharacters(String input) {
        assertThat(Slugs.slugify(input)).isEmpty();
    }

    @Test
    void foldsCaseAndAccentsIndependentlyOfMachineLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(Slugs.slugify("PIANO")).isEqualTo("piano");
            assertThat(Slugs.identityKey("DÉVELOPPEMENT Web"))
                    .isEqualTo(Slugs.identityKey("developpement---web"));
            assertThat(Slugs.identityKey("Cafe\u0301")).isEqualTo(Slugs.identityKey("Café"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void keepsLegacyLookupSeparateAndDoesNotAliasEscapesWithLiteralWords() {
        assertThat(Slugs.legacySlug("C++")).isEqualTo("c");
        assertThat(Slugs.legacySlug("C#")).isEqualTo("c");
        assertThat(Slugs.identityKey("C++")).isNotEqualTo(Slugs.identityKey("C plus plus"));
        assertThat(Slugs.identityKey("C#")).isNotEqualTo(Slugs.identityKey("C sharp"));
        assertThat(Slugs.identityKey("C++")).isNotEqualTo(Slugs.identityKey("c~2b~2b"));
    }

    @Test
    void boundsLongSymbolSlugsWithoutLosingIdentity() {
        String name = "A" + "+".repeat(99);
        assertThat(Slugs.slugify(name)).hasSizeLessThanOrEqualTo(255);
        assertThat(Slugs.slugify(name)).endsWith(Slugs.identityKey(name));
        assertThat(Slugs.slugify(name)).isNotEqualTo(Slugs.slugify("A" + "+".repeat(98) + "#"));
    }
}
