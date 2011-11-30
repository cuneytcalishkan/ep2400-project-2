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
    private static final String PAR_LEADER_ANN_CYCLES = "leaderAnnCycles";
    private static final String PAR_LEADER_DISSEMINATION_TTL = "leaderDisseminationTTL";
    private final int cacheSize;
    private final int leaderAnnCycles;
    private final int ttl;
    private Peer estimatedLeader;
    private Peer electedLeader;
    private int leaderCounter;
    private final String prefix;
    private ArrayList<Peer> cache;
    private ArrayList<Peer> electionGroup;

    public Gradient(String prefix) {
        super(prefix);
        this.prefix = prefix;
        cacheSize = Configuration.getInt(prefix + "." + PAR_CACHE);
        leaderAnnCycles = Configuration.getInt(prefix + "." + PAR_LEADER_ANN_CYCLES);
        ttl = Configuration.getInt(prefix + "." + PAR_LEADER_DISSEMINATION_TTL);
        cache = new ArrayList<Peer>();
        electionGroup = new ArrayList<Peer>();
        estimatedLeader = null;
        electedLeader = null;
        leaderCounter = 0;
    }

    @Override
    public void nextCycle(Node node, int protocolID) {
        UtilityComparator utilComp = new UtilityComparator(node, protocolID);
        int time = CommonState.getIntTime();
        int linkableID = FastConfig.getLinkable(protocolID);
        Linkable linkable = (Linkable) node.getProtocol(linkableID);

        int degree = linkable.degree();
        int nbIndex = CommonState.r.nextInt(degree);
        Node randomNode = linkable.getNeighbor(nbIndex);
        if (!randomNode.isUp()) {
            return;
        }

        if (cache.isEmpty()) {
            cache.add(new Peer(randomNode, time));
        }

        Gradient cycGrad = (Gradient) randomNode.getProtocol(protocolID);
        mergeNeighbours(node, cycGrad.getNeighbours());
        Collections.sort(cache, utilComp);

        Gradient cg = (Gradient) cache.get(0).getNode().getProtocol(protocolID);
        mergeNeighbours(node, cg.getNeighbours());
        Collections.sort(cache, utilComp);

        for (int i = cache.size() - 1; i >= cacheSize; i--) {
            cache.remove(i);
        }

        leaderElection(protocolID);
    }

    private void leaderElection(int protocolId) {
        if (leaderCounter == leaderAnnCycles) {
            //TODO start leader election
            leaderCounter = 0;
            ArrayList<Peer> leaders = new ArrayList<Peer>();
            for (Peer peer : cache) {
                Node pn = peer.getNode();
                Gradient pg = (Gradient) pn.getProtocol(protocolId);
                Peer peersLeader = pg.whoIsYourLeader(protocolId);
                if (peersLeader != null) {
                    double plv = ((Gradient) peersLeader.getNode().getProtocol(protocolId)).getValue();
                    //There is someone else who can be the leader
                    if (plv > value) {
                        return;
                    }
                    leaders.add(peersLeader);
                }
            }
            System.out.println("I am the leader :) " + getValue());
        } else {
            //Who is my leader?
            Peer newLeader = whoIsYourLeader(protocolId);
            if (newLeader != null) {
                if (!newLeader.equals(estimatedLeader)) {
                    estimatedLeader = newLeader;
                } else {
                    leaderCounter++;
                }
            }
        }
    }

    private void mergeNeighbours(Node node, List<Peer> neighbours) {
        for (Peer n : neighbours) {
            if (!cache.contains(n) && (!n.getNode().equals(node))) {
                cache.add(n);
            }
        }
    }

    public Peer whoIsYourLeader(int protocolId) {
        if (cache.isEmpty()) {
            return null;
        } else {
            Peer leader = cache.get(0);
            double leaderUtility = ((Gradient) leader.getNode().getProtocol(protocolId)).getValue();
            for (Peer peer : cache) {
                if (peer.getNode().isUp()) {
                    double peerUtility = ((Gradient) peer.getNode().getProtocol(protocolId)).getValue();
                    if (peerUtility >= leaderUtility) {
                        leaderUtility = peerUtility;
                        leader = peer;
                    } else {
                        break;
                    }
                }
            }
            return leader;
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

        if (v1 <= myValue && v2 <= myValue) {
            if (v1 > v2) {
                return -1;
            } else {
                return 1;
            }
        }

        if (v1 > myValue && v2 <= myValue) {
            return -1;
        } else {
            return 1;
        }
    }
}
