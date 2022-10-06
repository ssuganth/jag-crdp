package ca.bc.gov.open.crdp.models;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MqErrorLog {
    private String message;
    private String method;
    private String exception;
    private Object request;
}
