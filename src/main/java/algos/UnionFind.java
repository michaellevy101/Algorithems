package algos;

public class UnionFind {
    private final int[] parent;
    private final int[] rank;
    private int count; // Number of connected components

    /**
     * Initializes the UnionFind data structure with n elements.
     */
    public UnionFind(int n) {
        parent = new int[n];
        rank = new int[n];
        count = n;

        for (int i = 0; i < n; i++) {
            parent[i] = i; // Each element is its own parent initially
            rank[i] = 0;   // Initial rank of each tree (single node) is 0
        }
    }

    /**
     * Finds the representative (root) of the set containing element i,
     * using path compression.
     */
    public int find(int i) {
        if (parent[i] == i) {
            return i;
        }

        // Path compression: set parent[i] directly to the root
        parent[i] = find(parent[i]);
        return parent[i];
    }

    /**
     * Unites the sets containing elements i and j, using union by rank.
     */
    public void union(int i, int j) {
        int rootI = find(i);
        int rootJ = find(j);

        if (rootI != rootJ) {
            // Attach the smaller rank tree under the larger rank tree
            if (rank[rootI] > rank[rootJ]) {
                parent[rootJ] = rootI;
            } else if (rank[rootI] < rank[rootJ]) {
                parent[rootI] = rootJ;
            } else {
                // If ranks are the same, attach one to the other (e.g., J to I)
                parent[rootJ] = rootI;
                // Increment the rank of the new root
                rank[rootI]++;
            }
            count--; // Decrease the number of components after a successful union
        }
    }

    /**
     * Checks if two elements belong to the same set.
     */
    public boolean connected(int i, int j) {
        return find(i) == find(j);
    }

    /**
     * Returns the number of disjoint sets (connected components).
     */
    public int getCount() {
        return count;
    }
}

