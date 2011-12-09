/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.kth.ep2400.gradient;

import peersim.config.Configuration;
import peersim.core.Control;
import peersim.core.Network;

/**
 *
 * @author cuneyt
 */
public class GradientObserver implements Control {

    private final static String PAR_PROTOCOL = "protocol";
    private final String prefix;
    private final int pid;

    /**
     * Control protocol that is used to collect performance metrics over the network.
     * @param prefix The protocol prefix provided by Peersim.
     */
    public GradientObserver(String prefix) {
        this.prefix = prefix;
        this.pid = Configuration.getPid(this.prefix + "." + PAR_PROTOCOL);
    }

    @Override
    public boolean execute() {
        long time = peersim.core.CommonState.getTime();

        for (int i = 0; i < Network.size(); i++) {
            Gradient3 g = (Gradient3) Network.get(i).getProtocol(pid);
            String nl = "[";
            nl += "Time: " + time + ", ";
            nl += "My value:{" + g.getValue() + "}, ";
            Peer bestNeighbor = g.whoIsYourHighestNeighbor();
            if (bestNeighbor != null) {
                double best = ((Gradient3) bestNeighbor.getNode().getProtocol(pid)).getValue();
                Peer l = g.whoIsTheLeader(1);
                if (l != null) {
                    Gradient3 lg = (Gradient3) l.getNode().getProtocol(pid);
                    nl += "Leader:[" + lg.getValue() + ", " + l.getTimeStamp() + "], ";
                }
                //nl += "Best neighbor:[" + best + "]";
                if (g.getElectedLeader() != null) {
                    nl += "Elected leader:[" + ((Gradient3) g.getElectedLeader().getNode().getProtocol(pid)).getValue() + "]";
                } else {
                    //nl += "Elected leader:[null]";
                }
            }

            for (Peer peer : g.getNeighbors()) {
                Gradient3 ng = (Gradient3) peer.getNode().getProtocol(pid);
                nl += ng.getValue() + ", ";
            }
            nl += "]";
            System.out.println(nl);
        }

        return false;
    }
}
