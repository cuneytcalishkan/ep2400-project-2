/*
 * Copyright (c) 2010 LCN, EE school, KTH
 *
 */
package edu.kth.ep2400.gradient;

import java.util.ArrayList;
import java.util.List;

import peersim.cdsim.CDProtocol;
import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.Linkable;
import peersim.core.Node;

/**
 * 
 * Template class for CYCLON implementation
 * 
 */
public class Cyclon implements CDProtocol, Linkable {

    private List<Peer> entries;
    private final int cacheSize;
    private final int shuffleLength;
    private ArrayList<Peer> sentList;
    private String prefix;
    /**
     * Cache size.
     * 
     * @config
     */
    private static final String PAR_CACHE = "cache_size";
    /**
     * Shuffle Length.
     * 
     * @config
     */
    private static final String PAR_SHUFFLE_LENGTH = "shuffle_length";

    // ====================== initialization ===============================
    // =====================================================================
    public Cyclon(String prefix) {
        this.prefix = prefix;
        this.cacheSize = Configuration.getInt(prefix + "." + PAR_CACHE);
        this.shuffleLength = Configuration.getInt(prefix + "."
                + PAR_SHUFFLE_LENGTH);
        this.entries = new ArrayList<Peer>(cacheSize);
    }

    public Cyclon(String prefix, int cacheSize, int shuffleLength) {
        this.prefix = prefix;
        this.cacheSize = cacheSize;
        this.shuffleLength = shuffleLength;
        this.entries = new ArrayList<Peer>(cacheSize);
    }

    // ---------------------------------------------------------------------
    @Override
    public Object clone() {

        Cyclon cyclon = new Cyclon(this.prefix, this.cacheSize,
                this.shuffleLength);
        return cyclon;
    }

    // ====================== Linkable implementation =====================
    // ====================================================================
    @Override
    public Node getNeighbor(int i) {
        return entries.get(i).getNode();
    }

    // --------------------------------------------------------------------
    /** Might be less than cache size. */
    @Override
    public int degree() {
        return entries.size();
    }

    // --------------------------------------------------------------------
    @Override
    public boolean addNeighbor(Node node) {
        Peer a = new Peer(node, 0);
        return this.entries.add(a);

    }

    // --------------------------------------------------------------------
    @Override
    public void pack() {
    }

    // --------------------------------------------------------------------
    @Override
    public boolean contains(Node n) {

        for (int i = 0; i < entries.size(); i++) {

            if (entries.get(i).getNode().equals(n)) {
                return true;
            }
        }
        return false;
    }

    private void validate() {
        if (entries.size() > cacheSize) {
            System.out.println(" CYCLON constraint is invalid : Entry size is higher than cache size");
            System.out.println(" Terminating now");
        }

    }

    // ===================== CDProtocol implementations ===================
    // ====================================================================
    @Override
    public void nextCycle(Node n, int protocolID) {
        validate();
        // TODO Implement your code for task 1.1 here
        if (degree() == 0) {
            return;
        }
        // Increment neighbour ages
        for (Peer neighbour : entries) {
            neighbour.setTimeStamp(neighbour.getTimeStamp() + 1);
        }
        // Get the oldest neighbour
        Peer oldest = getOldest();
        // Get l-1 neighbours
        ArrayList<Peer> shuffleList = getShuffleList();
        if (!shuffleList.contains(oldest)) {
            shuffleList.remove(shuffleList.size() - 1);
        } else {
            shuffleList.remove(oldest);
        }
        // Replace Q's entry with a new entry of age 0 and with P's address
        shuffleList.add(new Peer(n, 0));
        Cyclon luckyPeer = (Cyclon) oldest.getNode().getProtocol(protocolID);
        sentList = shuffleList;
        // Send the updated subset to peer Q
        luckyPeer.updateNeighbours(shuffleList, true, this, n);
    }

    public void updateNeighbours(ArrayList<Peer> list, boolean active,
            Cyclon from, Node n) {
        if (active) {
            ArrayList<Peer> shuffleList = getShuffleList();
            from.updateNeighbours(shuffleList, false, this, n);
            entries.addAll(list);
            while (degree() > cacheSize) {
                entries.remove(0);
            }
        } // Receive from Q a subset of its entries.
        else {
            // Discard entries pointing at P and entries already contained in
            // P's cache.
            for (Peer entry : list) {
                if ((!contains(entry.getNode()))
                        && (!entry.getNode().equals(n))) {
                    entries.add(entry);
                }
            }
            // Update P's cache to include all remaining entries, by firstly
            // using empty cahce slots, and secondly replacing entries among the
            // ones sent to Q.
            while (degree() > cacheSize) {
                if (!sentList.isEmpty()) {
                    entries.remove(sentList.remove(0));
                } else {
                    entries.remove(0);
                }
            }
            sentList = null;
        }
    }

    private Peer getOldest() {
        Peer oldest = new Peer(null, 0);
        for (Peer entry : entries) {
            if (oldest.getTimeStamp() <= entry.getTimeStamp()) {
                oldest = entry;
            }
        }
        return oldest;
    }

    private ArrayList<Peer> getShuffleList() {
        ArrayList<Peer> result = new ArrayList<Peer>();
        int num = 0;
        int length = shuffleLength;
        if (degree() < shuffleLength) {
            length = degree();
        }
        while (num < length) {
            Peer e = entries.get(CommonState.r.nextInt(entries.size()));
            result.add(e);
            num++;
        }
        return result;
    }

    @Override
    public void onKill() {
    }
}
