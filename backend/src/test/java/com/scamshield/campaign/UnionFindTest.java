package com.scamshield.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The clustering DSA in isolation: correctness of find/union, transitivity, and component count. */
class UnionFindTest {

    @Test
    void startsFullyDisjoint() {
        UnionFind uf = new UnionFind(5);
        assertThat(uf.componentCount()).isEqualTo(5);
        assertThat(uf.connected(0, 1)).isFalse();
    }

    @Test
    void unionMergesAndIsTransitive() {
        UnionFind uf = new UnionFind(6);
        // Two reposts of scam A (0,1,2) and two of scam B (3,4).
        uf.union(0, 1);
        uf.union(1, 2);
        uf.union(3, 4);

        assertThat(uf.connected(0, 2)).as("transitivity via 1").isTrue();
        assertThat(uf.connected(3, 4)).isTrue();
        assertThat(uf.connected(2, 3)).as("separate campaigns stay separate").isFalse();
        // {0,1,2}, {3,4}, {5} = three components.
        assertThat(uf.componentCount()).isEqualTo(3);
    }

    @Test
    void unionOfAlreadyConnectedIsNoOp() {
        UnionFind uf = new UnionFind(3);
        assertThat(uf.union(0, 1)).isTrue();
        assertThat(uf.union(1, 0)).as("already connected").isFalse();
        assertThat(uf.componentCount()).isEqualTo(2);
    }

    @Test
    void survivesLongChainsViaPathCompression() {
        int n = 10_000;
        UnionFind uf = new UnionFind(n);
        for (int i = 1; i < n; i++) {
            uf.union(i - 1, i);
        }
        assertThat(uf.componentCount()).isEqualTo(1);
        assertThat(uf.connected(0, n - 1)).isTrue();
    }

    @Test
    void rejectsNegativeSize() {
        assertThatThrownBy(() -> new UnionFind(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
