package strings;

import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the Aho-Corasick string matching algorithm.
 * Extends the reusable test base class and adds specific multi-pattern test cases.
 */
class AhoCorasickSearchTest extends StringMatchingAlgorithmTest {

    @Override
    protected List<Integer> invoke(String text, String pattern) {
        return AhoCorasickSearch.search(text, pattern);
    }

    // ======== MULTI-PATTERN SEARCH TESTS ========

    @Test
    public void testMultiplePatterns_BasicCase() {
        List<String> patterns = Arrays.asList("HE", "SHE", "HIS", "HERS");
        Map<String, List<Integer>> results = AhoCorasickSearch.searchMultiple("USHERS", patterns);
        
        // "USHERS" contains: "SHE" at 1, "HE" at 2, and "HERS" at 2
        assertEquals(3, results.size());
        assertEquals(Arrays.asList(2), results.get("HE"));
        assertEquals(Arrays.asList(1), results.get("SHE"));
        assertEquals(Arrays.asList(2), results.get("HERS"));
        assertNull(results.get("HIS"));
    }

    @Test
    public void testMultiplePatterns_OverlappingMatches() {
        List<String> patterns = Arrays.asList("AA", "AAA", "AAAA");
        Map<String, List<Integer>> results = AhoCorasickSearch.searchMultiple("AAAAA", patterns);
        
        assertEquals(3, results.size());
        assertEquals(Arrays.asList(0, 1, 2, 3), results.get("AA"));
        assertEquals(Arrays.asList(0, 1, 2), results.get("AAA"));
        assertEquals(Arrays.asList(0, 1), results.get("AAAA"));
    }

    @Test
    public void testMultiplePatterns_NoMatches() {
        List<String> patterns = Arrays.asList("XYZ", "ABC", "DEF");
        Map<String, List<Integer>> results = AhoCorasickSearch.searchMultiple("HELLO WORLD", patterns);
        
        assertTrue(results.isEmpty());
    }

    @Test
    public void testMultiplePatterns_EmptyText() {
        List<String> patterns = Arrays.asList("HE", "SHE");
        Map<String, List<Integer>> results = AhoCorasickSearch.searchMultiple("", patterns);
        
        assertTrue(results.isEmpty());
    }

    @Test
    public void testMultiplePatterns_EmptyPatterns() {
        List<String> patterns = Arrays.asList();
        Map<String, List<Integer>> results = AhoCorasickSearch.searchMultiple("HELLO", patterns);
        
        assertTrue(results.isEmpty());
    }

    @Test
    public void testMultiplePatterns_NullInput() {
        Map<String, List<Integer>> results = AhoCorasickSearch.searchMultiple(null, Arrays.asList("HE"));
        assertTrue(results.isEmpty());
        
        results = AhoCorasickSearch.searchMultiple("HELLO", null);
        assertTrue(results.isEmpty());
    }

    @Test
    public void testMultiplePatterns_ComplexExample() {
        List<String> patterns = Arrays.asList("ABCD", "BCD", "CD", "D");
        Map<String, List<Integer>> results = AhoCorasickSearch.searchMultiple("ABCDEFABCD", patterns);
        
        assertEquals(4, results.size());
        assertEquals(Arrays.asList(0, 6), results.get("ABCD"));
        assertEquals(Arrays.asList(1, 7), results.get("BCD"));
        assertEquals(Arrays.asList(2, 8), results.get("CD"));
        assertEquals(Arrays.asList(3, 9), results.get("D"));
    }

    @Test
    public void testMultiplePatterns_SingleCharacterPatterns() {
        List<String> patterns = Arrays.asList("A", "B", "C");
        Map<String, List<Integer>> results = AhoCorasickSearch.searchMultiple("ABCABC", patterns);
        
        assertEquals(3, results.size());
        assertEquals(Arrays.asList(0, 3), results.get("A"));
        assertEquals(Arrays.asList(1, 4), results.get("B"));
        assertEquals(Arrays.asList(2, 5), results.get("C"));
    }

    @Test
    public void testMultiplePatterns_DuplicatePatterns() {
        List<String> patterns = Arrays.asList("AB", "AB", "BC");
        Map<String, List<Integer>> results = AhoCorasickSearch.searchMultiple("ABCAB", patterns);
        
        assertEquals(2, results.size());
        assertEquals(Arrays.asList(0, 3), results.get("AB"));
        assertEquals(Arrays.asList(1), results.get("BC"));
    }

    @Test
    public void testMultiplePatterns_PrefixSuffixOverlap() {
        // Test patterns where one is prefix of another
        List<String> patterns = Arrays.asList("AB", "ABAB", "BAB", "B");
        Map<String, List<Integer>> results = AhoCorasickSearch.searchMultiple("ABABAB", patterns);
        
        assertEquals(4, results.size());
        assertEquals(Arrays.asList(0, 2, 4), results.get("AB"));
        assertEquals(Arrays.asList(0, 2), results.get("ABAB"));
        assertEquals(Arrays.asList(1, 3), results.get("BAB"));
        assertEquals(Arrays.asList(1, 3, 5), results.get("B"));
    }

    // ======== CASE SENSITIVITY TESTS ========

    @Test
    public void testCaseInsensitive_Basic() {
        List<String> patterns = Arrays.asList("He", "She", "HIS");
        Map<String, List<Integer>> results = AhoCorasickSearch.searchMultiple("ushers", patterns, true);
        
        assertEquals(2, results.size());
        assertEquals(Arrays.asList(2), results.get("He"));
        assertEquals(Arrays.asList(1), results.get("She"));
    }

    @Test
    public void testCaseInsensitive_MixedCase() {
        List<String> patterns = Arrays.asList("ABC", "bcd", "CdE");
        Map<String, List<Integer>> results = AhoCorasickSearch.searchMultiple("AbCdEfAbC", patterns, true);
        
        // "AbCdEfAbC" contains: "ABC" at 0 and 6, "bcd" at 1, "CdE" at 2 (case-insensitive)
        assertEquals(3, results.size());
        assertEquals(Arrays.asList(0, 6), results.get("ABC"));
        assertEquals(Arrays.asList(1), results.get("bcd"));
        assertEquals(Arrays.asList(2), results.get("CdE"));
    }

    @Test
    public void testCaseSensitive_NoMatch() {
        List<String> patterns = Arrays.asList("ABC", "def");
        Map<String, List<Integer>> results = AhoCorasickSearch.searchMultiple("abcDEF", patterns, false);
        
        assertTrue(results.isEmpty());
    }

    // ======== INSTANCE-BASED SEARCH TESTS ========

    @Test
    public void testInstanceSearch_Basic() {
        List<String> patterns = Arrays.asList("cat", "dog", "bird");
        AhoCorasickSearch ac = new AhoCorasickSearch(patterns);
        
        Map<String, List<Integer>> results = ac.searchMultiple("The cat and dog are friends");
        
        assertEquals(2, results.size());
        assertEquals(Arrays.asList(4), results.get("cat"));
        assertEquals(Arrays.asList(12), results.get("dog"));
    }

    @Test
    public void testInstanceSearch_CaseInsensitive() {
        List<String> patterns = Arrays.asList("Cat", "DOG", "bIrD");
        AhoCorasickSearch ac = new AhoCorasickSearch(patterns, true);
        
        Map<String, List<Integer>> results = ac.searchMultiple("the cat and dog are friends");
        
        assertEquals(2, results.size());
        assertEquals(Arrays.asList(4), results.get("Cat"));
        assertEquals(Arrays.asList(12), results.get("DOG"));
    }

    @Test
    public void testInstanceSearch_EmptyPatterns() {
        AhoCorasickSearch ac = new AhoCorasickSearch(Arrays.asList());
        Map<String, List<Integer>> results = ac.searchMultiple("hello world");
        
        assertTrue(results.isEmpty());
    }

    @Test
    public void testInstanceSearch_NullPatterns() {
        AhoCorasickSearch ac = new AhoCorasickSearch(null);
        Map<String, List<Integer>> results = ac.searchMultiple("hello world");
        
        assertTrue(results.isEmpty());
    }

    // ======== EDGE CASES AND ERROR HANDLING ========

    @Test
    public void testEdgeCase_EmptyPatternsInList() {
        List<String> patterns = Arrays.asList("ABC", "", "DEF", null, "GHI");
        Map<String, List<Integer>> results = AhoCorasickSearch.searchMultiple("ABCDEFGHI", patterns);
        
        assertEquals(3, results.size());
        assertEquals(Arrays.asList(0), results.get("ABC"));
        assertEquals(Arrays.asList(3), results.get("DEF"));
        assertEquals(Arrays.asList(6), results.get("GHI"));
    }

    @Test
    public void testEdgeCase_VeryLongText() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longText.append("ABCDEFGHIJ");
        }
        
        List<String> patterns = Arrays.asList("ABC", "DEF", "XYZ");
        Map<String, List<Integer>> results = AhoCorasickSearch.searchMultiple(longText.toString(), patterns);
        
        assertEquals(2, results.size());
        assertEquals(1000, results.get("ABC").size());
        assertEquals(1000, results.get("DEF").size());
        assertNull(results.get("XYZ"));
    }

    @Test
    public void testEdgeCase_ManySmallPatterns() {
        List<String> patterns = new ArrayList<>();
        for (char c = 'A'; c <= 'Z'; c++) {
            patterns.add(String.valueOf(c));
        }
        
        Map<String, List<Integer>> results = AhoCorasickSearch.searchMultiple("ABCDEFGHIJKLMNOPQRSTUVWXYZ", patterns);
        
        assertEquals(26, results.size());
        for (int i = 0; i < 26; i++) {
            char c = (char) ('A' + i);
            assertEquals(Arrays.asList(i), results.get(String.valueOf(c)));
        }
    }

    // ======== ALGORITHM-SPECIFIC TESTS ========

    @Test
    public void testAhoCorasick_ClassicExample() {
        // Classic example from Aho-Corasick paper
        List<String> patterns = Arrays.asList("he", "she", "his", "hers");
        Map<String, List<Integer>> results = AhoCorasickSearch.searchMultiple("ushers", patterns);
        
        // "ushers" contains: "she" at 1, "he" at 2, and "hers" at 2
        assertEquals(3, results.size());
        assertEquals(Arrays.asList(2), results.get("he"));
        assertEquals(Arrays.asList(1), results.get("she"));
        assertEquals(Arrays.asList(2), results.get("hers"));
        assertNull(results.get("his"));
    }

    @Test
    public void testAhoCorasick_FailureLinkTest() {
        // Test that exercises failure links extensively
        List<String> patterns = Arrays.asList("GCAGAGAG", "AGAG", "GAG", "AG");
        Map<String, List<Integer>> results = AhoCorasickSearch.searchMultiple("GCATCGCAGAGAGTATACAGTACG", patterns);
        
        // All 4 patterns are found in the text
        // Text breakdown: GCATCGCAGAGAGTATACAGTACG
        // Position 5-12: GCAGAGAG (contains GAG at offsets 3 and 5, i.e., positions 8 and 10)
        // Position 18-19: AG appears again
        assertEquals(4, results.size());
        assertEquals(Arrays.asList(5), results.get("GCAGAGAG"));
        assertEquals(Arrays.asList(7, 9), results.get("AGAG"));
        assertEquals(Arrays.asList(8, 10), results.get("GAG"));  // Fixed: GAG only at positions 8 and 10
        assertEquals(Arrays.asList(7, 9, 11, 18), results.get("AG"));  // Fixed: AG also at position 18
    }

    @Test
    public void testAhoCorasick_OutputLinkTest() {
        // Test that exercises output links for nested pattern matches
        List<String> patterns = Arrays.asList("A", "AA", "AAA", "AAAA", "AAAAA");
        Map<String, List<Integer>> results = AhoCorasickSearch.searchMultiple("AAAAA", patterns);
        
        assertEquals(5, results.size());
        assertEquals(Arrays.asList(0, 1, 2, 3, 4), results.get("A"));
        assertEquals(Arrays.asList(0, 1, 2, 3), results.get("AA"));
        assertEquals(Arrays.asList(0, 1, 2), results.get("AAA"));
        assertEquals(Arrays.asList(0, 1), results.get("AAAA"));
        assertEquals(Arrays.asList(0), results.get("AAAAA"));
    }

    @Test
    public void testAhoCorasick_PerformanceStressTest() {
        // Create many overlapping patterns
        List<String> patterns = new ArrayList<>();
        String base = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        
        // Add all substrings of length 2-5
        for (int len = 2; len <= 5; len++) {
            for (int i = 0; i <= base.length() - len; i++) {
                patterns.add(base.substring(i, i + len));
            }
        }
        
        // Search in repeated alphabet
        String text = base + base + base;
        Map<String, List<Integer>> results = AhoCorasickSearch.searchMultiple(text, patterns);
        
        // Should find many overlapping matches
        assertTrue(results.size() > 50);
        
        // Verify a few specific patterns
        assertEquals(Arrays.asList(0, 26, 52), results.get("AB"));
        assertEquals(Arrays.asList(0, 26, 52), results.get("ABCD"));
    }

    // ======== COMPATIBILITY TESTS ========

    @Test
    public void testSinglePatternCompatibility_BasicSearch() {
        // Test that single pattern search works the same as other algorithms
        String text = "ABABCABABAB";
        String pattern = "ABAB";
        
        List<Integer> acResults = AhoCorasickSearch.search(text, pattern);
        List<Integer> kmpResults = KMPSearch.search(text, pattern);
        
        assertEquals(kmpResults, acResults);
    }

    @Test
    public void testSinglePatternCompatibility_EdgeCases() {
        // Empty text
        assertEquals(Collections.emptyList(), AhoCorasickSearch.search("", "ABC"));
        
        // Empty pattern
        assertEquals(Collections.emptyList(), AhoCorasickSearch.search("ABC", ""));
        
        // Single character
        assertEquals(Arrays.asList(0, 2, 4), AhoCorasickSearch.search("ABABA", "A"));
        
        // No matches
        assertEquals(Collections.emptyList(), AhoCorasickSearch.search("ABC", "XYZ"));
    }

    @Test
    public void testNullHandling() {
        // Test null handling for single pattern search
        assertThrows(IllegalArgumentException.class, () -> AhoCorasickSearch.search(null, "pattern"));
        assertThrows(IllegalArgumentException.class, () -> AhoCorasickSearch.search("text", null));
        assertThrows(IllegalArgumentException.class, () -> AhoCorasickSearch.search(null, null));
        
        // Test null handling for instance search
        AhoCorasickSearch ac = new AhoCorasickSearch(Arrays.asList("test"));
        assertThrows(IllegalArgumentException.class, () -> ac.searchMultiple(null));
    }
}
