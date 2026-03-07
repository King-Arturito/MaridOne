package org.example.maridone.component;


@FunctionalInterface
public interface CheckerInterface {
    boolean isSelf(Long id, String username);
}
