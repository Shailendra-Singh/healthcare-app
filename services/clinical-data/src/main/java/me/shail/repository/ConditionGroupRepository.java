package me.shail.repository;

import io.quarkus.data.hibernate.RecordRepository;
import io.smallrye.mutiny.Uni;
import jakarta.data.repository.Find;
import jakarta.data.repository.OrderBy;
import java.util.List;
import me.shail.model.ConditionGroup;

public interface ConditionGroupRepository extends RecordRepository.Reactive.CustomId<ConditionGroup, Short> {

    @Find
    @OrderBy("icdPrefix")
    Uni<List<ConditionGroup>> findAllOrderedByPrefix();

    @Find
    Uni<ConditionGroup> findByIcdPrefix(String icdPrefix);
}
