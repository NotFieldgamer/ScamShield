package com.verity.matching;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aho-Corasick multi-pattern string matcher.
 *
 * <p>Finds every occurrence of any of thousands of patterns in a single left-to-right pass over
 * the text — O(text length + total matches), independent of the number of patterns. Built once
 * from the scam-phrase registry and reused for every request.
 *
 * <p>Three ideas:
 * <ol>
 *   <li>a <b>trie</b> (goto function) of all patterns;</li>
 *   <li><b>failure links</b>: from a node for string {@code s}, the fail link points to the node
 *       for the longest proper suffix of {@code s} that is also a trie prefix — so on a mismatch
 *       we fall back to the longest still-viable partial match instead of restarting;</li>
 *   <li><b>output links</b>: each node collects the patterns that end at it <em>or</em> at any node
 *       reachable via its failure chain, so suffix matches are reported without extra walking.</li>
 * </ol>
 */
public final class AhoCorasickMatcher {

    private static final class Node {
        final Map<Character, Node> children = new HashMap<>();
        Node fail;
        final List<Integer> outputs = new ArrayList<>(); // indices of patterns ending here
    }

    private final Node root = new Node();
    private final List<String> patterns;

    public AhoCorasickMatcher(List<String> patterns) {
        this.patterns = List.copyOf(patterns);
        for (int i = 0; i < this.patterns.size(); i++) {
            insert(this.patterns.get(i), i);
        }
        buildFailureLinks();
    }

    private void insert(String pattern, int patternIndex) {
        Node node = root;
        for (int i = 0; i < pattern.length(); i++) {
            node = node.children.computeIfAbsent(pattern.charAt(i), c -> new Node());
        }
        node.outputs.add(patternIndex);
    }

    /** Breadth-first assignment of failure and output links (Aho-Corasick construction). */
    private void buildFailureLinks() {
        Deque<Node> queue = new ArrayDeque<>();
        root.fail = root;
        for (Node child : root.children.values()) {
            child.fail = root;
            queue.add(child);
        }
        while (!queue.isEmpty()) {
            Node current = queue.poll();
            for (Map.Entry<Character, Node> entry : current.children.entrySet()) {
                char edge = entry.getKey();
                Node child = entry.getValue();

                Node f = current.fail;
                while (f != root && !f.children.containsKey(edge)) {
                    f = f.fail;
                }
                Node candidate = f.children.get(edge);
                child.fail = (candidate != null && candidate != child) ? candidate : root;

                // The fail node was processed earlier (it is shallower), so its outputs already
                // include its own failure chain — one merge collects all suffix matches.
                child.outputs.addAll(child.fail.outputs);
                queue.add(child);
            }
        }
    }

    /** Every match as (patternIndex, start, end) with end exclusive; includes overlaps. */
    public List<Match> findAll(String text) {
        List<Match> matches = new ArrayList<>();
        Node node = root;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            while (node != root && !node.children.containsKey(c)) {
                node = node.fail;
            }
            Node next = node.children.get(c);
            node = (next != null) ? next : root;
            for (int patternIndex : node.outputs) {
                int length = patterns.get(patternIndex).length();
                matches.add(new Match(patternIndex, i - length + 1, i + 1));
            }
        }
        return matches;
    }

    public String pattern(int index) {
        return patterns.get(index);
    }

    public record Match(int patternIndex, int start, int end) {}
}
