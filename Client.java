import java.io.*;
import java.net.*;

public class Client {

    //Adresse IPv4 du serveur
    private static String ip;
    //Port TCP serveur
    private static int port;
    //Socket du client
    private static Socket socket;
    //Entrée clavier
    private static BufferedReader stdin;
    //Ecriture en sortie
    private static PrintWriter out;
    //Ecriture en entrée
    private static BufferedReader in;

    public static void main(String[] args) throws IOException {
        stdin = new BufferedReader(new InputStreamReader(System.in));
        //Vérification des arguments
        if (args.length != 2) {
            //Arguments invalides
            System.out.println("Usage: java EchoClient @server @port");
            //Fin du programme
            System.exit(1);
        }

        //Adresse IP du serveur
        ip = args[0];
        //Port TCP serveur
        port = Integer.parseInt(args[1]);

        //Connexion au serveur
        try {
            socket = new Socket(ip, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (UnknownHostException e) {
            System.err.println("# Connexion impossible");
            e.printStackTrace();
            return;
        }

        //Session client
        try {
            //Demande pseudo client
            System.out.print("# Entrez votre pseudo: ");
            //Pseudo
            String pseudo = "";
            //Booléen vérification pseudo
            boolean pseudoValid = false;
            //Tant que la pseudo n'est pas valide
            do {
                //Récupération du pseudo au clavier
                pseudo = stdin.readLine();
                //Si le pseudo contient des caractères autres que l'alphabet et que la taille est >= à 3
                if (pseudo.matches("[a-zA-Z]+") && pseudo.length() >= 3)
                    //Pseudo valide
                    pseudoValid = true;
                else
                    //Message d'erreur
                    System.out.print("# Pseudo invalide: ");
            } while (!pseudoValid);
            //Envoi du pseudo au server
            out.println(pseudo);
            //Message de connexion
            System.out.println("# Connexion au serveur en cours.");

            //Instanciation d'un thread gérant les messages reçus du server
            new Thread(new ServerMessageReceiver()).start();

            //Instanciation d'un thread gérant la saisie clavier du client
            new Thread(new KeyboardInput()).start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void exit() {
        try {
            in.close();
            out.close();
            stdin.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static class ServerMessageReceiver extends Thread {
        @Override
        public void run() {
            try {
                while (!socket.isInputShutdown()) {
                    /* réception des données */
                    String message = in.readLine();
                    //Effacement du contenu affiché sur la dernière ligne
                    System.out.print("\033[2K");
                    //Affichage du message
                    if (message != null) {
                        System.out.println("\b\b" + message);
                        System.out.print("> ");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static class KeyboardInput extends Thread {
        @Override
        public void run() {
            try {
                while (true) {
                    String message = "";
                    System.out.print("> ");
                    //Récupération du message au clavier
                    message = stdin.readLine();
                    //Si message vide
                    if (message.length() == 0) {
                        System.out.println("# Connexion terminée.");
                        //Fermeture de la sortie du socket
                        socket.shutdownOutput();
                        socket.shutdownInput();
                        break;
                    } else {
                        //Envoi du message au serveur
                        out.println(message);
                    }
                }
                Client.exit();
                System.err.println("# Fin de la session.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
