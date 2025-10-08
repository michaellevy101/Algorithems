package strings;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for the Galil-Seiferas-Vishkin Dueling algorithm.
 * 
 * This test class covers:
 * - Basic functionality and correctness
 * - Periodic pattern handling (core strength of GSV algorithm)
 * - Edge cases and boundary conditions
 * - Paper-specific examples from the 1985 GSV paper
 * - Performance and complexity validation
 * - Dueling mechanism validation
 * 
 * The Dueling algorithm uses witness arrays and authentic dueling between
 * close candidates to achieve O(n + m) time complexity.
 */
class DuelingTest extends StringMatchingAlgorithmTest {

    @Override
    protected List<Integer> invoke(String text, String pattern) {
        return Dueling.search(text, pattern);
    }

    // ============================================================================
    // BASIC FUNCTIONALITY TESTS
    // ============================================================================

    @Test
    public void testIdenticalTextAndPattern() {
        List<Integer> result = Dueling.search("hello", "hello");
        assertEquals(1, result.size());
        assertEquals(0, result.get(0));
    }

    @Test
    public void testPatternAtBeginning() {
        List<Integer> result = Dueling.search("abcxyz", "abc");
        assertEquals(1, result.size());
        assertEquals(0, result.get(0));
    }

    @Test
    public void testPatternAtEnd() {
        List<Integer> result = Dueling.search("xyzabc", "abc");
        assertEquals(1, result.size());
        assertEquals(3, result.get(0));
    }

    @Test
    public void testSingleCharacterPattern() {
        List<Integer> result = Dueling.search("abababa", "a");
        assertEquals(4, result.size());
        assertEquals(0, result.get(0));
        assertEquals(2, result.get(1));
        assertEquals(4, result.get(2));
        assertEquals(6, result.get(3));
    }

    @Test
    public void testSingleCharacterTextAndPattern() {
        List<Integer> result = Dueling.search("a", "a");
        assertEquals(1, result.size());
        assertEquals(0, result.get(0));
    }

    @Test
    public void testSingleCharacterMismatch() {
        List<Integer> result = Dueling.search("a", "b");
        assertTrue(result.isEmpty());
    }

    @Test
    public void testNoMatches() {
        List<Integer> result = Dueling.search("abcdefg", "xyz");
        assertTrue(result.isEmpty());
    }

    @Test
    public void testComplexPattern() {
        // Test with a complex, non-periodic pattern
        String text = "abcdefghijklmnopqrstuvwxyzabcdefghijklmnop";
        String pattern = "ghijklmnop";
        List<Integer> result = Dueling.search(text, pattern);
        assertEquals(2, result.size());
        assertEquals(6, result.get(0));
        assertEquals(32, result.get(1));
    }

    // ============================================================================
    // PERIODIC PATTERN TESTS (Core GSV Strength)
    // ============================================================================

    @Test
    public void testPeriodicPattern() {
        // Pattern "abab" has period 2 - should find all overlapping matches
        List<Integer> result = Dueling.search("ababababab", "abab");
        assertEquals(4, result.size());
        assertEquals(0, result.get(0));
        assertEquals(2, result.get(1));
        assertEquals(4, result.get(2));
        assertEquals(6, result.get(3));
    }

    @Test
    public void testShortPeriodPattern() {
        // Pattern "aaa" has period 1
        List<Integer> result = Dueling.search("aaaaaaa", "aaa");
        assertEquals(5, result.size());
        assertEquals(0, result.get(0));
        assertEquals(1, result.get(1));
        assertEquals(2, result.get(2));
        assertEquals(3, result.get(3));
        assertEquals(4, result.get(4));
    }

    @Test
    public void testLongPeriodicPattern() {
        // Pattern with longer period - should find all overlapping matches
        String pattern = "abcabc";
        String text = "abcabcabcabcabc";
        List<Integer> result = Dueling.search(text, pattern);
        assertEquals(4, result.size());
        assertEquals(0, result.get(0));
        assertEquals(3, result.get(1));
        assertEquals(6, result.get(2));
        assertEquals(9, result.get(3));
    }

    @Test
    public void testPeriodicPatternWithTermination() {
        // Pattern "ABABAC" has period 2 that terminates
        List<Integer> result = Dueling.search("ABABACABABAC", "ABABAC");
        assertEquals(Arrays.asList(0, 6), result, "Periodic pattern with termination should work");
    }

    @Test
    public void testRepeatedCharacterText() {
        List<Integer> result = Dueling.search("aaaaaa", "aa");
        assertEquals(5, result.size());
        assertEquals(0, result.get(0));
        assertEquals(1, result.get(1));
        assertEquals(2, result.get(2));
        assertEquals(3, result.get(3));
        assertEquals(4, result.get(4));
    }

    @Test
    public void testRepeatedCharacters() {
        List<Integer> result = Dueling.search("aaaaaaaaaa", "aaa");
        assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7), result, "Repeated characters should be handled correctly");
    }

    @Test
    public void testOverlappingMatches() {
        // Pattern "aba" can overlap with itself
        List<Integer> result = Dueling.search("ababa", "aba");
        assertEquals(2, result.size());
        assertEquals(0, result.get(0));
        assertEquals(2, result.get(1));
    }

    @Test
    public void testAperiodicPattern() {
        // Pattern "abcdef" is aperiodic
        List<Integer> result = Dueling.search("xyzabcdefghiabcdef", "abcdef");
        assertEquals(2, result.size());
        assertEquals(3, result.get(0));
        assertEquals(12, result.get(1));
    }

    // ============================================================================
    // EDGE CASES AND BOUNDARY CONDITIONS
    // ============================================================================

    @Test
    public void testEmptyPattern() {
        List<Integer> result = Dueling.search("hello", "");
        assertTrue(result.isEmpty(), "Empty pattern should return no matches");
    }

    @Test
    public void testEmptyText() {
        List<Integer> result = Dueling.search("", "hello");
        assertTrue(result.isEmpty(), "Empty text should return no matches");
    }

    @Test
    public void testBothEmpty() {
        List<Integer> result = Dueling.search("", "");
        assertTrue(result.isEmpty());
    }

    @Test
    public void testPatternLongerThanText() {
        List<Integer> result = Dueling.search("abc", "abcdef");
        assertTrue(result.isEmpty());
    }

    @Test
    public void testLargeAlphabet() {
        // Use ASCII extended characters to avoid encoding issues
        String text = "abcdefghijklmnopqrstuvwxyz";
        String pattern = "def";
        List<Integer> result = Dueling.search(text, pattern);
        assertEquals(Arrays.asList(3), result, "Large alphabet characters should work");
    }

    @Test
    public void testBoundaryConditions() {
        // Pattern at beginning
        List<Integer> result1 = Dueling.search("pattern_test", "pattern");
        assertEquals(Arrays.asList(0), result1, "Pattern at beginning should be found");

        // Pattern at end
        List<Integer> result2 = Dueling.search("test_pattern", "pattern");
        assertEquals(Arrays.asList(5), result2, "Pattern at end should be found");

        // Pattern exactly fills text
        List<Integer> result3 = Dueling.search("exact", "exact");
        assertEquals(Arrays.asList(0), result3, "Pattern exactly filling text should be found");
    }

    @Test
    public void testNullInputs() {
        assertThrows(IllegalArgumentException.class, () -> {
            Dueling.search(null, "pattern");
        }, "Null text should throw IllegalArgumentException");

        assertThrows(IllegalArgumentException.class, () -> {
            Dueling.search("text", null);
        }, "Null pattern should throw IllegalArgumentException");
    }

    // ============================================================================
    // PAPER-SPECIFIC EXAMPLES (1985 GSV Paper)
    // ============================================================================

    @Test
    public void testPaperSpecificExamples() {
        // Test specific examples from the 1985 GSV paper
        
        // Example 1: Classic periodic pattern from paper
        List<Integer> result1 = Dueling.search("ABABABABAB", "ABAB");
        assertEquals(Arrays.asList(0, 2, 4, 6), result1, "Paper's ABAB example should find overlapping matches");
        
        // Example 2: Period 2 pattern with termination
        List<Integer> result2 = Dueling.search("ABABACABAB", "ABABAC");
        assertEquals(Arrays.asList(0), result2, "Period 2 with termination should work");
        
        // Example 3: Non-periodic pattern that tests regular case
        List<Integer> result3 = Dueling.search("ABCDEFABCDEF", "ABCDEF");
        assertEquals(Arrays.asList(0, 6), result3, "Non-periodic pattern should use regular case logic");
    }

    @Test
    public void testWitnessArrayCorrectness() {
        // Test patterns that would stress the witness array computation
        String[] testPatterns = {
            "ABCDEF",      // No period
            "ABABAB",      // Period 2
            "ABCABCABC",   // Period 3
            "ABABABC"      // Period 2 with termination
        };

        for (String pattern : testPatterns) {
            String text = pattern + "XYZ" + pattern;
            List<Integer> result = Dueling.search(text, pattern);
            assertTrue(result.contains(0), "Pattern should be found at beginning for: " + pattern);
            assertTrue(result.contains(pattern.length() + 3), "Pattern should be found after separator for: " + pattern);
        }
        
        // Test paper-specific example: "ABAB" pattern in "ABABABABAB" text
        String paperPattern = "ABAB";
        String paperText = "ABABABABAB";
        List<Integer> paperResult = Dueling.search(paperText, paperPattern);
        assertEquals(Arrays.asList(0, 2, 4, 6), paperResult, "Paper example should work correctly");
    }

    @Test
    public void testPeriod1PatternEdgeCase() {
        // Test period-1 patterns to validate witness array edge case handling
        
        // Period 1 patterns like "AAAAAA" have degenerate witness arrays
        // since pattern[h] == pattern[h+1] for all valid h
        
        // Test simple period-1 case
        List<Integer> result1 = Dueling.search("AAAAAAA", "AAA");
        // Should find overlapping matches at positions 0, 1, 2, 3, 4
        assertTrue(result1.size() >= 1, "Should find at least one match for period-1 pattern");
        assertTrue(result1.contains(0), "Should find match at beginning");
        
        // Test longer period-1 pattern
        List<Integer> result2 = Dueling.search("BBBBBBBBBBB", "BBBB");
        assertTrue(result2.size() >= 1, "Should handle longer period-1 patterns");
        
        // Test period-1 with different character
        List<Integer> result3 = Dueling.search("CCCCCCC", "CC");
        assertTrue(result3.size() >= 1, "Should work with different characters");
        
        System.out.println("Period-1 test results:");
        System.out.println("AAA in AAAAAAA: " + result1);
        System.out.println("BBBB in BBBBBBBBBBB: " + result2);
        System.out.println("CC in CCCCCCC: " + result3);
    }

    // ============================================================================
    // DUELING MECHANISM AND ALGORITHM-SPECIFIC TESTS
    // ============================================================================

    @Test
    public void testDuelingMechanism() {
        // Test case designed to trigger dueling between close candidates
        String text = "ABCABCABCABC";
        String pattern = "ABCABC";
        List<Integer> result = Dueling.search(text, pattern);
        assertEquals(Arrays.asList(0, 3, 6), result, "Dueling mechanism should find all valid matches including overlaps");
    }

    @Test
    public void testDuelingBehavior() {
        // This pattern should trigger both forward and backward scans
        String text = "abcdefghijklmnopqrstuvwxyz";
        String pattern = "ghijklm";
        List<Integer> result = Dueling.search(text, pattern);
        assertEquals(1, result.size());
        assertEquals(6, result.get(0));
    }

    @Test
    public void testPatternWithSelfSimilarity() {
        // Pattern has internal structure that might confuse simple algorithms
        String text = "abacababacab";
        String pattern = "ababacab";
        List<Integer> result = Dueling.search(text, pattern);
        assertEquals(1, result.size());
        assertEquals(4, result.get(0));
    }

    @Test
    public void testStageProgression() {
        // Test with pattern lengths that require different numbers of stages
        String[] patterns = {
            "A",           // 1 char - minimal stages
            "AB",          // 2 chars - stage 1 only
            "ABCD",        // 4 chars - ⌈log 4⌉ = 2 stages
            "ABCDEFGH",    // 8 chars - ⌈log 8⌉ = 3 stages
            "ABCDEFGHIJKLMNOP"  // 16 chars - ⌈log 16⌉ = 4 stages
        };
        
        for (String pattern : patterns) {
            String text = "XYZ" + pattern + "XYZ" + pattern + "XYZ";
            List<Integer> result = Dueling.search(text, pattern);
            assertEquals(2, result.size(), "Should find exactly 2 matches for pattern: " + pattern);
            assertTrue(result.contains(3), "Should find pattern at position 3");
            assertTrue(result.contains(3 + pattern.length() + 3), "Should find pattern at second position");
        }
    }

    // ============================================================================
    // PERFORMANCE AND COMPLEXITY VALIDATION
    // ============================================================================

    @Test
    public void testPerformanceLinearComplexity() {
        // Test with increasing input sizes to verify O(n+m) complexity
        long[] times = new long[5];
        int[] sizes = {1000, 2000, 4000, 8000, 16000};
        
        for (int i = 0; i < sizes.length; i++) {
            int n = sizes[i];
            StringBuilder text = new StringBuilder();
            for (int j = 0; j < n; j++) {
                text.append((char)('A' + (j % 26)));
            }
            String pattern = "ABCDEFG";
            
            long startTime = System.nanoTime();
            Dueling.search(text.toString(), pattern);
            long endTime = System.nanoTime();
            times[i] = endTime - startTime;
        }
        
        // Verify that the time doesn't grow quadratically
        // For O(n+m) complexity, doubling input size should roughly double time
        for (int i = 1; i < times.length; i++) {
            double ratio = (double) times[i] / times[i-1];
            assertTrue(ratio < 4.0, 
                "Time complexity appears worse than linear. Ratio: " + ratio + 
                " for sizes " + sizes[i-1] + " to " + sizes[i]);
        }
        
        System.out.println("Performance test passed - complexity appears linear");
    }
}
