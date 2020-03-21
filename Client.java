import java.io.*;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class Client {

    //Adresse IPv4 du serveur
    private static String ip;
    //Port TCP serveur
    private static int port;
    //Socket du client
    private static SocketChannel socket;
    //Entrée clavier
    private static BufferedReader stdin;

    private static boolean allowInput = true;

    public static void main(String[] args) throws IOException {
        //Récupération de la saisie clavier
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
            //Ouverture d'un canal du socket
            socket = SocketChannel.open();
            //Connexion du socket à l'adresse ip et au port passés en paramètre
            socket.connect(new InetSocketAddress(ip, port));
        } catch (UnknownHostException e) {
            //Message d'erreur
            System.err.println("# Connexion impossible");
            e.printStackTrace();
        }

        //Instanciation d'un thread gérant les messages reçus du server
        new Thread(new ServerMessageReceiver()).start();

        //Instanciation d'un thread gérant la saisie clavier du client
        new Thread(new KeyboardInput()).start();
    }

    //Extraire le message contenu dans le buffer
    private static String extractMessage(ByteBuffer buffer) {
        //Inversion du buffer
        buffer.flip();
        //Instanciation d'un vecteur de bytes
        ArrayList<Byte> data = new ArrayList<>();
        //Tant qu'il reste du contenu à consommer dans le buffer
        while(buffer.hasRemaining()) {
            //Consommation d'un caractère
            byte b = buffer.get();
            //Ajout du caractère dans le tableau s'il ne vaut pas rien
            if (b != 0) data.add(b);
        }
        //Instanciation d'un tableau de bytes de la taille du vecteur
        byte[] conversion = new byte[data.size()];
        //Ajout des bytes du vecteur dans le tableau de bytes
        for(int i = 0; i < data.size(); i++)
            conversion[i] = data.get(i);
        //Retour du message au format chaine de caractères et suppression des retours à la ligne
        return new String(conversion, StandardCharsets.UTF_8);
    }

    //Thread gérant les messages reçus depuis le serveur
    public static class ServerMessageReceiver extends Thread {
        @Override
        public void run() {
            try {
                //Instanciation d'un buffer de bytes de taille 128
                ByteBuffer buffer = ByteBuffer.allocate(128);
                while (socket.read(buffer) != -1) {
                    String message = extractMessage(buffer);
                    //Effacement du contenu affiché sur la dernière ligne
                    System.out.print("\033[2K");
                    //Affichage du message
                    if (message.length() > 0) {
                        System.out.print(message);
                    }
                    buffer.clear();
                }
//                System.out.println("# Déconnection du serveur.");
                allowInput = false;
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
                System.out.println("# Pour vous connecter au serveur entrez la commande : LOGIN pseudo");
                while (allowInput) {
                    //Récupération du message au clavier
                    String message = stdin.readLine();
                    //Envoi du message
                    buffer.put(message.getBytes());
                    //Inversion du buffer
                    buffer.flip();
                    if (socket.write(buffer) == -1) break;
                    //Nettoyage du buffer
                    buffer.clear();
                }
                stdin.close();
                socket.close();
                interrupt();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
