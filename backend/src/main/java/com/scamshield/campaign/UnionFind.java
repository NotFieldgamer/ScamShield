package com.scamshield.campaign;

/**
 * Disjoint-set union over {@code [0, n)} with path compression and union by rank — near-constant
 * amortized find/union (inverse-Ackermann). Used to cluster near-duplicate postings: each
 * near-duplicate pair is a union, and the resulting components are the campaigns (the same scam
 * reposted under different names). Deliberately index-based and dependency-free so it is trivially
 * unit-testable in isolation from the database.
 */
public final class UnionFind {

    private final int[] parent;
    private final int[] rank;
    private int components;

    public UnionFind(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("size must be non-negative");
        }
        parent = new int[n];
        rank = new int[n];
        components = n;
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
    }

    /** Representative of i's set, compressing the path to the root along the way. */
    public int find(int i) {
        int root = i;
        while (parent[root] != root) {
            root = parent[root];
        }
        while (parent[i] != root) {
            int next = parent[i];
            parent[i] = root;
            i = next;
        }
        return root;
    }

    /** Merge the sets containing i and j. Returns true if they were previously separate. */
    public boolean union(int i, int j) {
        int ri = find(i);
        int rj = find(j);
        if (ri == rj) {
            return false;
        }
        if (rank[ri] < rank[rj]) {
            int tmp = ri;
            ri = rj;
            rj = tmp;
        }
        parent[rj] = ri;
        if (rank[ri] == rank[rj]) {
            rank[ri]++;
        }
        components--;
        return true;
    }

    public boolean connected(int i, int j) {
        return find(i) == find(j);
    }

    public int componentCount() {
        return components;
    }
}
