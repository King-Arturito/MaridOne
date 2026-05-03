package org.example.maridone.exception.notfound;

public class PathNotFoundException extends RuntimeException {
    public PathNotFoundException(Long pathId) {
        super("Document with id: " + pathId + " not found");
    }
}
