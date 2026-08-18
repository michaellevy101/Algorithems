package strings;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * Aho-Corasick String Matching Algorithm (1975) - Multi-Pattern Search
 * 
 * This implementation provides the complete Aho-Corasick algorithm for efficiently
 * searching multiple patterns simultaneously in a text. AC is the gold standard
 * for multi-pattern string matching, achieving O(n + m + z) time complexity where
 * n = text length, m = total pattern lengths, z = number of matches.
 * 
 * ## ALGORITHM OVERVIEW
 * 
 * Aho-Corasick extends the KMP algorithm's failure function concept to work with
 * multiple patterns by organizing them in a trie (prefix tree) with failure links.
 * It consists of three main phases:
 * 
 * 1. **Trie Construction**: Build a trie containing all patterns
 * 2. **Failure Links**: Add failure transitions for efficient pattern shifting
 * 3. **Output Links**: Add direct links to pattern matches for faster reporting
 * 
 * ## KEY INNOVATION: Failure Links in a Trie
 * 
 * **The Core Insight:**
 * When we fail to match a character at a trie node, instead of restarting from
 * the root, we follow a "failure link" to the longest proper suffix that's also
 * a prefix of some pattern in the trie.
 * 
 * **Visual Example for patterns ["HE", "SHE", "HIS", "HERS"]:**
 * ```
 * Trie Structure:
 *     root(0)
 *    /   |   \
 *   H(1) S(2) ...
 *   |    |
 *   E(3) H(4)
 *   |    |
 *   R(5) E(6)
 *   |    
 *   S(7) 
 * 
 * Failure Links (->):
 * - Node 4(H): -> Node 1(H)  [SH fails, try H]
 * - Node 6(E): -> Node 3(E)  [SHE fails, try HE] 
 * - Node 5(R): -> Node 0     [HER fails, restart]
 * ```
 * 
 * ## SEARCH ALGORITHM VISUALIZATION
 * 
 * **Searching patterns ["HE", "SHE", "HIS"] in "USHERS":**
 * ```
 * Text: U S H E R S
 * Index: 0 1 2 3 4 5
 * 
 * Step 1: U - no match from root, stay at root
 * Step 2: S - match! go to node S
 * Step 3: H - from S, try H -> success! go to SH node
 *              Also check output: SH node has failure link to H node
 * Step 4: E - from SH, try E -> success! go to SHE node
 *              Found pattern "SHE" at position 1!
 *              Follow failure link to HE node, found "HE" at position 2!
 * Step 5: R - from SHE, try R -> fail, follow failure to HE, try R -> fail,
 *              follow failure to root
 * Step 6: S - from root, try S -> success!
 * ```
 * 
 * ## WHY IT WORKS: The Mathematical Foundation
 * 
 * **Failure Link Property:**
 * For any node representing string α, its failure link points to the node
 * representing the longest proper suffix of α that is also a prefix of some pattern.
 * This ensures we never miss any potential matches when shifting.
 * 
 * **Output Link Optimization:**
 * Instead of following multiple failure links to find all matches, output links
 * directly point to nodes representing patterns that end at the current position.
 * 
 * ## MULTI-PATTERN STRENGTH
 * 
 * **Single Pass Efficiency:**
 * Unlike running multiple single-pattern algorithms, AC processes the text only
 * once regardless of the number of patterns, making it incredibly efficient for:
 * - Virus scanning (thousands of virus signatures)
 * - Content filtering (multiple banned words)
 * - Bioinformatics (DNA/protein sequence matching)
 * - Network intrusion detection
 * 
 * ## USAGE EXAMPLES
 * 
 * ```java
 * // Multi-pattern search
 * List<String> patterns = Arrays.asList("HE", "SHE", "HIS", "HERS");
 * Map<String, List<Integer>> matches = AhoCorasickSearch.searchMultiple("USHERS", patterns);
 * // Returns: {"HE": [2], "SHE": [1]} - overlapping matches found efficiently
 * 
 * // Single pattern (for compatibility)
 * List<Integer> matches = AhoCorasickSearch.search("USHERS", "HE");
 * // Returns: [2] - single pattern search
 * 
 * // Case-insensitive search
 * AhoCorasickSearch ac = new AhoCorasickSearch(patterns, true);
 * Map<String, List<Integer>> matches = ac.searchMultiple("ushers");
 * // Handles case-insensitive matching
 * ```
 * 
 * Time Complexity: O(n + m + z) where:
 * - n = text length
 * - m = sum of all pattern lengths  
 * - z = number of pattern occurrences
 * 
 * Space Complexity: O(m * σ) where σ = alphabet size (for trie storage)
 * 
 * Aho-Corasick is the definitive algorithm for multi-pattern string matching.
 */
public class AhoCorasickSearch {

    private static final Logger LOG = LogManager.getLogger(AhoCorasickSearch.class);
    
    /**
     * Trie node representing a state in the Aho-Corasick automaton.
     * 
     * **TRIE NODE STRUCTURE:**
     * Each node represents a prefix of one or more patterns and contains:
     * 
     * - **children**: Map of character → child node for pattern matching transitions
     *   Example: If node represents "SH", children might contain 'E' → node for "SHE"
     * 
     * - **failure**: Failure link pointing to longest proper suffix that's also a prefix
     *   Example: Node "SHE" has failure link to node "HE" (longest matching suffix)
     * 
     * - **output**: Set of complete patterns that end at this node
     *   Example: Node at end of "SHE" has output = {"SHE"}
     * 
     * - **outputLink**: Direct link to next node in failure chain with pattern output
     *   Example: Node "SHE" might have outputLink to node "HE" for faster reporting
     * 
     * - **depth**: Distance from root (length of represented prefix)
     *   Example: Root has depth 0, 'S' has depth 1, "SH" has depth 2
     * 
     * **EXAMPLE NODE for pattern "SHELL" in position "SHE":**
     * ```
     * Node representing "SHE":
     * - children: {'L' → node for "SHEL"}
     * - failure: → node "HE" (longest suffix match)
     * - output: {"SHE"} if "SHE" is a complete pattern
     * - outputLink: → node "HE" if it has output
     * - depth: 3
     * ```
     */
    private static class TrieNode {
        // Map storing transitions: character → next state
        // Example: {'a' → node1, 'b' → node2} means we can transition to node1 on 'a'
        private final Map<Character, TrieNode> children;
        
        // Failure link: where to go when current path fails to match
        // Points to the longest proper suffix that's a prefix of some pattern
        private TrieNode failure;
        
        // Set of complete patterns ending at this node
        // Example: At end of "SHE", output = {"SHE"}; could be empty for internal nodes
        private final Set<String> output;
        
        // Optimization: direct link to next node with output in failure chain
        // Avoids following multiple failure links during search
        private TrieNode outputLink;
        
        // Depth in trie = length of prefix this node represents
        // Root is depth 0, each edge increases depth by 1
        private final int depth;
        
        /**
         * Creates a new trie node at the specified depth.
         * 
         * @param depth the depth of this node in the trie (distance from root)
         * 
         * Example usage in trie construction:
         * ```java
         * TrieNode root = new TrieNode(0);           // Root at depth 0
         * TrieNode nodeH = new TrieNode(1);          // 'H' at depth 1
         * TrieNode nodeHE = new TrieNode(2);         // "HE" at depth 2
         * root.addChild('H', nodeH);
         * nodeH.addChild('E', nodeHE);
         * ```
         */
        public TrieNode(int depth) {
            this.children = new HashMap<>();
            this.failure = null;
            this.output = new HashSet<>();
            this.outputLink = null;
            this.depth = depth;
        }
        
        /**
         * Adds a child node for the given character transition.
         * 
         * @param c the character that triggers the transition
         * @param child the target node for this transition
         * 
         * Example: addChild('E', nodeE) creates transition: current --'E'--> nodeE
         */
        public void addChild(char c, TrieNode child) {
            children.put(c, child);
        }
        
        /**
         * Gets the child node for the given character, or null if no such transition exists.
         * 
         * @param c the character to look up
         * @return the child node, or null if no transition exists
         * 
         * Example: getChild('E') returns the node reached by following edge labeled 'E'
         */
        public TrieNode getChild(char c) {
            return children.get(c);
        }
        
        /**
         * Checks if a transition exists for the given character.
         * 
         * @param c the character to check
         * @return true if a transition exists, false otherwise
         * 
         * Example: hasChild('E') returns true if there's an edge labeled 'E' from this node
         */
        public boolean hasChild(char c) {
            return children.containsKey(c);
        }
        
        public Set<Character> getChildrenKeys() {
            return children.keySet();
        }
        
        /**
         * Marks this node as the end of a complete pattern.
         * 
         * @param pattern the complete pattern that ends at this node
         * 
         * Example: After inserting "SHE", we call addOutput("SHE") on the final node
         * Note: A node can have multiple outputs if it's the end of multiple patterns
         * (e.g., node representing "HE" ends both "HE" and could be part of "SHE")
         */
        public void addOutput(String pattern) {
            output.add(pattern);
        }
        
        /**
         * Gets all complete patterns that end at this node.
         * 
         * @return set of patterns ending here (may be empty for internal nodes)
         * 
         * Example: getOutput() might return {"SHE"} for the node at end of "SHE"
         */
        public Set<String> getOutput() {
            return output;
        }
        
        /**
         * Checks if any complete patterns end at this node.
         * 
         * @return true if this node marks the end of at least one pattern
         * 
         * Example: Returns true for nodes at end of patterns, false for internal nodes
         */
        public boolean hasOutput() {
            return !output.isEmpty();
        }
        
        public void setFailure(TrieNode failure) {
            this.failure = failure;
        }
        
        public TrieNode getFailure() {
            return failure;
        }
        
        public void setOutputLink(TrieNode outputLink) {
            this.outputLink = outputLink;
        }
        
        public TrieNode getOutputLink() {
            return outputLink;
        }
        
        public int getDepth() {
            return depth;
        }
    }
    
    private final TrieNode root;
    private final boolean caseInsensitive;
    
    /**
     * Constructs an Aho-Corasick automaton for the given patterns.
     * 
     * **CONSTRUCTION PROCESS:**
     * 1. Initialize root node
     * 2. Build trie from all patterns (Phase 1)
     * 3. Compute failure links via BFS (Phase 2)
     * 4. Build output links for optimization (Phase 3)
     * 
     * @param patterns list of patterns to search for (must not contain null or empty strings)
     * @param caseInsensitive whether to perform case-insensitive matching
     * 
     * **EXAMPLE USAGE:**
     * ```java
     * // Case-sensitive search for virus signatures
     * List<String> viruses = Arrays.asList("MALWARE", "TROJAN", "VIRUS");
     * AhoCorasickSearch ac = new AhoCorasickSearch(viruses, false);
     * 
     * // Case-insensitive search for profanity filter
     * List<String> badWords = Arrays.asList("bad", "worse", "worst");
     * AhoCorasickSearch filter = new AhoCorasickSearch(badWords, true);
     * // Will match "BAD", "Bad", "bad", etc.
     * ```
     * 
     * Time Complexity: O(m * σ) where m = sum of pattern lengths, σ = alphabet size
     * Space Complexity: O(m * σ) for trie storage
     */
    public AhoCorasickSearch(List<String> patterns, boolean caseInsensitive) {
        this.caseInsensitive = caseInsensitive;
        this.root = new TrieNode(0);  // Root represents empty string ""
        
        // Build the complete automaton if patterns are provided
        if (patterns != null && !patterns.isEmpty()) {
            buildTrie(patterns);           // Phase 1: Create trie structure
            buildFailureLinks();           // Phase 2: Add failure transitions
            buildOutputLinks();            // Phase 3: Optimize output reporting
        }
    }
    
    /**
     * Constructs a case-sensitive Aho-Corasick automaton.
     * 
     * @param patterns list of patterns to search for
     */
    public AhoCorasickSearch(List<String> patterns) {
        this(patterns, false);
    }
    
    /**
     * Searches for all occurrences of multiple patterns in the given text.
     * This is the main multi-pattern search method - searches all patterns in one pass.
     * 
     * **EXAMPLE:**
     * ```java
     * String text = "USHERS";
     * List<String> patterns = Arrays.asList("HE", "SHE", "HIS", "HERS");
     * Map<String, List<Integer>> matches = AhoCorasickSearch.searchMultiple(text, patterns);
     * 
     * // Result: {"SHE": [1], "HE": [2]}
     * // Explanation:
     * // - "SHE" found at position 1: U[SHE]RS
     * // - "HE" found at position 2: US[HE]RS
     * // - "HIS" not found
     * // - "HERS" not found
     * ```
     * 
     * **OVERLAPPING MATCHES:**
     * ```java
     * String text = "AAAA";
     * List<String> patterns = Arrays.asList("AA", "AAA");
     * Map<String, List<Integer>> matches = AhoCorasickSearch.searchMultiple(text, patterns);
     * 
     * // Result: {"AA": [0, 1, 2], "AAA": [0, 1]}
     * // Finds all overlapping occurrences
     * ```
     * 
     * @param text the text to search in (cannot be null)
     * @param patterns the patterns to search for (cannot be null or empty)
     * @return map from pattern to list of starting positions where each pattern is found
     *         (empty lists omitted, returns empty map if no matches)
     * 
     * Time Complexity: O(n + m + z) where n=text length, m=sum of pattern lengths, z=matches
     */
    public static Map<String, List<Integer>> searchMultiple(String text, List<String> patterns) {
        return searchMultiple(text, patterns, false);
    }
    
    /**
     * Searches for all occurrences of multiple patterns in the given text.
     * 
     * @param text the text to search in
     * @param patterns the patterns to search for
     * @param caseInsensitive whether to perform case-insensitive matching
     * @return map from pattern to list of positions where each pattern is found
     */
    public static Map<String, List<Integer>> searchMultiple(String text, List<String> patterns, boolean caseInsensitive) {
        if (text == null || patterns == null || patterns.isEmpty()) {
            return new HashMap<>();
        }
        
        AhoCorasickSearch ac = new AhoCorasickSearch(patterns, caseInsensitive);
        return ac.searchMultiple(text);
    }
    
    /**
     * Searches for all occurrences of a single pattern in the given text.
     * This method provides compatibility with other string matching algorithms like KMP.
     * 
     * **EXAMPLE:**
     * ```java
     * String text = "AABAACAADAABAAABAA";
     * String pattern = "AABA";
     * List<Integer> positions = AhoCorasickSearch.search(text, pattern);
     * 
     * // Result: [0, 9, 13]
     * // Positions where "AABA" starts:
     * // [AABA]ACAADAABAAABAA - position 0
     * // AABAACAAD[AABA]AABAA - position 9  
     * // AABAACAADAAB[AABA]A - position 13 (overlapping with previous)
     * ```
     * 
     * **EDGE CASES:**
     * ```java
     * // Empty text or pattern
     * search("", "pattern");    // Returns []
     * search("text", "");       // Returns []
     * 
     * // Pattern longer than text
     * search("AB", "ABCD");     // Returns []
     * 
     * // Pattern equals text
     * search("ABC", "ABC");     // Returns [0]
     * ```
     * 
     * @param text the text to search in (cannot be null)
     * @param pattern the pattern to search for (cannot be null)
     * @return list of starting positions where the pattern is found (empty if no matches)
     * @throws IllegalArgumentException if text or pattern is null
     * 
     * Time Complexity: O(n + m) where n=text length, m=pattern length
     */
    public static List<Integer> search(String text, String pattern) {
        // Validate inputs
        if (text == null || pattern == null) {
            throw new IllegalArgumentException("Text and pattern cannot be null");
        }
        
        // Handle empty string edge cases
        if (text.isEmpty() || pattern.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Use multi-pattern search with single pattern
        Map<String, List<Integer>> results = searchMultiple(text, Arrays.asList(pattern));
        return results.getOrDefault(pattern, Collections.emptyList());
    }
    
    /**
     * Searches for all patterns in the given text using this automaton.
     * This is the core search algorithm that processes text in a single pass.
     * 
     * **ALGORITHM WALKTHROUGH:**
     * ```
     * Text: "USHERS", Patterns: ["HE", "SHE", "HIS"]
     * 
     * Step-by-step execution:
     * i=0, char='U': No transition from root, stay at root
     * i=1, char='S': Transition root --S--> node(S)
     * i=2, char='H': Transition node(S) --H--> node(SH)
     * i=3, char='E': Transition node(SH) --E--> node(SHE)
     *                Report: "SHE" at position 1
     *                Follow output link to node(HE)
     *                Report: "HE" at position 2
     * i=4, char='R': No transition from node(SHE), follow failure to node(HE)
     *                No transition from node(HE), follow failure to root
     *                No transition from root, stay at root
     * i=5, char='S': Transition root --S--> node(S)
     * 
     * Final result: {"SHE": [1], "HE": [2]}
     * ```
     * 
     * @param text the text to search in (cannot be null)
     * @return map from pattern to list of starting positions where each pattern is found
     * @throws IllegalArgumentException if text is null
     * 
     * Time Complexity: O(n + z) where n=text length, z=number of matches
     * (trie construction time O(m) already paid in constructor)
     */
    public Map<String, List<Integer>> searchMultiple(String text) {
        // Validate input
        if (text == null) {
            throw new IllegalArgumentException("Text cannot be null");
        }
        
        // Empty text can't contain any patterns
        if (text.isEmpty()) {
            return new HashMap<>();
        }
        
        // Initialize result map: pattern → list of positions
        Map<String, List<Integer>> results = new HashMap<>();
        
        // Convert text to lowercase if case-insensitive mode
        String searchText = caseInsensitive ? text.toLowerCase() : text;
        
        // Start at root of the automaton
        TrieNode currentNode = root;
        
        /*
         * MAIN SEARCH LOOP - Process each character in the text
         * 
         * **WHAT HAPPENS AT EACH STEP:**
         * For each character c at position i in text:
         * 1. Find next state: Try to transition on c, use failure links if needed
         * 2. Report matches: Check if any patterns end at current position
         * 3. Continue: Move to next character
         * 
         * **EXAMPLE at position i=3, char='E' in text "USHERS":**
         * - Current node represents "SH"
         * - Try transition: node(SH) --E--> node(SHE) ✓ exists
         * - Move to node(SHE)
         * - Report: "SHE" found at position i-2 = 1
         * - Follow output link: node(HE) also has output
         * - Report: "HE" found at position i-1 = 2
         */
        for (int i = 0; i < searchText.length(); i++) {
            char c = searchText.charAt(i);
            
            /*
             * STEP 1: Find the next state by following the automaton
             * 
             * **THE TRANSITION STRATEGY:**
             * - First, try direct transition from current node on character c
             * - If no transition exists, follow failure links until we find one
             * - If we reach root with no transition, stay at root
             * 
             * **WHY THIS WORKS:**
             * Failure links point to longest proper suffixes, so we're efficiently
             * trying all possible pattern prefixes that could match at this position.
             * 
             * **EXAMPLE:**
             * If at node "SH" with char 'X':
             * - node(SH) has no child 'X'
             * - Follow failure(SH) → node(H)
             * - node(H) has no child 'X'  
             * - Follow failure(H) → root
             * - Root has no child 'X', stay at root
             */
            while (currentNode != root && !currentNode.hasChild(c)) {
                // No direct transition, follow failure link to try next best match
                currentNode = currentNode.getFailure();
                LOG.debug("Following failure link from depth {} to depth {}", 
                         currentNode.getDepth() + 1, currentNode.getDepth());
            }
            
            // After the loop, either currentNode has child c, or currentNode == root
            if (currentNode.hasChild(c)) {
                // Found a valid transition, follow it
                currentNode = currentNode.getChild(c);
                LOG.debug("Transition on '{}' to depth {}", c, currentNode.getDepth());
            } else {
                // No transition even from root, stay at root
                // This means current character doesn't start any pattern
                LOG.debug("No transition on '{}', staying at root", c);
            }
            
            /*
             * STEP 2: Report all patterns ending at current position
             * 
             * **WHY WE CHECK EVERY POSITION:**
             * Even though we just transitioned to currentNode, there might be
             * multiple patterns ending here due to overlaps.
             * 
             * **EXAMPLE at node(SHE):**
             * - Node(SHE) has output "SHE" (found at position i-2)
             * - Output link points to node(HE) with output "HE" (found at position i-1)
             * - Both patterns reported in one go
             * 
             * This is the key advantage of Aho-Corasick: finding all overlapping
             * matches efficiently using output links instead of backtracking.
             */
            reportMatches(currentNode, i, results);
        }
        
        return results;
    }
    
    /**
     * Reports all patterns that end at the current position in the text.
     * Uses output links to efficiently traverse all matches without backtracking.
     * 
     * **HOW IT WORKS:**
     * Starting from the current node, follow output links to find all nodes
     * representing patterns that end at this text position.
     * 
     * **EXAMPLE:**
     * ```
     * Text: "USHERS", position=3 (character 'E'), node=SHE
     * 
     * Reporting process:
     * 1. Check node(SHE): has output "SHE"
     *    → Pattern "SHE" length 3, ends at position 3
     *    → Starts at position 3 - 3 + 1 = 1
     *    → Report: "SHE" at position 1
     * 
     * 2. Follow output link: node(SHE) → node(HE)
     *    → Pattern "HE" length 2, ends at position 3
     *    → Starts at position 3 - 2 + 1 = 2
     *    → Report: "HE" at position 2
     * 
     * 3. Follow output link: node(HE) → null
     *    → No more output links, done
     * 
     * Result: Found both "SHE" at 1 and "HE" at 2
     * ```
     * 
     * **WHY OUTPUT LINKS ARE IMPORTANT:**
     * Without output links, we'd need to follow failure links repeatedly:
     * node(SHE) → failure → node(HE) → failure → root
     * Output links shortcut directly to nodes with patterns.
     * 
     * @param node the current trie node representing the matched prefix
     * @param position the current position in text (0-indexed, inclusive)
     * @param results the results map to update with found patterns
     * 
     * Time Complexity: O(k) where k = number of patterns ending at this position
     */
    private void reportMatches(TrieNode node, int position, Map<String, List<Integer>> results) {
        TrieNode current = node;
        
        // Follow the output link chain to report all patterns
        // This loop terminates when we reach a node with no output link (null)
        while (current != null) {
            // Check if current node marks the end of any pattern(s)
            if (current.hasOutput()) {
                // A node can have multiple outputs if multiple patterns end here
                for (String pattern : current.getOutput()) {
                    // Calculate where the pattern starts in the text
                    // If pattern "SHE" ends at position 3, it starts at 3 - 3 + 1 = 1
                    int startPos = position - pattern.length() + 1;
                    LOG.debug("Pattern '{}' found at position {}", pattern, startPos);
                    
                    // Add to results map: pattern → list of positions
                    // computeIfAbsent creates new ArrayList if pattern not yet in map
                    results.computeIfAbsent(pattern, k -> new ArrayList<>()).add(startPos);
                }
            }
            
            // Move to next node in output link chain
            // Will be null when no more patterns to report
            current = current.getOutputLink();
        }
    }
    
    /**
     * Phase 1: Build the trie (prefix tree) containing all patterns.
     * 
     * **TRIE CONSTRUCTION ALGORITHM:**
     * For each pattern:
     * 1. Start at root
     * 2. For each character, follow existing edge or create new node
     * 3. Mark final node with pattern output
     * 
     * **DETAILED EXAMPLE for patterns ["HE", "SHE", "HIS", "HERS"]:**
     * ```
     * Step 1: Insert "HE"
     *   root --H--> H(1) --E--> E(2) [output: "HE"]
     * 
     * Step 2: Insert "SHE"
     *   root --S--> S(3) --H--> H(4) --E--> E(5) [output: "SHE"]
     *   (Creates new branch from root)
     * 
     * Step 3: Insert "HIS"
     *   root --H--> H(1) --I--> I(6) --S--> S(7) [output: "HIS"]
     *   (Reuses H(1), creates new branch for I)
     * 
     * Step 4: Insert "HERS"
     *   root --H--> H(1) --E--> E(2) --R--> R(8) --S--> S(9) [output: "HERS"]
     *   (Reuses path H→E, extends it)
     * 
     * Final trie structure:
     *          root(0)
     *         /      \
     *       H(1)     S(3)
     *      /   \       |
     *    E(2)  I(6)   H(4)
     *     |     |       |
     *    R(8)  S(7)   E(5)
     *     |
     *    S(9)
     * 
     * Outputs: E(2)→"HE", S(7)→"HIS", E(5)→"SHE", S(9)→"HERS"
     * ```
     * 
     * **PREFIX SHARING:**
     * Patterns with common prefixes share trie nodes:
     * - "HE" and "HERS" share path root→H→E
     * - "HE" and "HIS" share node H(1)
     * This is why trie is space-efficient for pattern sets with overlap.
     * 
     * @param patterns list of patterns to insert into trie (nulls and empty strings skipped)
     * 
     * Time Complexity: O(m) where m = sum of all pattern lengths
     * Space Complexity: O(m * σ) where σ = alphabet size (worst case, no sharing)
     */
    private void buildTrie(List<String> patterns) {
        LOG.debug("Building trie for {} patterns", patterns.size());
        
        // Insert each pattern into the trie
        for (String pattern : patterns) {
            // Skip invalid patterns
            if (pattern == null || pattern.isEmpty()) {
                continue;
            }
            
            // Convert to lowercase for case-insensitive matching
            String insertPattern = caseInsensitive ? pattern.toLowerCase() : pattern;
            
            // Start at root and traverse/create path for this pattern
            TrieNode current = root;
            
            // Process each character in the pattern
            for (int i = 0; i < insertPattern.length(); i++) {
                char c = insertPattern.charAt(i);
                
                // Create new node if transition doesn't exist
                if (!current.hasChild(c)) {
                    // New node is one level deeper than current
                    current.addChild(c, new TrieNode(current.getDepth() + 1));
                    LOG.debug("Created new node for '{}' at depth {}", c, current.getDepth() + 1);
                }
                
                // Follow the edge to the next node
                current = current.getChild(c);
            }
            
            // Mark this node as the end of the pattern
            // Store original pattern (preserves case for case-insensitive search)
            current.addOutput(pattern);
            LOG.debug("Pattern '{}' added to trie, ending at depth {}", pattern, current.getDepth());
        }
    }
    
    /**
     * Phase 2: Build failure links using BFS traversal.
     * 
     * **FAILURE LINK CONSTRUCTION ALGORITHM:**
     * 
     * The failure link for a node representing string α points to the node
     * representing the longest proper suffix of α that is also a prefix of
     * some pattern in the trie.
     * 
     * **EXAMPLE for trie with patterns ["HE", "SHE", "HIS"]:**
     * ```
     * BFS Level 1 (depth 1): H(1), S(3)
     * - failure(H) = root (no proper suffix)
     * - failure(S) = root (no proper suffix)
     * 
     * BFS Level 2 (depth 2): E(2), I(6), H(4)  
     * - failure(E) = root (suffix "E" not a prefix)
     * - failure(I) = root (suffix "I" not a prefix)
     * - failure(H) = H(1) (suffix "H" matches prefix "H")
     * 
     * BFS Level 3 (depth 3): S(7), E(5)
     * - failure(S) = root (suffix "IS" not a prefix)
     * - failure(E) = E(2) (suffix "E" matches node E from "HE")
     * ```
     * 
     * **WHY THIS WORKS:**
     * When we fail at node representing "SHE" and need to continue with next
     * character, we follow failure("SHE") = "HE" because "HE" is the longest
     * suffix of "SHE" that's also a prefix of some pattern.
     */
    private void buildFailureLinks() {
        LOG.debug("Building failure links");
        
        Queue<TrieNode> queue = new LinkedList<>();
        
        // Initialize level 1 nodes (direct children of root)
        for (Character c : root.getChildrenKeys()) {
            TrieNode child = root.getChild(c);
            child.setFailure(root);
            queue.offer(child);
            LOG.debug("Set failure for depth 1 node '{}' to root", c);
        }
        
        // Process remaining levels using BFS
        while (!queue.isEmpty()) {
            TrieNode current = queue.poll();
            
            // Process all children of current node
            for (Character c : current.getChildrenKeys()) {
                TrieNode child = current.getChild(c);
                queue.offer(child);
                
                /*
                 * FIND FAILURE LINK FOR CHILD
                 * 
                 * To find failure link for string α + c (where α is represented by current):
                 * 1. Start with failure(α) 
                 * 2. Follow failure links until we find a node with transition on c
                 * 3. If found, failure(α + c) = that transition target
                 * 4. If not found, failure(α + c) = root
                 */
                TrieNode failureCandidate = current.getFailure();
                
                while (failureCandidate != root && !failureCandidate.hasChild(c)) {
                    failureCandidate = failureCandidate.getFailure();
                }
                
                // After the loop, failureCandidate is either root or has child c
                if (failureCandidate.hasChild(c)) {
                    child.setFailure(failureCandidate.getChild(c));
                    LOG.debug("Set failure for node at depth {} to depth {}", 
                             child.getDepth(), child.getFailure().getDepth());
                } else {
                    // Only happens if failureCandidate == root && root has no child c
                    child.setFailure(root);
                    LOG.debug("Set failure for node at depth {} to root", child.getDepth());
                }
            }
        }
    }
    
    /**
     * Phase 3: Build output links for efficient pattern reporting.
     * 
     * **OUTPUT LINK OPTIMIZATION:**
     * 
     * Instead of following failure links repeatedly to find all patterns
     * that end at a position, output links directly connect to the next
     * node with pattern output.
     * 
     * **EXAMPLE:**
     * ```
     * For patterns ["HE", "SHE"]:
     * 
     * When we're at node representing "SHE":
     * - This node has output "SHE"
     * - Its failure link points to "HE" node which has output "HE"  
     * - So outputLink("SHE") = "HE" node for direct access
     * 
     * During search, when we reach "SHE" node:
     * 1. Report "SHE" from current node
     * 2. Follow outputLink to "HE" node and report "HE"
     * 3. No more output links, done
     * 
     * This avoids having to follow failure links during search.
     * ```
     * 
     * **CONSTRUCTION ALGORITHM:**
     * For each node, follow its failure link chain and find the first
     * node with pattern output. Set output link to that node.
     */
    private void buildOutputLinks() {
        LOG.debug("Building output links");
        
        Queue<TrieNode> queue = new LinkedList<>();
        
        // Add all level 1 nodes to queue
        for (Character c : root.getChildrenKeys()) {
            queue.offer(root.getChild(c));
        }
        
        // Process all nodes using BFS
        while (!queue.isEmpty()) {
            TrieNode current = queue.poll();
            
            // Add children to queue for processing
            for (Character c : current.getChildrenKeys()) {
                queue.offer(current.getChild(c));
            }
            
            /*
             * FIND OUTPUT LINK FOR CURRENT NODE
             * 
             * Follow failure links until we find a node with output,
             * then set that as the output link for direct access.
             */
            TrieNode failureNode = current.getFailure();
            
            while (failureNode != null && !failureNode.hasOutput()) {
                failureNode = failureNode.getFailure();
            }
            
            if (failureNode != null && failureNode.hasOutput()) {
                current.setOutputLink(failureNode);
                LOG.debug("Set output link for node at depth {} to node at depth {}", 
                         current.getDepth(), failureNode.getDepth());
            }
        }
    }
}
