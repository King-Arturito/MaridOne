package org.example.maridone.common;

import org.springframework.data.jpa.domain.Specification;

public class CommonSpecs {

    public static <T> Specification<T> fieldEquals(String fieldName, Object value) {

        return (root, query, cb) -> {
            if (value == null) {
                return cb.conjunction();
            }

            return cb.equal(root.get(fieldName), value);
        };
    }

    public static <T> Specification<T> fieldNotEquals(String fieldName, Object value) {
        return (root, query, cb) -> {
            if (value == null) {
                return cb.conjunction();
            }
            return cb.notEqual(root.get(fieldName), value);
        };
    }

    public static <T, V extends Comparable<? super V>> Specification<T> fieldLessThan(String fieldName, V value) {
        return (root, query, cb) -> {
            if (value == null) {
                return cb.conjunction();
            }

            return cb.lessThan(root.get(fieldName), value);
        };
    }

    public static <T, V extends Comparable<? super V>> Specification<T> fieldGreaterThan(String fieldName, V value) {
        return (root, query, cb) -> {
            if (value == null) {
                return cb.conjunction();
            }

            return cb.greaterThan(root.get(fieldName), value);
        };
    }

    public static <T, V extends Comparable<? super V>> Specification<T> fieldLessThanOrEqual(String fieldName, V value) {
        return (root, query, cb) -> {
            if (value == null) {
                return cb.conjunction();
            }

            return cb.lessThanOrEqualTo(root.get(fieldName), value);
        };
    }

    public static <T, V extends Comparable<? super V>> Specification<T> fieldGreaterThanOrEqual(String fieldName, V value) {
        return (root, query, cb) -> {
            if (value == null) {
                return cb.conjunction();
            }

            return cb.greaterThanOrEqualTo(root.get(fieldName), value);
        };
    }
}
