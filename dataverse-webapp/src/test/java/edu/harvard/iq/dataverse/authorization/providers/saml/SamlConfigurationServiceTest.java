package edu.harvard.iq.dataverse.authorization.providers.saml;

import com.onelogin.saml2.settings.Saml2Settings;
import edu.harvard.iq.dataverse.persistence.user.SamlIdentityProvider;
import edu.harvard.iq.dataverse.persistence.user.SamlIdentityProviderRepository;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SamlConfigurationServiceTest {

    @Mock
    private SettingsServiceBean settingsService;

    @Mock
    private SamlIdentityProviderRepository samlIdpRepository;

    private SamlConfigurationService service;

    private Map<String, Object> idpSettings = new HashMap<>();

    @BeforeEach
    void setUp() {
        idpSettings.clear();
        service = new SamlConfigurationService(settingsService, samlIdpRepository, (metadataUrl, idpEntityId) -> {
            idpSettings.put("onelogin.saml2.idp.entityid", idpEntityId);
            return idpSettings;
        });
    }

    // -------------------- TESTS --------------------

    @Test
    void buildSettings__fromProvider() {
        // given
        SamlIdentityProvider provider = new SamlIdentityProvider(1L, "idpEntityId", "url", "name");
        mockSpSettings();

        // when
        Saml2Settings settings = service.buildSettings(provider);

        // then
        assertThat(settings)
                .extracting(Saml2Settings::getSpEntityId, Saml2Settings::getIdpEntityId)
                .containsExactly("spEntityId", "idpEntityId");
    }

    @Test
    void buildSettings__formProviderId() {
        // given
        SamlIdentityProvider provider = new SamlIdentityProvider(1L, "idpEntityId", "url", "name");
        when(samlIdpRepository.findByEntityId("idpEntityId")).thenReturn(provider);
        mockSpSettings();

        // when
        Saml2Settings settings = service.buildSettings("idpEntityId");

        // then
        assertThat(settings)
                .extracting(Saml2Settings::getSpEntityId, Saml2Settings::getIdpEntityId)
                .containsExactly("spEntityId", "idpEntityId");
    }

    @Test
    void buildSpSettings() {
        // given
        mockSpSettings();

        // when
        Saml2Settings settings = service.buildSpSettings();

        // then
        assertThat(settings.getSpEntityId()).isEqualTo("spEntityId");
    }

    @Test
    void getRegisteredProviders() {
        // given
        when(samlIdpRepository.findAll()).thenReturn(Arrays.asList(
                new SamlIdentityProvider(1L, "eid1", "url1", "dn1"),
                new SamlIdentityProvider(2L, "eid2", "url2", "dn2")));

        // when
        List<SamlIdentityProvider> registeredProviders = service.getRegisteredProviders();

        // then
        assertThat(registeredProviders).extracting(SamlIdentityProvider::getId, SamlIdentityProvider::getMetadataUrl)
                .containsExactly(
                        tuple(1L, "url1"),
                        tuple(2L, "url2"));
    }

    @Test
    void getProviderById() {
        // given
        when(samlIdpRepository.findById(3L))
                .thenReturn(Optional.of(new SamlIdentityProvider(3L, "eid", "url", "name")));

        // when
        SamlIdentityProvider provider = service.getProviderById(3L);

        // then
        assertThat(provider).extracting(SamlIdentityProvider::getId, SamlIdentityProvider::getDisplayName)
                .containsExactly(3L, "name");
    }

    // -------------------- PRIVATE --------------------

    private void mockSpSettings() {
        when(settingsService.getFileSettingsForPrefix(":onelogin"))
                .thenReturn(Collections.singletonMap("onelogin.saml2.sp.entityid", "spEntityId"));
    }
}