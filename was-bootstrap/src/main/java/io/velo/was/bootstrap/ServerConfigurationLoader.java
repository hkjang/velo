package io.velo.was.bootstrap;

import io.velo.was.config.ServerConfiguration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ServerConfigurationLoader {

    private ServerConfigurationLoader() {
    }

    public static ServerConfiguration load(Path path) throws IOException {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(false);

        Constructor constructor = new Constructor(ServerConfiguration.class, loaderOptions);
        constructor.addTypeDescription(new TypeDescription(ServerConfiguration.class));

        Yaml yaml = new Yaml(constructor);
        try (InputStream inputStream = Files.newInputStream(path)) {
            ServerConfiguration configuration = yaml.loadAs(inputStream, ServerConfiguration.class);
            if (configuration == null) {
                throw new IllegalArgumentException("Configuration file is empty: " + path);
            }
            configuration.validate();
            return configuration;
        }
    }
}

