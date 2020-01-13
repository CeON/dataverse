package edu.harvard.iq.dataverse.annotations.processors;


import edu.harvard.iq.dataverse.annotations.PermissionNeeded;
import edu.harvard.iq.dataverse.interceptors.Restricted;
import edu.harvard.iq.dataverse.persistence.DvObject;
import edu.harvard.iq.dataverse.persistence.user.Permission;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Option;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;


public class PermissionDataProcessor {

    // -------------------- LOGIC --------------------

    public Set<RestrictedObject> gatherPermissionRequirements(Method method, Object[] parameterValues) {
        if (method == null || parameterValues == null || parameterValues.length == 0) {
            return Collections.emptySet();
        }

        Map<String, PermissionNeeded> permissionMap = createPermissionMap(extractAnnotations(method));

        BiConsumer<Set<RestrictedObject>, Tuple2<String, DvObject>> updateResultWithParameter = (result, param) -> {
                PermissionNeeded config = permissionMap.computeIfAbsent(param._1,
                        s -> { throw new IllegalStateException("No permission data for name: " + s); });
                DvObject object = param._2;
                result.add(createRestrictedObject(object, config));
                if (config.needsOnOwner().length > 0) {
                    result.add(createRestrictedOwnerObject(object, config));
                }
        };

        return createNamedParameterStream(method, parameterValues)
                .collect(HashSet::new, updateResultWithParameter, Set::addAll);
    }

    // -------------------- PRIVATE --------------------

    private Map<String, PermissionNeeded> createPermissionMap(PermissionNeeded[] configuration) {
        return Arrays.stream(configuration)
                .collect(Collectors.toMap(PermissionNeeded::on, Function.identity()));
    }

    private PermissionNeeded[] extractAnnotations(Method method) {
        return Option.of(method.getAnnotation(Restricted.class))
                .map(Restricted::value)
                .getOrElse(new PermissionNeeded[0]);
    }

    private RestrictedObject createRestrictedObject(DvObject object, PermissionNeeded config) {
        return RestrictedObject.of(config.on(), object, toSet(config.needs()), config.allRequired());
    }

    private RestrictedObject createRestrictedOwnerObject(DvObject object, PermissionNeeded config) {
        DvObject owner = object != null ? object.getOwner() : null;
        return RestrictedObject.of(config.on() + "_owner", owner, toSet(config.needsOnOwner()), config.allRequired());
    }

    private Set<Permission> toSet(Permission[] array) {
        return Arrays.stream(array)
                .collect(Collectors.toSet());
    }

    private Stream<Tuple2<String, DvObject>> createNamedParameterStream(Method method, Object[] parameterValues) {
        Annotation[][] annotations = method.getParameterAnnotations();
        if (annotations.length < parameterValues.length) {
            throw new IllegalStateException("Mismatch between annotation and parameter array size: too few parameters. " +
                    "Cannot properly process annotation.");
        }

        return IntStream.range(0, parameterValues.length)
                .mapToObj(i -> Tuple.of(annotations[i], parameterValues[i]))
                .map(t -> Tuple.of(extractName(t._1), t._2))
                .filter(t -> !Name.EMPTY.equals(t._1))
                .map(t -> Tuple.of(t._1.name, (DvObject) t._2));
    }

    private Name extractName(Annotation[] annotations) {
        return Arrays.stream(annotations)
                .filter(a -> a instanceof PermissionNeeded)
                .map(PermissionNeeded.class::cast)
                .map(PermissionNeeded::value)
                .map(Name::new)
                .findFirst()
                .orElse(Name.EMPTY);
    }

    // -------------------- INNER CLASSES --------------------

    private static class Name {
        public final String name;

        public static final Name EMPTY = new Name(null);

        public Name(String name) {
            this.name = name;
        }
    }
}
