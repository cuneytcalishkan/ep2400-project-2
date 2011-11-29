/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.kth.ep2400.gradient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import peersim.cdsim.CDProtocol;
import peersim.config.Configuration;
import peersim.config.FastConfig;
import peersim.core.CommonState;
import peersim.core.Linkable;
import peersim.core.Node;
import peersim.vector.SingleValueHolder;

/**
 *
 * @author Cuneyt Caliskan
 */
public class Gradient extends SingleValueHolder implements CDProtocol {

    private static final String PAR_CACHE = "cache";
    private final int cacheSize;
    private final String prefix;
    private ArrayList<Peer> cache;

    public Gradient(String prefix) {
        super(prefix);
        this.prefix = prefix;
        this.cacheSize = Configuration.getInt(prefix + "." + PAR_CACHE);
        this.cache = new ArrayList<Peer>();
    }

    @Override
    public void nextCycle(Node node, int protocolID) {

        int linkableID = FastConfig.getLinkable(protocolID);
        Linkable linkable = (Linkable) node.getProtocol(linkableID);

        int degree = linkable.degree();
        int nbIndex = CommonState.r.nextInt(degree);
        Node peer = linkable.getNeighbor(nbIndex);
        if (!peer.isUp()) {
            return;
        }

        if (cache.isEmpty()) {
            cache.add(new Peer(peer, 0));
        }

        Collections.sort(cache, new UtilityComparator(node, protocolID));

        Peer closest = cache.get(0);
        Gradient cg = (Gradient) closest.getNode().getProtocol(protocolID);
        List<Peer> neighborsList = cg.getNeighbours();

        for (Peer n : neighborsList) {
            if (!cache.contains(n) && (!n.getNode().equals(node))) {
                cache.add(n);
            }
        }

        Collections.sort(cache, new UtilityComparator(node, protocolID));
        for (int i = cache.size() - 1; i >= cacheSize; i--) {
            cache.remove(i);
        }
    }

    @Override
    public Object clone() {
        Gradient g = new Gradient(prefix);
        return g;
    }

    public List<Peer> getNeighbours() {
        return cache;
    }

    public static int compare(Peer o1, Peer o2, Node node, int protocolId) {
        double myValue = ((Gradient) node.getProtocol(protocolId)).getValue();
        double v1 = ((Gradient) o1.getNode().getProtocol(protocolId)).getValue();
        double v2 = ((Gradient) o2.getNode().getProtocol(protocolId)).getValue();

        if (v1 == v2) {
            return 0;
        }

        if (v1 > myValue && v2 > myValue) {
            if (v1 > v2) {
                return 1;
            } else {
                return -1;
            }
        }

        if (v1 < myValue && v2 < myValue) {
            if (v1 > v2) {
                return -1;
            } else {
                return 1;
            }
        }

        if (v1 > myValue && v2 < myValue) {
            return -1;
        } else {
            return 1;
        }
    }
}
