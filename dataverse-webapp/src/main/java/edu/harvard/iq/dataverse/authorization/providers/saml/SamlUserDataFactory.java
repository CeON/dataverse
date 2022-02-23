package edu.harvard.iq.dataverse.authorization.providers.saml;

import com.onelogin.saml2.Auth;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class SamlUserDataFactory {
    private static final String[] NAME = new String[] {"urn:oid:2.5.4.42", "firstName", "givenName", "cn"};
    private static final String[] SURNAME = new String[] {"urn:oid:2.5.4.4", "lastName", "surname", "sn"};
    private static final String[] EMAIL = new String[] {"urn:oid:1.2.840.113549.1.9.1", "email", "mail"};

    // -------------------- LOGIC --------------------

    public static SamlUserData create(Auth auth) {
        if (auth == null) {
            throw new IllegalArgumentException("Auth object cannot be null");
        }
        SamlUserData userData = new SamlUserData();
        userData.setId(auth.getNameId());
        userData.setIdpEntityId(auth.getSettings().getIdpEntityId());
        userData.setRawData(new HashMap<>(auth.getAttributes()));
        tryToSetAdditionalUserData(userData, auth.getAttributes());
        return userData;
    }

    // -------------------- PRIVATE --------------------

    private static void tryToSetAdditionalUserData(SamlUserData userData, Map<String, List<String>> attributes) {
        setIfNotEmpty(userData::setName, checkForKeys(attributes, NAME));
        setIfNotEmpty(userData::setSurname, checkForKeys(attributes, SURNAME));
        setIfNotEmpty(userData::setEmail, checkForKeys(attributes, EMAIL));
    }

    private static void setIfNotEmpty(Consumer<String> setter, List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        setter.accept(values.get(0));
    }

    private static List<String> checkForKeys(Map<String, List<String>> map, String[] keys) {
        return Arrays.stream(keys)
                .map(map::get)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }
}
