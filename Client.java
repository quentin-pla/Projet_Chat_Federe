import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class Client {

    //Adresse IPv4 du serveur
    private static String ip;

    //Port TCP serveur
    private static int port;

    //Pseudo du client
    private static String pseudo;

    //Salon où se situe le client
    private static String fair;

    //Socket du client
    private static SocketChannel socket;

    //Executeur
    private static Executor executor;

    //Entrée clavier
    private static BufferedReader stdin;

    //Liste des ports des serveurs ouverts
    private static HashSet<Integer> availableServerPorts;

    //Autorisation d'utiliser le clavier
    private static boolean allowInput;

    //Singleton contenant des méthodes pour le chat
    public static ChatFunctions chatFunctions;

    //Liste des messages à afficher de manière ordonnée
    private static PriorityQueue<String> messagesToAnalyze = new PriorityQueue<>();

    public static void main(String[] args) throws IOException {
        //Vérification des arguments
        if (args.length != 2) {
            //Arguments invalides
            System.out.println("Usage: java EchoClient @server @port");
            //Fin du programme
            System.exit(1);
        }
        //Récupération de l'instance de la classe ChatFunctions
        chatFunctions = ChatFunctions.getInstance();
        //Initialisation de l'executeur
        executor = Executors.newFixedThreadPool(2);
        //Récupération de la saisie clavier
        stdin = new BufferedReader(new InputStreamReader(System.in));
        //Initialisation de la liste des serveurs disponibles
        availableServerPorts = new HashSet<>();
        //Adresse IP du serveur
        ip = args[0];
        //Port TCP serveur
        port = Integer.parseInt(args[1]);

        //Initialisation du client
        init();
    }

    //Initialisation du client
    public static void init() {
        //Connexion au serveur
        try {
            //Ouverture d'un canal du socket
            socket = SocketChannel.open();
            //Connexion du socket à l'adresse ip et au port passés en paramètre
            socket.connect(new InetSocketAddress(ip, port));
            //Message instruction
            System.out.println("# Pour vous connecter au serveur entrez la commande : LOGIN pseudo");
        } catch (IOException e) {
            //Message d'erreur
            System.err.println("# Connexion impossible");
            e.printStackTrace();
        }

        //Accès clavier activé
        allowInput = true;

        //Instanciation d'un thread gérant les messages reçus du server
        executor.execute(new ServerMessageReceiver());

        //Instanciation d'un thread gérant la saisie clavier du client
        executor.execute(new KeyboardInput());

    }

    //Reconnecter le client vers un autre serveur en cas de rupture
    private static void reconnectToServer() {
        if (availableServerPorts.size() > 0) {
            try {
                //Récupération du port du serveur
                port = (int) availableServerPorts.toArray()[0];
                //Initialisation du client pour se connecter au serveur
                init();
                //Suppression du port dans la liste des ports disponibles
                availableServerPorts.remove(port);
                //Envoi d'un message pour se connecter avec le même pseudo
                String message = "RECONNECT " + fair + "@" + pseudo;
                //Écriture du message dans le serveur pour se connecter
                socket.write(ByteBuffer.wrap((message).getBytes()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //Thread gérant les messages reçus depuis le serveur
    public static class ServerMessageReceiver implements Runnable {
        @Override
        public void run() {
            try {
                //Instanciation d'un buffer de bytes de taille 128
                ByteBuffer buffer = ByteBuffer.allocate(128);
                //Tant qu'il est possible de lire depuis le socket
                while (socket.read(buffer) != -1) {
                    //Récupération du message
                    String message = chatFunctions.extractMessage(buffer);
                    if (message.length() > 0) {
                        if (message.contains("\n")) {
                            String[] strings = message.split("\n");
                            messagesToAnalyze.addAll(Arrays.asList(strings));
                        } else {
                            messagesToAnalyze.add(message);
                        }
                    }
                    while (messagesToAnalyze.size() > 0) {
                        //Récupération de la tete de la liste des messages
                        message = messagesToAnalyze.poll();
                        //Analyse du message
                        if (message != null) analyseMessage(message);
                    }
                    //Nettoyage du buffer
                    buffer.clear();
                }
                //Essai reconnexion vers autre serveur ouvert
                if (availableServerPorts.size() == 0) {
                    allowInput = false;
                    System.out.println("# Déconnexion du serveur");
                }
                else reconnectToServer();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void analyseMessage(String message) {
            //Message provenant du serveur pour mettre à jour la liste des serveurs disponibles
            if (message.startsWith("UPDATELINKS")) {
                //Récupération des ports
                message = message.substring(message.indexOf('[') + 1, message.lastIndexOf(']'));
                //On vérifie qu'il y a des ports dans la liste
                if (message.length() > 0) {
                    //Explosion du string en tableau de string pour séparer les ports
                    String[] stringPorts = message.split(", ");
                    //Ajout des ports dans la liste des ports déjà connectés
                    for (String port : stringPorts)
                        availableServerPorts.add(Integer.parseInt(port));
                } else {
                    availableServerPorts.clear();
                }
            }
            //Message du serveur retournant le pseudo du client après avoir été connecté
            else if (message.startsWith("PSEUDO")) {
                //Définition du pseudo au client
                pseudo = message.substring(message.indexOf('[') + 1, message.lastIndexOf(']'));
            }
            //Message du serveur retournant le pseudo du client après avoir été connecté
            else if (message.startsWith("FAIR")) {
                //Définition du pseudo au client
                fair = message.substring(message.indexOf('[') + 1, message.lastIndexOf(']'));
            }
            else {
                //Effacement du contenu affiché sur la dernière ligne
                System.out.print("\033[2K");
                //Affichage du message
                System.out.println(message);
            }
        }
    }

    public static class KeyboardInput implements Runnable {
        @Override
        public void run() {
            try {
                //Instanciation d'un buffer de bytes
                ByteBuffer buffer = ByteBuffer.allocate(128);
                //Tant qu'il est autorisé d'utiliser le clavier
                while (allowInput) {
                    //Récupération du message au clavier
                    String message = stdin.readLine();
                    if (message.equals("c")) System.out.print(" " + availableServerPorts);
                    //Envoi du message
                    buffer.put(message.getBytes());
                    //Inversion du buffer
                    buffer.flip();
                    int checkWrite = socket.write(buffer);
                    //Nettoyage du buffer
                    buffer.clear();
                    if (checkWrite == -1) break;
                }
                //Fermeture du socket
                socket.close();
                //Fermeture de l'entrée clavier
                stdin.close();
                System.exit(0);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
