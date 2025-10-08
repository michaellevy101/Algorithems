package strings;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the textbook Morris-Pratt search algorithm.
 * Extends the reusable test base class with comprehensive test cases.
 */
class MorrisPrattSearchTest extends StringMatchingAlgorithmTest {

    @Override
    protected List<Integer> invoke(String text, String pattern) {
        return MorrisPrattSearch.search(text, pattern);
    }

    // Morris-Pratt specific table building tests
    @Test
    public void tableTestEmptyPattern(){
        assertArrayEquals(new int[]{}, MorrisPrattSearch.buildTable(""));
    }

    @Test
    public void tableTestSingleChar(){
        assertArrayEquals(new int[]{0}, MorrisPrattSearch.buildTable("a"));
    }

    @Test
    public void tableTestNoRepeats(){
        assertArrayEquals(new int[]{0, 0, 0}, MorrisPrattSearch.buildTable("abc"));
    }

    @Test
    public void tableTestSimpleRepeats(){
        assertArrayEquals(new int[]{0, 1}, MorrisPrattSearch.buildTable("aa"));
    }

    @Test
    public void tableTestComplexPattern(){
        assertArrayEquals(new int[]{0, 0, 0, 1, 0, 1, 0, 1}, MorrisPrattSearch.buildTable("gcagagag"));
    }

    @Test
    public void tableTestABCABCABC(){
        assertArrayEquals(new int[]{0, 0, 0, 1, 2, 3, 4, 5, 6}, MorrisPrattSearch.buildTable("abcabcabc"));
    }

    @Test
    public void tableTestANANAS(){
        assertArrayEquals(new int[]{0, 0, 1, 2, 3, 0}, MorrisPrattSearch.buildTable("ANANAS"));
    }

    @Test
    public void tableTestABABABC(){
        assertArrayEquals(new int[]{0, 0, 1, 2, 3, 4, 0}, MorrisPrattSearch.buildTable("ABABABC"));
    }
}
