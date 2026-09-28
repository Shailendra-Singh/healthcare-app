package me.shail.dto;

import me.shail.model.LabTest;

public record LabTestDto(Short id, String name) {

    public static LabTestDto from(LabTest labTest) {
        return labTest == null ? null : new LabTestDto(labTest.id, labTest.name);
    }
}
