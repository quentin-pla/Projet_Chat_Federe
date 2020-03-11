import java.io.*;
import java.net.*;

public class Client {

    public static void main(String[] args) throws IOException {
        //Socket du client
        Socket socket;
        //Adresse IPv4 du serveur
        String ip;
        //Port TCP serveur
        int port;
        //Entrée clavier
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        //Ecriture en sortie
        PrintWriter out;
        //Ecriture en entrée
        BufferedReader in;

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
            //Message validation
            System.out.println("# Pseudo envoyé au serveur.");

            while (true) {
                String message;
                System.out.print("# Message: ");
                //Récupération du message au clavier
                message = stdin.readLine();
                //Si message vide
                if (message.length() == 0) {
                    System.out.println("# Connexion terminée.");
                    //Fermeture de la sortie du socket
                    socket.shutdownOutput();
                    break;
                } else {
                    //Envoi du message au serveur
                    out.println(message);
                }
            }

            //Fermeture
            in.close();
            out.close();
            stdin.close();
            socket.close();

            System.err.println("# Fin de la session.");
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(8);
        }
    }
}
