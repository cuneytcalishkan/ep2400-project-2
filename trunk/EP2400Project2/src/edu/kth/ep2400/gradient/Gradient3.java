/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.kth.ep2400.gradient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
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
public class Gradient3 extends SingleValueHolder implements CDProtocol {

    private static final String PAR_CACHE = "cache";
    private static final String PAR_LEADER_ANN_CYCLES = "leaderAnnCycles";
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
    private TreeMap<Long, Message> messages;
    private long msgId;
    private long localMessageCounter = 0;
    private ArrayList<String> queue;
    private PreferenceComparator prefComp;
    private int failOverMessages = 0;
    private int newLeaderMessages = 0;

    /**
     * Gradient overlay topology built according to preference function.
     * @param prefix
     */
    public Gradient3(String prefix) {
        super(prefix);
        this.prefix = prefix;
        cacheSize = Configuration.getInt(prefix + "." + PAR_CACHE);
        leaderSearchCycles = Configuration.getInt(prefix + "." + PAR_LEADER_ANN_CYCLES);
        protocolId = 0;
        node = null;
        cache = new ArrayList<Peer>();
        randomSet = new ArrayList<Peer>();
        electionGroup = new ArrayList<Peer>();
        messages = new TreeMap(new MessageComparator());
        queue = new ArrayList<String>();
        msgId = 0;
        bestNeighbor = null;
        electedLeader = null;
        leaderCounter = 0;
        votesCollected = 0;
    }

    @Override
    public void nextCycle(Node node, int protocolID) {
        protocolId = protocolID;
        this.node = node;
        prefComp = new PreferenceComparator(node, protocolId);

        //Select a peer P from random set and add P2 to random set from P's random set
        manageRandomSet();

        if (cache.isEmpty()) {
            cache = mergeNeighbors(randomSet, cache);
        }
        //Select probabilistically a random peer from both random set and cache biased towards higher utility
        ArrayList<Peer> rs = mergeNeighbors(randomSet, cache);
        Peer luckyPeer = selectProbabilisticNeighbor(rs);
        //Retrieve the similar set of the Neighbor
        Gradient3 cg = (Gradient3) luckyPeer.getNode().getProtocol(protocolId);
        //Merge the similar sets
        cache = mergeNeighbors(cg.getNeighbors(), cache);
        removeDeadLinks();
        checkLeader();
        //Sort according to preference function
        Collections.sort(cache, prefComp);
        //Remove the least significant Neighbors to fit to cache size
        for (int i = cache.size() - 1; i >= cacheSize; i--) {
            if (electionGroup.contains(cache.get(i))) {
                ((Gradient3) cache.get(i).getNode().getProtocol(protocolId)).youAreOutOfElectionGroup();
            }
            cache.remove(i);
        }
        if (((new Peer(node, 0)).equals(electedLeader))) {
            electionGroup.clear();
            electionGroup.addAll(cache);
            for (Peer p : cache) {
                Peer pl = ((Gradient3) p.getNode().getProtocol(protocolId)).updateElectionGroup(electionGroup, electedLeader, messages);
                if (!pl.equals(electedLeader)) {
                    for (Peer peer : electionGroup) {
                        ((Gradient3) peer.getNode().getProtocol(protocolId)).youAreOutOfElectionGroup();
                    }
                    electionGroup.clear();
                    electedLeader = null;
                    break;
                }
            }
        }
        if (electLeader()) {
            twoPhaseCommit();
        }
//        publishMessage();
//        if (whoIsTheLeader(1) != null) {
//            List<Message> result = new ArrayList<>();
//            if (messages.isEmpty()) {
//                result = pullMessage(1, -1);
//            } else {
//                result = pullMessage(messages.firstKey() + 1, -1);
//            }
//            for (Message message : result) {
//                messages.put(message.getId(), message);
//            }
//            System.out.println(messages.size() + " messages in total by " + getValue());
//        }

    }

    /**
     * This method is used to maintain the election group members when the neighbors of the leader change.
     * When the leader adds new neighbors, they are asked to be the members of the election group.
     * @param newElectionGroup The new election group members.
     * @param newLeader The currently elected leader.
     * @param msgs Message history
     */
    public Peer updateElectionGroup(List<Peer> newElectionGroup, Peer newLeader, TreeMap<Long, Message> msgs) {
        Peer result = electedLeader;
        if (isTheLeaderAlive()) {
            Gradient3 nlg = (Gradient3) newLeader.getNode().getProtocol(protocolId);
            Gradient3 lg = (Gradient3) electedLeader.getNode().getProtocol(protocolId);
            if (lg.getValue() < nlg.getValue()) {
                electionGroup.clear();
                electionGroup.addAll(newElectionGroup);
                electedLeader = newLeader;
                messages = msgs;
                if (!messages.isEmpty()) {
                    msgId = messages.firstKey();
                }
                result = newLeader;
            }
        } else {
            electionGroup.clear();
            electionGroup.addAll(newElectionGroup);
            electedLeader = newLeader;
            messages = msgs;
            if (!messages.isEmpty()) {
                msgId = messages.firstKey();
            }
            result = newLeader;
        }
        return result;
    }

    /**
     * This method is called by the election group members when they detect the leader's failure.
     * They vote on their best potential leader through this method.
     * @param electionGroup The election group
     */
    public void youAreBlessed(ArrayList<Peer> electionGroup) {
        votesCollected++;
        if (votesCollected > electionGroup.size() / 2) {
            votesCollected = 0;
            failOverMessages++;
            twoPhaseCommit();
        }
    }

    /**
     * Returns the message history.
     * @return The published messages stored in this node.
     */
    public TreeMap<Long, Message> getMessageHistory() {
        return messages;
    }

    /**
     * This method is used to maintain the election group to kick the old members
     * when new ones replace them.
     */
    public void youAreOutOfElectionGroup() {
        electedLeader = null;
        electionGroup.clear();
    }

    /**
     * Retrieves a random neighbor P from the peer sampling service and selects
     * a random peer P2 from P's random set and adds it to the random set of this node.
     */
    private void manageRandomSet() {
        int time = CommonState.getIntTime();
        int linkableID = FastConfig.getLinkable(protocolId);
        Linkable linkable = (Linkable) node.getProtocol(linkableID);

        int degree = linkable.degree();
        if (degree == 0) {
            return;
        }
        randomSet.clear();
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
        List<Peer> peersRandomSet = ((Gradient3) randomPeer.getProtocol(protocolId)).getRandomSet();
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
     * members of the election group if it can be the leader. If they all reply positively,
     * then the second phase is run and they are made to adopt this node as their leader.
     */
    private void twoPhaseCommit() {
        if (electionGroup.isEmpty()) {
            electionGroup.addAll(cache);
        }
        kickOldElectionGroup();
        boolean iCanBeLeader = true;
        //First phase of the procedure. Ask all the election members if I can be the leader
        for (Peer p : electionGroup) {
            Gradient3 pg = (Gradient3) p.getNode().getProtocol(protocolId);
            iCanBeLeader = pg.canIBeYourLeader(node);
            newLeaderMessages += 2;
            if (!iCanBeLeader) {
                break;
            }
        }
        //Second phase. If all replied YES, make them adopt me as their leader.
        if (iCanBeLeader) {
            Peer me = new Peer(node, CommonState.getIntTime());
            electedLeader = me;
            for (Peer p : electionGroup) {
                ((Gradient3) p.getNode().getProtocol(protocolId)).adoptMeAsLeader(node, electionGroup);
                newLeaderMessages++;
            }
            System.out.println("I am the leader :) " + getValue() + " with " + messages.size() + " messages!");
        }
    }

    /**
     * Tells old election group members that they are out and selects its similar set as the new election group.
     */
    private void kickOldElectionGroup() {
        Peer me = new Peer(node, CommonState.getIntTime());
        Gradient3 g = (Gradient3) electionGroup.get(0).getNode().getProtocol(protocolId);
        TreeMap<Long, Message> msgh = g.getMessageHistory();
        for (Peer p : electionGroup) {
            //Kick the old election group members
            if (!p.equals(me)) {
                g = (Gradient3) p.getNode().getProtocol(protocolId);
                if (g.getMessageHistory().size() > msgh.size()) {
                    msgh = g.getMessageHistory();
                }
                g.youAreOutOfElectionGroup();
            }
        }
        messages = msgh;
        if (!messages.isEmpty()) {
            msgId = messages.firstKey();
        }
        electionGroup.clear();
        electionGroup.addAll(cache);
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
     * @return {@code true} if the calling node can be my leader, {@code false} otherwise.
     */
    public boolean canIBeYourLeader(Node n) {
        double candidateutility = ((Gradient3) n.getProtocol(protocolId)).getValue();
        if (isTheLeaderAlive()) {
            Gradient3 lg = (Gradient3) electedLeader.getNode().getProtocol(protocolId);
            if (lg.getValue() > candidateutility) {
                return false;
            } else {
                if (getValue() > candidateutility) {
                    return false;
                } else {
                    return true;
                }
            }
        } else {
            if (getValue() > candidateutility) {
                return false;
            } else {
                return true;
            }
        }
    }

    /**
     * Removes the dead links in the similar set and election group.
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
    }

    /**
     * Checks if the current elected leader is alive or not. If it is not alive,
     * it will consult to the election group and will collect votes from them. If
     * the majority agrees on the failure of the leader, this node will vote on 
     * its potential leader.
     */
    private void checkLeader() {
        if (electedLeader != null && !isTheLeaderAlive()) {
            if (consultToElectionGroup()) {
                electedLeader = null;
                System.out.println(getValue() + " detected leader failure at " + CommonState.getIntTime());
                double plv = 0;
                Peer bestPotential = new Peer(node, CommonState.getIntTime());
                for (Peer p : electionGroup) {
                    Gradient3 pl = (Gradient3) p.getNode().getProtocol(protocolId);
                    Peer pbp = pl.whoIsThePotentialLeader();
                    pl = (Gradient3) pbp.getNode().getProtocol(protocolId);
                    if (plv < pl.getValue()) {
                        plv = pl.getValue();
                        bestPotential = pbp;
                    }
                }
                ((Gradient3) bestPotential.getNode().getProtocol(protocolId)).youAreBlessed(electionGroup);
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
        Collections.sort(list, new UtilityComparator());
        double lb = 0;
        double ub = lb;
        double p = 100;
        double r = CommonState.r.nextDouble() * p;
        for (int i = 0; i < list.size(); i++) {
            Peer peer = list.get(i);
            p /= 2;
            ub += p;
            if (r >= lb && r < ub) {
                return peer;
            }
            lb = ub;
        }



        return null;
    }

    /**
     * The procedure is run if there is no elected leader currently or the elected 
     * leader has failed and this node is part of the election group.
     * If there is no elected leader yet, each peer will try to find out if it can 
     * be the leader by calling {@link #amITheNewLeader()} method.
     * If the node is part of the election group, that means there is an elected leader,
     * it will try to detect if the leader is alive or not. If the leader is alive, the method
     * returns. If the leader is dead, it will consult to the election group. If they all agree,
     * it will try to be the new leader.
     * @return {@code true} if a leader election should be run, {@code false} otherwise
     */
    private boolean electLeader() {
        if (!electionGroup.isEmpty()) {
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
            failOverMessages += 2;
            Gradient3 pg = (Gradient3) peer.getNode().getProtocol(protocolId);
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
     * @return {@code true] if there is an elected leader and it is alive, {@code false} otherwise.
     */
    public boolean isTheLeaderAlive() {
        if (electedLeader != null && electedLeader.getNode().isUp()) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * When a node identifies that it is time to start election because {@link #amITheNewLeader()} method
     * returned true, it will call this method to determine if he is the node with the highest utility
     * among its neighbors' neighbors.
     * @return {@code true} if this node can be the new leader.
     */
    private boolean canStartElection() {
        for (Peer peer : cache) {
            Gradient3 pg = (Gradient3) peer.getNode().getProtocol(protocolId);
            Peer peersBest = pg.whoIsYourHighestNeighbor();
            if (peersBest != null) {
                double plv = ((Gradient3) peersBest.getNode().getProtocol(protocolId)).getValue();
                //Is there someone else who can be the leader
                if (plv > value) {
                    return false;
                }
            }
        }
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
            Peer newLeader = whoIsYourHighestNeighbor();
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
    private ArrayList<Peer> mergeNeighbors(List<Peer> list1, List<Peer> list2) {
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
     * Selects the highest utility among the neighbors.
     * @return The highest utility node among the neighbors or null if there are no neighbors.
     */
    public Peer whoIsYourHighestNeighbor() {
        if (!cache.isEmpty()) {
            Peer best = null;
            double leaderUtility = 0;
            for (Peer peer : cache) {
                if (peer.getNode().isUp()) {
                    double peerUtility = ((Gradient3) peer.getNode().getProtocol(protocolId)).getValue();
                    if (peerUtility > leaderUtility) {
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

    /**
     * This method determines who the elected leader is by passing messages over the gradient.
     * @param step The number of steps traveled so far.
     * @return The elected leader if there is one or null if there is no elected leader.
     */
    public Peer whoIsTheLeader(int step) {
        Peer leader = null;
        if (electedLeader == null) {
            Peer best = whoIsYourHighestNeighbor();
            if (best != null) {
                Gradient3 g = (Gradient3) best.getNode().getProtocol(protocolId);
                if (g.getValue() > getValue()) {
                    leader = g.whoIsTheLeader(step + 1);
                }
            }
        } else {
            if (((Gradient3) electedLeader.getNode().getProtocol(protocolId)).stillLeader()) {
                electedLeader.setTimeStamp(step);
                leader = electedLeader;
            } else {
                electedLeader = null;
                electionGroup.clear();
                Peer best = whoIsYourHighestNeighbor();
                if (best != null) {
                    Gradient3 g = (Gradient3) best.getNode().getProtocol(protocolId);
                    if (g.getValue() > getValue()) {
                        leader = g.whoIsTheLeader(step + 1);
                    }
                }
            }
        }
        return leader;

    }

    /**
     * This method determines who is the potential leader in the gradient by relaying
     * messages over the gradient towards the higher utility nodes.
     * @return The highest utility node in the gradient reached by this query or null
     * if none of the nodes have no neighbors.
     */
    public Peer whoIsThePotentialLeader() {
        if (electedLeader == null) {
            if (cache.isEmpty()) {
                return null;
            } else {
                Gradient3 ng = (Gradient3) whoIsYourHighestNeighbor().getNode().getProtocol(protocolId);
                if (ng.getValue() < value) {
                    //End condition when the leader is reached.
                    return new Peer(node, CommonState.getIntTime());
                }
                return ng.whoIsThePotentialLeader();
            }
        } else {
            if (electedLeader.getNode().isUp()) {
                return electedLeader;
            } else {
                Gradient3 ng = (Gradient3) whoIsYourHighestNeighbor().getNode().getProtocol(protocolId);
                if (ng.getValue() < value) {
                    //End condition when the leader is reached.
                    return new Peer(node, CommonState.getIntTime());
                }
                return ng.whoIsThePotentialLeader();
            }
        }
    }

    @Override
    public Object clone() {
        Gradient3 g = new Gradient3(prefix);
        return g;
    }

    /**
     * 
     * @return
     */
    public List<Peer> getNeighbors() {
        return cache;
    }

    /**
     * The preference function required to sort the neighbors to construct the gradient.
     * @param o1 First peer to be compared.
     * @param o2 Second peer to be compared.
     * @param node This node
     * @param protocolId Protocol id of {@link Gradient3} protocol.
     * @return 1 if {@code o2} is preferred over {@code o1}, 0 if they are equal and -1 
     * if {@code o1} is preferred over {@code o2}.
     */
    public static int compare(Peer o1, Peer o2, Node node, int protocolId) {
        double myValue = ((Gradient3) node.getProtocol(protocolId)).getValue();
        double v1 = ((Gradient3) o1.getNode().getProtocol(protocolId)).getValue();
        double v2 = ((Gradient3) o2.getNode().getProtocol(protocolId)).getValue();

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

    /**
     * 
     * @return The random set provided by the peer sampling service.
     */
    public ArrayList<Peer> getRandomSet() {
        return randomSet;
    }

    /**
     * 
     * @return The current elected leader.
     */
    public Peer getElectedLeader() {
        return electedLeader;
    }

    /**
     * Every cycle, each node publishes a message. If there is an elected leader,
     * the leader is asked to broadcast it. If no elected leader yet, the messages are
     * put into queue and published when the leader is elected.
     */
    private void publishMessage() {
        Peer leader = whoIsTheLeader(1);
        String msg = (++localMessageCounter) + " " + getValue();
        queue.add(msg);
        if (leader != null) {
            ((Gradient3) leader.getNode().getProtocol(protocolId)).broadcastMessage(queue);
            queue.clear();
        }
    }

    /**
     * This method is only revoked on the elected leader to broadcast a message that 
     * is published by a node in the gradient. The message is also pushed down to the
     * election group to keep replicas of the messages in case of a leader failure.
     * @param msg The message to be broadcasted.
     */
    public synchronized void broadcastMessage(List<String> msgs) {
        for (String mc : msgs) {
            Message m = new Message(++msgId, mc);
            messages.put(m.getId(), m);
            for (Peer p : electionGroup) {
                ((Gradient3) p.getNode().getProtocol(protocolId)).pushMessage(m);
            }
        }
    }

    /**
     * 
     * @param msg This method is invoked only on the election group members. It is
     * used to keep replicas of the broadcasted messages.
     */
    public void pushMessage(Message msg) {
        messages.put(msg.getId(), msg);
    }

    /**
     * Pull message starting from id {@code from} up to id {@code to}. If {@code to} is 
     * set to -1, it means all messages up to now.
     * @param from Lower bound of the messages to be pulled.
     * @param to Upper bound of the messages to be pulled. -1 indicates all messages.
     * @return List of messages accumulated over the gradient.
     */
    public List<Message> pullMessage(long from, long to) {
        ArrayList<Message> result = new ArrayList<Message>();
        long fk = 1;
        if (!messages.isEmpty()) {
            fk = messages.firstKey();
        }
        boolean finished = false;
        Peer best = whoIsYourHighestNeighbor();
        Gradient3 bn = null;
        if (best != null) {
            bn = (Gradient3) best.getNode().getProtocol(protocolId);
        }
        if (from > fk) {
            if (bn != null) {
                if (!(bn.getValue() < getValue())) {
                    result.addAll(bn.pullMessage(from, to));
                }
            }
        } else {
            long f = from;
            long t = to;

            if (to == -1) {
                t = fk;
            } else {
                if (to <= fk) {
                    t = to;
                } else {
                    t = fk;
                    finished = false;
                }
            }
            if (f != t) {
                for (long i = f; i <= t; i++) {
                    result.add(messages.get(i));
                }
            } else {
                t--;
            }
            if ((new Peer(node, 0)).equals(electedLeader)) {
                finished = true;
            }
            if (!finished) {
                if (bn != null) {
                    result.addAll(bn.pullMessage(t + 1, to));
                }
            }
        }
        return result;
    }

    public boolean stillLeader() {
        Peer me = new Peer(node, 0);
        if (electedLeader.equals(me)) {
            return true;
        }
        return false;
    }

    public int getFailOverMessages() {
        return failOverMessages;
    }

    public int getNewLeaderMessages() {
        return newLeaderMessages;
    }

    class MessageComparator implements Comparator<Long> {

        @Override
        public int compare(Long o1, Long o2) {

            if (o1 > o2) {
                return -1;
            } else if (o1 < o2) {
                return 1;
            } else {
                return 0;
            }
        }
    }

    class NeighborComparator implements Comparator<Peer> {

        @Override
        public int compare(Peer o1, Peer o2) {

            double v1 = ((Gradient3) o1.getNode().getProtocol(protocolId)).getValue();
            double v2 = ((Gradient3) o2.getNode().getProtocol(protocolId)).getValue();
            if (v1 < v2) {
                return 1;
            } else if (v1 > v2) {
                return -1;
            } else {
                return 0;
            }
        }
    }

    class UtilityComparator implements Comparator<Peer> {

        @Override
        public int compare(Peer o1, Peer o2) {
            Gradient3 g1 = (Gradient3) o1.getNode().getProtocol(protocolId);
            Gradient3 g2 = (Gradient3) o2.getNode().getProtocol(protocolId);
            if (g1.getValue() < g2.getValue()) {
                return 1;
            } else if (g1.getValue() > g2.getValue()) {
                return -1;
            } else {
                return 0;
            }
        }
    }
}
