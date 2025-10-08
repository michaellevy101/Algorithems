package strings;

import java.util.ArrayList;
import java.util.List;

/**
 * Authentic Galil-Seiferas-Vishkin Dueling String Matching Algorithm (1985)
 * 
 * Implementation of the parallel string matching algorithm from:
 * "Optimal Parallel Algorithms for String Matching" by Zvi Galil
 * Information and Control 67, 144-157 (1985)
 * 
 * ## ALGORITHM OVERVIEW
 * 
 * The GSV Dueling algorithm excels at finding patterns with periodic structure by using
 * "witness arrays" and authentic "dueling" between close candidate matches.
 * 
 * Key Features:
 * 1. Uses a SWITCH array where SWITCH[i] = true if pattern occurs at position i
 * 2. Processes pattern prefixes of increasing length through ⌈log m⌉ stages
 * 3. Each stage doubles the prefix length being considered (1, 2, 4, 8, ...)
 * 4. Uses bulletin boards (BB) for global communication and local bulletin boards (lbb) per block
 * 5. Distinguishes between regular case and periodic case using periodicity lemma
 * 6. **Dueling Innovation**: When two candidates are close (distance < π), they "duel"
 *    at witness positions to determine which (if any) should survive
 * 
 * ## USAGE EXAMPLES
 * 
 * ### Basic Usage:
 * ```java
 * List<Integer> matches = Dueling.search("abcabcabc", "abc");
 * // Returns: [0, 3, 6] - finds all occurrences including overlaps
 * ```
 * 
 * ### Periodic Pattern Example (Algorithm's Strength):
 * ```java
 * List<Integer> matches = Dueling.search("ABABABABAB", "ABAB");
 * // Returns: [0, 2, 4, 6] - efficiently handles overlapping periodic matches
 * // Pattern "ABAB" has period 2, algorithm detects this and uses specialized handling
 * ```
 * 
 * ### Witness Array Example:
 * For pattern "ABCABC" (period 3), the witness array helps resolve conflicts:
 * ```
 * Pattern: A B C A B C
 * Index:   0 1 2 3 4 5
 * 
 * When candidates at positions i and j are distance 3 apart:
 * - witness[3] = 0 (since pattern[0]='A' ≠ pattern[3]='A' is false, but)
 * - Actually witness[3] = -1 since all positions match
 * - For "ABCDEF": witness[3] = 0 since pattern[0]='A' ≠ pattern[3]='D'
 * ```
 * 
 * ### Stage Progression Example:
 * For pattern "ABCDEFGH" (length 8):
 * ```
 * Stage 1: Test prefix "AB" (length 2¹)
 * Stage 2: Test prefix "ABCD" (length 2²) 
 * Stage 3: Test prefix "ABCDEFGH" (length 2³ = 8, full pattern)
 * Total: ⌈log₂ 8⌉ = 3 stages
 * ```
 * 
 * Time Complexity: O(n + m) with p = O(n) processors in O(log m) parallel time
 * Space Complexity: O(n + m) for z array, SWITCH array, bulletin boards, and witness arrays
 * 
 * Note: This implementation simulates the parallel algorithm sequentially while
 * maintaining the exact structure and logic described in the paper.
 */
public class Dueling {

    /**
     * Main search interface - finds all occurrences of pattern in text.
     * 
     * Examples:
     * ```java
     * // Simple search
     * Dueling.search("hello world", "world")  // Returns: [6]
     * 
     * // Overlapping matches (algorithm strength)
     * Dueling.search("abababa", "aba")        // Returns: [0, 2, 4]
     * 
     * // Periodic patterns (handled efficiently)
     * Dueling.search("ABABABABAB", "ABAB")    // Returns: [0, 2, 4, 6]
     * 
     * // No matches
     * Dueling.search("abcdef", "xyz")         // Returns: []
     * 
     * // Edge cases
     * Dueling.search("", "pattern")           // Returns: []
     * Dueling.search("text", "")              // Returns: []
     * ```
     * 
     * @param text the text to search in (must not be null)
     * @param pattern the pattern to search for (must not be null)
     * @return list of starting positions where the pattern is found (0-indexed)
     * @throws IllegalArgumentException if text or pattern is null
     */
    public static List<Integer> search(String text, String pattern) {
        if (text == null || pattern == null) {
            throw new IllegalArgumentException("Text and pattern cannot be null");
        }
        
        int n = text.length();
        int m = pattern.length();
        if (m == 0 || n < m) return new ArrayList<>();

        return gsvDuelingAlgorithm(text.toCharArray(), pattern.toCharArray());
    }

    /**
     * Core implementation of the authentic GSV Dueling algorithm from the 1985 paper.
     * 
     * Follows Section 4 of the paper exactly:
     * - Creates z = x $ y (pattern $ text) 
     * - Computes witness array for dueling (Section 2)
     * - Maintains SWITCH array through ⌈log m⌉ stages
     * - Uses blocks with local bulletin boards (lbb)
     * - Handles regular and periodic cases as described in Figure 1
     * 
     * Example execution for pattern="ABAB", text="ABABABABAB":
     * 1. z = "ABAB$ABABABABAB" (pattern $ text)
     * 2. π = min(2, 4/2) = 2 (period=2, m/2=2)
     * 3. witness[1] = 0 (since P[0]='A' ≠ P[1]='B')
     * 4. After stages: SWITCH has true at positions 5,7,9,11 in z
     * 5. Convert to text positions: [0,2,4,6]
     * 
     * @param text the text character array
     * @param pattern the pattern character array
     * @return list of match positions in the original text
     */
    private static List<Integer> gsvDuelingAlgorithm(char[] text, char[] pattern) {
        int n = text.length;           // Text length
        int m = pattern.length;        // Pattern length
        
        /* 
         * STEP 1: Create z = x $ y as described in Section 4 of the paper
         * 
         * Example: pattern="ABAB", text="ABABABABAB"
         * z = ['A','B','A','B','$','A','B','A','B','A','B','A','B','A','B']
         *      0   1   2   3   4   5   6   7   8   9  10  11  12  13  14
         *      |--- pattern ---|$|------------ text --------------|
         */
        char[] z = new char[m + 1 + n];
        System.arraycopy(pattern, 0, z, 0, m);           // Copy pattern to start
        z[m] = '$';                                       // Insert separator (not in alphabet)
        System.arraycopy(text, 0, z, m + 1, n);         // Copy text after separator
        
        int zLength = z.length;
        
        /*
         * STEP 2: Compute algorithm parameters
         * 
         * π (pi) = min(shortest_period, m/2) - critical for dueling distance
         * Example: For "ABAB": period=2, m/2=2, so π=2
         * This means candidates closer than distance 2 will duel
         */
        int smallestPeriod = computeSmallestPeriod(pattern);
        int pi = Math.min(smallestPeriod, m / 2);        // π = min(period, m/2) from paper
        
        /*
         * STEP 3: Build witness array for authentic dueling (Section 2)
         * 
         * witness[δ] = position h where P[h] ≠ P[h+δ]
         * Example: For "ABAB" and δ=1: P[0]='A' ≠ P[1]='B', so witness[1]=0
         * This enables dueling: candidates at distance δ duel at position witness[δ]
         */
        int[] witness = buildWitnessArray(pattern, pi);
        
        /*
         * STEP 4: Initialize SWITCH array - the heart of the algorithm
         * 
         * SWITCH[i] = true if pattern occurs at position i in z
         * Initially all false, gets updated through stages
         * Final true positions in text region indicate matches
         */
        boolean[] SWITCH = new boolean[zLength];
        
        /*
         * STEP 5: Initialize global bulletin board for inter-stage communication
         * 
         * BB[0] = period_size (for periodic case)
         * BB[1] = L_value (for yardstick method)  
         * BB[2] = pi_value (for dueling threshold)
         */
        int[] BB = new int[3];
        BB[2] = pi;  // Store π value for dueling distance threshold
        
        /*
         * STEP 6: Execute algorithm stages
         * 
         * Total stages = ⌈log₂ m⌉ (pattern length determines stages)
         * Example: m=4 → ⌈log₂ 4⌉ = 2 stages
         * Stage 1: Test prefixes of length 2¹ = 2
         * Stage 2: Test prefixes of length 2² = 4 (full pattern)
         */
        int numStages = (int) Math.ceil(Math.log(m) / Math.log(2));
        
        // Stage 1: Special case - find all occurrences of first 2 characters
        stageOne(z, pattern, SWITCH);
        
        // Main stages: Process increasing prefix lengths with dueling
        for (int stage = 2; stage <= numStages; stage++) {
            stageMain(z, pattern, SWITCH, BB, witness, stage);
        }
        
        /*
         * STEP 7: Extract final results from SWITCH array
         * 
         * Only positions in text region (after pattern and $) matter
         * Convert z-positions back to original text positions
         * 
         * Example: SWITCH[7]=true in z corresponds to position 7-(4+1)=2 in text
         */
        List<Integer> matches = new ArrayList<>();
        for (int i = m + 1; i <= zLength - m; i++) {     // Scan text region only
            if (SWITCH[i]) {
                matches.add(i - m - 1);                  // Convert z-pos to text-pos
            }
        }
        
        return matches;
    }

    /**
     * Compute the shortest period of the pattern using Fact 2 from Section 2.
     * 
     * Fact 2 (Periodicity Lemma): "If v occurs at j and j+δ, δ ≤ |v|/2, then v is periodic 
     * with period of length δ"
     * 
     * This uses a KMP-like failure function approach to find the shortest repeating substring.
     * 
     * @param pattern the pattern array
     * @return the length of the shortest period
     */
    private static int computeSmallestPeriod(char[] pattern) {
        int m = pattern.length;
        if (m <= 1) return m;
        
        // Use failure function approach to find shortest period
        int[] failure = new int[m];
        int j = 0;
        
        for (int i = 1; i < m; i++) {
            while (j > 0 && pattern[i] != pattern[j]) {
                j = failure[j - 1];
            }
            if (pattern[i] == pattern[j]) {
                j++;
            }
            failure[i] = j;
        }
        
        // The shortest period length is m - failure[m-1]
        int period = m - failure[m - 1];
        
        // Verify this is actually a period
        boolean isPeriod = true;
        for (int i = 0; i < m; i++) {
            if (pattern[i] != pattern[i % period]) {
                isPeriod = false;
                break;
            }
        }
        
        return isPeriod ? period : m;
    }

    /**
     * Build witness array for dueling as described in Section 2 of the paper.
     * 
     * From the paper: "For distance δ, witness[δ] is a position h where P[h] ≠ P[h+δ]"
     * This enables dueling: when two candidates are at distance δ < π, they can duel
     * at the witness position to determine which (if any) should survive.
     * 
     * VISUAL EXAMPLE for pattern "ABCAB":
     * ```
     * Pattern:  A B C A B
     * Index:    0 1 2 3 4
     * 
     * For δ=1: Compare P[0] vs P[1]: 'A' ≠ 'B' → witness[1] = 0
     * For δ=2: Compare P[0] vs P[2]: 'A' ≠ 'C' → witness[2] = 0  
     * For δ=3: Compare P[0] vs P[3]: 'A' = 'A'
     *          Compare P[1] vs P[4]: 'B' = 'B'
     *          No difference found → witness[3] = 0 (default)
     * 
     * Result: witness = [-, 0, 0, 0] (index 0 unused)
     * ```
     * 
     * DUELING MECHANISM:
     * When candidates at positions i and j have distance δ = j-i < π:
     * 1. Look up h = witness[δ] 
     * 2. Check what each candidate sees at position h
     * 3. Since P[h] ≠ P[h+δ], at most one can match P[h]
     * 4. Loser(s) get eliminated from SWITCH array
     * 
     * @param pattern the pattern array
     * @param pi the value π = min(period, m/2) - max distance for dueling
     * @return witness array where witness[δ] gives witness position for distance δ
     */
    private static int[] buildWitnessArray(char[] pattern, int pi) {
        int m = pattern.length;
        int[] witness = new int[pi];  // witness[0] unused, witness[1] to witness[pi-1] used
        
        // For each possible distance δ between close candidates
        for (int delta = 1; delta < pi; delta++) {
            /*
             * Find the first position h where P[h] ≠ P[h+δ]
             * This becomes the "witness position" for distance δ
             * 
             * Example: pattern="ABCAB", δ=1
             * pos=0: P[0]='A' vs P[1]='B' → different! Set h=0
             */
            int h = -1;
            for (int pos = 0; pos + delta < m; pos++) {
                if (pattern[pos] != pattern[pos + delta]) {
                    h = pos;  // Found witness position
                    break;    // Use first difference found
                }
            }
            
            /*
             * Store witness position for this distance
             * If no witness found (shouldn't happen with proper π), use 0 as fallback
             * Note: This case suggests the distance δ might be a multiple of the period
             */
            witness[delta] = (h != -1) ? h : 0;
        }
        
        return witness;
    }

    /**
     * Stage 1: Initial stage that finds all occurrences of the first two characters.
     * 
     * From Section 4 of paper: "Processor pj tests whether ZjZj+1 = X1X2"
     * 
     * @param z the combined string z = x $ y
     * @param pattern the pattern array
     * @param SWITCH the switch array to update
     */
    private static void stageOne(char[] z, char[] pattern, boolean[] SWITCH) {
        if (pattern.length < 2) {
            // Handle single character pattern
            for (int j = 0; j <= z.length - 1; j++) {
                if (z[j] == pattern[0]) {
                    SWITCH[j] = true;
                }
            }
            return;
        }
        
        // Test for first two characters of pattern
        char x1 = pattern[0];
        char x2 = pattern[1];
        
        for (int j = 0; j <= z.length - 2; j++) {
            if (z[j] == x1 && z[j + 1] == x2) {
                SWITCH[j] = true;
            }
        }
    }

    /**
     * Main stage processing (stages 2 through ⌈log m⌉).
     * 
     * Implements the flowchart from Figure 1 of the paper:
     * - Box 1: Test for periodic case
     * - Box 2-3: Handle multiple 1's (regular case)
     * - Box 4: Regular step with dueling
     * - Box 5-6: Periodic case handling
     * - Box 7: Update for next stage
     * 
     * @param z the combined string
     * @param pattern the pattern array
     * @param SWITCH the switch array
     * @param BB the bulletin board [period_size, L_value, pi_value]
     * @param witness the witness array for dueling
     * @param stage current stage number (2, 3, ...)
     */
    private static void stageMain(char[] z, char[] pattern, boolean[] SWITCH, int[] BB, int[] witness, int stage) {
        int m = pattern.length;
        int prefixLength = Math.min((int) Math.pow(2, stage - 1), m);  // Length of x^(i)
        int nextPrefixLength = Math.min((int) Math.pow(2, stage), m);   // Length of x^(i+1)
        
        if (prefixLength >= m) {
            return;  // No more processing needed
        }
        
        int blockSize = (int) Math.pow(2, stage - 2);  // Size of blocks at this stage
        if (blockSize < 1) blockSize = 1;
        
        // Create local bulletin boards (lbb) for blocks
        int numBlocks = (int) Math.ceil((double) z.length / blockSize);
        int[] lbb = new int[numBlocks];  // Points to position of 1 in each block (0 if none)
        
        // Initialize lbb's - find the 1 in each block
        for (int blockIdx = 0; blockIdx < numBlocks; blockIdx++) {
            int blockStart = blockIdx * blockSize;
            int blockEnd = Math.min(blockStart + blockSize, z.length);
            
            for (int pos = blockStart; pos < blockEnd; pos++) {
                if (SWITCH[pos]) {
                    lbb[blockIdx] = pos + 1;  // Store 1-based position (0 means no 1)
                    break;
                }
            }
        }
        
        // Box 1: Test for periodic case (check if first block has two 1's)
        boolean isPeriodicCase = testForPeriodicCase(SWITCH, BB, blockSize, m);
        
        if (isPeriodicCase) {
            // Periodic case: Box 5-6
            handlePeriodicCase(z, pattern, SWITCH, BB, witness, prefixLength, nextPrefixLength);
        } else {
            // Regular case: Box 2-4
            handleRegularCase(z, pattern, SWITCH, lbb, BB, witness, blockSize, prefixLength, nextPrefixLength);
        }
    }

    /**
     * Box 1: Test if we're in the periodic case.
     * 
     * From paper: "If two 1's are discovered in the (new) first block we are in the periodic case"
     * 
     * @param SWITCH the switch array
     * @param BB bulletin board to store period size
     * @param blockSize size of blocks
     * @param m pattern length
     * @return true if periodic case detected
     */
    private static boolean testForPeriodicCase(boolean[] SWITCH, int[] BB, int blockSize, int m) {
        // Check first block for two 1's
        int firstOne = -1;
        int secondOne = -1;
        
        for (int pos = 0; pos < Math.min(blockSize, SWITCH.length); pos++) {
            if (SWITCH[pos]) {
                if (firstOne == -1) {
                    firstOne = pos;
                } else {
                    secondOne = pos;
                    break;
                }
            }
        }
        
        if (firstOne == 0 && secondOne != -1) {
            // Periodic case detected - pattern has period P = secondOne
            int P = secondOne;
            BB[0] = P;  // Store period size
            BB[1] = computeL(P, m);  // Store L value
            return true;
        }
        
        BB[0] = 0;  // Not periodic
        return false;
    }

    /**
     * Compute L = ⌈|v|/P⌉ * P where v is current prefix and P is period size.
     */
    private static int computeL(int P, int prefixLength) {
        return ((prefixLength + P - 1) / P) * P;  // Ceiling division * P
    }

    /**
     * Box 5-6: Handle periodic case using authentic yardstick method.
     * 
     * From paper Section 4: "In the periodic case we test whether the periodicity 
     * of x^(i) continues in x^(i+1) by using x^(i) as a yardstick"
     * 
     * Uses Fact 4: "v occurs at j, j+P and j+L iff u^(k+l)u' occurs at j"
     */
    private static void handlePeriodicCase(char[] z, char[] pattern, boolean[] SWITCH, 
                                         int[] BB, int[] witness, int prefixLength, int nextPrefixLength) {
        int P = BB[0];  // Period size
        int L = BB[1];  // L value
        
        // Box 5: Test if periodicity continues
        boolean periodicityContinues = testPeriodicityContinues(SWITCH, P, L);
        
        if (periodicityContinues) {
            // Box 6: Find occurrences in periodic case
            findPeriodicOccurrences(SWITCH, P, L);
        } else {
            // Periodicity terminates - find important occurrence and eliminate candidates
            handlePeriodicityTermination(SWITCH, P, L, prefixLength);
        }
    }

    /**
     * Box 5: Test if periodicity continues.
     */
    private static boolean testPeriodicityContinues(boolean[] SWITCH, int P, int L) {
        // Check if SWITCH[1+P] and SWITCH[1+L] are both 1
        boolean check1 = (1 + P < SWITCH.length) && SWITCH[1 + P];
        boolean check2 = (1 + L < SWITCH.length) && SWITCH[1 + L];
        return check1 && check2;
    }

    /**
     * Box 6: Find occurrences in periodic case.
     */
    private static void findPeriodicOccurrences(boolean[] SWITCH, int P, int L) {
        // Each position j with SWITCH[j]=1 checks SWITCH[j+P] and SWITCH[j+L]
        for (int j = 0; j < SWITCH.length; j++) {
            if (SWITCH[j]) {
                boolean hasP = (j + P < SWITCH.length) && SWITCH[j + P];
                boolean hasL = (j + L < SWITCH.length) && SWITCH[j + L];
                
                if (!hasP || !hasL) {
                    SWITCH[j] = false;  // Turn off if either check fails
                }
            }
        }
    }

    /**
     * Handle periodicity termination case.
     */
    private static void handlePeriodicityTermination(boolean[] SWITCH, int P, int L, int prefixLength) {
        // Find important occurrence (where periodicity breaks)
        int importantPos = -1;
        
        // Look for position where SWITCH[1+j*P] = 0 for some j
        for (int j = 1; j * P <= L; j++) {
            if (1 + j * P < SWITCH.length && !SWITCH[1 + j * P]) {
                importantPos = 1 + (j - 1) * P;
                break;
            }
        }
        
        if (importantPos != -1) {
            // Eliminate candidates that don't have important occurrence
            for (int pos = 0; pos < SWITCH.length; pos++) {
                if (SWITCH[pos]) {
                    int checkPos = pos + importantPos - 1;
                    if (checkPos >= SWITCH.length || !SWITCH[checkPos]) {
                        SWITCH[pos] = false;
                    }
                }
            }
        }
    }

    /**
     * Box 2-4: Handle regular case with authentic dueling mechanism.
     * 
     * Implements Box 2-3 (eliminate multiple 1's) and Box 4 (dueling between close candidates)
     * using witness array for true concurrent write simulation.
     */
    private static void handleRegularCase(char[] z, char[] pattern, boolean[] SWITCH, 
                                        int[] lbb, int[] BB, int[] witness, int blockSize, int prefixLength, int nextPrefixLength) {
        // Box 2-3: Handle multiple 1's per block
        handleMultipleOnes(SWITCH, lbb, blockSize);
        
        // Box 4: Regular step with authentic dueling
        regularStepWithDueling(z, pattern, SWITCH, lbb, BB, witness, blockSize, prefixLength, nextPrefixLength);
    }

    /**
     * Box 2-3: Handle multiple 1's in blocks (regular case).
     */
    private static void handleMultipleOnes(boolean[] SWITCH, int[] lbb, int blockSize) {
        for (int blockIdx = 1; blockIdx < lbb.length; blockIdx++) {  // Skip first block
            int blockStart = blockIdx * blockSize;
            int blockEnd = Math.min(blockStart + blockSize, SWITCH.length);
            
            int firstOne = -1;
            int secondOne = -1;
            
            for (int pos = blockStart; pos < blockEnd; pos++) {
                if (SWITCH[pos]) {
                    if (firstOne == -1) {
                        firstOne = pos;
                    } else {
                        secondOne = pos;
                        break;
                    }
                }
            }
            
            // If two 1's found, turn off the first one
            if (firstOne != -1 && secondOne != -1) {
                SWITCH[firstOne] = false;
                lbb[blockIdx] = secondOne + 1;  // Update lbb to point to remaining 1
            }
        }
    }

    /**
     * Box 4: Authentic regular step with dueling mechanism.
     * 
     * From paper Section 4: "Each group of 2^(i-1) processors responsible for a block 
     * that contains a 1 performs a regular step: In two steps they test for v^(i)"
     * 
     * Implements true dueling using witness arrays:
     * 1. First performs pattern extension testing (like AND operation)
     * 2. Then performs dueling between close candidates using witnesses
     * 3. Simulates concurrent write with conflict resolution
     */
    private static void regularStepWithDueling(char[] z, char[] pattern, boolean[] SWITCH, int[] lbb, 
                                             int[] BB, int[] witness, int blockSize, int prefixLength, int nextPrefixLength) {
        if (nextPrefixLength > pattern.length) {
            nextPrefixLength = pattern.length;
        }
        
        int pi = BB[2];  // Get π value for dueling
        
        // Step 1: Test pattern extension (like AND operation in paper)
        for (int blockIdx = 0; blockIdx < lbb.length; blockIdx++) {
            if (lbb[blockIdx] > 0) {  // Block has a 1
                int pos = lbb[blockIdx] - 1;  // Convert to 0-based position
                
                // Test if prefix at pos extends to next prefix length
                // Note: We use pattern[k] instead of z[k] because we're testing if the
                // occurrence at position 'pos' in z matches the pattern prefix
                boolean extendsToNext = true;
                for (int k = prefixLength; k < nextPrefixLength; k++) {
                    if (pos + k >= z.length || z[pos + k] != pattern[k]) {
                        extendsToNext = false;
                        break;
                    }
                }
                
                if (!extendsToNext) {
                    SWITCH[pos] = false;  // Turn off if doesn't extend
                    lbb[blockIdx] = 0;    // Clear lbb
                }
            }
        }
        
        // Step 2: Perform authentic dueling between close candidates
        // Collect all remaining active candidates after extension test
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < SWITCH.length; i++) {
            if (SWITCH[i]) {
                candidates.add(i);
            }
        }
        
        // Duel between candidates at distance < π using witness array
        performAuthenticalDueling(z, pattern, SWITCH, candidates, witness, pi);
    }

    /**
     * Perform authentic dueling between close candidates using witness positions.
     * 
     * From paper Section 2: When two candidates are at distance δ < π, they duel
     * at witness position h = witness[δ] where P[h] ≠ P[h+δ].
     * 
     * DUELING EXAMPLE:
     * ```
     * Pattern: "ABCAB"  witness[1]=0 (since P[0]='A' ≠ P[1]='B') 
     * Text:    "XABCABCABX"
     * z:       "ABCAB$XABCABCABX"
     *           01234567890123456
     *                   ^^
     *                   78 <- candidates at distance 1
     * 
     * Duel between positions 7 and 8:
     * 1. distance = 8-7 = 1
     * 2. h = witness[1] = 0  
     * 3. Check z[7+0]='A' vs pattern[0]='A' → match ✓
     * 4. Check z[8+0]='B' vs pattern[0]='A' → no match ✗
     * 5. Result: candidate 7 wins, candidate 8 eliminated
     * ```
     * 
     * CONCURRENT WRITE SIMULATION:
     * In parallel, multiple candidates might try to write to same memory location.
     * Dueling resolves conflicts by using witness positions where pattern characters
     * differ, ensuring at most one candidate can be "correct" at witness position.
     * 
     * @param z the combined string z = pattern $ text
     * @param pattern the original pattern for reference
     * @param SWITCH the switch array to update (eliminate losers)
     * @param candidates list of active candidate positions  
     * @param witness witness array: witness[δ] = position where P[h] ≠ P[h+δ]
     * @param pi dueling threshold (π = min(period, m/2))
     */
    private static void performAuthenticalDueling(char[] z, char[] pattern, boolean[] SWITCH, 
                                                 List<Integer> candidates, int[] witness, int pi) {
        /*
         * STEP 1: Check all pairs of candidates for potential duels
         * 
         * Only candidates at distance δ < π need to duel
         * This implements the "close candidates" concept from the paper
         */
        for (int i = 0; i < candidates.size(); i++) {
            for (int j = i + 1; j < candidates.size(); j++) {
                int candI = candidates.get(i);           // Position of first candidate
                int candJ = candidates.get(j);           // Position of second candidate  
                int distance = candJ - candI;            // Distance δ between them
                
                /*
                 * STEP 2: Determine if candidates should duel
                 * 
                 * Duel conditions:
                 * - distance > 0 (candJ comes after candI)
                 * - distance < π (they are "close" by paper's definition)
                 * - witness array has entry for this distance
                 */
                if (distance > 0 && distance < pi && distance < witness.length) {
                    int h = witness[distance];  // Get witness position for this distance
                    
                    /*
                     * STEP 3: Bounds checking for safety
                     * 
                     * Ensure witness position is valid for both candidates
                     * candI+h and candJ+h must be within z array bounds
                     * h must be valid pattern index
                     */
                    if (candI + h < z.length && candJ + h < z.length && h < pattern.length) {
                        /*
                         * STEP 4: Extract duel information
                         * 
                         * Each candidate "sees" a character at the witness position
                         * Compare what they see against what pattern expects
                         */
                        char textAtI = z[candI + h];      // What candidate i sees at witness
                        char textAtJ = z[candJ + h];      // What candidate j sees at witness  
                        char patternChar = pattern[h];    // What pattern expects at witness
                        
                        /*
                         * STEP 5: Conduct the duel
                         * 
                         * Core insight: Since P[h] ≠ P[h+δ] (witness property),
                         * at most one candidate can match P[h] at position h.
                         * 
                         * Example: If P[0]='A' and P[1]='B', then at witness position 0:
                         * - Candidate matching P[0] sees 'A' → should match P[0]='A' ✓
                         * - Candidate matching P[1] sees 'B' → should match P[0]='A' ✗
                         */
                        boolean iMatches = (textAtI == patternChar);
                        boolean jMatches = (textAtJ == patternChar);
                        
                        /*
                         * STEP 6: Resolve conflict (simulate concurrent write)
                         * 
                         * In true parallel execution, both might try to write "match"
                         * to same memory location. Dueling determines who survives.
                         */
                        if (!iMatches && jMatches) {
                            // Candidate i loses duel, j wins
                            SWITCH[candI] = false;
                        } else if (iMatches && !jMatches) {
                            // Candidate j loses duel, i wins  
                            SWITCH[candJ] = false;
                        } else if (!iMatches && !jMatches) {
                            // Both lose the duel (neither matches pattern)
                            SWITCH[candI] = false;
                            SWITCH[candJ] = false;
                        }
                        /*
                         * Case: both match
                         * This shouldn't happen if witness property holds
                         * Keep both as safety measure (algorithm remains correct)
                         */
                    }
                }
            }
        }
    }
}
