package dev.codebasedoctor.api;
import dev.codebasedoctor.config.Redactor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.*;
@RestControllerAdvice
public class ApiErrors {
 private final Redactor redactor;
 public ApiErrors(Redactor redactor){this.redactor=redactor;}
 @ExceptionHandler(IllegalArgumentException.class)ResponseEntity<?> bad(IllegalArgumentException e){return error(400,e.getMessage());}
 @ExceptionHandler(NoSuchElementException.class)ResponseEntity<?> missing(){return error(404,"Job not found.");}
 @ExceptionHandler(IllegalStateException.class)ResponseEntity<?> conflict(IllegalStateException e){return error(409,e.getMessage());}
 @ExceptionHandler(HttpMessageNotReadableException.class)ResponseEntity<?> malformed(){return error(400,"A valid JSON request is required.");}
 private ResponseEntity<?> error(int status,String message){return ResponseEntity.status(status).body(Map.of("error",redactor.clean(message)));}
}
