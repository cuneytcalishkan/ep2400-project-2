/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.kth.ep2400.gradient;

import java.util.Comparator;
import peersim.core.Node;

/**
 *
 * @author Cuneyt Caliskan
 */
public class PreferenceComparator implements Comparator<Peer> {

    private final Node node;
    private final int protocolId;

    /**
     * Constructs a comparator that compares the given 2 {@link Peer}s according
     * to gradient's preference function.
     * @param node The node that constructs this object.
     * @param protocolId The protocol id of the node.
     */
    public PreferenceComparator(Node node, int protocolId) {
        this.node = node;
        this.protocolId = protocolId;
    }

    @Override
    public int compare(Peer o1, Peer o2) {

        return Gradient3.compare(o1, o2, node, protocolId);
    }
}
