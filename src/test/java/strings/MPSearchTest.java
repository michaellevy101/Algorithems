package strings;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MPSearchTest {

    @BeforeAll
    public static final void prepare(){
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration config = context.getConfiguration();
        LoggerConfig rootConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
        rootConfig.setLevel(Level.DEBUG);
        context.updateLoggers();
    }

    @Test
    public void testEmpty(){
        assertTrue(MPSearch.search("",  "").isEmpty());
    }

    @Test
    public void testEmptyText(){
        assertTrue(MPSearch.search("abc",  "").isEmpty());
    }

    @Test
    public void testEmptyPattern(){
        assertTrue(MPSearch.search("",  "abc").isEmpty());
    }

    @Test
    public void singleEqualsCharacter(){
        assertTrue(!MPSearch.search("a",  "a").isEmpty());
    }

    @Test
    public void singleNotEqualCharacter(){
        assertTrue(MPSearch.search("a",  "b").isEmpty());
    }

    @Test
    public void twoMatches(){
        List<Integer> result = MPSearch.search("aa", "a");
        assertTrue(result.size() == 2);
        assertEquals(0, result.get(0));
        assertEquals(1, result.get(1));
    }

    @Test
    public void cocacola(){
        List<Integer> result = MPSearch.search("COCOCACOLA", "COCACOLA");
        assertEquals(1, result.size());
        assertEquals(2, result.get(0));
    }

    @Test
    public void ananasCheck(){
        List<Integer> result = MPSearch.search("ANANAANANAS", "ANANAS");
        assertEquals(1, result.size());
        assertEquals(5, result.get(0));
    }

    @Test
    public void abcabcdCheck(){
        List<Integer> result = MPSearch.search("abcabcabcabcd", "abcabcd");
        assertEquals(1, result.size());
        assertEquals(6, result.get(0));
    }

    @Test
    public void gcatcgcagagagtatacagtacgCheck(){
        List<Integer> result = MPSearch.search("gcatcgcagagagtatacagtacg", "gcagagag");
        assertEquals(1, result.size());
        assertEquals(5, result.get(0));
    }

    @Test
    public void tableTest1(){
        assertArrayEquals(new int[]{0, 0, 0, 1, 0, 1, 0, 1}, MPSearch.buildTable("gcagagag"));
    }
}