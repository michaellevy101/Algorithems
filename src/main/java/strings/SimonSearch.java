package strings;

import java.util.LinkedList;
import java.util.List;

public class SimonSearch {

    void preSimon(char[] pattern) {
        List[] automaton = new List[pattern.length - 2];

    }

    private void setTransition(LinkedList<Integer> backwardEdges, int backwardCharIndex) {
        backwardEdges.add(backwardCharIndex);
    }

    public static final LinkedList[] buildTable(char[] pattern){
        LinkedList[] simonTable = new LinkedList[pattern.length - 2];

        int[] mpTable = MPSearch.buildTable(pattern);

        for (int patternIndex = 1; patternIndex < pattern.length; ++patternIndex) {
            int t = mpTable[patternIndex - 1];

            if (pattern[patternIndex] == pattern[t])
                continue;

            while (t > 0) {
                if (pattern[patternIndex] != pattern[t] && simonTable[patternIndex]){

                }
            }

            if (t == 0 && pattern[patternIndex] != pattern[t]) {

            }
        }

        return simonTable;
    }
}
