package strings;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class KMPSearch {

    public static final List<Integer> search(String str, String word){
        if (str.length() == 0 || word.length() == 0)
            return Collections.emptyList();

        char[] text = str.toCharArray();
        char[] pattern = word.toCharArray();
        List<Integer> results = new LinkedList<>();

        int[] table = buildTable(pattern);

        int matchingCharactersLength = 0;

        for (int textIndex = 0; textIndex < text.length; ++textIndex) {
            while (matchingCharactersLength > 0 && pattern[matchingCharactersLength] != text[textIndex])
                matchingCharactersLength = table[matchingCharactersLength -1];

            if (pattern[matchingCharactersLength] == text[textIndex])
                matchingCharactersLength++;

            if (matchingCharactersLength == pattern.length) {
                results.add(textIndex - pattern.length + 1);
                matchingCharactersLength = table[matchingCharactersLength - 1];
            }
        }

        return results;
    }

    public static final int[] buildTable(String pattern){
        return buildTable(pattern.toCharArray());
    }

    private static final int[] buildTable(char[] pattern){
        int[] commonPrefixSuffixTable = new int[pattern.length];

        if (pattern.length == 0)
            return commonPrefixSuffixTable;

        commonPrefixSuffixTable[0] = 0;
        int commonPrefixSuffixLength = 0;

        for (int index = 2; index < pattern.length; ++index) {
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
                Let's represent the pattern "AACAAA" as ABCDEF and given that: A == D && B == E && C != F.
                In order to shift our common prefix suffix with size 2 (as before) the following conditions must be obtained:
                1.  A == E -> but since E == B (given) we can check if A == B.
                2.  B == F -> but since E == B (given) so if A == B (Condition 1) and F == A -> A == B == E == F.
                */
                commonPrefixSuffixLength = commonPrefixSuffixTable[commonPrefixSuffixLength - 1];

            if (pattern[index] == pattern[commonPrefixSuffixLength])
                commonPrefixSuffixLength++;

            commonPrefixSuffixTable[index] = commonPrefixSuffixLength;
        }

        return commonPrefixSuffixTable;
    }

    public static void main(String[] args) {
        System.out.println(Arrays.toString(buildTable("AAAACAAAAB")));
        System.out.println(Arrays.toString(buildTable("COCACOLA")));
        System.out.println(Arrays.toString(buildTable("AAAAAA")));
        System.out.println(Arrays.toString(buildTable("ABABABC")));
        System.out.println(Arrays.toString(buildTable("ABCDABD ")));
        System.out.println(Arrays.toString(buildTable("ABACABABC ")));
        System.out.println(Arrays.toString(buildTable("ABACABABA ")));
        System.out.println(Arrays.toString(buildTable("PARTICIPATE IN PARACHUTE ")));
        System.out.println(search("ABABABABABABC", "ABABABC"));
    }

}
