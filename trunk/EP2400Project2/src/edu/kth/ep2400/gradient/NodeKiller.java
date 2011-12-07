/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.kth.ep2400.gradient;

import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.Control;
import peersim.core.Network;
import peersim.core.Node;

/**
 *
 * @author cuneyt
 */
public class NodeKiller implements Control {

    private final String PAR_NODES_TO_KILL = "numOfNodesToKill";
    private final String PAR_START_ROUND = "startRound";
    private final String PAR_STEP = "killStep";
    private final String PAR_END_ROUND = "endRound";
    private final int numOfNodesToKill;
    private final int startRound;
    private final int killStep;
    private final int endRound;

    /**
     * This controller is run to kill {@link #numOfNodesToKill} nodes every 
     * {@link #killStep} rounds starting from {@link #startRound} and ending, not
     * including, at {@link #endRound}.
     * @param prefix The prefix of this protocol provided by Peersim
     */
    public NodeKiller(String prefix) {
        numOfNodesToKill = Configuration.getInt(prefix + "." + PAR_NODES_TO_KILL);
        startRound = Configuration.getInt(prefix + "." + PAR_START_ROUND);
        killStep = Configuration.getInt(prefix + "." + PAR_STEP);
        endRound = Configuration.getInt(prefix + "." + PAR_END_ROUND);
    }

    @Override
    public boolean execute() {
        int time = CommonState.getIntTime();
        if (startRound > time || time >= endRound) {
            return false;
        }

        if (time == startRound || (time - startRound) % killStep == 0) {
            System.out.println("killing " + numOfNodesToKill + " nodes");
            for (int i = 0; i < numOfNodesToKill; i++) {
                Node n = Network.remove();
                System.out.println(n.getID() + " removed");
            }
        }
        return false;
    }
}
