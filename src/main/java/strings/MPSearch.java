package strings;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Morris-Pratt String Matching Algorithm (1970) - Educational Implementation
 * 
 * This implementation provides the original Morris-Pratt algorithm with comprehensive
 * debug logging and detailed explanations. MP was the groundbreaking algorithm that
 * first achieved O(n + m) time complexity for string matching by introducing the
 * failure function (LPS array) concept.
 * 
 * ## ALGORITHM OVERVIEW
 * 
 * Morris-Pratt revolutionized string matching by recognizing that when a mismatch
 * occurs, we don't need to restart comparison from the beginning. Instead, we can
 * use information about the pattern's structure to skip redundant comparisons.
 * 
 * ## KEY INNOVATION: The Failure Function (LPS Array)
 * 
 * **The Core Insight:**
 * When we have a mismatch after matching k characters, we ask: "What's the longest
 * prefix of the pattern that's also a suffix of what we just matched?"
 * 
 * **Visual Example for pattern "ABABCABAB":**
 * ```
 * Pattern: A B A B C A B A B
 * Index:   0 1 2 3 4 5 6 7 8
 * LPS:     0 0 1 2 0 1 2 3 4
 * 
 * At position 8 (end): LPS[8] = 4
 * This means: "ABAB" (prefix) = "ABAB" (suffix)
 * So if we fail after matching the full pattern, we can continue
 * from position 4 instead of restarting from 0!
 * ```
 * 
 * ## SEARCH ALGORITHM VISUALIZATION
 * 
 * **Searching "ABAB" in "ABABCABABAB":**
 * ```
 * Text:    A B A B C A B A B A B
 * Index:   0 1 2 3 4 5 6 7 8 9 10
 * Pattern: A B A B (LPS = [0,0,1,2])
 * 
 * textIndex=0, matchingLength=0:
 * pattern[0]='A' == text[0]='A' ✓ → matchingLength=1
 * 
 * textIndex=1, matchingLength=1:
 * pattern[1]='B' == text[1]='B' ✓ → matchingLength=2
 * 
 * textIndex=2, matchingLength=2:
 * pattern[2]='A' == text[2]='A' ✓ → matchingLength=3
 * 
 * textIndex=3, matchingLength=3:
 * pattern[3]='B' == text[3]='B' ✓ → matchingLength=4
 * matchingLength == pattern.length → MATCH at position 0!
 * 
 * Reset: matchingLength = LPS[3] = 2 (smart restart!)
 * 
 * textIndex=4, matchingLength=2:
 * pattern[2]='A' != text[4]='C' ✗
 * matchingLength = LPS[1] = 0
 * pattern[0]='A' != text[4]='C' ✗ → continue
 * 
 * ...eventually finds next match at position 7
 * ```
 * 
 * ## WHY IT WORKS: The Mathematical Foundation
 * 
 * **Prefix-Suffix Property:**
 * If we've matched k characters and fail at position k, and LPS[k-1] = j,
 * then we know pattern[0..j-1] appears at the end of our matched text.
 * So we can safely skip ahead and start comparing from pattern[j].
 * 
 * **Example:**
 * ```
 * Pattern: A B A B C
 * Text:    A B A B X...
 *          ✓ ✓ ✓ ✓ ✗  (fail at position 4)
 * 
 * LPS[3] = 2, meaning "AB" (prefix) = "AB" (suffix)
 * So we know text ends with "...AB", we can restart from pattern[2]
 * ```
 * 
 * ## USAGE EXAMPLES
 * 
 * ```java
 * // Basic search with debug logging
 * List<Integer> matches = MPSearch.search("ababcababa", "abab");
 * // Returns: [0, 5] with detailed debug output
 * 
 * // Overlapping matches
 * List<Integer> matches = MPSearch.search("aaaaaaa", "aaa");
 * // Returns: [0, 1, 2, 3, 4] - demonstrates prefix reuse
 * 
 * // LPS table construction
 * int[] lps = MPSearch.buildTable("ABABCABAB");
 * // Returns: [0, 0, 1, 2, 0, 1, 2, 3, 4] with explanation
 * ```
 * 
 * Time Complexity: O(n + m) where n = text length, m = pattern length
 * Space Complexity: O(m) for the LPS array
 * 
 * Morris-Pratt is the foundation algorithm that inspired KMP and many other optimizations.
 */
public class MPSearch {

    private static final Logger LOG = LogManager.getLogger(MPSearch.class);

    /**
     * Searches for all occurrences of a pattern in the given text using Morris-Pratt algorithm.
     * 
     * **ALGORITHM WALKTHROUGH:**
     * 
     * The MP search maintains two key variables:
     * - textIndex: current position in text (increments every iteration)
     * - matchingCharactersLength: how many characters we've matched so far
     * 
     * **EXAMPLE: Searching "ABA" in "ABABA":**
     * ```
     * Text:    A B A B A
     * Index:   0 1 2 3 4
     * Pattern: A B A (LPS = [0,0,1])
     * 
     * textIndex=0, matchingLength=0:
     *   pattern[0]='A' == text[0]='A' ✓ → matchingLength=1
     * 
     * textIndex=1, matchingLength=1:
     *   pattern[1]='B' == text[1]='B' ✓ → matchingLength=2
     * 
     * textIndex=2, matchingLength=2:
     *   pattern[2]='A' == text[2]='A' ✓ → matchingLength=3
     *   matchingLength == pattern.length → MATCH at position 0!
     *   Reset: matchingLength = LPS[2] = 1 (reuse prefix "A")
     * 
     * textIndex=3, matchingLength=1:
     *   pattern[1]='B' == text[3]='B' ✓ → matchingLength=2
     * 
     * textIndex=4, matchingLength=2:
     *   pattern[2]='A' == text[4]='A' ✓ → matchingLength=3
     *   MATCH at position 2!
     * ```
     * 
     * @param str the text to search in (must not be null)
     * @param word the pattern to search for (must not be null)
     * @return list of starting positions where the pattern is found, empty list if not found
     * @throws IllegalArgumentException if text or pattern is null
     */
    public static List<Integer> search(String str, String word){
        // Null validation
        if (str == null || word == null) {
            throw new IllegalArgumentException("Text and pattern cannot be null");
        }
        
        if (str.isEmpty() || word.isEmpty())
            return Collections.emptyList();

        char[] text = str.toCharArray();
        char[] pattern = word.toCharArray();
        List<Integer> results = new LinkedList<>();

        /*
         * STEP 1: Build the LPS table for smart shifting
         * 
         * This preprocessing step creates the failure function that tells us
         * how to shift the pattern when a mismatch occurs.
         */
        int[] table = buildTable(pattern);

        /*
         * STEP 2: Initialize search variables
         * 
         * matchingCharactersLength tracks how many characters of the pattern
         * we've successfully matched so far. This is both:
         * 1. The count of matched characters
         * 2. The index of next character to compare in pattern
         */
        int matchingCharactersLength = 0;

        /*
         * STEP 3: Main search loop
         * 
         * We iterate through each character in the text. The key insight is that
         * we never move backward in the text - only forward or stay in place.
         * 
         * Optimization: If remaining text is shorter than remaining pattern,
         * we can stop early (pattern.length - matchingCharactersLength <= text.length - textIndex)
         */
        for (int textIndex = 0; textIndex < text.length && pattern.length - matchingCharactersLength <= text.length - textIndex; ++textIndex) {
            /*
             * STEP 3a: Handle mismatches using failure function
             * 
             * When we have a mismatch but have already matched some characters,
             * we use the LPS table to determine how to shift the pattern.
             * This while loop may execute multiple times if we have nested failures.
             */
            while (matchingCharactersLength > 0 && pattern[matchingCharactersLength] != text[textIndex]) {
                LOG.debug("Mismatch occurred: pattern[" + matchingCharactersLength + "] = " + pattern[matchingCharactersLength] + " != text[" + textIndex + "] = " + text[textIndex]);
                
                /*
                 * Use failure function to determine new pattern position
                 * 
                 * table[matchingCharactersLength - 1] tells us how many characters
                 * from the beginning of the pattern match the end of what we just matched.
                 * This allows us to skip redundant comparisons.
                 */
                matchingCharactersLength = table[matchingCharactersLength - 1];
                LOG.debug("Pattern index = " + matchingCharactersLength);
            }

            /*
             * STEP 3b: Compare current characters
             * 
             * After handling any mismatches above, we compare the current
             * characters and update our matching length accordingly.
             */
            if (pattern[matchingCharactersLength] != text[textIndex]) {
                // Mismatch at start of pattern (matchingCharactersLength = 0)
                LOG.debug("Mismatch occurred: pattern[" + matchingCharactersLength + "] = " + pattern[matchingCharactersLength] + " != text[" + textIndex + "] = " + text[textIndex]);
                // matchingCharactersLength stays 0, textIndex will increment
            } else {
                // Characters match - extend our current match
                LOG.debug("pattern[" + matchingCharactersLength + "] = " + pattern[matchingCharactersLength] + " == text[" + textIndex + "] = " + text[textIndex]);
                matchingCharactersLength++;
            }

            /*
             * STEP 3c: Check for complete pattern match
             * 
             * If we've matched the entire pattern, record the match position
             * and use the failure function to set up for finding the next match.
             */
            if (matchingCharactersLength == pattern.length) {
                // Calculate match start position
                int matchPosition = textIndex - pattern.length + 1;
                LOG.debug("Pattern found at index = " + matchPosition);
                results.add(matchPosition);
                
                /*
                 * Prepare for next potential match using failure function
                 * 
                 * Instead of restarting from 0, we use table[pattern.length - 1]
                 * to determine how much of the pattern prefix we can reuse.
                 * This enables finding overlapping matches efficiently.
                 */
                matchingCharactersLength = table[matchingCharactersLength - 1];
                LOG.debug("Pattern index = " + matchingCharactersLength);
            }
        }

        return results;
    }

    /**
     * Builds the Morris-Pratt failure function table for the given pattern.
     * This table stores the length of the longest proper prefix that is also a suffix
     * for each position in the pattern.
     * 
     * @param pattern the pattern string (must not be null)
     * @return LPS (Longest Proper Prefix which is also Suffix) array
     * @throws IllegalArgumentException if pattern is null
     */
    public static int[] buildTable(String pattern){
        if (pattern == null) {
            throw new IllegalArgumentException("Pattern cannot be null");
        }
        return buildTable(pattern.toCharArray());
    }

    /**
     * Builds the Morris-Pratt failure function table for the given pattern.
     * 
     * The LPS array helps determine how many characters can be skipped when a mismatch occurs.
     * For each position i, LPS[i] contains the length of the longest proper prefix of 
     * pattern[0..i] that is also a suffix of pattern[0..i].
     * 
     * Example: For pattern "ABABCABAB"
     * LPS = [0, 0, 1, 2, 0, 1, 2, 3, 4]
     * 
     * @param pattern the pattern character array
     * @return LPS array of same length as pattern
     */
    public static int[] buildTable(char[] pattern){
        int[] commonPrefixSuffixTable = new int[pattern.length];

        if (pattern.length == 0)
            return commonPrefixSuffixTable;

        commonPrefixSuffixTable[0] = 0;
        int commonPrefixSuffixLength = 0;

        for (int index = 1; index < pattern.length; ++index) {
            /*
            This loop trying to locate the maximum common prefix suffix for the pattern until the current index.
            The idea is that if the last prefix character equals to the last suffix character we should increment the common prefix suffix length.
            For instance the pattern "ABABAC" starts with the substring "ABABA" which has the common prefix+suffix ABA,
            so the commonPrefixSuffixLength = 3, and the index is 4.
            Now our index incremented to 5 and pattern[index] = 'C' which is the end of the pattern suffix.
            If our prefix ends with 'c', our common prefix suffix length will be 4 else we have to recalculate the common prefix suffix length.
            In our example: pattern[5] ('C') != pattern[3] ('B').
            If our pattern was "ABABAB" -> pattern[5] ('B') == pattern[3] ('B') and our commonPrefixSuffixLength will be incremented to 4.
            */

            /*
              If we have common prefix suffix characters and the current char (End of the suffix) != commonPrefixSuffixLength(End of the prefix).
             */
            while (commonPrefixSuffixLength > 0 && pattern[index] != pattern[commonPrefixSuffixLength])
                /*
                Decrease the common prefix suffix length and try to compare again.
                For example: if the pattern is "AACAAA" and for index 5 the commonPrefixSuffixLength is 2 "AA",
                We can't increment the commonPrefixSuffixLength to 3 because pattern[5] ('A) != pattern[2] ('C')
                so try to create new common prefix suffix with length of 2 ("AA").

                In order to find out a shorter suffix of the pattern let's look into the facts:
                The pattern "AACAA" has a common prefix suffix of length 2 (indexes: [0-1],[3-4]).
                We can't add the last A to the common prefix suffix because pattern[5] ('A) != pattern[2] ('C').
                So maybe there is a shorter common prefix suffix that ends at index 4 (Add the new char at index 5 later).

                This shorter suffix must be the suffix of the previous suffix (suffix of index [3-4]) and because [0-1] == [3-4]
                we can check that in the location 1 that we already calculated.
                */
                commonPrefixSuffixLength = commonPrefixSuffixTable[commonPrefixSuffixLength - 1];

            if (pattern[index] == pattern[commonPrefixSuffixLength])
                commonPrefixSuffixLength++;

            commonPrefixSuffixTable[index] = commonPrefixSuffixLength;
        }

        return commonPrefixSuffixTable;
    }


}
