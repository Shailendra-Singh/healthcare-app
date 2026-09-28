package me.shail.repository;

import jakarta.data.repository.Insert;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import java.util.List;
import me.shail.model.TaskEvent;

@Repository
public interface TaskEventRepository {

    @Insert
    void insert(TaskEvent event);

    @Insert
    void insertAll(List<TaskEvent> events);

    @Query("from TaskEvent where taskId = :taskId order by id")
    List<TaskEvent> findByTask(Long taskId);
}
