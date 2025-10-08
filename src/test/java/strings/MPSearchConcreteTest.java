package strings;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concrete test class for MPSearch algorithm.
 * Includes algorithm-specific table building tests from the original main method.
 */
class MPSearchConcreteTest extends StringMatchingAlgorithmTest {

    @Override
    protected List<Integer> invoke(String text, String pattern) {
        return MPSearch.search(text, pattern);
    }

    // MP-specific table building tests from MPSearch.main() method
    @Test
    public void tableTest1(){
        assertArrayEquals(new int[]{0, 0, 0, 1, 0, 1, 0, 1}, MPSearch.buildTable("gcagagag"));
    }

    @Test
    public void tableTestAAAACAAAAAB(){
        int[] expected = {0, 1, 2, 3, 0, 1, 2, 3, 4, 0};
        assertArrayEquals(expected, MPSearch.buildTable("AAAACAAAAB"));
    }

    @Test
    public void tableTestCOCACOLA(){
        int[] expected = {0, 0, 1, 0, 1, 2, 0, 0};
        assertArrayEquals(expected, MPSearch.buildTable("COCACOLA"));
    }

    @Test
    public void tableTestAAAAAA(){
        int[] expected = {0, 1, 2, 3, 4, 5};
        assertArrayEquals(expected, MPSearch.buildTable("AAAAAA"));
    }

    @Test
    public void tableTestABABABC(){
        int[] expected = {0, 0, 1, 2, 3, 4, 0};
        assertArrayEquals(expected, MPSearch.buildTable("ABABABC"));
    }

    @Test
    public void tableTestABCDABDWithSpace(){
        int[] expected = {0, 0, 0, 0, 1, 2, 0, 0};
        assertArrayEquals(expected, MPSearch.buildTable("ABCDABD "));
    }

    @Test
    public void tableTestABACAABCWithSpace(){
        int[] expected = {0, 0, 1, 0, 1, 2, 3, 2, 0, 0};
        assertArrayEquals(expected, MPSearch.buildTable("ABACABABC "));
    }

    @Test
    public void tableTestABACABABAWithSpace(){
        int[] expected = {0, 0, 1, 0, 1, 2, 3, 2, 3, 0};
        assertArrayEquals(expected, MPSearch.buildTable("ABACABABA "));
    }

    @Test
    public void tableTestPARTICIPATEWithSpace(){
        int[] expected = {0, 0, 0, 0, 0, 0, 0, 1, 2, 0, 0, 0, 0, 0, 0, 1, 2, 3, 0, 0, 0, 0, 0, 0, 0};
        assertArrayEquals(expected, MPSearch.buildTable("PARTICIPATE IN PARACHUTE "));
    }
}
