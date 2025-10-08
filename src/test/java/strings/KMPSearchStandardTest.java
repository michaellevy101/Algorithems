package strings;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the correct KMP search algorithm implementation.
 * Extends the reusable test base class with comprehensive test cases.
 */
class KMPSearchStandardTest extends StringMatchingAlgorithmTest {

    @Override
    protected List<Integer> invoke(String text, String pattern) {
        return KMPSearchStandard.search(text, pattern);
    }

    // KMP Standard specific table building tests
    @Test
    public void kmpStandardTableTestEmptyPattern(){
        assertArrayEquals(new int[]{}, KMPSearchStandard.buildTable(""));
    }

    @Test
    public void kmpStandardTableTestSingleChar(){
        assertArrayEquals(new int[]{0}, KMPSearchStandard.buildTable("a"));
    }

    @Test
    public void kmpStandardTableTestNoRepeats(){
        assertArrayEquals(new int[]{0, 0, 0}, KMPSearchStandard.buildTable("abc"));
    }

    @Test
    public void kmpStandardTableTestSimpleRepeats(){
        assertArrayEquals(new int[]{0, 1}, KMPSearchStandard.buildTable("aa"));
    }

    @Test
    public void kmpStandardTableTestComplexPattern(){
        assertArrayEquals(new int[]{0, 0, 0, 1, 0, 1, 0, 1}, KMPSearchStandard.buildTable("gcagagag"));
    }

    @Test
    public void kmpStandardTableTestABCABCABC(){
        assertArrayEquals(new int[]{0, 0, 0, 1, 2, 3, 4, 5, 6}, KMPSearchStandard.buildTable("abcabcabc"));
    }

    @Test
    public void kmpStandardTableTestANANAS(){
        assertArrayEquals(new int[]{0, 0, 1, 2, 3, 0}, KMPSearchStandard.buildTable("ANANAS"));
    }

    @Test
    public void kmpStandardTableTestABABABC(){
        assertArrayEquals(new int[]{0, 0, 1, 2, 3, 4, 0}, KMPSearchStandard.buildTable("ABABABC"));
    }
}
