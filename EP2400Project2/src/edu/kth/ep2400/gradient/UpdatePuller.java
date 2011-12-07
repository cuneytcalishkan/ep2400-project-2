/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.kth.ep2400.gradient;

import java.util.ArrayList;
import java.util.List;
import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.Control;
import peersim.core.Network;

/**
 *
 * @author cuneyt
 */
public class UpdatePuller implements Control {

    private static final String PAR_PROTOCOL = "protocol";
    private final int pid;

    /**
     * This controller is run every cycle to pull messages from the leader.
     * @param prefix
     */
    public UpdatePuller(String prefix) {
        pid = Configuration.getPid(prefix + "." + PAR_PROTOCOL);
    }

    @Override
    public boolean execute() {

        /*
         * Every cycle, a random peer from the network is selected to pull messages.
         * The lower and upper bounds of the message ids are generated randomly from the maximum
         * number of messages published so far.
         */
        int rn = CommonState.r.nextInt(Network.size());
        Gradient3 g = (Gradient3) Network.get(rn).getProtocol(pid);
        Peer leader = g.whoIsTheLeader(1);
        if (leader == null) {
            return false;
        }
        List<Message> messages = new ArrayList<Message>();
        messages = g.pullMessage(CommonState.r.nextInt(rn * CommonState.getIntTime()), CommonState.r.nextInt(rn * CommonState.getIntTime()));
        System.out.println(messages.size() + " messages pulled");
        if (!messages.isEmpty()) {
            //print out the first message
            System.out.println(messages.get(0));
            if (messages.size() > 1) //print out the last message
            {
                System.out.println(messages.get(messages.size() - 1));
            }
        }
        return false;
    }
}
