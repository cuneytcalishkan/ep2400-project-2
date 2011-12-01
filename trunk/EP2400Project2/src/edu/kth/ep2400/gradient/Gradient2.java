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
public class Gradient2 extends SingleValueHolder implements CDProtocol {

    private static final String PAR_CACHE = "cache";
    private static final String PAR_LEADER_ANN_CYCLES = "leaderAnnCycles";
    private static final String PAR_LEADER_DISSEMINATION_TTL = "leaderDisseminationTTL";
    private final int cacheSize;
    private final int leaderSearchCycles;
    private final int ttl;
    private Peer estimatedLeader;
    private Peer electedLeader;
    private int leaderCounter;
    private final String prefix;
    private ArrayList<Peer> cache;
    private ArrayList<Peer> randomSet;
    private ArrayList<Peer> electionGroup;

    public Gradient2(String prefix) {
        super(prefix);
        this.prefix = prefix;
        cacheSize = Configuration.getInt(prefix + "." + PAR_CACHE);
        leaderSearchCycles = Configuration.getInt(prefix + "." + PAR_LEADER_ANN_CYCLES);
        ttl = Configuration.getInt(prefix + "." + PAR_LEADER_DISSEMINATION_TTL);
        cache = new ArrayList<Peer>();
        randomSet = new ArrayList<Peer>();
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
        if (degree == 0) {
            return;
        }
        //Retrieve the random set from the peer sampling sevice
        for (int i = 0; i < degree; i++) {
            Node sampledPeer = linkable.getNeighbor(i);
            if (sampledPeer.isUp() && !sampledPeer.equals(node)) {
                randomSet.add(new Peer(sampledPeer, time));
            }
        }
        //Pick a random neighbour from the random set
        int nbIndex = CommonState.r.nextInt(randomSet.size());
        Node randomPeer = randomSet.get(nbIndex).getNode();
        //Retrieve random neighbour's random set
        List<Peer> peersRandomSet = ((Gradient2) randomPeer.getProtocol(protocolID)).getRandomSet();
        if (!peersRandomSet.isEmpty()) {
            //Add a random neighbour to random set
            nbIndex = CommonState.r.nextInt(peersRandomSet.size());
            Peer randomPeersRandomPeer = peersRandomSet.get(nbIndex);
            if (!node.equals(randomPeersRandomPeer.getNode()) && !randomSet.contains(randomPeersRandomPeer)) {
                randomPeersRandomPeer.setTimeStamp(time);
                randomSet.add(randomPeersRandomPeer);
            }
        }
        if (cache.isEmpty()) {
            cache = mergeNeighbours(node, cache, randomSet);
        }
        //Select probabilistically a random peer from both random set and cache biased towards higher utility
        randomSet = mergeNeighbours(node, cache, randomSet);
        Peer luckyPeer = selectProbabilisticNeighbour(randomSet, protocolID);
        //Retrieve the similar set of the neighbour
        Gradient2 cg = (Gradient2) luckyPeer.getNode().getProtocol(protocolID);
        //Merge the similar sets
        cache = mergeNeighbours(node, cg.getNeighbours(), cache);
        eliminateDeadNeighbors();
        //Sort according to preference function
        Collections.sort(cache, utilComp);
        //Remoce the least significant neighbours to fit to cache size
        for (int i = cache.size() - 1; i >= cacheSize; i--) {
            cache.remove(i);
        }
        if (electLeader(node, protocolID)) {
            electedLeader = new Peer(node, time);
            electionGroup.clear();
            electionGroup.addAll(cache);
            ArrayList<Peer> rejected = new ArrayList<Peer>();
            for (Peer p : electionGroup) {
                Gradient2 pg = (Gradient2) p.getNode().getProtocol(protocolID);
                if (!pg.canIBeYourLeader(node, protocolID)) {
                    rejected.add(p);
                }
            }

            if (rejected.isEmpty()) {
                for (Peer p : electionGroup) {
                    ((Gradient2) p.getNode().getProtocol(protocolID)).adoptMeAsLeader(node, electionGroup);
                }
            } else {
                ArrayList<Peer> potentialLeaders = new ArrayList<Peer>();
                for (Peer peer : rejected) {
                    potentialLeaders.add(((Gradient2) peer.getNode().getProtocol(protocolID)).whoIsYourLeader(protocolID));
                }
                //TODO contact the potential leaders
            }
        }
    }

    /**
     * The new leader makes the election group members adopt it as their leader.
     * @param node New leader
     * @param electionGroup Election process members
     */
    public void adoptMeAsLeader(Node node, List<Peer> electionGroup) {
        int time = CommonState.getIntTime();
        electedLeader = new Peer(node, time);
        this.electionGroup.clear();
        this.electionGroup.addAll(electionGroup);
    }

    /**
     * Can the calling node become my leader?
     * @param n Calling node
     * @param pid The protocol id
     * @return {@code true} if I don't have any elected leader or the calling node has greater utility, {@code false} otherwise.
     */
    public boolean canIBeYourLeader(Node n, int pid) {
        double candidateutility = ((Gradient2) n.getProtocol(pid)).getValue();
        if (electedLeader == null || !electedLeader.getNode().isUp()) {
            return true;
        } else if (((Gradient2) electedLeader.getNode().getProtocol(pid)).getValue() < candidateutility) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Removes the links to dead neighbors.
     */
    private void eliminateDeadNeighbors() {
        ArrayList<Peer> nodesToRemove = new ArrayList<Peer>();
        for (Peer peer : cache) {
            if (!peer.getNode().isUp()) {
                nodesToRemove.add(peer);
            }
        }
        cache.removeAll(nodesToRemove);
        if (electedLeader != null && !electedLeader.getNode().isUp()) {
            electedLeader = null;
            leaderCounter = leaderSearchCycles;
        }
    }

    /**
     * Chooses a random peer from the given {@code list} with a weighted probability
     * according to the utilities of the peers.
     * @param list list peers to choose a random peer
     * @param pid the protocol id
     * @return a probabilistically random chosen peer or {@code null} if the list is empty
     */
    private Peer selectProbabilisticNeighbour(List<Peer> list, int pid) {
        double utilitySum = 0;
        for (Peer p : list) {
            utilitySum += ((Gradient2) p.getNode().getProtocol(pid)).getValue();
        }
        int luckyNumber = CommonState.r.nextInt((int) utilitySum);
        utilitySum = 0;
        for (Peer p : list) {
            double pv = ((Gradient2) p.getNode().getProtocol(pid)).getValue();
            if (luckyNumber >= utilitySum && luckyNumber < (utilitySum + pv)) {
                return p;
            } else {
                utilitySum += pv;
            }
        }
        return null;
    }

    /**
     * The procedure is run if there is no elected leader currently or the elected leader has failed and this node is part of the election group.
     * If there is no elected leader yet, each peer will wait until {@link #leaderSearchCycles} of cycles
     * passes without adopting a new neighbor with a higher utility than the current highest utility neighbor.
     * If {@link #leaderSearchCycles} of cycles are passed, the leaders of the neighbors are collected.
     *      If there is a potential leader with a higher utility than this one's, this node exits the procedure.
     *      If not, this node is the potential leader and will return {@code true} to indicate that a leader election should be run.
     * Else, this node will check its own leader and will increment the {@link #leaderCounter} if the leader has not changed or
     * will set the {@link #estimatedLeader} to its new potential leader and reset the {@link #leaderCounter} to 0.
     * @param node the node of this protocol
     * @param pid the protocol id
     * @return {@code true} if a leader election should be run, {@code false} otherwise
     */
    private boolean electLeader(Node node, int pid) {

        if (electedLeader != null) {
            if (electedLeader.getNode().isUp()) {
                return false;
            } else {
                if (!electionGroup.contains(new Peer(node, 0))) {
                    return false;
                }
                int aliveCounter = 0;
                for (Peer peer : electionGroup) {
                    Gradient2 pg = (Gradient2) peer.getNode().getProtocol(pid);
                    if (pg.isTheLeaderAlive()) {
                        aliveCounter++;
                    }
                    //If the majority says that the leader is alive, cancel the procedure
                    if (aliveCounter > electionGroup.size() / 2) {
                        return false;
                    }
                }
                return startElection(pid);
            }
        } else {
            return amITheNewLeader(pid);
        }
    }

    public boolean isTheLeaderAlive() {
        return electedLeader.getNode().isUp();
    }

    private boolean startElection(int pid) {
        leaderCounter = 0;
        ArrayList<Peer> leaders = new ArrayList<Peer>();
        for (Peer peer : cache) {
            Gradient2 pg = (Gradient2) peer.getNode().getProtocol(pid);
            Peer peersLeader = pg.whoIsYourLeader(pid);
            if (peersLeader != null) {
                double plv = ((Gradient2) peersLeader.getNode().getProtocol(pid)).getValue();
                //There is someone else who can be the leader
                if (plv > value) {
                    return false;
                }
                leaders.add(peersLeader);
            }
        }
        System.out.println("I am the leader :) " + getValue());
        return true;
    }

    private boolean amITheNewLeader(int pid) {
        if (leaderCounter == leaderSearchCycles) {
            return startElection(pid);
        } else {
            //Who is my leader?
            Peer newLeader = whoIsYourLeader(pid);
            if (newLeader != null) {
                if (!newLeader.equals(estimatedLeader)) {
                    estimatedLeader = newLeader;
                    leaderCounter = 0;
                } else {
                    leaderCounter++;
                }
            }
            return false;
        }
    }

    /**
     * Merges the given two lists by eliminating the duplicates and the items containing {@code node}.
     * @param node The node of this protocol
     * @param list1 First list to be merged
     * @param list2 Second list to be merged
     * @return Merged list with the duplicates eliminated and entries pointing at {@code node} excluded.
     */
    private ArrayList<Peer> mergeNeighbours(Node node, List<Peer> list1, List<Peer> list2) {
        ArrayList<Peer> result = new ArrayList<Peer>();
        result.addAll(list2);
        for (Peer n : list1) {
            if (!result.contains(n) && (!n.getNode().equals(node))) {
                result.add(n);
            }
        }
        return result;
    }

    /**
     * Selects the potential leader which has the highest utility among the neighbors.
     * @param protocolId The protocol id
     * @return The potential leader among the neighbors or null if there are no neighbors.
     */
    public Peer whoIsYourLeader(int protocolId) {
        if (electedLeader != null) {
            return electedLeader;
        }
        if (!cache.isEmpty()) {
            Peer leader = cache.get(0);
            double leaderUtility = ((Gradient2) leader.getNode().getProtocol(protocolId)).getValue();
            for (Peer peer : cache) {
                if (peer.getNode().isUp()) {
                    double peerUtility = ((Gradient2) peer.getNode().getProtocol(protocolId)).getValue();
                    if (peerUtility >= leaderUtility) {
                        leaderUtility = peerUtility;
                        leader = peer;
                    } else {
                        break;
                    }
                }
            }
            return leader;
        } else {
            return null;
        }
    }

    @Override
    public Object clone() {
        Gradient2 g = new Gradient2(prefix);
        return g;
    }

    public List<Peer> getNeighbours() {
        return cache;
    }

    public static int compare(Peer o1, Peer o2, Node node, int protocolId) {
        double myValue = ((Gradient2) node.getProtocol(protocolId)).getValue();
        double v1 = ((Gradient2) o1.getNode().getProtocol(protocolId)).getValue();
        double v2 = ((Gradient2) o2.getNode().getProtocol(protocolId)).getValue();

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

    public ArrayList<Peer> getRandomSet() {
        return randomSet;
    }
}
