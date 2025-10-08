package strings;

import java.util.ArrayList;
import java.util.List;

/**
 * Morris-Pratt String Matching Algorithm (1970) - Original Window-Based Implementation
 * 
 * This implementation provides the classic Morris-Pratt algorithm using explicit
 * window positioning and shifting. Unlike other MP implementations that track
 * character positions, this version maintains a clear "window" concept where
 * we explicitly track where the pattern is positioned relative to the text.
 * 
 * ## ALGORITHM OVERVIEW
 * 
 * This window-based approach makes the Morris-Pratt algorithm easier to understand
 * by visualizing the pattern as a "sliding window" over the text. When a mismatch
 * occurs, we use the LPS array to determine the optimal window shift distance.
 * 
 * ## KEY CONCEPT: Explicit Window Positioning
 * 
 * **Traditional vs Window-Based Approach:**
 * ```
 * Traditional MP: Tracks text pointer + pattern pointer
 * Window-Based:   Tracks window start position + comparison within window
 * 
 * Text:    A B A B C A B A B A B
 * Window:  [A B A B] <- pattern positioned at textPos=0
 * 
 * On mismatch: Calculate shift distance and move window
 * New Window:    [A B A B] <- pattern positioned at textPos=2
 * ```
 * 
 * ## WINDOW SHIFTING VISUALIZATION
 * 
 * **Searching "ABAB" in "ABABCABABAB":**
 * ```
 * Text:    A B A B C A B A B A B
 * Index:   0 1 2 3 4 5 6 7 8 9 10
 * Pattern: A B A B (LPS = [0,0,1,2])
 * 
 * Window 1: textPos=0
 * Text:    [A B A B] C A B A B A B
 * Pattern:  A B A B
 *           ✓ ✓ ✓ ✓ → MATCH at position 0!
 * 
 * Shift calculation: 4 - LPS[3] = 4 - 2 = 2
 * New textPos = 0 + 2 = 2
 * 
 * Window 2: textPos=2  
 * Text:    A B [A B C] A B A B A B
 * Pattern:      A B A B
 *               ✓ ✓ ✗  → mismatch at pattern[2]
 * 
 * Shift calculation: 2 - LPS[1] = 2 - 0 = 2
 * New textPos = 2 + 2 = 4
 * 
 * Window 3: textPos=4
 * Text:    A B A B [C A B A] B A B
 * Pattern:          A B A B
 *                   ✗ → mismatch at pattern[0]
 * Shift by 1: textPos = 5
 * 
 * Window 4: textPos=5
 * Text:    A B A B C [A B A B] A B
 * Pattern:            A B A B
 *                     ✓ ✓ ✓ ✓ → MATCH at position 5!
 * ```
 * 
 * ## SHIFT CALCULATION STRATEGY
 * 
 * **The Window Shift Formula:**
 * ```
 * When mismatch occurs at pattern position k:
 * shift_distance = k - LPS[k-1]
 * 
 * Why this works:
 * - We've matched k characters
 * - LPS[k-1] tells us how many we can reuse from the beginning
 * - So we need to shift by (k - reusable_chars)
 * ```
 * 
 * **Example with pattern "ABABCABAB":**
 * ```
 * LPS = [0, 0, 1, 2, 0, 1, 2, 3, 4]
 * 
 * If mismatch at position 6 (pattern[6] != text[?]):
 * - We matched 6 characters: "ABABCA"
 * - LPS[5] = 1, meaning 1 character reusable
 * - Shift = 6 - 1 = 5 positions
 * ```
 * 
 * ## USAGE EXAMPLES
 * 
 * ```java
 * // Basic window-based search
 * List<Integer> matches = MorrisPrattSearch.search("ababcababa", "abab");
 * // Returns: [0, 5] with clear window movements
 * 
 * // Overlapping pattern detection
 * List<Integer> matches = MorrisPrattSearch.search("aaaaaaa", "aaa");
 * // Returns: [0, 1, 2, 3, 4] with optimal shifts
 * 
 * // LPS table for window shifting
 * int[] lps = MorrisPrattSearch.buildTable("ABABCABAB");
 * // Returns: [0, 0, 1, 2, 0, 1, 2, 3, 4]
 * ```
 * 
 * Time Complexity: O(n + m) where n = text length, m = pattern length
 * Space Complexity: O(m) for the LPS array
 * 
 * This window-based approach makes Morris-Pratt easier to understand and debug.
 */
public class MorrisPrattSearch {

    // ----------------------------------------------------
    // Morris-Pratt Algorithm (1970) - Original Implementation
    // This is the classic MP algorithm with explicit window shifting,
    // WITHOUT the KMP optimization that checks for redundant comparisons.
    // ----------------------------------------------------

    // ----------------------------------------------------
    // Step 1: Preprocessing - Building the LPS Array (Failure Function)
    // LPS[i] stores the length of the longest proper prefix of pattern[0..i] 
    // that is also a suffix of pattern[0..i].
    // This is the standard failure function used in Morris-Pratt.
    // 
    // Example: For pattern "ABABABC"
    // - LPS[0] = 0 (A has no proper prefix/suffix)
    // - LPS[1] = 0 (AB has no matching prefix/suffix)
    // - LPS[2] = 1 (ABA: prefix "A" matches suffix "A")
    // - LPS[3] = 2 (ABAB: prefix "AB" matches suffix "AB")
    // - LPS[4] = 3 (ABABA: prefix "ABA" matches suffix "ABA")
    // - LPS[5] = 4 (ABABAB: prefix "ABAB" matches suffix "ABAB")
    // - LPS[6] = 0 (ABABABC: no matching prefix/suffix)
    // Result: [0, 0, 1, 2, 3, 4, 0]
    // ----------------------------------------------------
    private static int[] buildLPSArray(String pattern) {
        int m = pattern.length();
        if (m == 0) return new int[0]; // Handle empty pattern
        
        int[] lps = new int[m];
        int len = 0; // Length of the previous longest prefix suffix
        int i = 1;

        lps[0] = 0; // LPS for a single character is always 0

        while (i < m) {
            if (pattern.charAt(i) == pattern.charAt(len)) {
                // Characters match: extend the current prefix/suffix length
                len++;
                lps[i] = len;
                i++;
            } else {
                // Characters don't match
                if (len != 0) {
                    // Try a shorter prefix/suffix by using the failure function
                    len = lps[len - 1];
                    // Don't increment i, recheck with the new len
                } else {
                    // No prefix/suffix match possible
                    lps[i] = 0;
                    i++;
                }
            }
        }
        return lps;
    }

    // ----------------------------------------------------
    // Public method for testing the table building functionality
    // ----------------------------------------------------
    /**
     * Builds the LPS (Longest Proper Prefix which is also Suffix) array for the given pattern.
     * This is the failure function used by the Morris-Pratt algorithm.
     * 
     * @param pattern the pattern string (must not be null)
     * @return LPS array where LPS[i] is the length of the longest proper prefix 
     *         of pattern[0..i] that is also a suffix of pattern[0..i]
     * @throws IllegalArgumentException if pattern is null
     */
    public static int[] buildTable(String pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException("Pattern cannot be null");
        }
        return buildLPSArray(pattern);
    }

    // ----------------------------------------------------
    // Step 2: Morris-Pratt Search with Explicit Window Shifting
    // This implements the original MP algorithm where we explicitly
    // track window positions and shift the pattern based on the LPS array.
    // ----------------------------------------------------
    /**
     * Searches for all occurrences of a pattern in the given text using Morris-Pratt algorithm.
     * 
     * This implementation uses explicit window shifting, tracking the current window position
     * and using the LPS array to determine optimal shift distances when mismatches occur.
     * 
     * **WINDOW-BASED ALGORITHM WALKTHROUGH:**
     * 
     * **Example: Searching "ABA" in "ABABA":**
     * ```
     * Text:    A B A B A
     * Index:   0 1 2 3 4
     * Pattern: A B A (LPS = [0,0,1])
     * 
     * Window 1: textPos=0
     * Text:    [A B A] B A  <- window covers text[0..2]
     * Pattern:  A B A
     *           ✓ ✓ ✓  → MATCH at position 0!
     * 
     * Shift calculation: m - LPS[m-1] = 3 - 1 = 2
     * New textPos = 0 + 2 = 2
     * 
     * Window 2: textPos=2
     * Text:    A B [A B A] <- window covers text[2..4]
     * Pattern:      A B A
     *               ✓ ✓ ✓  → MATCH at position 2!
     * 
     * Result: [0, 2]
     * ```
     * 
     * **Window Shift Strategy:**
     * - On mismatch at pattern[k]: shift = k - LPS[k-1]
     * - On full match: shift = m - LPS[m-1] (for overlapping matches)
     * - No match at start: shift = 1 (slide window one position)
     * 
     * @param text the text to search in (must not be null)
     * @param pattern the pattern to search for (must not be null)
     * @return list of starting positions where the pattern is found, empty list if not found
     * @throws IllegalArgumentException if text or pattern is null
     */
    public static List<Integer> search(String text, String pattern) {
        // Null validation
        if (text == null || pattern == null) {
            throw new IllegalArgumentException("Text and pattern cannot be null");
        }
        
        int n = text.length();
        int m = pattern.length();
        if (m == 0 || n < m) return new ArrayList<>();

        /*
         * STEP 1: Build LPS array for optimal window shifting
         * 
         * The LPS array is the key to Morris-Pratt's efficiency.
         * It tells us how to shift the window when mismatches occur.
         */
        int[] lps = buildLPSArray(pattern);
        List<Integer> matches = new ArrayList<>();

        /*
         * STEP 2: Initialize window position
         * 
         * textPos represents where our pattern window starts in the text.
         * Think of it as sliding a pattern-sized window across the text.
         */
        int textPos = 0; // Current window start position in text

        /*
         * STEP 3: Main search loop - slide window across text
         * 
         * We continue until the window would extend beyond the text.
         * The condition (textPos <= n - m) ensures we don't read past text end.
         */
        while (textPos <= n - m) {
            /*
             * STEP 3a: Try to match pattern at current window position
             * 
             * patternPos tracks our position within the pattern
             * textIndex tracks our current comparison position in text
             */
            int patternPos = 0; // Position within pattern (0 to m-1)
            int textIndex = textPos; // Current text position being compared

            /*
             * STEP 3b: Character-by-character matching within current window
             * 
             * Continue matching while:
             * 1. We haven't reached end of pattern (patternPos < m)
             * 2. We haven't reached end of text (textIndex < n)  
             * 3. Characters match (pattern[patternPos] == text[textIndex])
             */
            while (patternPos < m && textIndex < n && 
                   pattern.charAt(patternPos) == text.charAt(textIndex)) {
                patternPos++;   // Move to next pattern character
                textIndex++;    // Move to next text character
            }

            /*
             * STEP 3c: Analyze match result and determine next window position
             */
            if (patternPos == m) {
                /*
                 * CASE 1: Complete pattern match found
                 * 
                 * We've matched all m characters of the pattern.
                 * Record the match and calculate shift for next potential match.
                 */
                matches.add(textPos);
                
                /*
                 * Calculate optimal shift for overlapping matches
                 * 
                 * Formula: shift = m - LPS[m-1]
                 * 
                 * Example: pattern "ABA", LPS=[0,0,1]
                 * After finding match, shift = 3 - 1 = 2
                 * This allows us to find overlapping occurrences efficiently.
                 */
                int shift = m - lps[m - 1];
                textPos += shift;
            } else {
                /*
                 * CASE 2: Mismatch occurred or reached text end
                 * 
                 * Determine shift distance based on how much we matched.
                 */
                if (patternPos == 0) {
                    /*
                     * CASE 2a: No characters matched (mismatch at first character)
                     * 
                     * Since first character doesn't match, slide window by 1.
                     * No smart shifting possible - must check next position.
                     */
                    textPos++;
                } else {
                    /*
                     * CASE 2b: Partial match, then mismatch
                     * 
                     * We matched patternPos characters, then failed.
                     * Use LPS array to calculate optimal shift distance.
                     * 
                     * Formula: shift = patternPos - LPS[patternPos-1]
                     * 
                     * Why this works:
                     * - We matched patternPos characters successfully
                     * - LPS[patternPos-1] tells us how many characters we can reuse
                     * - We shift by the difference to align reusable prefix
                     */
                    int shift = patternPos - lps[patternPos - 1];
                    textPos += shift;
                }
            }
        }

        return matches;
    }
}
