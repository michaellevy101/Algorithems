package strings;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KMPSearchTest extends MPSearchTest{

    @Test
    public void tableTest1(){
        assertArrayEquals(new int[]{-1, 0, -1, 1, -1, 0, -1, 1, 4, -1, 0, 2}, KMPSearch.buildTable("bcbabcbaebc"));
    }

    @Test
    public void gcagagagTableCheck(){
        assertArrayEquals(new int[]{-1, 0, 0, -1, 1, -1, 1, -1, 1}, KMPSearch.buildTable("gcagagag"));
    }

    @Override
    protected List<Integer> invoke(String text, String pattern) {
        System.out.println("Pattern = " + pattern);
        System.out.println("MPTable = " + Arrays.toString(MPSearch.buildTable(pattern)));
        System.out.println("KMPTable = " + Arrays.toString(KMPSearch.buildTable(pattern)));
        return KMPSearch.search(text, pattern);
    }
}