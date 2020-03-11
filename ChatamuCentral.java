import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

public class ChatamuCentral {

    //Liste des clients connectés au serveur
    private static ArrayList<ClientThread> clients = new ArrayList<>();

    public static void main(String[] args) {
        if (args.length == 1) {
            try {
                ServerSocket socketServeur = new ServerSocket(Integer.parseInt(args[0]));
                System.out.println("# Démarrage du chatamu central sur le port " + args[0]);
                while (true) {
                    Socket socketClient = socketServeur.accept();
                    ClientThread thread = new ClientThread(socketClient);
                    thread.start();
                    clients.add(thread);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void broadcast(String author, String message) {
        for (ClientThread client : clients)
            if (!client.pseudo.equals(author)) client.outgoingMessages.add(message);
    }

    public static class ClientThread extends Thread {
        private static final int MAX_OUTGOING = 10;
        private Socket socket;
        private String pseudo;
        private ArrayBlockingQueue<String> outgoingMessages = new ArrayBlockingQueue<>(MAX_OUTGOING);

        public ClientThread(Socket clientSocket) {
            this.socket = clientSocket;
        }

        public void run() {
            String msg;
            try {
                //Flux entrant du client
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                //Flux sortant du client
                OutputStream output = socket.getOutputStream();
                PrintWriter writer = new PrintWriter(output, true);
                //Récupération du pseudo du client
                pseudo = in.readLine();
                //Message de connexion
                System.out.println("# Connexion de " + pseudo + " au serveur.");
                writer.println("# Vous êtes connecté au serveur chatamu central !");
                while (true) {
                    //Liste des messages à envoyer
                    List<String> messagesToSend = new LinkedList<>();
                    //Récupération des messages à envoyer
                    outgoingMessages.drainTo(messagesToSend);
                    //Pour chaque message à envoyer
                    for (String message : messagesToSend)
                        //Écriture du message sur le flux sortant du client
                        writer.println(message);
                    //Récupération du message du client
                    msg = in.readLine();
                    //Si la taille du message vaut 0, sortie boucle
                    if (msg == null || msg.length() == 0) break;
                    else {
                        String outMsg = pseudo + ">" + msg;
                        System.out.println(outMsg);
                        broadcast(pseudo, outMsg);
                    }
                }
                System.out.println("# Déconnexion de " + pseudo + ".");
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}