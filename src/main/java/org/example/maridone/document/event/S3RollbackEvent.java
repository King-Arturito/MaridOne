package org.example.maridone.document.event;

import java.util.List;

public record S3RollbackEvent(List<String> s3Keys) { }
