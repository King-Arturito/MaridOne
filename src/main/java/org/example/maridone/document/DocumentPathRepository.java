package org.example.maridone.document;

import org.example.maridone.document.path.DocumentPath;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentPathRepository extends JpaRepository<DocumentPath, Long> {

    @Query("select dp.username from DocumentPath dp where dp.pathId = :pathId")
    Optional<String> findUsernameByPathId(@Param("pathId") Long pathId);
}
