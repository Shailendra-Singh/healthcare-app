package me.shail.repository;

import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import java.util.List;
import me.shail.model.TaskType;

@Repository
public interface TaskTypeRepository {

    @Query("from TaskType order by code")
    List<TaskType> findAll();
}
