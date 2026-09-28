package me.shail.dto;

import me.shail.model.Specialty;

public record SpecialtyDto(Short id, String name) {

    public static SpecialtyDto from(Specialty specialty) {
        return specialty == null ? null : new SpecialtyDto(specialty.id, specialty.name);
    }
}
