package org.example.maridone.exception;
import org.example.maridone.enums.ErrorCode;
import org.example.maridone.exception.badrequest.*;
import org.example.maridone.exception.dto.ErrorResponseDto;
import org.example.maridone.exception.notfound.*;
import org.example.maridone.exception.unauthorized.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponseDto> handleBaseException(BaseException ex) {

        ErrorCode code = ex.getErrorCode();

        return ResponseEntity
                .status(code.getStatus())
                .body(new ErrorResponseDto(code, ex.getMessage()));
    }

    // Spring / framework exceptions you don't control
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponseDto> handleMaxUpload(MaxUploadSizeExceededException ex) {
        ErrorCode code = ErrorCode.MAX_UPLOAD_SIZE_EXCEEDED;

        return ResponseEntity
                .status(code.getStatus())
                .body(new ErrorResponseDto(code, "Max upload size exceeded"));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponseDto> handleMultipart(MultipartException ex) {
        ErrorCode code = ErrorCode.FILE_TOO_LARGE;

        return ResponseEntity
                .status(code.getStatus())
                .body(new ErrorResponseDto(code, "File upload error"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgument(IllegalArgumentException ex) {
        ErrorCode code = ErrorCode.INVALID_ACTION;

        return ResponseEntity
                .status(code.getStatus())
                .body(new ErrorResponseDto(code, ex.getMessage()));
    }

    // fallback
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleUnexpected(Exception ex) {
        ErrorCode code = ErrorCode.INTERNAL_ERROR;

        return ResponseEntity
                .status(code.getStatus())
                .body(new ErrorResponseDto(
                        code,
                        ex.getMessage() != null ? ex.getMessage() : "Unexpected server error"
                ));
    }
}