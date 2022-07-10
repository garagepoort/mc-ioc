package be.garagepoort.mcioc.configuration;

import be.garagepoort.mcioc.IocException;
import be.garagepoort.mcioc.ReflectionUtils;
import be.garagepoort.mcioc.configuration.yaml.configuration.file.FileConfiguration;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PropertyInjector {
    public static void injectConfigurationProperties(Object bean, Map<String, FileConfiguration> configs) {
        setProperties(configs, bean);
    }

    private static void setProperties(Map<String, FileConfiguration> configs, Object o) {
        try {
            for (Field f : getAllFields(new LinkedList<>(), o.getClass())) {
                if (!f.isAnnotationPresent(ConfigProperty.class)) {
                    continue;
                }
                ConfigTransformer configTransformer = null;
                if (f.isAnnotationPresent(ConfigTransformer.class)) {
                    configTransformer = f.getAnnotation(ConfigTransformer.class);
                }
                ConfigProperty annotation = f.getAnnotation(ConfigProperty.class);
                Optional<Object> parsedConfigValue = parseConfig(f.getType(), annotation, configTransformer, configs);
                if (parsedConfigValue.isPresent()) {
                    f.setAccessible(true);
                    f.set(o, parsedConfigValue.get());
                }
            }
        } catch (IllegalAccessException e) {
            throw new IocException("Cannot inject property. Make sure the field is public", e);
        }
    }

    public static <T> Optional<T> parseConfig(Class type, ConfigProperty configAnnotation, ConfigTransformer configTransformer, Map<String, FileConfiguration> configs) {
        try {
            Optional<T> configValue = ReflectionUtils.getConfigValue(configAnnotation.value(), configs);
            if (!configValue.isPresent()) {
                return Optional.empty();
            }
            if (configTransformer != null) {
                Object transformedConfig = configValue.get();
                for (Class<? extends IConfigTransformer> transformerClass : configTransformer.value()) {
                    Constructor<?> declaredConstructor = transformerClass.getDeclaredConstructors()[0];
                    IConfigTransformer iConfigTransformer;
                    Class<?>[] parameterTypes = declaredConstructor.getParameterTypes();
                    if (parameterTypes.length == 1) {
                        iConfigTransformer = (IConfigTransformer) declaredConstructor.newInstance(type);
                    } else if (parameterTypes.length == 0) {
                        iConfigTransformer = (IConfigTransformer) declaredConstructor.newInstance();
                    } else {
                        throw new IocException("Invalid IConfigTransformer. Invalid constructor");
                    }
                    setProperties(configs, iConfigTransformer);
                    transformedConfig = iConfigTransformer.mapConfig(transformedConfig);
                }
                return (Optional<T>) Optional.ofNullable(transformedConfig);
            } else {
                return configValue;
            }
        } catch (InstantiationException | InvocationTargetException | IllegalAccessException e) {
            throw new IocException("Cannot create configtransformer", e);
        }
    }

    private static List<Field> getAllFields(List<Field> fields, Class<?> type) {
        fields.addAll(Arrays.asList(type.getDeclaredFields()));

        if (type.getSuperclass() != null) {
            getAllFields(fields, type.getSuperclass());
        }

        return fields;
    }

    public static Enum getInstance(final String value, final Class enumClass) {
        return Enum.valueOf(enumClass, value);
    }
}