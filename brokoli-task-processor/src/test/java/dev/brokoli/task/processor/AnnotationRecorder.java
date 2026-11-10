package dev.brokoli.task.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.util.Set;
import java.util.TreeSet;

@SupportedAnnotationTypes("*")
final class AnnotationRecorder extends AbstractProcessor {

    private final Set<String> seen = new TreeSet<>();

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        annotations.forEach(annotation -> seen.add(annotation.getQualifiedName().toString()));
        return false;
    }

    Set<String> seen() {
        return seen;
    }
}
