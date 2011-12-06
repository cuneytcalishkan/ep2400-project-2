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

    public UpdatePuller(String prefix) {
        pid = Configuration.getPid(prefix + "." + PAR_PROTOCOL);
    }

    @Override
    public boolean execute() {


        int rn = CommonState.r.nextInt(Network.size());
        Gradient3 g = (Gradient3) Network.get(rn).getProtocol(pid);
        Peer leader = g.whoIsTheLeader(1);
        if (leader == null) {
            return false;
        }
        List<Message> messages = new ArrayList<Message>();
        messages = g.pullMessage(CommonState.r.nextInt(rn * CommonState.getIntTime()), CommonState.r.nextInt(rn * CommonState.getIntTime()));
        System.out.println(messages.size() + " messages pulled");
        for (Message message : messages) {
            System.out.println(message);
        }
        return false;
    }
}
