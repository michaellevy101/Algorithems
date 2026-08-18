package strings;

import java.util.*;

public class DebugTest {
    public static void main(String[] args) {
        // Test the failing case
        List<String> patterns = Arrays.asList("HE", "SHE", "HIS", "HERS");
        Map<String, List<Integer>> results = AhoCorasickSearch.searchMultiple("USHERS", patterns);
        
        System.out.println("Text: USHERS");
        System.out.println("Patterns: " + patterns);
        System.out.println("Results found: " + results.size());
        
        for (Map.Entry<String, List<Integer>> entry : results.entrySet()) {
            System.out.println("Pattern '" + entry.getKey() + "' found at positions: " + entry.getValue());
        }
        
        // Let's also test the case insensitive issue
        System.out.println("\n--- Case Insensitive Test ---");
        List<String> patterns2 = Arrays.asList("ABC", "bcd", "CdE");
        Map<String, List<Integer>> results2 = AhoCorasickSearch.searchMultiple("AbCdEfAbC", patterns2, true);
        
        System.out.println("Text: AbCdEfAbC");
        System.out.println("Patterns: " + patterns2);
        System.out.println("Results found: " + results2.size());
        
        for (Map.Entry<String, List<Integer>> entry : results2.entrySet()) {
            System.out.println("Pattern '" + entry.getKey() + "' found at positions: " + entry.getValue());
        }
    }
}
