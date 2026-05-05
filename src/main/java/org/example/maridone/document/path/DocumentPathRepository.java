package org.example.maridone.document.path;

import org.example.maridone.document.dto.DocumentResponseDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentPathRepository extends JpaRepository<DocumentPath, Long>, JpaSpecificationExecutor<DocumentPath> {

    @Query("select dp.userAccount.username from DocumentPath dp where dp.pathId = :pathId")
    Optional<String> findUsernameByPathId(@Param("pathId") Long pathId);

    @Query("select new " +
            "org.example.maridone.document.dto.DocumentResponseDto(" +
            "dp.pathId, dp.fileName, dp.fileType, dp.fileSizeBytes, dp.createdAt, dp.updatedAt, dp.isPublic" +
            ") from DocumentPath dp where dp.userAccount.username = :username")
    List<DocumentResponseDto> findAllByUsername(@Param("username") String username);
}