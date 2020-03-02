package edu.harvard.iq.dataverse.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author rmp553
 */
public class MarkupCheckerTest {

    @Test
    public void sanitizeBasicHTML_script() {
        //GIVEN
        String safeStr = "<script>alert('hi')</script>";
        //WHEN
        String sanitized = MarkupChecker.sanitizeBasicHTML(safeStr);
        //THEN
        assertEquals("", sanitized);
    }

    @Test
    public void sanitizeBasicHTML_map() {
        //GIVEN
        String unsafeStr = "<map name=\"rtdcCO\">";
        String safeStr = "<map name=\"rtdcCO\"></map>";
        //WHEN
        String sanitized = MarkupChecker.sanitizeBasicHTML(unsafeStr);
        //THEN
        assertEquals(safeStr, sanitized);
    }

    @Test
    public void sanitizeBasicHTML_area() {
        //GIVEN
        String unsafeStr = "<area shape=\"rect\" coords=\"42,437,105,450\" href=\"/dvn/dv/rtdc/faces/study/StudyPage.xhtml?globalId=hdl:10904/10006\" title=\"Galactic Center (DHT02)\" alt=\"Galactic Center (DHT02)\">";
        //WHEN
        String sanitized = MarkupChecker.sanitizeBasicHTML(unsafeStr);
        //THEN
        assertEquals(unsafeStr, sanitized);
    }

    @Test
    public void sanitizeBasicHTML_mapAndArea() {
        //GIVEN
        String unsafeStr = "<map name=\"rtdcCO\"><area shape=\"rect\" coords=\"42,437,105,450\" href=\"/dvn/dv/rtdc/faces/study/StudyPage.xhtml?globalId=hdl:10904/10006\" title=\"Galactic Center (DHT02)\" alt=\"Galactic Center (DHT02)\"></map>";
        //WHEN
        String sanitized = MarkupChecker.sanitizeBasicHTML(unsafeStr);
        //THEN
        assertEquals(unsafeStr, sanitized);
    }

    @Test
    public void sanitizeBasicHTML_paragraph() {
        //GIVEN
        String unsafeStr = "<p>hello</";
        String safeStr = "<p>hello&lt;/</p>";
        //WHEN
        String sanitized = MarkupChecker.sanitizeBasicHTML(unsafeStr);
        //THEN
        assertEquals(safeStr, sanitized);
    }

    @Test
    public void sanitizeBasicHTML_heading() {
        //GIVEN
        String unsafeStr = "<h1>hello</h2>";
        String safeStr = "<h1>hello</h1>";
        //WHEN
        String sanitized = MarkupChecker.sanitizeBasicHTML(unsafeStr);
        //THEN
        assertEquals(safeStr, sanitized);
    }

    @Test
    public void sanitizeBasicHTML_anchor() {
        //GIVEN
        String unsafeStr = "the <a href=\"http://dataverse.org\" target=\"_blank\">Dataverse project</a> in a new window";
        String safeStr = "the \n<a href=\"http://dataverse.org\" target=\"_blank\" rel=\"nofollow\">Dataverse project</a> in a new window";
        //WHEN
        String sanitized = MarkupChecker.sanitizeBasicHTML(unsafeStr);
        //THEN
        assertEquals(safeStr, sanitized);
    }

    @Test
    public void sanitizeBasicHTML_anchor2() {
        //GIVEN
        String unsafeStr = "the <a href=\"http://dataverse.org\">Dataverse project</a> in a new window";
        String safeStr = "the \n<a href=\"http://dataverse.org\" rel=\"nofollow\" target=\"_blank\">Dataverse project</a> in a new window";
        //WHEN
        String sanitized = MarkupChecker.sanitizeBasicHTML(unsafeStr);
        //THEN
        assertEquals(safeStr, sanitized);
    }

    @Test
    public void sanitizeBasicHTML_null() {
        assertNull(MarkupChecker.sanitizeBasicHTML(null));
    }

    @Test
    public void stripAllTags() {
        assertEquals("", MarkupChecker.stripAllTags(""));
    }

    @Test
    public void stripAllTags_null() {
        assertNull(MarkupChecker.stripAllTags(null));
    }

    @Test
    public void escapeHtml() {
        assertEquals("foo&lt;br&gt;bar", MarkupChecker.escapeHtml("foo<br>bar"));
    }

}
