package me.yin.simplescript;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class SimpleScriptLoader implements PluginLoader {

    @Override
    public void classloader(@NotNull PluginClasspathBuilder classpathBuilder) {
        var resolver = new MavenLibraryResolver();

        resolver.addRepository(
                new RemoteRepository.Builder(
                        "central",
                        "default",
                        MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR
                ).build()
        );

        var inputStream = SimpleScriptLoader.class.getClassLoader().getResourceAsStream("libraries.txt");
        if (inputStream == null) {
            throw new IllegalStateException("Missing libraries.txt in plugin jar");
        }

        try (var reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            reader.lines()
                    .filter(line -> !line.isBlank())
                    .forEach(line -> resolver.addDependency(new Dependency(new DefaultArtifact(line), null)));
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        classpathBuilder.addLibrary(resolver);
    }
}
