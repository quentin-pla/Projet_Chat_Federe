import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

public class ClientNIO {

    //Adresse IPv4 du serveur
    private static String ip;
    //Port TCP serveur
    private static int port;
    //Socket du client
    private static SocketChannel socket;
    //Entrée clavier
    private static BufferedReader stdin;

    public static void main(String[] args) throws IOException {
        System.setProperty("file.encoding","UTF-8");
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
            socket = SocketChannel.open();
            socket.connect(new InetSocketAddress(ip, port));
        } catch (UnknownHostException e) {
            System.err.println("# Connexion impossible");
            e.printStackTrace();
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
            ByteBuffer buffer = ByteBuffer.allocate(128);
            buffer.put(pseudo.getBytes());
            buffer.flip();
            socket.write(buffer);
            buffer.clear();
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

    //Extraire le message contenu dans le buffer
    private static String extractMessage(ByteBuffer buffer) {
        //Inversion du buffer
        buffer.flip();
        //Instanciation d'un tableau de caractères de taille MESSAGE_LENGTH
        ArrayList<Byte> data = new ArrayList<>();
        //Tant qu'il reste du contenu à consommer dans le buffer
        while(buffer.hasRemaining()) {
            byte b = buffer.get();
            //Consommation d'un caractère
            if (b != 0) data.add(b);
        }
        byte[] conversion = new byte[data.size()];
        for(int i = 0; i < data.size(); i++)
            conversion[i] = data.get(i);
        //Retour du message au format chaine de caractères
        //Suppression du retour à la ligne
        return new String(conversion, StandardCharsets.UTF_8).replace("\n","");
    }

    public static class ServerMessageReceiver extends Thread {
        @Override
        public void run() {
            try {
                ByteBuffer buffer = ByteBuffer.allocate(128);
                while (true) {
                    socket.read(buffer);
                    String message = extractMessage(buffer);
                    //Effacement du contenu affiché sur la dernière ligne
                    System.out.print("\033[2K");
                    if (message.contains("\b")) break;
                    //Affichage du message
                    if (message.length() > 0) {
                        System.out.println("\b\b" + message);
                        System.out.print("> ");
                    }
                    buffer.clear();
                }
                stdin.close();
                socket.close();
                System.out.println("# Déconnection du serveur.");
                interrupt();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static class KeyboardInput extends Thread {
        @Override
        public void run() {
            try {
                ByteBuffer buffer = ByteBuffer.allocate(128);
                while (true) {
                    System.out.print("> ");
                    //Récupération du message au clavier
                    String message = stdin.readLine();
                    if (message.length() == 0) message = "\b";
                    //Envoi du message
                    buffer.put(message.getBytes());
                    buffer.flip();
                    //Envoi du message au serveur
                    socket.write(buffer);
                    //Nettoyage du buffer
                    buffer.clear();
                    if (message.equals("\b")) break;
                }
                interrupt();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
