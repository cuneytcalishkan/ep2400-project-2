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
            Gradient g = (Gradient) Network.get(i).getProtocol(pid);
            System.out.println("Time: " + time);
            System.out.println("My value:[" + g.getValue() + "]");
            Peer leader = g.whoIsYourLeader(pid);
            if (leader != null) {
                System.out.println("My leader:[" + ((Gradient) g.whoIsYourLeader(pid).getNode().getProtocol(pid)).getValue() + "]");
            }
            String nl = "[";
            for (Peer peer : g.getNeighbours()) {
                Gradient ng = (Gradient) peer.getNode().getProtocol(pid);
                nl += ng.getValue() + ", ";
            }
            nl += "]";
            System.out.println(nl);
        }

        return false;
    }
}
