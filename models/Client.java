package models;

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
 * Client serveur
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
     * Entrée clavier
     */
    private BufferedReader stdin;

    /**
     * Liste des ports des serveurs ouverts
     */
    private HashSet<Integer> availableServerPorts;

    /**
     * Utiliser le client depuis le terminal
     */
    private static boolean fromTerminal;

    /**
     * Singleton contenant des méthodes pour le chat
     */
    public static ChatFunctions chatFunctions;

    /**
     * Liste des messages à afficher de manière ordonnée
     */
    private PriorityQueue<String> messagesToAnalyze = new PriorityQueue<>();

    /**
     * Sortie sur laquelle les messages sont affichés
     */
    private PrintStream output = System.out;

    /**
     * Activation/Désactivation mode debugage
     */
    private final boolean debugMode = false;

    private ServerMessageReceiver serverMessageReceiver;

    private KeyboardInput keyboardInput;

    /**
     * Main
     * @param args arguments
     */
    public static void main(String[] args) {
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
        String port = args[1];
        //Utilisation du client depuis le terminal
        fromTerminal = true;
        //Instanciation d'un client
        new Client(ip, port, null);
    }

    /**
     * Constructeur
     * @param ip ip du serveur
     * @param port port du serveur
     */
    public Client(String ip, String port, PrintStream output) {
        //Définition de la sortie des messages (par défaut System.out)
        if (output != null) this.output = output;
        //Initialisation de l'adresse ip du serveur
        this.ip = ip;
        //Si le port contient que des chiffres
        if (port.matches("[0-9]+")) {
            //Initialisation du port du serveur
            this.port = Integer.parseInt(port);
            //Initialisation du client
            init();
        }
        else this.output.println("[ERROR PORT]");
    }

    /**
     * Initialisation du client
     */
    public void init() {
        //Connexion au serveur
        try {
            //Ouverture d'un canal du socket
            socket = SocketChannel.open();
            //Connexion du socket à l'adresse ip et au port passés en paramètre
            socket.connect(new InetSocketAddress(ip, port));
            //Message succès
            printSuccess();
        } catch (IOException e) {
            //Message d'erreur
            printError("[CONNECTION REFUSED]");
            //Suite du code ignorée
            return;
        }
        //Récupération de l'instance de la classe models.chatamu.ChatFunctions
        chatFunctions = ChatFunctions.getInstance();

        //Initialisation de l'executeur
        Executor executor = Executors.newFixedThreadPool((fromTerminal) ? 2 : 1);
        //Initialisation de la liste des serveurs disponibles
        availableServerPorts = new HashSet<>();

        //Instanciation d'un thread gérant les messages reçus du server
        executor.execute(serverMessageReceiver = new ServerMessageReceiver());

        //Si on souhaite utiliser la saisie clavier
        if (fromTerminal) {
            //Récupération de la saisie clavier
            stdin = new BufferedReader(new InputStreamReader(System.in));
            //Instanciation d'un thread gérant la saisie clavier du client
            executor.execute(keyboardInput = new KeyboardInput());
        }
    }

    /**
     * Écrire dans la sortie console un message de succès/erreur
     * @param result message d'erreur
     */
    private void printError(String result) {
        //Si le client est lancé depuis un terminal
        if (fromTerminal) result = "# " + chatFunctions.traductResult(result);
        //Affichage du message
        if (result != null) output.println(result);
    }

    /**
     * Renvoi
     */
    private void printSuccess() {
        if (!fromTerminal) output.println("[SUCCESS]");
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
                printSuccess();
            } catch (IOException e) {
                //Message d'erreur
                printError("[FAIL RECONNECT]");
            }
        }
    }

    /**
     * Envoyer un message au serveur
     * @param message message à envoyer
     */
    public void writeToSocket(String message) {
        try {
            socket.write(ByteBuffer.wrap((message + "\n").getBytes()));
        } catch (IOException e) {
            printError("[SOCKET WRITE FAILED]");
        }
    }

    /**
     * Récupérer le salon dans lequel est situé le client
     */
    public String getFair() {
        return fair;
    }

    /**
     * Récupérer le pseudo du client
     */
    public String getPseudo() {
        return pseudo;
    }

    /**
     * Déconnecter le client du serveur
     */
    public void disconnect() {
        try {
            //Terminer l'exécution du thread gérant la réception des messages serveur
            if (serverMessageReceiver != null) serverMessageReceiver.terminate();
            //Si le client est exécuté depuis le terminal
            if (fromTerminal) {
                //Terminer l'exécution de la saisie clavier
                keyboardInput.terminate();
                //Fermeture de l'entrée clavier
                stdin.close();
            }
            //Fermeture du socket
            socket.close();
            //Fermeture de l'application
            System.exit(0);
        } catch (IOException ignored) {}
    }

    /**
     * Thread gérant les messages reçus depuis le serveur
     */
    public class ServerMessageReceiver implements Runnable {
        //Variable pour définir si le thread fonctionne ou pas
        private volatile boolean running = true;

        //Terminer l'exécution du thread
        public void terminate() {
            this.running = false;
        }

        @Override
        public void run() {
            try {
                //Instanciation d'un buffer de bytes de taille 128
                ByteBuffer buffer = ByteBuffer.allocate(128);
                //Tant qu'il est possible de lire depuis le socket
                while (running && socket.read(buffer) != -1) {
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
                    //Déconnexion du client
                    disconnect();
                    //Notification de déconnexion
                    printError("[DISCONNECT]");
                }
                //Reconnexion au serveur
                else reconnectToServer();
            } catch (IOException e) {
                //Message d'erreur
                printError("[CONNECTION LOST]");
            }
        }

        /**
         * Analyser le message reçu
         * @param message message
         */
        private void analyseMessage(String message) {
            //Variable contenant l'opération à effectuer
            String operation = message;
            //Variable contenant l'argument passé en paramètre
            String argument = "";
            //Opération de mise à jour
            if (!message.startsWith("[") && !message.contains(">") && message.contains("[") && message.endsWith("]")) {
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
                case "LISTFAIRS":
                    //On vérifie qu'il y a des salons dans la liste
                    if (argument.length() > 0) {
                        //Affichage des salons disponibles
                        if (fromTerminal) output.println("# Salons disponibles: " + argument);
                        else output.println(message);
                    }
                    else
                        //Affichage du message en sortie
                        if (fromTerminal) output.println("# Il n'y a pas de salons disponibles");
                    break;
                //Initialisation du pseudo au client
                case "PSEUDO":
                    pseudo = argument;
                    break;
                //Initialisation du salon dans lequel est situé le client
                case "FAIR":
                    fair = argument;
                    break;
                //Messages d'erreur
                case "[USERNAME ALREADY USED]":
                case "[USERNAME INVALID]":
                case "[FAIR NOT FOUND]":
                case "[INVALID FAIR NAME]":
                case "[FAIR ALREADY CREATED]":
                case "[SAME FAIR LOCATION]":
                case "[ERROR CHATAMU]":
                    printError(operation);
                    break;
                case "[SUCCESS]":
                    printSuccess();
                    break;
                default:
                    if (fromTerminal)
                        //Effacement du contenu affiché sur la dernière ligne
                        output.print("\033[2K");
                    //Affichage du message
                    output.println(message);
                    break;
            }
        }
    }

    /**
     * Thread gérant l'entrée clavier
     */
    public class KeyboardInput implements Runnable {
        //Variable pour définir si le thread fonctionne ou pas
        private volatile boolean running = true;

        //Terminer l'exécution du thread
        public void terminate() {
            this.running = false;
        }

        @Override
        public void run() {
            try {
                //Instanciation d'un buffer de bytes
                ByteBuffer buffer = ByteBuffer.allocate(128);
                //Tant qu'il est autorisé d'utiliser le clavier
                while (running) {
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
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
