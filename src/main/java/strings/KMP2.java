package strings;


import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class KMP2 {

    public static List<Integer> search(String str, String word) {
        if (str.length() == 0 || word.length() == 0)
            return Collections.emptyList();

        char[] text = str.toCharArray();
        char[] pattern = word.toCharArray();
        List<Integer> results = new LinkedList<>();

        int[] table = buildTable(pattern);

        int i = 0;
        int j = 0;

        while (i <= text.length - pattern.length)  {
            while (j < pattern.length && text[i + j] == pattern[j])
                j++;

            if (j == pattern.length) {
                results.add(i);
                i = i + j - table[j];
                j = table[j] + 1;
                continue;
            }

            i = i + j - table[j];
            j = Math.max(0, table[j]);
        }

        return results;
    }

    public static final int[] buildTable(String pattern){
        return buildTable(pattern.toCharArray());
    }

    public static final int[] buildTable(char[] pattern){
        if (pattern.length == 0)
            return new int[0];

        int[] kmpTable = new int[pattern.length + 1];
        int[] mpFunction = KMPSearch.buildTable(pattern);

        kmpTable[0] = -1;
        kmpTable[pattern.length] = mpFunction[pattern.length - 1];

        for (int j = 1; j < pattern.length; ++j) {
            int t = mpFunction[j - 1];

            while (t != 0 && pattern[j] == pattern[t])
                t = mpFunction[t - 1];

            if (t != 0){
                kmpTable[j] = t;
            } else if (pattern[j] == pattern[0]){
                kmpTable[j] = -1;
            } else {
                kmpTable[j] = 0;
            }
        }

        return kmpTable;
    }
}