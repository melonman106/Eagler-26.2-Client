package net.lax1dude.eaglercraft.v1_8.teavmcompat.dfu.plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.teavm.classlib.ReflectionContext;
import org.teavm.classlib.ReflectionSupplier;
import org.teavm.model.MethodDescriptor;

/** Reads the generated closed class/method reflection allowlist. */
public final class EaglerDfuReflectionSupplier implements ReflectionSupplier {
    private static final Data DATA = load();

    @Override
    public Collection<String> getAccessibleFields(ReflectionContext context, String className) {
        return DATA.fields.getOrDefault(className, Collections.emptyList());
    }

    @Override
    public Collection<MethodDescriptor> getAccessibleMethods(ReflectionContext context, String className) {
        return DATA.methods.getOrDefault(className, Collections.emptyList());
    }

    @Override
    public boolean isClassFoundByName(ReflectionContext context, String className) {
        return DATA.classes.contains(className);
    }

    private static Data load() {
        InputStream stream = EaglerDfuReflectionSupplier.class.getResourceAsStream(
                "/META-INF/eagler/dfu-runtime-reflection.tsv");
        if (stream == null) throw new IllegalStateException("DFU proof reflection roots are absent");
        Data data = new Data();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                String[] part = line.split("\\t", 3);
                if (part.length < 2) throw new IllegalStateException("malformed DFU proof root: " + line);
                if (part[0].equals("C")) data.classes.add(part[1]);
                else if (part[0].equals("M")) {
                    if (part.length != 3) throw new IllegalStateException("malformed DFU proof method root: " + line);
                    data.methods.computeIfAbsent(part[1], ignored -> new ArrayList<>())
                            .add(MethodDescriptor.parse(part[2]));
                } else if (part[0].equals("F")) {
                    if (part.length != 3) throw new IllegalStateException("malformed DFU proof field root: " + line);
                    data.fields.computeIfAbsent(part[1], ignored -> new ArrayList<>()).add(part[2]);
                } else throw new IllegalStateException("unknown DFU proof reflection root: " + line);
            }
            return data;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static final class Data {
        final Set<String> classes = new HashSet<>();
        final Map<String, List<String>> fields = new HashMap<>();
        final Map<String, List<MethodDescriptor>> methods = new HashMap<>();
    }
}
