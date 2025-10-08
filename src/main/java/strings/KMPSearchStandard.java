package strings;

import java.util.ArrayList;
import java.util.List;

/**
 * KMP (Knuth-Morris-Pratt) String Matching Algorithm - Standard Implementation
 * 
 * This implementation provides the classical KMP algorithm as originally described
 * by Knuth, Morris, and Pratt in 1977. It uses the standard LPS (Longest Proper 
 * Prefix which is also Suffix) array without the advanced optimizations found in
 * other KMP variants.
 * 
 * ## ALGORITHM OVERVIEW
 * 
 * The KMP algorithm revolutionized string matching by eliminating backtracking in
 * the text. When a mismatch occurs, instead of sliding the pattern one position,
 * we use the LPS array to determine the optimal shift distance.
 * 
 * ## KEY INSIGHT: Never Move Text Pointer Backward
 * 
 * **Traditional Naive Approach:**
 * ```
 * Text:    A B A B C A B A B C A B
 * Pattern: A B A B C
 *          ↑ mismatch at position 4
 * Naive:   Move pattern 1 position → restart comparison from text[1]
 * ```
 * 
 * **KMP Approach:**
 * ```
 * Text:    A B A B C A B A B C A B  
 * Pattern: A B A B C
 *          ↑ mismatch at position 4
 * KMP:     Use LPS[3]=2 → shift pattern, continue from text position 4
 * Result:  No backtracking in text!
 * ```
 * 
 * ## LPS ARRAY CONSTRUCTION EXAMPLE
 * 
 * **For pattern "ABABCABAB":**
 * ```
 * Pattern: A B A B C A B A B
 * Index:   0 1 2 3 4 5 6 7 8
 * LPS:     0 0 1 2 0 1 2 3 4
 * 
 * Explanation:
 * - LPS[0] = 0: Single character has no proper prefix/suffix
 * - LPS[1] = 0: "AB" has no matching prefix/suffix  
 * - LPS[2] = 1: "ABA" has prefix "A" = suffix "A"
 * - LPS[3] = 2: "ABAB" has prefix "AB" = suffix "AB"
 * - LPS[4] = 0: "ABABC" has no matching prefix/suffix
 * - LPS[5] = 1: "ABABCA" has prefix "A" = suffix "A"
 * - LPS[6] = 2: "ABABCAB" has prefix "AB" = suffix "AB"
 * - LPS[7] = 3: "ABABCABA" has prefix "ABA" = suffix "ABA"
 * - LPS[8] = 4: "ABABCABAB" has prefix "ABAB" = suffix "ABAB"
 * ```
 * 
 * ## COMPLETE SEARCH EXAMPLE
 * 
 * **Searching "ABAB" in "ABABCABABAB":**
 * ```
 * Text:    A B A B C A B A B A B
 * Index:   0 1 2 3 4 5 6 7 8 9 10
 * Pattern: A B A B (LPS = [0,0,1,2])
 * 
 * Step 1: i=0, j=0
 * Text[0]='A' == Pattern[0]='A' ✓ → i=1, j=1
 * Text[1]='B' == Pattern[1]='B' ✓ → i=2, j=2  
 * Text[2]='A' == Pattern[2]='A' ✓ → i=3, j=3
 * Text[3]='B' == Pattern[3]='B' ✓ → i=4, j=4
 * j==4 → MATCH at position 0!
 * 
 * Step 2: j=LPS[3]=2, i=4 (no backtrack!)
 * Text[4]='C' != Pattern[2]='A' ✗
 * j=LPS[1]=0
 * Text[4]='C' != Pattern[0]='A' ✗ → i=5
 * 
 * Step 3: Continue from i=5...
 * Eventually finds match at position 7: "ABAB"
 * ```
 * 
 * ## USAGE EXAMPLES
 * 
 * ```java
 * // Basic search
 * List<Integer> matches = KMPSearchStandard.search("ababcababa", "abab");
 * // Returns: [0, 5] - finds all occurrences
 * 
 * // Overlapping patterns  
 * List<Integer> matches = KMPSearchStandard.search("aaaaaaa", "aaa");
 * // Returns: [0, 1, 2, 3, 4] - handles overlaps correctly
 * 
 * // LPS table construction
 * int[] lps = KMPSearchStandard.buildTable("ABABCABAB");
 * // Returns: [0, 0, 1, 2, 0, 1, 2, 3, 4]
 * ```
 * 
 * Time Complexity: O(n + m) where n = text length, m = pattern length
 * Space Complexity: O(m) for the LPS array
 * 
 * This is the foundational KMP algorithm that inspired many optimized variants.
 */
public class KMPSearchStandard {

    /**
     * Step 1: Preprocessing - Computes the KMP LPS Array
     * 
     * The LPS (Longest Proper Prefix which is also Suffix) array is the heart of KMP.
     * For each position i, LPS[i] stores the length of the longest proper prefix of
     * pattern[0..i] that is also a suffix of pattern[0..i].
     * 
     * **STEP-BY-STEP EXAMPLE for pattern "ABABCABAB":**
     * ```
     * Pattern: A B A B C A B A B
     * Index:   0 1 2 3 4 5 6 7 8
     * 
     * i=0: LPS[0] = 0 (by definition, single char has no proper prefix/suffix)
     * 
     * i=1: len=0, pattern[1]='B' vs pattern[0]='A' → mismatch
     *      len=0, so LPS[1]=0, i→2
     * 
     * i=2: len=0, pattern[2]='A' vs pattern[0]='A' → match!
     *      len→1, LPS[2]=1, i→3
     * 
     * i=3: len=1, pattern[3]='B' vs pattern[1]='B' → match!
     *      len→2, LPS[3]=2, i→4
     * 
     * i=4: len=2, pattern[4]='C' vs pattern[2]='A' → mismatch
     *      len≠0, so len=LPS[1]=0 (try shorter prefix)
     *      len=0, pattern[4]='C' vs pattern[0]='A' → mismatch
     *      len=0, so LPS[4]=0, i→5
     * 
     * i=5: len=0, pattern[5]='A' vs pattern[0]='A' → match!
     *      len→1, LPS[5]=1, i→6
     * 
     * i=6: len=1, pattern[6]='B' vs pattern[1]='B' → match!
     *      len→2, LPS[6]=2, i→7
     * 
     * i=7: len=2, pattern[7]='A' vs pattern[2]='A' → match!
     *      len→3, LPS[7]=3, i→8
     * 
     * i=8: len=3, pattern[8]='B' vs pattern[3]='B' → match!
     *      len→4, LPS[8]=4, done
     * 
     * Final LPS: [0, 0, 1, 2, 0, 1, 2, 3, 4]
     * ```
     * 
     * **THE KEY INSIGHT:**
     * When we have a mismatch at position i with current prefix length 'len',
     * we don't start over. Instead, we use LPS[len-1] to find the next shorter
     * prefix to try. This is the genius of the algorithm!
     * 
     * **Why LPS[len-1]?**
     * If we've matched 'len' characters and now have a mismatch, we know:
     * - pattern[0..len-1] matches text[current-len..current-1]
     * - Since LPS[len-1] gives us the longest prefix/suffix of pattern[0..len-1]
     * - We can skip ahead and try matching from position LPS[len-1]
     */
    private static int[] buildLPSArray(String pattern) {
        int m = pattern.length();
        int[] lps = new int[m];
        
        /*
         * INITIALIZATION:
         * len = length of current longest prefix that's also suffix
         * i = current position we're computing LPS for
         * 
         * We start with len=0 (no prefix yet) and i=1 (LPS[0] is always 0)
         */
        int len = 0; // Length of the previous longest prefix suffix
        int i = 1;   // Start from second character (LPS[0] = 0 by definition)

        /*
         * MAIN LOOP: Compute LPS for each position
         * 
         * This loop maintains two important invariants:
         * 1. LPS[0..i-1] are correctly computed
         * 2. len = LPS[i-1] (length of longest prefix/suffix ending at i-1)
         */
        while (i < m) {
            /*
             * CASE 1: Characters match - extend current prefix/suffix
             * 
             * If pattern[i] == pattern[len], we can extend the current
             * longest prefix/suffix by one character.
             */
            if (pattern.charAt(i) == pattern.charAt(len)) {
                len++;           // Extend prefix length
                lps[i] = len;    // Record new LPS value
                i++;             // Move to next position
            } else {
                /*
                 * CASE 2: Mismatch - try shorter prefix/suffix
                 * 
                 * When characters don't match, we need to find a shorter
                 * prefix that might extend to include pattern[i].
                 */
                if (len != 0) {
                    /*
                     * Try next shorter prefix: use LPS[len-1]
                     * 
                     * This is the key optimization: instead of trying all
                     * possible shorter prefixes, we jump directly to the
                     * next candidate using the LPS array itself!
                     */
                    len = lps[len - 1];
                    // Don't increment i - we recheck pattern[i] with new len
                } else {
                    /*
                     * No shorter prefix possible (len = 0)
                     * 
                     * We've tried all possible prefixes for position i,
                     * so LPS[i] = 0 (no proper prefix/suffix).
                     */
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
     * This is the failure function used by the KMP algorithm.
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
    // Step 2: Searching - KMP is defined by its use of the LPS array
    // The search logic is identical to MP, but the guarantee of O(n+m)
    // is attributed to the combined work of Knuth, Morris, and Pratt.
    // ----------------------------------------------------
    /**
     * Searches for all occurrences of a pattern in the given text using KMP algorithm.
     * 
     * This implementation uses the standard KMP approach where the text pointer never
     * moves backward, and the LPS array is used to determine how far to shift the
     * pattern when a mismatch occurs.
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

        int[] lps = buildLPSArray(pattern);
        List<Integer> matches = new ArrayList<>();

        int i = 0; // Pointer for the text (never moves backward)
        int j = 0; // Pointer for the pattern (also represents length of current match)

        while (i < n) {
            if (pattern.charAt(j) == text.charAt(i)) {
                // Characters match: Move both pointers forward.
                i++;
                j++;
            }

            if (j == m) {
                // Match found: Full pattern matched starting at (i - j).
                matches.add(i - j);

                // Shift: Use the LPS table to find the longest overlapping prefix.
                // 'j' is set to the length of the new prefix to be checked.
                j = lps[j - 1];
            } else if (i < n && pattern.charAt(j) != text.charAt(i)) {
                // Mismatch occurred:
                if (j != 0) {
                    // Smart shift: Use the LPS table to avoid rechecking matched characters.
                    // The text pointer 'i' remains in place.
                    j = lps[j - 1];
                } else {
                    // j is 0: No match at the first character, simply advance text pointer.
                    i++;
                }
            }
        }
        return matches;
    }
}
