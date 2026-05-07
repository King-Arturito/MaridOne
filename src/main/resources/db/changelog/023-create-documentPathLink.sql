CREATE TABLE document_path_links (
    path_id BIGINT NOT NULL,
    connected_entity VARCHAR(255),
    connected_id BIGINT
);

CREATE INDEX idx_link_connected
    ON document_path_links (connected_entity, connected_id);

ALTER TABLE document_path_links
    ADD CONSTRAINT fk_doc_path_links_path_id
        FOREIGN KEY (path_id)
            REFERENCES document_path (path_id)
            ON DELETE CASCADE;