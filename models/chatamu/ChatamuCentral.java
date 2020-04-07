package models.chatamu;

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

/**
 * Serveur models.chatamu.ChatamuCentral
 */
class ChatamuCentral {

    //*************ATTRIBUTS STATIQUES******************//

    /**
     * Port du serveur
     */
    private static int serverPort;

    /**
     * Pseudo du serveur
     */
    private static String serverLogin;

    /**
     * Salon central du serveur
     */
    private static String serverFair;

    /**
     * Singleton contenant des méthodes pour le chat
     */
    private static ChatFunctions chatFunctions;

    //*******************************//

    /**
     * Entrée clavier
     */
    private BufferedReader stdin;

    /**
     * Liste des clients connectés au serveur
     */
    private ArrayList<SocketHandler> clients = new ArrayList<>();

    /**
     * Liste des clients connectés au serveur
     */
    private HashMap<Integer, HashSet<String>> clientsUsernames = new HashMap<>();

    /**
     * Liste des ports des serveurs connectés ainsi que la liste des ports des serveurs auxquels ils sont connectés
     */
    private HashMap<Integer, HashSet<Integer>> linkedServersPorts = new HashMap<>();

    /**
     * Liste des salons créés par les clients
     */
    private HashMap<Integer, HashSet<String>> clientsFairs = new HashMap<>();

    /**
     * Sélecteur
     */
    private Selector selector;

    /**
     * Executeur
     */
    private Executor executor;

    /**
     * Nombre de messages groupés maximal à envoyer
     */
    private final int MAX_OUTGOING = 50;

    /**
     * Activation/Désactivation mode debugage
     */
    private final boolean debugMode = true;

    /**
     * Main
     * @param args arguments
     */
    public static void main(String[] args) {
        //Nombre d'arguments passés en paramètres
        int argc = args.length;
        //Serveur chatamucentral
        ChatamuCentral chatamu;
        //Port passé en paramètre
        String port = args[0];
        //Vérification nombre d'arguments et syntaxe port
        if (argc == 1 && port.matches("[0-9]+")) {
            try {
                //Initialisation variable port serveur
                serverPort = Integer.parseInt(args[0]);
                //Initialisation variable pseudo serveur
                serverLogin = "#" + serverPort;
                //Initialisation du salon central du serveur
                serverFair = "";
                //Récupération de l'instance de la classe models.chatamu.ChatFunctions
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
            System.out.println("Utilisation: java models.chatamu.ChatamuCentral port");
        }
    }

    /**
     * Démarrage du serveur
     * @param port port du serveur
     */
    private void demarrer(int port) {
        System.out.println("# Démarrage du serveur models.chatamu.ChatamuCentral sur le port " + port);
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

            //Executeur en mode pool de threads voleurs de travail
            executor = Executors.newWorkStealingPool();

            //Récupération de la saisie clavier
            stdin = new BufferedReader(new InputStreamReader(System.in));

            //Thread gérant les entrées clavier
            executor.execute(new KeyboardInput());

            //Tant que le serveur est en fonctionnement
            while (selector.isOpen())
                //Accepter les connexions entrantes
                acceptConnections();

        } catch (IOException e) {
            System.out.println("# Arrêt anormal du serveur.");
            e.printStackTrace();
        }
    }

    /**
     * Accepter les connexions entrantes
     */
    private void acceptConnections() {
        try {
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
                    ServerSocketChannel server = (ServerSocketChannel) key.channel();
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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Ajouter un message à la liste des messages à envoyer des clients
     * @param fair salon
     * @param author auteur du message
     * @param message message
     * @param printOut affichage du message sur le serveur
     */
    private void broadcast(String fair, String author, String message, boolean printOut) {
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
                    //Si le salon du client est celui passé en paramètre et que le message ne provient pas d'un serveur
                    else if (client.fairLocation.equals(fair))
                        //Message client
                        if (!author.startsWith("#"))
                            //Ajout du message à la liste du client
                            client.outgoingMessages.add(message.substring(message.indexOf("@")+1));
                        //Message serveur
                        else
                            //Ajout du message à la liste du client
                            client.outgoingMessages.add(message);
        //Si on souhaite afficher le message et que le salon est celui du serveur
        if (printOut && fair.equals(serverFair)) {
            //Suppression du @ en début de message s'il provient d'un client
            if (!author.startsWith("#")) message = message.substring(1);
            //Affichage du message
            System.out.println(message);
        }
    }

    /**
     * Vérification syntaxique d'un paramètre (pseudo, nom de salon)
     */
    private boolean isSyntaxValid(String argument) {
        //Vérification du pseudo, alphabétique, supérieur à 3 caractères
        return argument.matches("[a-zA-Z0-9]+") && argument.length() >= 3;
    }

    /**
     * Vérifier qu'un pseudo n'est pas déja attribué
     * @param pseudo pseudo sélectionné
     * @return booléen
     */
    private boolean isPseudoAvailable(String pseudo) {
        //Parcours de la liste des pseudos sur chaque serveur
        for (HashSet<String> usernames : clientsUsernames.values())
            //Si le pseudo est contenu dans la liste on retourne faux
            if (usernames.contains(pseudo)) return false;
        return true;
    }

    /**
     * Vérifier qu'un salon est bien ouvert
     * @param fair salon sélectionné
     * @return booléen
     */
    private boolean isFairExisting(String fair) {
        //Parcours de la liste des salons sur chaque serveur
        for (HashSet<String> fairs : clientsFairs.values())
            //Si le salon est contenu dans la liste on retourne vrai
            if (fairs.contains(fair)) return true;
        return false;
    }

    /**
     * Établir une liaison à un autre serveur
     * @param port port du serveur à lier
     */
    private void linkToServer(int port) {
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
                socket.write(ByteBuffer.wrap(chatFunctions.secure("LINKTO " + serverPort).getBytes()));
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

    /**
     * Mise à jour des ports des serveurs ouverts
     */
    private void broadcastServerLinks() {
        broadcast(serverFair, serverLogin, chatFunctions.secure("UPDATELINKS " + linkedServersPorts.keySet()), false);
    }

    /**
     * Mise à jour des pseudos entre serveurs
     */
    private void broadcastUsernames() {
        for (SocketHandler serverLink : clients)
            if (serverLink.isServerSocketSender)
                serverLink.sendMessage(chatFunctions.secure("UPDATEUSERNAMES " + clientsUsernames.get(serverPort)));
    }

    /**
     * Mise à jour des pseudos entre serveurs
     */
    private void broadcastFairs() {
        for (SocketHandler serverLink : clients)
            if (serverLink.isServerSocketSender)
                serverLink.sendMessage(chatFunctions.secure("UPDATEFAIRS " + clientsFairs.get(serverPort)));
    }

    ////################# LISTE DES THREADS ##################

    /**
     * Thread gérant la saisie clavier
     */
    private class KeyboardInput implements Runnable {
        @Override
        public void run() {
            try {
                //Tant que le serveur fonctionne
                while (selector.isOpen()) {
                    String message;
                    //Récupération du message au clavier
                    message = stdin.readLine();
                    //Établissement d'une liaison au serveur distant
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

    /**
     * Thread gérant un client
     */
    private class SocketHandler implements Runnable {
        /**
         * Taille maximale pour un message
         */
        private final int MESSAGE_LENGTH = 128;

        /**
         * Socket client
         */
        private SocketChannel socket;

        /**
         * Pseudo du client
         */
        private String socketUsername;

        /**
         * Port du socket
         */
        private int socketPort;

        /**
         * Salon dans lequel est situé le socket
         */
        private String fairLocation = serverFair;

        /**
         * Liste des messages à envoyer au client
         */
        private ArrayBlockingQueue<String> outgoingMessages = new ArrayBlockingQueue<>(MAX_OUTGOING);

        /**
         * Liste des messages à analyser de manière ordonnée
         */
        private PriorityQueue<String> messagesToAnalyze = new PriorityQueue<>();

        /**
         * Propriété pour savoir si c'est un lien avec un serveur
         */
        private boolean isServerSocketSender = false;

        /**
         * Propriété pour savoir si c'est un receveur de messages provenant d'un lien avec un serveur
         */
        private boolean isServerSocketReceiver = false;

        /**
         * Constructeur
         * @param key clé
         */
        private SocketHandler(SelectionKey key) {
            //Récupération du canal de la clé
            this.socket = (SocketChannel) key.channel();
        }

        /**
         * Constructeur socket receveur de messages autre serveur
         * @param socket socket client
         * @param socketPort port du socket
         */
        private SocketHandler(SocketChannel socket, int socketPort) {
            //Initialisation du socket
            this.socket = socket;
            //Initialisation du port
            this.socketPort = socketPort;
            //Initialisation du pseudo
            this.socketUsername = "#" + socketPort;
            //Passage du socket en mode receveur de message serveur
            isServerSocketReceiver = true;
        }

        /**
         * Envoyer un message au client depuis le serveur
         * @param message message
         */
        private void sendMessage(String message) {
            //Vérification que le sélecteur est ouvert
            if (selector.isOpen()) {
                try {
                    //Tableau contenant le message en bytes
                    byte[] backbuffer = (message + "\n").getBytes();
                    //Envoi du message au client
                    socket.write(ByteBuffer.wrap(backbuffer));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * Envoyer les messages en attente du serveur au client
         */
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

        /**
         * Récupération des messsages à envoyer au client
         * @return liste de messages
         */
        private List<String> getMessagesToSend() {
            //Liste des messages à envoyer
            List<String> messagesToSend = new LinkedList<>();
            //Récupération des messages à envoyer
            outgoingMessages.drainTo(messagesToSend);
            //Retourne la liste des messages
            return messagesToSend;
        }

        /**
         * Récupérer la liste des salons disponibles
         * @return liste de salons
         */
        private ArrayList<String> getAvailableFairs() {
            //Initialisation d'un tableau contenant les salons disponibles
            ArrayList<String> availableFairs = new ArrayList<>();
            //Ajout des salons de chaque serveur dans la liste
            for (HashSet<String> fairs : clientsFairs.values())
                availableFairs.addAll(fairs);
            //Retourne la liste des salons
            return availableFairs;
        }

        /**
         * Extraire une liste depuis un string
         * @param string liste sous forme de string
         * @return liste sans duplicats
         */
        private HashSet<String> extractListFromString(String string) {
            //Explosion du string en tableau de string
            String[] stringList = string.split(", ");
            //Instanciation liste sans doublons
            HashSet<String> listSet = new HashSet<>();
            //Ajout des éléments à la liste
            Collections.addAll(listSet, stringList);
            //Renvoi de la liste
            return listSet;
        }

        /**
         * Découpage du message et ajout dans la liste des messages à analyser
         */
        private void prepareAnalyze(String message) {
            //Si le message n'est pas vide
            if (message.length() > 0)
                //Message contenant un retour à la ligne
                if (message.contains("\n")) {
                    //Séparation du message en plus petits messages
                    String[] strings = message.split("\n");
                    //Ajout des messages à la liste de ceux à analyser
                    messagesToAnalyze.addAll(Arrays.asList(strings));
                } else {
                    //Ajout de message à la liste pour l'analyser
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
                //Tant que le sélecteur est ouvert
                while (selector.isOpen()) {
                    //S'il est possible de lire depuis le client
                    if (socket.read(buffer) != -1) {
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
                        //Mode débugage
                        if (debugMode && messagesToAnalyze.size() > 0)
                            //Affichage des messages entrants en vert puis réinitialisation couleur par défaut
                            System.out.println("\u001B[32m" + "DEBUG -> " + messagesToAnalyze + "\u001B[0m");
                        //Tant qu'il y a des messages à traiter
                        while (messagesToAnalyze.size() > 0) {
                            //Récupération de la tete de la liste des messages
                            message = messagesToAnalyze.poll();
                            //Message non null
                            if (message != null) {
                                //Analyse du message
                                if (isServerSocketReceiver) analyseMessageAsReceiver(message);
                                else exit = analyseMessage(message);
                            }
                        }
                        //Si exit est à vrai, déconnexion du client
                        if (exit) break;
                        //Nettoyage du buffer
                        buffer.clear();
                    }
                    else break;
                }
                //Si le sélecteur est ouvert
                if (selector.isOpen()) {
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
                }
                //Fermeture du thread
                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /**
         * Analyser les messages provenant d'une liaison avec un serveur
         * @param message opération à effectuer
         */
        private void analyseMessageAsReceiver(String message) {
            //Variable pour récupérer le type d'opération
            String operation = "";
            //Variable pour récupérer le paramètre
            String argument = "";
            //L'opération demandée est une mise à jour
            if (message.startsWith("UPDATE")) {
                //Récupération du type de mise à jour
                operation = message.substring(0, message.indexOf(" "));
                //Récupération du paramètre
                argument = message.substring(message.indexOf('[') + 1, message.lastIndexOf(']'));
            }
            //Mode débugage
            if (debugMode)
                System.out.println("\u001B[32m" + "OPERATION -> '" + operation + "':'" + argument + "'\u001B[0m");
            //Actions à effectuer en fonction de l'opération demandée
            switch (operation) {
                case "UPDATELINKS\b":
                    //Mise à jour des liens de connexion
                    updateLinksOperation(argument);
                    break;
                case "UPDATEUSERNAMES\b":
                    //Mise à jour des pseudos utilisés
                    updateUsernamesOperation(argument);
                    break;
                case "UPDATEFAIRS\b":
                    //Mise à jour des salons ouverts
                    updateFairsOperation(argument);
                    break;
                //Affichage du message
                default:
                    //Message serveur
                    if (message.startsWith("#"))
                        //Propagation du message sur l'ensemble du réseau
                        broadcast(serverFair, socketUsername, message, true);
                    //Message client
                    else {
                        //Récupération du salon
                        String salon = message.substring(0, message.indexOf('@'));
                        //Récupération du message
                        message = message.substring(message.indexOf('@')+1);
                        //Renvoi du message sur le bon salon
                        if (salon.equals(serverFair))
                            //Propagation du message sur le salon central
                            broadcast(serverFair, socketUsername, message, true);
                        else
                            //Propagation du message sur le salon souhaité
                            broadcast(salon, socketUsername, message, true);
                    }
                    break;
            }
        }

        /**
         * Analyser les demandes faites par les clients
         * @param message opération demandée
         * @return booléen permettant de déconnecter le client ou pas du serveur
         */
        private boolean analyseMessage(String message) {
            //Récupération de l'action à effectuer
            String actionType = (!message.contains(" ")) ? message : message.substring(0, message.indexOf(" "));
            //Récupération du paramètre
            String argument = (!message.contains(" ")) ? "" : message.substring(message.indexOf(" ")+1);
            //Mode débugage
            if (debugMode)
                System.out.println("\u001B[32m" + "OPERATION -> '" + actionType + "':'" + argument + "'\u001B[0m");
            //Si le pseudo n'est pas définit
            if (socketUsername == null)
                switch (actionType) {
                    //Si le message est EXIT on déconnecte le socket du serveur
                    case "EXIT":
                        //Retourne vrai pour sortir de la boucle
                        return true;
                    //models.chatamu.Client se reconnectant au serveur
                    case "RECONNECT\b":
                        reconnectOperation(argument);
                        break;
                    //Demande d'authentification
                    case "LOGIN":
                        loginOperation(argument);
                        break;
                    //Demande de liaison entre serveurs
                    case "LINKTO\b":
                        linkOperation(argument);
                        break;
                    default:
                        //Envoie un messsage d'erreur
                        sendMessage("# ERROR LOGIN aborting chatamu protocol.");
                        return true;
                }
            //Si le pseudo est définit (client connecté)
            else
                switch (actionType) {
                    //Envoi d'un message
                    case "MESSAGE":
                        sendMessageOperation(argument);
                        break;
                    //Demande de création d'un salon
                    case "SALON":
                        createFairOperation(argument);
                        break;
                    //Afficher les salons ouverts
                    case "LIST":
                        //Renvoi la liste des salons ouverts au client
                        sendMessage(getAvailableFairs().toString());
                        break;
                    //Demande de connexion à un salon
                    case "JOIN":
                        joinFairOperation(argument);
                        break;
                    //Demande de déconnexion d'un salon
                    case "QUIT":
                        quitFairOperation(argument);
                        break;
                    default:
                        //Notification d'erreur
                        sendMessage("# ERROR chatamu.");
                        break;
                }
            //Retourne faux pour ne pas stopper la connexion au serveur
            return false;
        }

        /**
         * models.chatamu.Client souhaite envoyer un message
         * @param message message à envoyer
         */
        private void sendMessageOperation(String message) {
            //Envoi du message
            broadcast(fairLocation, socketUsername, fairLocation + "@" + socketUsername + " > " + message, true);
        }

        /**
         * Créer un nouveau salon
         * @param fair nom du salon
         */
        private void createFairOperation(String fair) {
            //Vérification de la syntaxe du nom du salon
            if (isSyntaxValid(fair)) {
                //Ajout du salon dans la liste de ceux du serveur
                clientsFairs.get(serverPort).add(fair);
                //Notification de création du salon
                broadcast(serverFair, serverLogin, "# Création du salon : " + fair + " (par " + socketUsername + ")", true);
                //Envoi de la liste des salons du serveur aux autres serveurs
                broadcastFairs();
            }
            else sendMessage("# Nom de salon invalide, veuillez réessayer");
        }

        /**
         * Rejoindre un salon
         * @param fair nom du salon
         */
        private void joinFairOperation(String fair) {
            //Vérification si le salon existe
            if (isFairExisting(fair)) {
                //Récupération du nom du salon et mise à jour de l'emplacement du client
                fairLocation = fair;
                //Envoi du salon au client
                sendMessage("FAIR [" + fair + "]");
                //Notification de connexion au salon
                broadcast(serverFair, serverLogin, "# Connexion de " + socketUsername + " au salon : " + fair, true);
            }
            else sendMessage("# Salon introuvable");
        }

        /**
         * Quitter un salon
         * @param fair nom du salon
         */
        private void quitFairOperation(String fair) {
            //Vérification si le salon existe
            if (isFairExisting(fair) && fairLocation.equals(fair)) {
                //Récupération du nom du salon et mise à jour de l'emplacement du client
                fairLocation = serverFair;
                //Envoi du salon central au client
                sendMessage("FAIR [" + serverFair + "]");
                //Notification de connexion au salon
                broadcast(serverFair, serverLogin, "# Déconnexion de " + socketUsername + " du salon : " + fair, true);
            }
            else sendMessage("# Salon introuvable");
        }

        /**
         * Mise à jour des liaisons entre serveurs
         * @param links liens
         */
        private void updateLinksOperation(String links) {
            //On vérifie qu'il y a des messages dans la liste
            if (links.length() > 0) {
                //Explosion du string en tableau de string pour séparer les ports
                String[] stringPorts = links.split(", ");
                //Ajout des ports dans la liste des ports déjà connectés
                for (String port : stringPorts)
                    linkedServersPorts.get(socketPort).add(Integer.parseInt(port));
            }
        }

        /**
         * Mise à jour des pseudos entre serveurs
         * @param usernames pseudos
         */
        private void updateUsernamesOperation(String usernames) {
            //On vérifie qu'il y a des pseudos dans la liste
            if (usernames.length() > 0) {
                //Ajout des pseudos du serveur dans la liste
                clientsUsernames.put(socketPort, extractListFromString(usernames));
            } else {
                //Suppression des éléments de la map pour le port
                clientsUsernames.remove(socketPort);
            }
        }

        /**
         * Mise à jour des salons ouverts entre serveurs
         * @param fairs salons
         */
        private void updateFairsOperation(String fairs) {
            //On vérifie qu'il y a des messages dans la liste
            if (fairs.length() > 0) {
                //Ajout des salons du serveur dans la liste
                clientsFairs.put(socketPort, extractListFromString(fairs));
            } else {
                //Suppression des éléments de la map pour le port
                clientsFairs.remove(socketPort);
            }
        }

        /**
         * Liaison avec un autre serveur
         * @param port port du serveur distant
         */
        private void linkOperation(String port) {
            //Indication que le socket est un lien serveur envoyeur de messages
            isServerSocketSender = true;
            //Définition du pseudo du socket
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

        /**
         * Reconnexion d'un utilisateur après la migration des clients d'un serveur en panne
         * @param argument contient le dernier salon et pseudo utilisés par le client
         */
        private void reconnectOperation(String argument) {
            //Récupération du salon
            fairLocation = argument.substring(0, argument.indexOf('@'));
            //Récupération du pseudo dans le message
            socketUsername = argument.substring(argument.indexOf('@')+1);
            sendMessage("# Migration vers le serveur models.chatamu.ChatamuCentral connecté au port " + serverPort);
            //Affichage d'un message sur le serveur
            System.out.println("# Reconnexion de " + socketUsername + " sur le serveur");
        }

        /**
         * Protocole d'identification au serveur chatamucentral
         * @param username pseudo sélectionné
         */
        private void loginOperation(String username) {
            //Initialisation du pseudo de la socket
            socketUsername = username;
            //Vérification du pseudo, alphabétique, supérieur à 3 caractères
            if (isSyntaxValid(socketUsername)) {
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

        /**
         * Liaison avec les serveurs connectés après la perte de connexion avec un serveur
         */
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
