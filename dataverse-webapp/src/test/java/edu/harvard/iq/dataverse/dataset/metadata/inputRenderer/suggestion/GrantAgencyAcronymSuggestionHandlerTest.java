package edu.harvard.iq.dataverse.dataset.metadata.inputRenderer.suggestion;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.dataset.metadata.inputRenderer.Suggestion;
import edu.harvard.iq.dataverse.persistence.dataset.suggestion.GrantSuggestion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class GrantAgencyAcronymSuggestionHandlerTest {

    @InjectMocks
    private GrantAgencyAcronymSuggestionHandler grantAgencyAcronymSuggestionHandler;

    @Mock
    private GrantSuggestionDao grantSuggestionDao;

    // -------------------- TESTS --------------------

    @Test
    public void generateSuggestions() {
        //given
        List<String> dbSuggestions = Lists.newArrayList("suggestion1", "suggestion2");
        when(grantSuggestionDao.fetchSuggestions(any(), anyString(), anyString(), anyInt())).thenReturn(dbSuggestions);

        //when
        List<Suggestion> suggestions = grantAgencyAcronymSuggestionHandler.generateSuggestions(
                ImmutableMap.of("filterField", "filterValue"), "query");

        //then
        assertThat(suggestions)
            .extracting(Suggestion::getValue)
            .containsExactly("suggestion1", "suggestion2");

        verify(grantSuggestionDao).fetchSuggestions(
                ImmutableMap.of("filterField", "filterValue"), GrantSuggestion.GRANT_AGENCY_ACRONYM_COLUMN, "query", 10);
    }
}
