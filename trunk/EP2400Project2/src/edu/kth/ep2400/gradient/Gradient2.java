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
    //private static final String PAR_LEADER_DISSEMINATION_TTL = "leaderDisseminationTTL";
    private final int cacheSize;
    private final int leaderSearchCycles;
    private int protocolId;
    private Node node;
    private Peer bestNeighbor;
    private Peer electedLeader;
    private int leaderCounter;
    private int votesCollected;
    private final String prefix;
    private ArrayList<Peer> cache;
    private ArrayList<Peer> randomSet;
    private ArrayList<Peer> electionGroup;

    public Gradient2(String prefix) {
        super(prefix);
        this.prefix = prefix;
        cacheSize = Configuration.getInt(prefix + "." + PAR_CACHE);
        leaderSearchCycles = Configuration.getInt(prefix + "." + PAR_LEADER_ANN_CYCLES);
        protocolId = 0;
        node = null;
        cache = new ArrayList<Peer>();
        randomSet = new ArrayList<Peer>();
        electionGroup = new ArrayList<Peer>();
        bestNeighbor = null;
        electedLeader = null;
        leaderCounter = 0;
        votesCollected = 0;
    }

    @Override
    public void nextCycle(Node node, int protocolID) {
        protocolId = protocolID;
        this.node = node;
        PreferenceComparator utilComp = new PreferenceComparator(node, protocolId);

        //Select a peer P from random set and add P2 to random set from P's random set
        manageRandomSet();

        if (cache.isEmpty()) {
            cache = mergeNeighbors(node, cache, randomSet);
        }
        //Select probabilistically a random peer from both random set and cache biased towards higher utility
        randomSet = mergeNeighbors(node, cache, randomSet);
        Peer luckyPeer = selectProbabilisticNeighbor(randomSet);
        //Retrieve the similar set of the Neighbor
        Gradient2 cg = (Gradient2) luckyPeer.getNode().getProtocol(protocolId);
        //Merge the similar sets
        cache = mergeNeighbors(node, cg.getNeighbors(), cache);
        removeDeadLinks();
        //Sort according to preference function
        Collections.sort(cache, utilComp);
        //Remove the least significant Neighbors to fit to cache size
        for (int i = cache.size() - 1; i >= cacheSize; i--) {
            cache.remove(i);
        }
        if (electLeader()) {
            twoPhaseCommit();
        }
    }

    public void youAreBlessed(ArrayList<Peer> electionGroup) {
        votesCollected++;
        if (votesCollected > electionGroup.size() / 2) {
            twoPhaseCommit();
        }
    }

    public void youAreOutOfElectionGroup() {
        electedLeader = null;
        electionGroup.clear();
    }

    private void manageRandomSet() {
        int time = CommonState.getIntTime();
        int linkableID = FastConfig.getLinkable(protocolId);
        Linkable linkable = (Linkable) node.getProtocol(linkableID);

        int degree = linkable.degree();
        if (degree == 0) {
            return;
        }
        //Retrieve the random set from the peer sampling service
        for (int i = 0; i < degree; i++) {
            Node sampledPeer = linkable.getNeighbor(i);
            if (sampledPeer.isUp() && !sampledPeer.equals(node)) {
                randomSet.add(new Peer(sampledPeer, time));
            }
        }
        //Pick a random Neighbor from the random set
        int nbIndex = CommonState.r.nextInt(randomSet.size());
        Node randomPeer = randomSet.get(nbIndex).getNode();
        //Retrieve random Neighbor's random set
        List<Peer> peersRandomSet = ((Gradient2) randomPeer.getProtocol(protocolId)).getRandomSet();
        if (!peersRandomSet.isEmpty()) {
            //Add a random Neighbor to random set
            nbIndex = CommonState.r.nextInt(peersRandomSet.size());
            Peer randomPeersRandomPeer = peersRandomSet.get(nbIndex);
            if (!node.equals(randomPeersRandomPeer.getNode()) && !randomSet.contains(randomPeersRandomPeer)) {
                randomPeersRandomPeer.setTimeStamp(time);
                randomSet.add(randomPeersRandomPeer);
            }
        }
    }

    /**
     * Two phase commit procedure to elect the leader. First, this node asks all the
     * members of the election group if it can be the leader. If they reply positively,
     * then the second phase is run and they are made to adopt this node as their leader.
     */
    private void twoPhaseCommit() {
        boolean iCanBeLeader = true;
        if (electionGroup.isEmpty()) {
            electionGroup.addAll(cache);
        }
        //First phase of the procedure. Ask all the election members if I can be the leader
        for (Peer p : electionGroup) {
            Gradient2 pg = (Gradient2) p.getNode().getProtocol(protocolId);
            iCanBeLeader = pg.canIBeYourLeader(node, protocolId);
            if (!iCanBeLeader) {
                break;
            }
        }
        //Second phase. If all replied YES, make them adopt me as their leader.
        if (iCanBeLeader) {
            electedLeader = new Peer(node, CommonState.getIntTime());
            Peer me = new Peer(node, CommonState.getIntTime());
            for (Peer p : electionGroup) {
                //Kick the old election group members
                if (!p.equals(me)) {
                    ((Gradient2) p.getNode().getProtocol(protocolId)).youAreOutOfElectionGroup();
                }
            }
            electionGroup.clear();
            electionGroup.addAll(cache);
            for (Peer p : electionGroup) {
                ((Gradient2) p.getNode().getProtocol(protocolId)).adoptMeAsLeader(node, electionGroup);
            }
            electionGroup.add(me);
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
        if (!isTheLeaderAlive()) {
            return true;
        } else if (((Gradient2) electedLeader.getNode().getProtocol(pid)).getValue() < candidateutility) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Removes the links to dead neighbors and checks if the leader is alive or not.
     */
    private void removeDeadLinks() {
        ArrayList<Peer> deadLinks = new ArrayList<Peer>();
        for (Peer peer : cache) {
            if (!peer.getNode().isUp()) {
                deadLinks.add(peer);
            }
        }
        cache.removeAll(deadLinks);
        deadLinks.clear();
        for (Peer peer : electionGroup) {
            if (!peer.getNode().isUp()) {
                deadLinks.add(peer);
            }
        }
        electionGroup.removeAll(deadLinks);
        if (electedLeader != null && !isTheLeaderAlive()) {
            electedLeader = null;
            //TODO another protocol should be followed: whoIsTheLeader()
            if (consultToElectionGroup()) {
                System.out.println(getValue() + " detected leader failure at " + CommonState.getIntTime());
                //twoPhaseCommit();
                double plv = 0;
                Peer bestPotential = null;
                for (Peer p : electionGroup) {
                    if (!p.equals(new Peer(node, 0))) {
                        Gradient2 pl = (Gradient2) p.getNode().getProtocol(protocolId);
                        Peer pbp = pl.whoIsTheLeader(0);
                        pl = (Gradient2) pbp.getNode().getProtocol(protocolId);
                        if (plv < pl.getValue()) {
                            plv = pl.getValue();
                            bestPotential = pbp;
                        }
                    }
                }
                ((Gradient2) bestPotential.getNode().getProtocol(protocolId)).youAreBlessed(electionGroup);
                System.out.println("Best potential leader value " + plv);
            }
        }
    }

    /**
     * Chooses a random peer from the given {@code list} with a weighted probability
     * according to the utilities of the peers.
     * @param list list peers to choose a random peer
     * @param pid the protocol id
     * @return a probabilistically random chosen peer or {@code null} if the list is empty
     */
    private Peer selectProbabilisticNeighbor(List<Peer> list) {
        double utilitySum = 0;
        for (Peer p : list) {
            utilitySum += ((Gradient2) p.getNode().getProtocol(protocolId)).getValue();
        }
        int luckyNumber = CommonState.r.nextInt((int) utilitySum);
        utilitySum = 0;
        for (Peer p : list) {
            double pv = ((Gradient2) p.getNode().getProtocol(protocolId)).getValue();
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
     * will set the {@link #bestNeighbor} to its new potential leader and reset the {@link #leaderCounter} to 0.
     * @return {@code true} if a leader election should be run, {@code false} otherwise
     */
    private boolean electLeader() {
        if (!electionGroup.isEmpty()) {
            if (electionGroup.contains(new Peer(node, 0))) {
                if (electedLeader != null) {
                    if (isTheLeaderAlive()) {
                        return false;
                    } else {
                        if (consultToElectionGroup()) {
                            return canStartElection();
                        } else {
                            return false;
                        }
                    }
                } else {
                    return canStartElection();
                }
            } else {
                return false;
            }
        } else {
            return amITheNewLeader();
        }
    }

    /**
     * Consult to the election group if the leader is really dead or not.
     * @return {@code true} if majority approves that the leader is dead, {@code false} otherwise.
     */
    private boolean consultToElectionGroup() {
        int aliveCounter = 0;
        for (Peer peer : electionGroup) {
            Gradient2 pg = (Gradient2) peer.getNode().getProtocol(protocolId);
            if (pg.isTheLeaderAlive()) {
                aliveCounter++;
            }
            //If the majority says that the leader is alive, cancel the procedure
            if (aliveCounter > electionGroup.size() / 2) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if the leader is alive or not.
     * @return {@code true] if the leader is alive, {@code false} otherwise.
     */
    public boolean isTheLeaderAlive() {
        if (electedLeader == null) {
            return false;
        } else {
            if (!electedLeader.getNode().isUp()) {
                electedLeader = null;
                return false;
            }
        }
        return true;
    }

    private boolean canStartElection() {
        for (Peer peer : cache) {
            Gradient2 pg = (Gradient2) peer.getNode().getProtocol(protocolId);
            Peer peersBest = pg.whoIsYourBestNeighbor();
            if (peersBest != null) {
                double plv = ((Gradient2) peersBest.getNode().getProtocol(protocolId)).getValue();
                //Is there someone else who can be the leader
                if (plv > value) {
                    return false;
                }
            }
        }
        System.out.println("I am the leader :) " + getValue());
        return true;
    }

    /**
     * Determines if this node is a leader candidate or not.
     * @return {@code true} if and only if this node has detected that its best 
     * neighbor has not changed for {@link #leaderSearchCycles} and all of its
     * neighbor's neighbors do not have a better neighbor than this node.
     * {@code false} otherwise.
     */
    private boolean amITheNewLeader() {
        if (leaderCounter == leaderSearchCycles) {
            leaderCounter = 0;
            return canStartElection();
        } else {
            //Who is my leader?
            Peer newLeader = whoIsYourBestNeighbor();
            if (newLeader != null) {
                if (!newLeader.equals(bestNeighbor)) {
                    bestNeighbor = newLeader;
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
    private ArrayList<Peer> mergeNeighbors(Node node, List<Peer> list1, List<Peer> list2) {
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
     * @return The potential leader among the neighbors or null if there are no neighbors.
     */
    public Peer whoIsYourBestNeighbor() {
        if (!cache.isEmpty()) {
            Peer best = cache.get(0);
            double leaderUtility = ((Gradient2) best.getNode().getProtocol(protocolId)).getValue();
            for (Peer peer : cache) {
                if (peer.getNode().isUp()) {
                    double peerUtility = ((Gradient2) peer.getNode().getProtocol(protocolId)).getValue();
                    if (peerUtility >= leaderUtility) {
                        leaderUtility = peerUtility;
                        best = peer;
                    } else {
                        //Since the cache is sorted, there is no need to continue
                        break;
                    }
                }
            }
            return best;
        } else {
            return null;
        }
    }

    public Peer whoIsTheLeader(int step) {
        if (electedLeader == null) {
            if (cache.isEmpty()) {
                return null;
            } else {
                Gradient2 ng = (Gradient2) whoIsYourBestNeighbor().getNode().getProtocol(protocolId);
                if (ng.getValue() < value) {
                    return new Peer(node, step);
                }
                return ng.whoIsTheLeader(step + 1);
            }
        } else {
            return electedLeader;
        }
    }

    public Peer whoIsYourLeader() {
        return electedLeader;
    }

    @Override
    public Object clone() {
        Gradient2 g = new Gradient2(prefix);
        return g;
    }

    public List<Peer> getNeighbors() {
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

    public Peer getElectedLeader() {
        return electedLeader;
    }
}
