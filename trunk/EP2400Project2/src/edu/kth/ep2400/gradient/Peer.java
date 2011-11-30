/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.kth.ep2400.gradient;

import peersim.core.Node;

/**
 *
 * @author Cuneyt Caliskan
 */
public class Peer {

    private Node node;
    private int timeStamp;

    public Peer(Node node, int timeStamp) {
        this.node = node;
        this.timeStamp = timeStamp;
    }

    public Node getNode() {
        return node;
    }

    public int getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(int timeStamp) {
        this.timeStamp = timeStamp;
    }

    @Override
    public boolean equals(Object obj) {

        if (obj == null) {
            return false;
        }

        if (!(obj instanceof Peer)) {
            return false;
        }
        Peer other = (Peer) obj;
        if (node.getID() == other.getNode().getID()) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return (int) node.getID();
    }
}
