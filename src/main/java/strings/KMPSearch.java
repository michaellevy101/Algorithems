package strings;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * KMP (Knuth-Morris-Pratt) String Matching Algorithm with Optimization
 * 
 * This implementation provides the true KMP algorithm with the crucial optimization
 * that distinguishes it from Morris-Pratt: it avoids redundant character comparisons
 * by using negative values in the failure function table.
 * 
 * ## ALGORITHM OVERVIEW
 * 
 * The KMP algorithm builds upon Morris-Pratt by adding an optimization step that
 * eliminates unnecessary character comparisons. When we know that characters will
 * match due to the pattern's structure, we can skip the comparison entirely.
 * 
 * ## KEY INNOVATION: Negative Values in Failure Function
 * 
 * **Visual Comparison for pattern "ABABABC":**
 * ```
 * Pattern:     A B A B A B C
 * Index:       0 1 2 3 4 5 6
 * MP Table:   [0 0 1 2 3 4 0]    <- Standard Morris-Pratt LPS
 * KMP Table:  [-1 0 -1 0 -1 0 4] <- KMP with optimization
 * ```
 * 
 * **Why -1 Values?**
 * When we have a mismatch at position i and need to shift, if pattern[table[i]] 
 * equals pattern[i], we know the comparison will fail again. The -1 tells us to
 * skip this comparison and move the text pointer instead.
 * 
 * ## CONCRETE EXAMPLE
 * 
 * **Searching "ABABABC" in "XABABABABCX":**
 * ```
 * Text:    X A B A B A B A B C X
 * Index:   0 1 2 3 4 5 6 7 8 9 10
 * 
 * Step 1: Align at position 1
 * Pattern: A B A B A B C
 * Text:      A B A B A B A B C
 *            ↑ mismatch at position 6
 * 
 * Step 2: KMP optimization kicks in
 * - table[6] = 4, but pattern[4]='A' = pattern[6]='C'? No, so use table[4] = -1
 * - The -1 means skip comparison, just advance text pointer
 * 
 * Step 3: New alignment at position 3  
 * Pattern:     A B A B A B C
 * Text:      A B A B A B A B C
 *                ✓ match found!
 * ```
 * 
 * ## USAGE EXAMPLES
 * 
 * ```java
 * // Basic search
 * List<Integer> matches = KMPSearch.search("ababcababa", "abab");
 * // Returns: [0, 5] - finds overlapping matches efficiently
 * 
 * // Periodic pattern (KMP's strength)
 * List<Integer> matches = KMPSearch.search("aaaaaa", "aaa");  
 * // Returns: [0, 1, 2, 3] - handles repetitive patterns optimally
 * 
 * // Table building
 * int[] kmpTable = KMPSearch.buildTable("ABABABC");
 * // Returns: [-1, 0, -1, 0, -1, 0, 4] with optimizations
 * ```
 * 
 * Time Complexity: O(n + m) where n = text length, m = pattern length
 * Space Complexity: O(m) for the KMP table
 * 
 * The negative values make this a true KMP implementation, not just Morris-Pratt.
 */
public class KMPSearch {

    private static final Logger LOG = LogManager.getLogger(KMPSearch.class);

    /**
     * Searches for all occurrences of a pattern in the given text using KMP algorithm.
     * 
     * @param str the text to search in
     * @param word the pattern to search for
     * @return list of starting positions where the pattern is found
     */
    public static List<Integer> search(String str, String word) {
        // Null validation
        if (str == null || word == null) {
            throw new IllegalArgumentException("Text and pattern cannot be null");
        }
        
        if (str.length() == 0 || word.length() == 0)
            return Collections.emptyList();

        char[] text = str.toCharArray();
        char[] pattern = word.toCharArray();
        List<Integer> results = new LinkedList<>();

        int[] table = buildTable(pattern);

        int i = 0;
        int j = 0;

        while (i <= text.length - pattern.length)  {
            while (j < pattern.length && text[i + j] == pattern[j]) {
                LOG.debug("pattern[" + j + "] = " + pattern[j] + " == text[" +(i + j) + "] = " + text[i + j]);
                j++;
            }

            if (j == pattern.length) {
                LOG.debug("Pattern found at index = " + i);
                results.add(i);
                i = i + j - table[j];
                j = table[j];
                LOG.debug("Switching window: text index = " + i + ", pattern index " + j);
                continue;
            }

            LOG.debug("Mismatch occurred: pattern[" + j + "] = " + pattern[j] + " != text[" +(i + j) + "] = " + text[i + j]);
            i = i + j - table[j];
            j = Math.max(0, table[j]);
            LOG.debug("Switching window: text index = " + i + ", pattern index " + j);
        }

        return results;
    }

    /**
     * Builds the KMP failure function table for the given pattern.
     * This table is used to determine how far to shift the pattern when a mismatch occurs.
     * 
     * @param pattern the pattern string
     * @return KMP table array
     */
    public static final int[] buildTable(String pattern){
        if (pattern == null) {
            throw new IllegalArgumentException("Pattern cannot be null");
        }
        return buildTable(pattern.toCharArray());
    }

    /**
     * Builds the KMP failure function table for the given pattern.
     * 
     * The KMP table includes optimizations over the standard Morris-Pratt table:
     * - Negative values (-1) indicate positions where character comparisons can be skipped
     * - This avoids redundant comparisons that are guaranteed to fail
     * 
     * **STEP-BY-STEP EXAMPLE for pattern "ABABABC":**
     * ```
     * Pattern:  A B A B A B C
     * Index:    0 1 2 3 4 5 6
     * MP Table: 0 0 1 2 3 4 0  <- Standard Morris-Pratt LPS
     * 
     * KMP Optimization Process:
     * 
     * j=1: t=MP[0]=0, pattern[1]='B' == pattern[0]='A'? No
     *      Since t=0 and pattern[1]≠pattern[0] → kmpTable[1]=0
     * 
     * j=2: t=MP[1]=0, pattern[2]='A' == pattern[0]='A'? Yes  
     *      Since pattern[2]==pattern[0] → kmpTable[2]=-1 (skip comparison)
     * 
     * j=3: t=MP[2]=1, pattern[3]='B' == pattern[1]='B'? Yes
     *      Since pattern[3]==pattern[1] → kmpTable[3]=-1 (skip comparison)
     *      But t>0, so while loop: t=MP[0]=0
     *      Now t=0 and pattern[3]≠pattern[0] → kmpTable[3]=0
     * 
     * j=4: t=MP[3]=2, pattern[4]='A' == pattern[2]='A'? Yes
     *      Since pattern[4]==pattern[2] → skip, use t=MP[1]=0
     *      Since pattern[4]=='A'==pattern[0] → kmpTable[4]=-1
     * 
     * j=5: t=MP[4]=3, pattern[5]='B' == pattern[3]='B'? Yes  
     *      Since pattern[5]==pattern[3] → skip, use t=MP[2]=1
     *      Since pattern[5]=='B'==pattern[1] → skip, use t=MP[0]=0
     *      Since t=0 and pattern[5]≠pattern[0] → kmpTable[5]=0
     * 
     * j=6: t=MP[5]=4, pattern[6]='C' == pattern[4]='A'? No
     *      Since t>0 → kmpTable[6]=4
     * 
     * Final KMP Table: [-1, 0, -1, 0, -1, 0, 4, 0]
     * ```
     * 
     * **THE KMP OPTIMIZATION LOGIC:**
     * ```
     * When should we use -1?
     * 
     * If we're at position j and have a mismatch, we'd normally check 
     * pattern[MP[j-1]] vs text[current]. But if pattern[j] == pattern[MP[j-1]],
     * we KNOW this comparison will fail again!
     * 
     * Example: pattern="ABAB", text="ABAC"
     * - Mismatch at j=3: pattern[3]='B' vs text[3]='C' 
     * - MP says try position MP[2]=1: pattern[1]='B' vs text[3]='C'
     * - But pattern[3]='B' == pattern[1]='B', so this will fail too!
     * - KMP optimization: Use -1 to skip this doomed comparison
     * ```
     * 
     * @param pattern the pattern character array
     * @return KMP table array with length = pattern.length + 1
     */
    protected static final int[] buildTable(char[] pattern){
        if (pattern.length == 0)
            return new int[0];

        /*
         * STEP 1: Initialize KMP table and get MP function
         * 
         * KMP table has length pattern.length + 1 (extra slot for boundary case)
         * We start with the Morris-Pratt failure function, then add optimizations
         */
        int[] kmpTable = new int[pattern.length + 1];
        int[] mpFunction = MPSearch.buildTable(pattern);

        /*
         * STEP 2: Set boundary values
         * 
         * kmpTable[0] = -1: Special marker for beginning of pattern
         * kmpTable[pattern.length] = MP[pattern.length-1]: Copy final MP value
         */
        kmpTable[0] = -1;
        kmpTable[pattern.length] = mpFunction[pattern.length - 1];

        /*
         * STEP 3: Apply KMP optimization for each position
         * 
         * For each position j, determine if we can optimize by skipping
         * redundant character comparisons using the -1 marker
         */
        for (int j = 1; j < pattern.length; ++j) {
            int t = mpFunction[j - 1];  // Start with MP failure function value

            /*
             * STEP 3a: Check if current optimization can be further optimized
             * 
             * If pattern[j] == pattern[t], then when we fail at j and try position t,
             * we'll fail again because the characters are the same. Keep looking for
             * a better position by following the MP chain.
             */
            while (t > 0 && pattern[j] == pattern[t])
                t = mpFunction[t - 1];

            /*
             * STEP 3b: Decide final KMP table value
             */
            if (t > 0) {
                // Found a valid fallback position that won't immediately fail
                kmpTable[j] = t;
            } else if (pattern[j] == pattern[0]) {
                // Special case: if pattern[j] matches first character, 
                // we know the comparison will fail, so use -1 to skip it entirely
                kmpTable[j] = -1;
            } else {
                // No optimization possible, use 0 (start over)
                kmpTable[j] = 0;
            }
        }

        return kmpTable;
    }
}
