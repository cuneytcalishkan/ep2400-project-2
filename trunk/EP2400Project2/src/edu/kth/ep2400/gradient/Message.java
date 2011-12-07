/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.kth.ep2400.gradient;

/**
 *
 * @author cuneyt
 */
public class Message {

    private long id;
    private String msg;

    /**
     * Creates a new message to be published on the gradient overlay.
     * @param id The id of the new message.
     * @param msg The message content.
     */
    public Message(long id, String msg) {
        this.id = id;
        this.msg = msg;
    }

    /**
     * 
     * @return The message id.
     */
    public long getId() {
        return id;
    }

    /**
     * 
     * @return The message content.
     */
    public String getMsg() {
        return msg;
    }

    @Override
    public String toString() {
        return id + ", [" + msg + "]";
    }
}
