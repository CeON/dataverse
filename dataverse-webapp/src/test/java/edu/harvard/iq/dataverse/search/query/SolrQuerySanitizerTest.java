package edu.harvard.iq.dataverse.search.query;

import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.DatasetFieldServiceBean;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.FieldType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

/**
 * @author madryk
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SolrQuerySanitizerTest {

    @InjectMocks
    private SolrQuerySanitizer querySanitizer;

    @Mock
    private DatasetFieldServiceBean datasetFieldService;

    @BeforeEach
    public void beforeEach() {
        DatasetFieldType fieldType1 = new DatasetFieldType("title", FieldType.TEXT, true);
        fieldType1.setId(1L);
        DatasetFieldType fieldType2 = new DatasetFieldType("year", FieldType.INT, true);
        fieldType2.setId(2L);

        when(datasetFieldService.findAllOrderedById()).thenReturn(Lists.newArrayList(fieldType1, fieldType2));
    }

    // -------------------- TESTS --------------------

    @ParameterizedTest
    @MethodSource("provideQueryThatShouldChange")
    public void replaceDatasetFieldTypes__SHOULD_REPLACE(String input, String expected) {
        // when & then
        assertEquals(expected, querySanitizer.sanitizeQuery(input));
    }

    @ParameterizedTest
    @MethodSource("provideQueryThatShouldntChange")
    public void replaceDatasetFieldTypes__SHOULDNT_REPLACE(String input) {
        // when & then
        assertEquals(input, querySanitizer.sanitizeQuery(input));
    }

    @ParameterizedTest
    @MethodSource("provideRorQueryThatShouldChange")
    public void sanitizeRorQuery(String input, String expected) {
        // when & then
        assertEquals(expected, querySanitizer.removeSolrSpecialChars(input));
    }

    @Test
    public void testSanitizeQuery() {
        assertEquals("", querySanitizer.sanitizeQuery(null));
        assertEquals("", querySanitizer.sanitizeQuery(""));
        assertEquals("doi\\:10.5072/FK2/4QEJQV", querySanitizer.sanitizeQuery("doi:10.5072/FK2/4QEJQV"));
        assertEquals("datasetPersistentIdentifier:doi\\:10.5072/FK2/4QEJQV", querySanitizer.sanitizeQuery("datasetPersistentIdentifier:doi:10.5072/FK2/4QEJQV"));
        assertEquals("doi\\:4QEJQV", querySanitizer.sanitizeQuery("doi:4QEJQV"));
        assertEquals("hdl\\:1902.1/21919", querySanitizer.sanitizeQuery("hdl:1902.1/21919"));
        assertEquals("datasetPersistentIdentifier:hdl\\:1902.1/21919", querySanitizer.sanitizeQuery("datasetPersistentIdentifier:hdl:1902.1/21919"));
    }


    // -------------------- PRIVATE --------------------

    private static Stream<String> provideQueryThatShouldntChange() {
        return Stream.of(
                    "title",
                    "(title",
                    "abc title abc",
                    "\"title:value\"",
                    "\"title:va\"lue",
                    "\"some title:value\"",
                    "\"title\":value",
                    "abc\"title:value",
                    "TiTLE:value",
                    "title&value",
                    " \\\\\" title:value" // string: \\" title:value (escape of \ char)
                );
    }

    private static Stream<Arguments> provideQueryThatShouldChange() {
        return Stream.of(
                Arguments.of("title:value", "dsf_txt_title:value"),
                Arguments.of("+title:value", "+dsf_txt_title:value"),
                Arguments.of("+ title:value", "+ dsf_txt_title:value"),
                Arguments.of("-title:value", "-dsf_txt_title:value"),
                Arguments.of("!title:value", "!dsf_txt_title:value"),
                Arguments.of("(title:value)", "(dsf_txt_title:value)"),
                Arguments.of("(abc)title:value", "(abc)dsf_txt_title:value"),
                Arguments.of("abc&title:value", "abc&dsf_txt_title:value"),
                Arguments.of("title:value   abc", "dsf_txt_title:value   abc"),
                Arguments.of("abc   title:value", "abc   dsf_txt_title:value"),
                Arguments.of(" \\\" title:value", " \\\" dsf_txt_title:value"), // escaped quotation
                Arguments.of(" \\\\\\\" title:value", " \\\\\\\" dsf_txt_title:value"), // escaped \ char and quotation
                Arguments.of("abc&title:value", "abc&dsf_txt_title:value"),
                Arguments.of("abc&title:value", "abc&dsf_txt_title:value"),
                Arguments.of("title:value AND year:value", "dsf_txt_title:value AND dsf_int_year:value")
                );
    }

    private static Stream<Arguments> provideRorQueryThatShouldChange() {
        return Stream.of(
                Arguments.of("title:value", "title value"),
                Arguments.of("+title:value", " title value"),
                Arguments.of("+ title:value", "  title value"),
                Arguments.of("-title:value", " title value"),
                Arguments.of("!title:value", " title value"),
                Arguments.of("(title:value)", " title value "),
                Arguments.of("(abc)title:value", " abc title value"),
                Arguments.of("abc&title:value", "abc title value"),
                Arguments.of("title:value   abc", "title value   abc"),
                Arguments.of("abc   title:value", "abc   title value"),
                Arguments.of(" \\\" title:value", "    title value"),
                Arguments.of(" \\\\\\\" title:value", "      title value"),
                Arguments.of("abc&title:value", "abc title value"),
                Arguments.of("abc&title:value", "abc title value"),
                Arguments.of("title:value AND year:value", "title value AND year value"),
                Arguments.of("\"title:value AND year:value\"", " title value AND year value ")
                );
    }
}
