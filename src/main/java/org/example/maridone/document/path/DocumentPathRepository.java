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

    @Query("SELECT DISTINCT dp FROM DocumentPath dp LEFT JOIN FETCH dp.links WHERE dp.userAccount.username = :username")
    List<DocumentPath> findAllByUsername(@Param("username") String username);

    @Query("SELECT DISTINCT dp FROM DocumentPath dp LEFT JOIN FETCH dp.links WHERE dp.userAccount.username = :username AND (dp.isPublic = false OR dp.isPublic IS NULL)")
    List<DocumentPath> findPrivateByUsername(@Param("username") String username);

    @Query("SELECT dp FROM DocumentPath dp JOIN dp.links link WHERE link.connectedEntity = :entityType AND link.connectedId = :connectedId")
    List<DocumentPath> findDocumentsByEntityAndId(
            @Param("entityType") String entityType,
            @Param("connectedId") Long connectedId
    );

    @Query("select dp FROM DocumentPath dp JOIN dp.links link WHERE link.connectedEntity = :entityType AND link.connectedId = null")
    List<DocumentPath> findAllByEntity(@Param("entityType") String entityType);

    @Query("select count(l) > 0 from DocumentPath dp join dp.links l " +
            "where l.connectedEntity = :connectedEntity and l.connectedId = :connectedId")
    boolean existsByEntity(@Param("connectedEntity") String connectedEntity,
                               @Param("connectedId") Long connectedId);
}
