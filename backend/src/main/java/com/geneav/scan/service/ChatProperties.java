package com.geneav.scan.service;

import com.geneav.scan.dto.ChatSuggestion;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Chat settings that are content rather than transport — currently the canned
 * question/answer pairs offered as chips in the widget.
 *
 * <p>They live in configuration, not code, so a wrong or stale answer is fixed by
 * editing {@code application.yml} and restarting rather than rebuilding, the same
 * arrangement the plan catalog uses for its limits.
 */
@ConfigurationProperties(prefix = "geneav.chat")
public class ChatProperties {

    /** Predefined questions, in the order they are shown. Empty disables the chips. */
    private List<ChatSuggestion> suggestions = new ArrayList<>();

    public List<ChatSuggestion> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<ChatSuggestion> suggestions) {
        this.suggestions = suggestions;
    }
}
