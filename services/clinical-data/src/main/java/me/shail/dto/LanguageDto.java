package me.shail.dto;

import me.shail.model.Language;

public record LanguageDto(Short id, String name) {

    public static LanguageDto from(Language language) {
        return language == null ? null : new LanguageDto(language.id, language.name);
    }
}
