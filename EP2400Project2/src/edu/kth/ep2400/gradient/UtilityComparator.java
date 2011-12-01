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
public class UtilityComparator implements Comparator<Peer> {

    private final Node node;
    private final int protocolId;

    public UtilityComparator(Node node, int protocolId) {
        this.node = node;
        this.protocolId = protocolId;
    }

    @Override
    public int compare(Peer o1, Peer o2) {

        return Gradient2.compare(o1, o2, node, protocolId);
    }
}
