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
                new Thread(new MessagesBroadcaster()).start();
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
        for (ClientThread client : clients) {
            if (!client.pseudo.equals(author)) client.outgoingMessages.add(message);
        }
        System.out.println(message);
    }

    public static class MessagesBroadcaster extends Thread {
        public void run() {
            while (true) {
                for (ClientThread client : clients) {
                    //Récupération des messages à envoyer
                    List<String> messages = client.getMessagesToSend();
                    //S'il y a des messages à envoyer
                    if (messages.size() > 0)
                        //Pour chaque message à envoyer
                        for (String message : messages)
                            //Écriture du message sur le flux sortant du client
                            client.sendMessage(message);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    throw new IllegalStateException(ex);
                }
            }
        }
    }

    public static class ClientThread extends Thread {
        private final int MAX_OUTGOING = 10;
        private final String clientsColor = "\u001B[32m";
        private final String userColor = "\u001B[34m";
        private final String defaultColor = "\u001B[0m";
        private Socket socket;
        private String pseudo;
        private PrintWriter writer;
        private ArrayBlockingQueue<String> outgoingMessages = new ArrayBlockingQueue<>(MAX_OUTGOING);

        public ClientThread(Socket clientSocket) throws IOException {
            socket = clientSocket;
            //Flux sortant du client
            writer = new PrintWriter(socket.getOutputStream(), true);
        }

        public void sendMessage(String message) {
            //Affichage du message en vert puis repassage en bleu
            writer.println(clientsColor + message + userColor);
        }

        public List<String> getMessagesToSend() {
            //Liste des messages à envoyer
            List<String> messagesToSend = new LinkedList<>();
            //Récupération des messages à envoyer
            outgoingMessages.drainTo(messagesToSend);
            return messagesToSend;
        }

        public void run() {
            String msg;
            try {
                //Flux entrant du client
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                //Récupération du pseudo du client
                pseudo = in.readLine();
                //Message de connexion
                broadcast(pseudo, defaultColor + "# Connexion de " + pseudo + " au serveur.");
                writer.println("# Vous êtes connecté au serveur chatamu central !" + userColor);
                while (true) {
                    //Récupération du message du client
                    msg = in.readLine();
                    //Si la taille du message vaut 0, sortie boucle
                    if (msg == null || msg.length() == 0) {
                        sendMessage(defaultColor);
                        break;
                    }
                    else {
                        String outMsg = pseudo + " > " + msg;
                        broadcast(pseudo, outMsg);
                    }
                }
                broadcast(pseudo, defaultColor + "# Déconnexion de " + pseudo + ".");
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}