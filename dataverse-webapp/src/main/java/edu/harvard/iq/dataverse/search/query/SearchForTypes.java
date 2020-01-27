package edu.harvard.iq.dataverse.search.query;

import com.google.common.base.Preconditions;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SearchForTypes {

    private Set<SearchObjectType> types = new HashSet<>();
    
    // -------------------- CONSTRUCTORS --------------------
    
    private SearchForTypes(Set<SearchObjectType> types) {
        Preconditions.checkArgument(types.size() > 0);
        this.types = types;
    }

    // -------------------- GETTERS --------------------
    
    public Set<SearchObjectType> getTypes() {
        return types;
    }

    // -------------------- LOGIC --------------------
    
    public static SearchForTypes byTypes(List<SearchObjectType> types) {
        return new SearchForTypes(new HashSet<>(types));
    }
    
    public static SearchForTypes byTypes(SearchObjectType ... types) {
        return byTypes(Arrays.asList(types));
    }
    
    public static SearchForTypes all() {
        return byTypes(SearchObjectType.values());
    }
}
