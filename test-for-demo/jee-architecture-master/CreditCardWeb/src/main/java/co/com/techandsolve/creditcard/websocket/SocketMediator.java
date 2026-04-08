package co.com.techandsolve.creditcard.websocket;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.enterprise.event.Observes;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;


@Singleton
@ServerEndpoint("/mediatorendpoint")
public class SocketMediator {

    private static Set<Session> peers = Collections.synchronizedSet(new HashSet<Session>());

    @OnMessage
    public String onMessage(String message, Session session) {
        System.out.println("message received " + message);
        for (Session peer : peers) {
            if (!peer.equals(session)) {
                try {
                    peer.getBasicRemote().sendText(message + " - retweet");
                } catch (IOException ex) {
                    Logger.getLogger(SocketMediator.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        return "message was received and processed: "+message;
    }

    @OnOpen
    public void onOpen(Session peer) {
        peers.add(peer);
    }

    public void onTimeEvent(@Observes @WBTimeEvent TimeEvent event) {
        System.out.println("Time Event observed " + event.getTimestamp());

        for (Session peer : peers) {
            try {
                peer.getBasicRemote().sendText("Time event: " + event.getTimestamp());
            } catch (IOException ex) {
                Logger.getLogger(SocketMediator.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

    
    @OnClose
    public void onClose(Session peer) {
        peers.remove(peer);
    }
}

