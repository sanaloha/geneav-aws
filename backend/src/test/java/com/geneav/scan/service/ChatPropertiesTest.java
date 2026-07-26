package com.geneav.scan.service;

import com.geneav.scan.dto.ChatSuggestion;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binds the real application.yml, so a typo in the suggestion list fails the build
 * rather than shipping a blank chip or a chip with no answer behind it.
 */
class ChatPropertiesTest {

    private final ChatProperties props = bindFromApplicationYml();

    private static ChatProperties bindFromApplicationYml() {
        MutablePropertySources sources = new MutablePropertySources();
        try {
            List<PropertySource<?>> loaded = new YamlPropertySourceLoader()
                    .load("application.yml", new ClassPathResource("application.yml"));
            loaded.forEach(sources::addLast);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read application.yml", e);
        }
        return new Binder(ConfigurationPropertySources.from(sources))
                .bind("geneav.chat", ChatProperties.class)
                .orElseGet(ChatProperties::new);
    }

    @Test
    void shipsTheFivePredefinedQuestions() {
        assertThat(props.getSuggestions()).hasSize(5);
    }

    @Test
    void everySuggestionIsCompletelyPopulated() {
        assertThat(props.getSuggestions()).allSatisfy(s -> {
            assertThat(s.id()).isNotBlank();
            assertThat(s.question()).isNotBlank();
            assertThat(s.answer()).isNotBlank();
        });
    }

    @Test
    void idsAreUnique() {
        assertThat(props.getSuggestions()).extracting(ChatSuggestion::id).doesNotHaveDuplicates();
    }

    /** Answers render in a 380px-wide bubble; long essays belong to the model. */
    @Test
    void answersStayShortEnoughForTheWidget() {
        assertThat(props.getSuggestions()).allSatisfy(s -> assertThat(s.answer()).hasSizeLessThan(300));
    }
}
