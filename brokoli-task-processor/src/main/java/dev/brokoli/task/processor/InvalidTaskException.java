package dev.brokoli.task.processor;

import javax.lang.model.element.Element;

final class InvalidTaskException extends RuntimeException {

    private final transient Element element;

    InvalidTaskException(String message, Element element) {
        super(message);
        this.element = element;
    }

    Element getElement() {
        return element;
    }
}
