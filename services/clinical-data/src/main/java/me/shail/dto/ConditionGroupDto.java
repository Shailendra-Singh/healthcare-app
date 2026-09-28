package me.shail.dto;

import me.shail.model.ConditionGroup;

public record ConditionGroupDto(Short id, String icdPrefix, String conditionName, boolean chronic) {

    public static ConditionGroupDto from(ConditionGroup group) {
        return group == null ? null
                : new ConditionGroupDto(group.id, group.icdPrefix, group.conditionName, group.chronic);
    }
}
