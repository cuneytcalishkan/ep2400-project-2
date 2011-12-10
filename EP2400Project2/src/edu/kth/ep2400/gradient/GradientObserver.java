/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.kth.ep2400.gradient;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import peersim.config.Configuration;
import peersim.core.Control;
import peersim.core.Network;
import peersim.util.IncrementalStats;

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
        FileWriter ldFos = null;
        FileWriter foFos = null;
        FileWriter nlFos = null;
        String newLine = "\n";
        long time = peersim.core.CommonState.getTime();
        IncrementalStats isLeaderDiscovery = new IncrementalStats();
        IncrementalStats isFailOver = new IncrementalStats();
        IncrementalStats isNewLeader = new IncrementalStats();
        for (int i = 0; i < Network.size(); i++) {
            Gradient3 g = (Gradient3) Network.get(i).getProtocol(pid);
            Peer leader = g.whoIsTheLeader(0);
            if (leader != null) {
                isLeaderDiscovery.add(leader.getTimeStamp());
                if (leader.equals(new Peer(Network.get(i), i))) {
                    isNewLeader.add(g.getNewLeaderMessages());
                }
            }

            isFailOver.add(g.getFailOverMessages());

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
        try {
            File leaderDiscovery = new File("sim-results" + File.separator + "leaderDiscovery.tsv");
            File newLeader = new File("sim-results" + File.separator + "newLeader.tsv");
            File failOver = new File("sim-results" + File.separator + "failOver.tsv");
            ldFos = new FileWriter(leaderDiscovery, true);
            ldFos.write(time + "\t" + isLeaderDiscovery.getAverage() + "\t" + isLeaderDiscovery.getMax() + newLine);
            foFos = new FileWriter(failOver, true);
            foFos.write(time + "\t" + isFailOver.getAverage() + "\t" + isFailOver.getMax() + newLine);
            nlFos = new FileWriter(newLeader, true);
            nlFos.write(time + "\t" + isNewLeader.getAverage() + "\t" + isNewLeader.getMax() + newLine);

            return false;
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            try {
                ldFos.close();
                foFos.close();
                nlFos.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return false;
    }
}
