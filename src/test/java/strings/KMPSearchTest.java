package strings;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the original KMP search algorithm.
 * Extends the reusable test base class with comprehensive test cases.
 */
class KMPSearchTest extends StringMatchingAlgorithmTest {

    @Override
    protected List<Integer> invoke(String text, String pattern) {
        return KMPSearch.search(text, pattern);
    }

    // KMP specific table building tests
    @Test
    public void kmpTableTestEmptyPattern(){
        assertArrayEquals(new int[]{}, KMPSearch.buildTable(""));
    }

    @Test
    public void kmpTableTestSingleChar(){
        assertArrayEquals(new int[]{-1, 0}, KMPSearch.buildTable("a"));
    }

    @Test
    public void kmpTableTestNoRepeats(){
        assertArrayEquals(new int[]{-1, 0, 0, 0}, KMPSearch.buildTable("abc"));
    }

    @Test
    public void kmpTableTestSimpleRepeats(){
        assertArrayEquals(new int[]{-1, -1, 1}, KMPSearch.buildTable("aa"));
    }

    @Test
    public void kmpTableTestComplexPattern(){
        assertArrayEquals(new int[]{-1, 0, 0, -1, 1, -1, 1, -1, 1}, KMPSearch.buildTable("gcagagag"));
    }

    @Test
    public void kmpTableTestABCABCABC(){
        assertArrayEquals(new int[]{-1, 0, 0, -1, 0, 0, -1, 0, 0, 6}, KMPSearch.buildTable("abcabcabc"));
    }

    @Test
    public void kmpTableTestANANAS(){
        assertArrayEquals(new int[]{-1, 0, -1, 0, -1, 3, 0}, KMPSearch.buildTable("ANANAS"));
    }

    @Test
    public void kmpTableTestABABABC(){
        assertArrayEquals(new int[]{-1, 0, -1, 0, -1, 0, 4, 0}, KMPSearch.buildTable("ABABABC"));
    }
}
