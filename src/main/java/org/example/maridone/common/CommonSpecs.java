package org.example.maridone.common;

import org.example.maridone.exception.InvalidRangeException;
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

    public static <T, V extends Comparable<? super V>> Specification<T> fieldIsBetween(String fieldName, V start, V end) {
        return (root, query, cb) -> {
            if (start == null || end == null) {
                return cb.conjunction();
            }
            if (start.compareTo(end) > 0) {
                throw new InvalidRangeException("start must be less than end.");
            }

            return cb.between(root.get(fieldName), start, end);
        };
    }

    public static <T, V extends Comparable<? super V>> Specification<T> checkOverlaps
            (String startFieldName, String endFieldName, V start, V end) {
        return (root, query, cb) -> {

            if (startFieldName == null || endFieldName == null) {
                return cb.conjunction();
            }
            return cb.and(
                    cb.lessThan(root.get(startFieldName), end),
                    cb.greaterThan(root.get(endFieldName), start)
            );
        };
    }
}
