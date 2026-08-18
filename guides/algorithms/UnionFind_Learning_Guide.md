# UnionFind Algorithm - Learning Guide

## Overview
UnionFind (Disjoint Set Union) is a data structure that efficiently tracks elements partitioned into disjoint sets. It supports two primary operations:
- **Find**: Determine which set an element belongs to
- **Union**: Merge two sets containing different elements

## Key Concepts

### 1. Path Compression
Flattens the tree structure during find operations for faster subsequent queries.

```java
public int find(int i) {
    if (parent[i] == i) return i;
    parent[i] = find(parent[i]); // Path compression
    return parent[i];
}
```

### 2. Union by Rank
Attaches smaller trees under larger ones to minimize tree height.

```java
public void union(int i, int j) {
    int rootI = find(i), rootJ = find(j);
    if (rootI != rootJ) {
        if (rank[rootI] > rank[rootJ]) {
            parent[rootJ] = rootI;
        } else if (rank[rootI] < rank[rootJ]) {
            parent[rootI] = rootJ;
        } else {
            parent[rootJ] = rootI;
            rank[rootI]++;
        }
        count--;
    }
}
```

## Implementation Details

### Data Structure Components
- **parent[]**: Array where parent[i] points to the parent of element i
- **rank[]**: Array tracking the approximate height of each tree
- **count**: Number of disjoint sets (connected components)

### Initialization
```java
public UnionFind(int n) {
    parent = new int[n];
    rank = new int[n];
    count = n;
    
    for (int i = 0; i < n; i++) {
        parent[i] = i; // Each element is its own parent initially
        rank[i] = 0;   // Initial rank of each tree is 0
    }
}
```

## Time Complexity
- **Find**: O(α(n)) - nearly constant with path compression
- **Union**: O(α(n)) - where α is the inverse Ackermann function
- **Space**: O(n)

*Note: α(n) is the inverse Ackermann function, which grows extremely slowly and is effectively constant for all practical values of n.*

## Visual Example

Initial state (5 elements):
```
0   1   2   3   4
↓   ↓   ↓   ↓   ↓
0   1   2   3   4
```

After `union(0,1)` and `union(2,3)`:
```
    1       3
   ↙       ↙
  0       2       4
                  ↓
                  4
```

After `union(1,2)` (connecting the two components):
```
        3
       ↙ ↘
      2   1
         ↙
        0     4
              ↓
              4
```

## Common Applications
1. **Kruskal's Algorithm** - Minimum Spanning Tree
2. **Network Connectivity** - Check if nodes are connected
3. **Dynamic Connectivity** - Online connectivity queries
4. **Image Processing** - Connected components in images
5. **Social Networks** - Friend groups and communities

## Example Usage
```java
UnionFind uf = new UnionFind(5); // Elements 0,1,2,3,4
uf.union(0, 1); // Connect 0 and 1
uf.union(2, 3); // Connect 2 and 3
System.out.println(uf.connected(0, 1)); // true
System.out.println(uf.connected(0, 2)); // false
System.out.println(uf.getCount()); // 3 components: {0,1}, {2,3}, {4}
```

## Practice Problems
1. **Number of Connected Components in Undirected Graph** (LeetCode 323)
2. **Friend Circles** (LeetCode 547)
3. **Accounts Merge** (LeetCode 721)
4. **Redundant Connection** (LeetCode 684)
5. **Number of Islands** (LeetCode 200) - Alternative approach

## Key Insights
- Path compression is crucial for performance - it flattens trees during find operations
- Union by rank prevents trees from becoming too tall
- The combination of both optimizations gives nearly constant time operations
- UnionFind is particularly useful when you need to efficiently determine connectivity between elements
