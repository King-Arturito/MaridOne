package org.example.maridone.document;

import org.example.maridone.document.path.DocumentPath;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentPathRepository extends JpaRepository<DocumentPath, Long>, JpaSpecificationExecutor<DocumentPath> {

    @Query("select dp.username.username from DocumentPath dp where dp.pathId = :pathId")
    Optional<String> findUsernameByPathId(@Param("pathId") Long pathId);

    @Modifying
    @Query(value = "UPDATE document_path SET username = :newUsername WHERE username = :oldUsername", nativeQuery = true)
    void reassignUsername(@Param("oldUsername") String oldUsername, @Param("newUsername") String newUsername);
}
