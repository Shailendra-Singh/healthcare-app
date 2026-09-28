package me.shail.dto;

import me.shail.model.Provider;

public record ProviderDto(Long id, String name) {

    public static ProviderDto from(Provider provider) {
        return provider == null ? null : new ProviderDto(provider.id, provider.name);
    }
}
