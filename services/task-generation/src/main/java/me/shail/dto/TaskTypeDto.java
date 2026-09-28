package me.shail.dto;

import me.shail.model.TaskType;

public record TaskTypeDto(String code, String name, String description) {

    public static TaskTypeDto from(TaskType type) {
        return new TaskTypeDto(type.code, type.name, type.description);
    }
}
