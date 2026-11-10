package dev.brokoli.task.processor;

import dev.brokoli.task.processor.TestCompiler.Source;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Set;

@SupportedAnnotationTypes("*")
final class SourceGenerator extends AbstractProcessor {

    private final Source source;
    private boolean generated;

    SourceGenerator(Source source) {
        this.source = source;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        if (generated) {
            return false;
        }
        generated = true;
        try (Writer out = processingEnv.getFiler().createSourceFile(source.qualifiedName()).openWriter()) {
            out.write(source.code());
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
        return false;
    }
}
