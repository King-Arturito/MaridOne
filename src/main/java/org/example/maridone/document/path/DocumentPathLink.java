package org.example.maridone.document.path;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

@Embeddable
public class DocumentPathLink {
    @Column(name = "connected_entity")
    private String connectedEntity;

    @Column(name =  "connected_id")
    private Long connectedId;

    public String getConnectedEntity() {
        return connectedEntity;
    }

    public void setConnectedEntity(String connectedEntity) {
        this.connectedEntity = connectedEntity;
    }

    public Long getConnectedId() {
        return connectedId;
    }

    public void setConnectedId(Long connectedId) {
        this.connectedId = connectedId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DocumentPathLink that = (DocumentPathLink) o;
        return Objects.equals(connectedEntity, that.connectedEntity) &&
                Objects.equals(connectedId, that.connectedId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectedEntity, connectedId);
    }
}
