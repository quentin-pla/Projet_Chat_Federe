import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

class ChatamuCentral {
    //Port du serveur
    private static int serverPort;

    //Pseudo du serveur
    private static String serverLogin;

    //Salon central du serveur
    private static String serverFair;

    //Entrée clavier
    private static BufferedReader stdin;

    //Liste des clients connectés au serveur
    private static ArrayList<SocketHandler> clients = new ArrayList<>();

    //Liste des clients connectés au serveur
    private static HashMap<Integer, HashSet<String>> clientsUsernames = new HashMap<>();

    //Liste des ports des serveurs connectés ainsi que la liste des ports des serveurs auxquels ils sont connectés
    private static HashMap<Integer, HashSet<Integer>> linkedServersPorts = new HashMap<>();

    //Liste des salons créés par les clients
    private static HashMap<Integer, HashSet<String>> clientsFairs = new HashMap<>();

    //Sélecteur
    private static Selector selector;

    //Executeur
    private static Executor executor;

    //Nombre de messages groupés maximal à envoyer
    private static final int MAX_OUTGOING = 50;

    //Singleton contenant des méthodes pour le chat
    public static ChatFunctions chatFunctions;

    //Main
    public static void main(String[] args) {
        //Nombre d'arguments passés en paramètres
        int argc = args.length;
        //Serveur chatamu
        ChatamuCentral chatamu;
        //Port
        String port = args[0];

        //Vérification nombre d'arguments et syntaxe port
        if (argc == 1 && port.matches("[0-9]+")) {
            try {
                //Initialisation variable port serveur
                serverPort = Integer.parseInt(args[0]);
                //Initialisation variable pseudo serveur
                serverLogin = "#" + serverPort;
                //Initialisation du salon du serveur
                serverFair = "";
                //Récupération de l'instance de la classe ChatFunctions
                chatFunctions = ChatFunctions.getInstance();
                //Instanciation du serveur
                chatamu = new ChatamuCentral();
                //Démarrage du serveur sur le port souhaité
                chatamu.demarrer(serverPort);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            //Message d'usage de la commande
            System.out.println("Usage: java ChatamuCentral port");
        }
    }

    //Démarrage du serveur
    public void demarrer(int port) {
        System.out.println("# Démarrage du chatamu central sur le port " + port);
        try {
            //Initialisation de la liste des pseudos pour le serveur
            clientsUsernames.put(serverPort, new HashSet<>());

            //Initialisation de la liste des salons pour le serveur
            clientsFairs.put(serverPort, new HashSet<>());

            //Ouverture du sélecteur
            selector = Selector.open();

            //Ouverture du socket du serveur
            ServerSocketChannel ssc = ServerSocketChannel.open();
            //Assignation du port au serveur
            ssc.socket().bind(new InetSocketAddress(port));
            //Configuration canal en mode non bloquant
            ssc.configureBlocking(false);
            //Jeu d'opérations
            int ops = ssc.validOps();
            //Enregistrement du canal sur le sélecteur
            ssc.register(selector, ops, null);

            //Pool de threads voleurs de travail
            //Executeur
            executor = Executors.newWorkStealingPool();

            //Récupération de la saisie clavier
            stdin = new BufferedReader(new InputStreamReader(System.in));

            //Thread gérant les entrées clavier
            executor.execute(new KeyboardInput());

            while (selector.isOpen()) {
                //Sélections des clés prêtes à être utilisées
                selector.select();
                //Liste des clés sélectionnées
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                //Iterateur sur l'ensemble de clés
                Iterator<SelectionKey> keys = selectionKeys.iterator();
                //Tant qu'il reste des clés non traitées
                while (keys.hasNext()) {
                    //Récupération de la clé
                    SelectionKey key = keys.next();
                    //Si la clé peut accepter une nouvelle connexion
                    if (key.isAcceptable()) {
                        //Récupération du canal du serveur
                        ServerSocketChannel server = (ServerSocketChannel)key.channel();
                        //Accepte la connexion au serveur
                        SocketChannel csc = server.accept();
                        //Configuration en mode non-bloquants
                        csc.configureBlocking(false);
                        //Enregistrement du canal de la clé en mode lecture
                        csc.register(selector, SelectionKey.OP_READ);
                    }
                    //Si le canal de la clé est prêt à être lu
                    if (key.isReadable()) {
                        //Création d'un thread client
                        SocketHandler socketHandler = new SocketHandler(key);
                        //Execution du thread
                        executor.execute(socketHandler);
                        //Ajout du client à la liste des clients connectés
                        clients.add(socketHandler);
                        //Annulation de la clé
                        key.cancel();
                    }
                }
                //Suppression des clés de la liste
                keys.remove();
            }
        } catch (IOException ex) {
            System.out.println("# Arrêt anormal du serveur.");
            ex.printStackTrace();
        }
    }

    //Ajouter un message à la liste des messages à envoyer des clients
    public static void broadcast(String fair, String author, String message, boolean printOut) {
        //Pour chaque client connecté
        for (SocketHandler client : clients)
            //Si le pseudo du client est définit
            if (client.socketUsername != null)
                //Si le pseudo est différent de l'auteur du message
                if (!client.socketUsername.equals(author))
                    //Si le client est un envoyeur de messages vers un autre serveur
                    // ou que le message provient du serveur
                    if (client.isServerSocketSender || message.startsWith("#"))
                        //Ajout du message à la liste du client
                        client.outgoingMessages.add(message);
                    //Si le salon du client est celui passé en paramètre et que ce n'est pas un serveur
                    else if (client.fairLocation.equals(fair) && !author.startsWith("#"))
                        //Ajout du message à la liste du client
                        client.outgoingMessages.add(message.substring(message.indexOf("@")+1));
        //Si on souhaite afficher le message et que le salon est celui du serveur
        if (printOut && fair.equals(serverFair)) {
            //Suppression du @ en début de message s'il provient d'un client
            if (!author.startsWith("#")) message = message.substring(1);
            //Affichage du message
            System.out.println(message);
        }
    }

    //Vérification du pseudo syntaxiquement
    public static boolean isPseudoValid(String pseudo) {
        //Vérification du pseudo, alphabétique, supérieur à 3 caractères
        return pseudo.matches("[a-zA-Z0-9]+") && pseudo.length() >= 3;
    }

    //Vérifier qu'un pseudo n'est pas déja attribué
    public static boolean isPseudoAvailable(String pseudo) {
        //Parcours de la liste des pseudos sur chaque serveur
        for (HashSet<String> usernames : clientsUsernames.values())
            //Si le pseudo est contenu dans la liste on retourne faux
            if (usernames.contains(pseudo)) return false;
        return true;
    }

    //Vérifier qu'un salon est bien ouvert
    public static boolean isFairExisting(String fair) {
        //Parcours de la liste des salons sur chaque serveur
        for (HashSet<String> fairs : clientsFairs.values())
            //Si le salon est contenu dans la liste on retourne vrai
            if (fairs.contains(fair)) return true;
        return false;
    }

    //Connecter se serveur à un autre serveur
    private static void linkToServer(int port) {
        //Si le port n'est pas contenu dans la liste des serveurs déjà connectés
        if (!linkedServersPorts.containsKey(port) && port != serverPort) {
            //Ajout du port à la liste des ports déjà connectés
            linkedServersPorts.put(port, new HashSet<>());
            try {
                //Ouverture d'un canal du socket
                SocketChannel socket = SocketChannel.open();
                //Connexion du socket à l'adresse ip et au port passés en paramètre
                socket.connect(new InetSocketAddress("localhost", port));
                //Envoi d'un message au serveur distant pour le lier à celui-ci
                socket.write(ByteBuffer.wrap(("LINKTO " + serverPort).getBytes()));
                //Lancement d'un thread gérant la réception des messages depuis le serveur distant
                executor.execute(new SocketHandler(socket, port));
                //Envoi d'un message de confirmaton de liaison
                System.out.println("# Liaison établie au serveur connecté sur le port " + port);
            } catch (IOException e) {
                //Message d'erreur
                System.err.println("# Liaison impossible avec le serveur distant");
                e.printStackTrace();
            }
        }
    }

    //Mise à jour des ports des serveurs ouverts
    private static void broadcastServerLinks() {
        broadcast(serverFair, serverLogin, "UPDATELINKS " + linkedServersPorts.keySet(), false);
    }

    //Mise à jour des pseudos entre serveurs
    private static void broadcastUsernames() {
        for (SocketHandler serverLink : clients)
            if (serverLink.isServerSocketSender)
                serverLink.sendMessage("UPDATEUSERNAMES " + clientsUsernames.get(serverPort));
    }

    //Mise à jour des pseudos entre serveurs
    private static void broadcastFairs() {
        for (SocketHandler serverLink : clients)
            if (serverLink.isServerSocketSender)
                serverLink.sendMessage("UPDATEFAIRS " + clientsFairs.get(serverPort));
    }

    ////################# LISTE DES THREADS ##################

    //Thread clavier
    public static class KeyboardInput implements Runnable {
        @Override
        public void run() {
            try {
                //Tant qu'il est autorisé d'utiliser le clavier
                while (selector.isOpen()) {
                    String message;
                    //Récupération du message au clavier
                    message = stdin.readLine();
                    //Si le message est SERVERCONNECT, établissement d'une liaison au serveur distant
                    if (message.startsWith("SERVERCONNECT ")) {
                        //Récupération du port
                        int port = Integer.parseInt(message.substring(14));
                        //Liaison du serveur
                        linkToServer(port);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //Thread client
    private static class SocketHandler implements Runnable {
        //Taille maximale pour un message
        private final int MESSAGE_LENGTH = 128;

        //Socket client
        private SocketChannel socket;

        //Pseudo du client
        private String socketUsername;

        //Port du socket
        private int socketPort;

        //Salon dans lequel est situé le socket
        private String fairLocation = serverFair;

        //Liste des messages à envoyer au client
        private ArrayBlockingQueue<String> outgoingMessages = new ArrayBlockingQueue<>(MAX_OUTGOING);

        //Liste des messages à analyser de manière ordonnée
        private PriorityQueue<String> messagesToAnalyze = new PriorityQueue<>();

        //Propriété pour savoir si c'est un lien avec un serveur
        private boolean isServerSocketSender = false;

        //Propriété pour savoir si c'est un receveur de messages provenant d'un lien avec un serveur
        private boolean isServerSocketReceiver = false;

        //Constructeur
        public SocketHandler(SelectionKey key) {
            //Récupération du canal de la clé
            this.socket = (SocketChannel) key.channel();
        }

        //Constructeur socket receveur de messages autre serveur
        public SocketHandler(SocketChannel socket, int socketPort) {
            //Récupération du socket
            this.socket = socket;
            this.socketPort = socketPort;
            this.socketUsername = "#" + socketPort;
            isServerSocketReceiver = true;
        }

        //Envoyer un message au client depuis le serveur
        private void sendMessage(String message) {
            try {
                //Tableau contenant le message en bytes
                byte[] backbuffer = (message + "\n").getBytes();
                //Envoi du message au client
                socket.write(ByteBuffer.wrap(backbuffer));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //Envoyer les messages du serveur au clients connectés
        private void sendServerMessages() {
            //Récupération des messages à envoyer
            List<String> messages = getMessagesToSend();
            //S'il y a des messages à envoyer
            if (messages.size() > 0)
                //Pour chaque message à envoyer
                for (String message : messages)
                    //Écriture du message sur le flux sortant du client
                    sendMessage(message);
        }

        //Récupération des messsages à envoyer au client
        private List<String> getMessagesToSend() {
            //Liste des messages à envoyer
            List<String> messagesToSend = new LinkedList<>();
            //Récupération des messages à envoyer
            outgoingMessages.drainTo(messagesToSend);
            //Retourne la liste des messages
            return messagesToSend;
        }

        //Découpage du message et ajout dans la liste des messages à analyser
        private void prepareAnalyze(String message) {
            //Si le message n'est pas vide
            if (message.length() > 0)
                if (message.contains("\n")) {
                    String[] strings = message.split("\n");
                    messagesToAnalyze.addAll(Arrays.asList(strings));
                } else {
                    messagesToAnalyze.add(message);
                }
        }

        @Override
        public void run() {
            try {
                //Instanciation d'un buffer de bytes d'une taille MESSAGE_LENGTH
                ByteBuffer buffer = ByteBuffer.allocate(MESSAGE_LENGTH);
                //Message
                String message;
                //Tant qu'il est possible de lire depuis le client
                while (socket.read(buffer) != -1) {
                    //Envoi au client des messages serveur
                    sendServerMessages();
                    //Si c'est un lien serveur on saute la suite
                    if (isServerSocketSender) continue;
                    //Récupération du message
                    message = chatFunctions.extractMessage(buffer);
                    //Ajout du message à la liste des messages à analyser
                    prepareAnalyze(message);
                    //Booléen déconnexion du client
                    boolean exit = false;
                    if (messagesToAnalyze.size() > 0)
                        System.out.println("-> " + messagesToAnalyze);
                    //Tant qu'il y a des messages à traiter
                    while (messagesToAnalyze.size() > 0) {
                        //Récupération de la tete de la liste des messages
                        message = messagesToAnalyze.poll();
                        //Message non null
                        if (message != null) {
                            //Analyse du message
                            if (isServerSocketReceiver) analyseMessageAsReceiver(message);
                            else exit = !analyseMessage(message);
                        }
                    }
                    //Si exit est à vrai, déconnexion du client
                    if (exit) break;
                    //Nettoyage du buffer
                    buffer.clear();
                }
                //Liaison du serveur aux serveurs proches ouverts après que le distant ferme
                if (isServerSocketReceiver) {
                    linkServerAfterDown();
                    //Afichage d'un message pour constater une rupture avec un serveur distant
                    System.out.println("# Rupture liaison serveur connecté au port " + socketPort);
                }
                //Si le pseudo est définit et que le client n'est pas un lien vers un serveur
                if (socketUsername != null && !socketUsername.startsWith("#"))
                    broadcast(serverFair, serverLogin, "# Déconnexion de " + socketUsername + ".", true);
                //Suppression du client dans la liste des clients
                clients.remove(this);
                //Suppression du pseudo dans la liste
                clientsUsernames.get(serverPort).remove(socketUsername);
                //Mise à jour des pseudos disponibles entre serveurs
                broadcastUsernames();
                //Fermeture du thread
                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void analyseMessageAsReceiver(String message) {
            if (message.startsWith("UPDATELINKS")     ||
                message.startsWith("UPDATEUSERNAMES") ||
                message.startsWith("UPDATEFAIRS")      ) {
                //Récupération du contenu entre crochet du message
                String content = message.substring(message.indexOf('[') + 1, message.lastIndexOf(']'));
                //Message code pour mettre à jour les liens
                if (message.startsWith("UPDATELINKS"))
                    //Lancement du protocole de mise à jour des liens de connexions entre serveurs
                    updateLinksProtocol(content);
                //Message code pour mettre à jour les liens
                else if (message.startsWith("UPDATEUSERNAMES")) {
                    //Lancement du protocole de mise à jour des pseudos entre serveurs
                    updateUsernamesProtocol(content);
                } else if (message.startsWith("UPDATEFAIRS"))
                    //Lancement du protocole de mise à jour des salons entre serveurs
                    updateFairsProtocol(content);
            }
            //Affichage du message
            else {
                if (message.startsWith("#"))
                    broadcast(serverFair, socketUsername, message, true);
                else {
                    //Récupération du salon
                    String salon = message.substring(0, message.indexOf('@'));
                    //Récupération du message
                    message = message.substring(message.indexOf('@')+1);
                    //Renvoi du message sur le bon salon
                    if (salon.equals(serverFair))
                        broadcast(serverFair, socketUsername, message, true);
                    else
                        broadcast(salon, socketUsername, message, true);
                }
            }
        }

        private boolean analyseMessage(String message) {
            //Si le message est EXIT on sort de la boucle
            if (message.equals("EXIT")) return false;
            //Si le message commence par LINKTO
            //on ajoute un backspace au début afin qu'un client ne puisse pas saisir la commande
            else if (message.startsWith("LINKTO "))
                //Lancement du protocole de liaison entre serveurs
                linkProtocol(message);
            else
                //Si le pseudo n'est pas définit
                if (socketUsername == null)
                    //Client se reconnectant au serveur
                    if (message.startsWith("RECONNECT "))
                        //Lancement du protocole de reconnexion
                        reconnectProtocol(message);
                    //Si le message commence par LOGIN
                    else if (message.startsWith("LOGIN "))
                        //Lancement du protocole d'identification
                        loginProtocol(message);
                    else {
                        sendMessage("# ERROR LOGIN aborting chatamu protocol.");
                        return false;
                    }
                else
                    //Demande d'envoi d'un message
                    if (message.startsWith("MESSAGE ")) {
                        //Récupération du message
                        message = message.substring(8);
                        //Envoi du message
                        broadcast(fairLocation, socketUsername, fairLocation + "@" + socketUsername + " > " + message, true);
                    }
                    //Demande de création d'un salon
                    else if (message.startsWith("SALON ")) {
                        //Lancement d'un protocole pour créer un salon
                        createFairProtocol(message);
                    }
                    //Demande de création d'un salon
                    else if (message.equals("LIST")) {
                        //Initialisation d'un tableau contenant les salons disponibles
                        ArrayList<String> availableFairs = new ArrayList<>();
                        //Ajout des salons de chaque serveur dans la liste
                        for (HashSet<String> fairs : clientsFairs.values())
                            availableFairs.addAll(fairs);
                        //Renvoi la liste des salons ouverts au client
                        sendMessage(availableFairs.toString());
                    }
                    //Demande de création d'un salon
                    else if (message.startsWith("JOIN ")) {
                        //Lancement du protocole pour rejoindre un salon
                        joinFairProtocol(message);
                    }
                    //Demande de création d'un salon
                    else if (message.startsWith("QUIT ")) {
                        //Lancement d'un protocole pour quitter un salon
                        quitFairProtocol(message);
                    }
                    else sendMessage("# ERROR chatamu.");
            return true;
        }

        private void createFairProtocol(String message) {
            //Récupération du nom du salon
            String fair = message.substring(6);
            if (isPseudoValid(fair)) {
                //Ajout du salon dans la liste de ceux du serveur
                clientsFairs.get(serverPort).add(fair);
                //Notification de création du salon
                sendMessage("# Création du salon : " + fair);
                //Envoi de la liste des salons du serveur aux autres serveurs
                broadcastFairs();
            }
            else sendMessage("# Nom de salon invalide, veuillez réessayer");
        }

        private void joinFairProtocol(String message) {
            //Récupération du nom du salon
            String fair = message.substring(5);
            //Vérification si le salon existe
            if (isFairExisting(fair)) {
                //Récupération du nom du salon et mise à jour de l'emplacement du client
                fairLocation = fair;
                //Envoi du salon au client
                sendMessage("FAIR [" + fair + "]");
                //Notification de connexion au salon
                broadcast(serverFair, serverLogin, "# Connexion de " + socketUsername + " au salon : " + fair, true);
            }
        }

        private void quitFairProtocol(String message) {
            //Récupération du nom du salon
            String fair = message.substring(5);
            //Vérification si le salon existe
            if (isFairExisting(fair) && fairLocation.equals(fair)) {
                //Récupération du nom du salon et mise à jour de l'emplacement du client
                fairLocation = serverFair;
                //Notification de connexion au salon
                broadcast(serverFair, serverLogin, "# Déconnexion de " + socketUsername + " du salon : " + fair, true);
            }
        }

        //Protocole de mise à jour de liaison entre serveurs
        private void updateLinksProtocol(String message) {
            //On vérifie qu'il y a des messages dans la liste
            if (message.length() > 0) {
                //Explosion du string en tableau de string pour séparer les ports
                String[] stringPorts = message.split(", ");
                //Ajout des ports dans la liste des ports déjà connectés
                for (String port : stringPorts)
                    linkedServersPorts.get(socketPort).add(Integer.parseInt(port));
            }
        }

        //Protocole de mise à jour des pseudos entre serveurs
        private void updateUsernamesProtocol(String message) {
            //On vérifie qu'il y a des messages dans la liste
            if (message.length() > 0) {
                //Explosion du string en tableau de string pour séparer les pseudos
                String[] pseudos = message.split(", ");
                HashSet<String> hashPseudos = new HashSet<>();
                Collections.addAll(hashPseudos, pseudos);
                //Ajout des pseudos du serveur dans la liste
                clientsUsernames.put(socketPort, hashPseudos);
            } else {
                clientsUsernames.remove(socketPort);
            }
        }

        //Protocole de mise à jour des salons ouverts entre serveurs
        private void updateFairsProtocol(String message) {
            //On vérifie qu'il y a des messages dans la liste
            if (message.length() > 0) {
                //Explosion du string en tableau de string pour séparer les salons
                String[] fairs = message.split(", ");
                //Instanciation liste sans doublons
                HashSet<String> hashFairs = new HashSet<>();
                //Ajout des salons à la liste
                Collections.addAll(hashFairs, fairs);
                //Ajout des salons du serveur dans la liste
                clientsFairs.put(socketPort, hashFairs);
            } else {
                clientsFairs.remove(socketPort);
            }
        }

        //Protocole de liaison avec un autre serveur
        private void linkProtocol(String message) {
            isServerSocketSender = true;
            String port = message.substring(7);
            socketUsername = "#" + port;
            //Liaison de ce serveur au serveur distant
            linkToServer(Integer.parseInt(port));
            //Mise à jour des liens entre chaque serveur connectés
            broadcastServerLinks();
            //Mise à jour des pseudos déjà utilisés pour le nouveau serveur
            broadcastUsernames();
            //Mise à jour des salons déjà créés pour le nouveau serveur
            broadcastFairs();
        }

        //Protocole de reconnexion d'un utilisateur après la migration d'un serveur en panne
        private void reconnectProtocol(String message) {
            //Récupération du salon
            fairLocation = message.substring(10, message.indexOf('@'));
            //Récupération du pseudo dans le message
            socketUsername = message.substring(message.indexOf('@')+1);
            //Affichage d'un message sur le serveur
            System.out.println("# Reconnexion de " + socketUsername + " sur le serveur");
        }

        //Protocole d'identification au serveur chatamucentral
        private void loginProtocol(String message) {
            //Récupération du pseudo dans le message
            socketUsername = message.substring(6);
            //Vérification du pseudo, alphabétique, supérieur à 3 caractères
            if (isPseudoValid(socketUsername)) {
                //Si le pseudo est disponible
                if (isPseudoAvailable(socketUsername)) {
                    //Ajout du pseudo à la liste des pseudos utilisés
                    clientsUsernames.get(serverPort).add(socketUsername);
                    //Notification de connexion du client au serveur
                    broadcast(serverFair, serverLogin, "# Connexion de " + socketUsername + " au serveur", true);
                    //Envoi du pseudo au client
                    sendMessage("PSEUDO [" + socketUsername + "]");
                    //Envoi du salon du serveur au client
                    sendMessage("FAIR [" + serverFair + "]");
                    //Envoi message instruction au client
                    sendMessage("# Pour envoyer un message saisir la commande : MESSAGE message");
                    //Envoi des serveurs disponibles en cas de perte de connexion au client
                    broadcastServerLinks();
                    //Mise à jour des pseudos utilisés entre serveurs
                    broadcastUsernames();
                } else {
                    socketUsername = null;
                    sendMessage("# Pseudo déjà utilisé sur le serveur, veuillez réessayer.");
                }
            } else {
                socketUsername = null;
                sendMessage("# Pseudo invalide, seuls les caractères de l'alphabet sont acceptés (3 caractères minimum).");
            }
        }

        //Liaison avec les serveurs connectés après la perte de connexion avec un serveur
        private void linkServerAfterDown() {
            //Récupération de la liste des serveurs connectés de ce serveur
            HashSet<Integer> linkedServerConnectedPorts = linkedServersPorts.get(socketPort);
            //Pour chaque port connecté, on vérifie qu'il soit dans la liste de ceux connectés à ce serveur
            for (int port : linkedServerConnectedPorts)
                //Si le port n'est pas présent dans la liste on se connecte au serveur distant
                if (!linkedServersPorts.containsKey(port))
                    //Établissement d'une liaison vers le serveur distant
                    linkToServer(port);
            //Suppression du port du serveur dans la liste des ports connectés
            linkedServersPorts.remove(socketPort);
            //Mise à jour de la liste des serveurs en fonctionnement pour les clients
            broadcastServerLinks();
        }
    }
}
