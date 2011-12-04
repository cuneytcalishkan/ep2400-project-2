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

    public GradientObserver(String prefix) {
        this.prefix = prefix;
        this.pid = Configuration.getPid(this.prefix + "." + PAR_PROTOCOL);
    }

    @Override
    public boolean execute() {
        long time = peersim.core.CommonState.getTime();

        for (int i = 0; i < Network.size(); i++) {
            Gradient2 g = (Gradient2) Network.get(i).getProtocol(pid);
            String nl = "[";
            nl += "Time: " + time + ", ";
            nl += "My value:{" + g.getValue() + "}, ";
            Peer bestNeighbor = g.whoIsYourBestNeighbor();
            if (bestNeighbor != null) {
                double best = ((Gradient2) bestNeighbor.getNode().getProtocol(pid)).getValue();
                Peer l = g.whoIsTheLeader(0);
                Gradient2 lg = (Gradient2) l.getNode().getProtocol(pid);
                nl += "Leader:[" + lg.getValue() + ", " + l.getTimeStamp() + "], ";
                nl += "Best neighbor:[" + best + "]";
                nl += "Elected leader:[" + g.getElectedLeader() + "]\n";
            }

            for (Peer peer : g.getNeighbors()) {
                Gradient2 ng = (Gradient2) peer.getNode().getProtocol(pid);
                nl += ng.getValue() + ", ";
            }
            nl += "]";
            System.out.println(nl);
        }

        return false;
    }
}
