package dev.brokoli.task.processor;

import javax.lang.model.type.TypeMirror;

final class UnresolvedTypeException extends RuntimeException {

    UnresolvedTypeException(TypeMirror type) {
        super(type + " does not resolve yet");
    }
}
