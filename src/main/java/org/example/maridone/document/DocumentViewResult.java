package org.example.maridone.document;

import org.springframework.core.io.Resource;

public record DocumentViewResult(Resource file, String fileName, String fileType) {}