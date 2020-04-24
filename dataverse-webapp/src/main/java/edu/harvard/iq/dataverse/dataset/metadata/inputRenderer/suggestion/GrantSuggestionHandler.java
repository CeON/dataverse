package edu.harvard.iq.dataverse.dataset.metadata.inputRenderer.suggestion;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.List;
import java.util.Map;

@Stateless
public class GrantSuggestionHandler implements SuggestionHandler {

    private GrantSuggestionDao grantSuggestionDao;

    // -------------------- CONSTRUCTORS --------------------

    @Deprecated
    public GrantSuggestionHandler() {
    }

    @Inject
    public GrantSuggestionHandler(GrantSuggestionDao grantSuggestionDao) {
        this.grantSuggestionDao = grantSuggestionDao;
    }

    // -------------------- LOGIC --------------------

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public List<String> generateSuggestions(Map<String, String> filters, String suggestionSourceFieldName, String suggestionSourceFieldValue) {

        return filters.isEmpty() ? grantSuggestionDao.fetchSuggestions(suggestionSourceFieldName, suggestionSourceFieldValue, 10) :
                grantSuggestionDao.fetchSuggestions(filters, suggestionSourceFieldName, suggestionSourceFieldValue, 10);
    }
}
