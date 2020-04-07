package models.chatamu;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * models.chatamu.Client serveur
 */
public class Client {

    /**
     * Adresse IPv4 du serveur
     */
    private String ip;

    /**
     * Port TCP serveur
     */
    private int port;

    /**
     * Pseudo du client
     */
    private String pseudo;

    /**
     * Salon où se situe le client
     */
    private String fair;

    /**
     * Socket du client
     */
    private SocketChannel socket;

    /**
     * Executeur
     */
    private Executor executor;

    /**
     * Entrée clavier
     */
    private BufferedReader stdin;

    /**
     * Liste des ports des serveurs ouverts
     */
    private HashSet<Integer> availableServerPorts;

    /**
     * Autorisation d'utiliser le clavier
     */
    private boolean allowInput;

    /**
     * Singleton contenant des méthodes pour le chat
     */
    public static ChatFunctions chatFunctions;

    /**
     * Liste des messages à afficher de manière ordonnée
     */
    private PriorityQueue<String> messagesToAnalyze = new PriorityQueue<>();

    /**
     * Activation/Désactivation mode debugage
     */
    private final boolean debugMode = true;

    /**
     * Main
     * @param args arguments
     */
    public static void main(String[] args) throws IOException {
        //Vérification des arguments
        if (args.length != 2) {
            //Arguments invalides
            System.out.println("Usage: java EchoClient @server @port");
            //Fin du programme
            System.exit(1);
        }

        //Adresse IP du serveur
        String ip = args[0];
        //Port TCP serveur
        int port = Integer.parseInt(args[1]);

        //Instanciation d'un client
        new Client(ip, port);

        //Message instruction
        System.out.println("# Pour vous connecter au serveur entrez la commande : LOGIN pseudo");
    }

    /**
     * Constructeur
     * @param ip ip du serveur
     * @param port port du serveur
     */
    public Client(String ip, int port) throws IOException {
        //Initialisation de l'adresse ip du serveur
        this.ip = ip;
        //Initialisation du port du serveur
        this.port = port;
        //Initialisation du client
        init();
    }

    /**
     * Initialisation du client
     */
    public void init() {
        //Connexion au serveur
        try {
            //Récupération de l'instance de la classe models.chatamu.ChatFunctions
            chatFunctions = ChatFunctions.getInstance();
            //Initialisation de l'executeur
            executor = Executors.newFixedThreadPool(2);
            //Récupération de la saisie clavier
            stdin = new BufferedReader(new InputStreamReader(System.in));
            //Initialisation de la liste des serveurs disponibles
            availableServerPorts = new HashSet<>();
            //Ouverture d'un canal du socket
            socket = SocketChannel.open();
            //Connexion du socket à l'adresse ip et au port passés en paramètre
            socket.connect(new InetSocketAddress(ip, port));
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

    /**
     * Reconnecter le client vers un autre serveur en cas de rupture
     */
    private void reconnectToServer() {
        if (availableServerPorts.size() > 0) {
            try {
                //Récupération du port du serveur
                port = (int) availableServerPorts.toArray()[0];
                //Initialisation du client
                init();
                //Suppression du port dans la liste des ports disponibles
                availableServerPorts.remove(port);
                //Envoi d'un message pour se connecter avec le même pseudo
                String message = chatFunctions.secure("RECONNECT " + fair + "@" + pseudo);
                //Écriture du message dans le serveur pour se connecter
                socket.write(ByteBuffer.wrap((message+"\n").getBytes()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Thread gérant les messages reçus depuis le serveur
     */
    public class ServerMessageReceiver implements Runnable {
        @Override
        public void run() {
            try {
                //Instanciation d'un buffer de bytes de taille 128
                ByteBuffer buffer = ByteBuffer.allocate(128);
                //Tant qu'il est possible de lire depuis le socket
                while (socket.read(buffer) != -1) {
                    //Récupération du message
                    String message = chatFunctions.extractMessage(buffer);
                    //Message non vide
                    if (message.length() > 0) {
                        //Message contenant au moins un retour à la ligne
                        if (message.contains("\n")) {
                            //Séparation du message en plusieurs messages
                            String[] strings = message.split("\n");
                            //Ajout des messages à la liste de ceux à analyser
                            messagesToAnalyze.addAll(Arrays.asList(strings));
                        } else {
                            //Ajout de message à la liste de ceux à analyser
                            messagesToAnalyze.add(message);
                        }
                    }
                    //Mode débugage
                    if (debugMode && messagesToAnalyze.size() > 0)
                        //Affichage des messages entrants en vert puis réinitialisation couleur par défaut
                        System.out.println("\u001B[32m" + "DEBUG -> " + messagesToAnalyze + "\u001B[0m");
                    //Tant qu'il reste des messsages à analyser
                    while (messagesToAnalyze.size() > 0) {
                        //Récupération de la tete de la liste des messages
                        message = messagesToAnalyze.poll();
                        //Analyse du message
                        if (message != null) analyseMessage(message);
                    }
                    //Nettoyage du buffer
                    buffer.clear();
                }
                //S'il n'y a plus de serveur ouvert
                if (availableServerPorts.size() == 0) {
                    //Désactivation de l'accès clavier
                    allowInput = false;
                    //Notification de déconnexion
                    System.out.println("# Déconnexion du serveur");
                }
                //Reconnexion au serveur
                else reconnectToServer();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * Analyser le message reçu
         * @param message message
         */
        private void analyseMessage(String message) {
            //Variable contenant l'opération à effectuer
            String operation = "";
            //Variable contenant l'argument passé en paramètre
            String argument = "";
            //Opération de mise à jour
            if (!message.contains("@") && message.contains("[") && message.contains("]")) {
                //Récupération de l'opération
                operation = message.substring(0, message.indexOf(" "));
                //Récupération du paramètre
                argument = message.substring(message.indexOf('[') + 1, message.lastIndexOf(']'));
            }
            //Mode débugage
            if (debugMode)
                System.out.println("\u001B[32m" + "OPERATION -> '" + operation + "':'" + argument + "'\u001B[0m");
            //Opération à effectuer
            switch (operation) {
                //Mettre à jour la liste des serveurs disponibles
                case "UPDATELINKS\b":
                    //On vérifie qu'il y a des ports dans la liste
                    if (argument.length() > 0) {
                        //Explosion du string en tableau de string pour séparer les ports
                        String[] stringPorts = argument.split(", ");
                        //Ajout des ports dans la liste des ports déjà connectés
                        for (String port : stringPorts)
                            availableServerPorts.add(Integer.parseInt(port));
                    } else {
                        //Suppression des ports de la liste
                        availableServerPorts.clear();
                    }
                    break;
                //Initialisation du pseudo au client
                case "PSEUDO":
                    pseudo = argument;
                    break;
                //Initialisation du salon dans lequel est situé le client
                case "FAIR":
                    fair = argument;
                    break;
                default:
                    //Effacement du contenu affiché sur la dernière ligne
                    System.out.print("\033[2K");
                    //Affichage du message
                    System.out.println(message);
                    break;
            }
        }
    }

    /**
     * Thread gérant l'entrée clavier
     */
    public class KeyboardInput implements Runnable {
        @Override
        public void run() {
            try {
                //Instanciation d'un buffer de bytes
                ByteBuffer buffer = ByteBuffer.allocate(128);
                //Tant qu'il est autorisé d'utiliser le clavier
                while (allowInput) {
                    //Récupération du message au clavier
                    String message = stdin.readLine();
                    //Envoi du message
                    buffer.put(message.getBytes());
                    //Inversion du buffer
                    buffer.flip();
                    //Vérification possibilité écriture sur socket
                    int checkWrite = socket.write(buffer);
                    //Nettoyage du buffer
                    buffer.clear();
                    //Si écriture impossible sortie de la boucle
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
