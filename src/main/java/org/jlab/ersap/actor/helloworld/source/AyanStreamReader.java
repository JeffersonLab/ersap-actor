package org.jlab.ersap.actor.helloworld.source;

import java.io.*;
import java.util.ArrayList;
import java.net.*;


public class AyanStreamReader {
    
    private String PORT;
    private Socket socket;
    private ServerSocket server;
    private DataInputStream in = null;

    public AyanStreamReader(String PORT){
        this.PORT = PORT;
        try {
            /*
             * Setting up the socket connection where the data is received from the client
             */
            server = new ServerSocket(Integer.parseInt(PORT));
            System.out.println("Server started");
            System.out.println("Waiting for a client ...");
            socket = server.accept();
            System.out.println("Client accepted");
            in = new DataInputStream(
                new BufferedInputStream(socket.getInputStream()));
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    public String readStreamContent() {
        // Todo: Note that this is streaming pipeline.
        //       Added an option to inform user about the ned of the file
        //       Otherwise you will see an "empty engine result" ErsapException thrown
         try {
            String line = in.readUTF();
            if (line == null) {
                 line = "end_of_stream";
             }
             
             return line;
        } catch (IOException e){
            return "Stream has finished sending";
        }
        //return "";
    }

    public void exit() {
        try {
            in.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        exit();
    }
}
