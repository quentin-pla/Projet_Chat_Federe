import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

class ChatamuCentral {
    //Port du serveur
    private static int serverPort;

    //Pseudo du serveur
    private static String serverLogin;

    //Entrée clavier
    private static BufferedReader stdin;

    //Liste des clients connectés au serveur
    private static ArrayList<SocketHandler> clients = new ArrayList<>();

    //Liste des clients connectés au serveur
    private static HashMap<Integer, HashSet<String>> clientsUsernames = new HashMap<>();

    //Liste des ports des serveurs connectés ainsi que la liste des ports des serveurs auxquels ils sont connectés
    private static HashMap<Integer, HashSet<Integer>> linkedServersPorts = new HashMap<>();

    //Sélecteur
    private static Selector selector;

    //Executeur
    private static Executor executor;

    //Nombre de messages groupés maximal à envoyer
    private static final int MAX_OUTGOING = 50;

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
    public static void broadcast(String author, String message, boolean printOut) {
        //Pour chaque client connecté
        for (SocketHandler client : clients)
            //Si le pseudo du client est définit
            if (client.socketUsername != null)
                //Si le pseudo est différent de l'auteur du message,
                // ajout du message à la liste du client
                if (!client.socketUsername.equals(author)) {
                    //Ajout du message à la liste du client
                    client.outgoingMessages.add(message);
                }
        //Affichage du message sur le serveur
        if (printOut) System.out.println(message);
    }

    //Vérification du pseudo syntaxiquement
    public static boolean isPseudoValid(String pseudo) {
        //Vérification du pseudo, alphabétique, supérieur à 3 caractères
        return pseudo.matches("[a-zA-Z0-9]+") && pseudo.length() >= 3;
    }

    //Vérifier qu'un pseudo n'est pas déja attribué
    public static boolean isPseudoAvailable(String pseudo) {
        //Retour vrai, pseudo disponible
        for (HashSet<String> usernames : clientsUsernames.values())
            if (usernames.contains(pseudo)) return false;
        return true;
    }

    //Extraire un message contenu dans un buffer
    private static String extractMessage(ByteBuffer buffer) {
        //Inversion du buffer
        buffer.flip();
        //Instanciation d'un tableau de caractères de taille MESSAGE_LENGTH
        ArrayList<Byte> data = new ArrayList<>();
        //Tant qu'il reste du contenu à consommer dans le buffer
        while(buffer.hasRemaining()) {
            //Consommation d'un caractère
            byte b = buffer.get();
            //Si le caractère est définit on l'ajoute à la liste
            if (b != 0) data.add(b);
        }
        //Instanciation d'un tableau de bytes
        byte[] conversion = new byte[data.size()];
        //Ajout des caractères de data dans le tableau
        for(int i = 0; i < data.size(); i++)
            conversion[i] = data.get(i);
        //Retour du message au format chaine de caractères, encodage UTF-8
        // suppression des retours à la ligne
        return new String(conversion, StandardCharsets.UTF_8).trim();
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
        broadcast(serverLogin, "UPDATELINKS " + linkedServersPorts.keySet(), false);
    }

    //Mise à jour des pseudos entre serveurs
    private static void broadcastUsernames() {
        for (SocketHandler serverLink : clients)
            if (serverLink.isServerLink)
                serverLink.sendMessage("UPDATEUSERNAMES " + clientsUsernames.get(serverPort));
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
                    else if (message.equals("c")) System.out.println(linkedServersPorts.toString());
                    else if (message.equals("u")) System.out.println(clientsUsernames.toString());
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
        private String socketUsername = null;

        //Port du socket
        private int socketPort;

        //Liste des messages à envoyer au client
        private ArrayBlockingQueue<String> outgoingMessages = new ArrayBlockingQueue<>(MAX_OUTGOING);

        //Liste des messages à analyser de manière ordonnée
        private PriorityQueue<String> messagesToAnalyze = new PriorityQueue<>();

        //Propriété pour savoir si c'est un lien avec un serveur
        private boolean isServerLink = false;

        //Propriété pour savoir si c'est un receveur de messages provenant d'un lien avec un serveur
        private boolean isServerReceiver = false;

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
            isServerReceiver = true;
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
                    if (isServerLink) continue;
                    //Récupération du message
                    message = extractMessage(buffer);
                    //Si le message n'est pas vide
                    if (message.length() > 0) {
                        if (message.contains("\n")) {
                            String[] strings = message.split("\n");
                            messagesToAnalyze.addAll(Arrays.asList(strings));
                        } else {
                            messagesToAnalyze.add(message);
                        }
                    }
                    //Booléen déconnexion du client
                    boolean exit = false;
//                    if (messagesToAnalyze.size() > 0)
//                        System.out.println("-> " + messagesToAnalyze);
                    //Tant qu'il y a des messages à traiter
                    while (messagesToAnalyze.size() > 0) {
                        //Récupération de la tete de la liste des messages
                        message = messagesToAnalyze.poll();
                        //Message non null
                        if (message != null) {
                            //Analyse du message
                            if (isServerReceiver) analyseMessageAsReceiver(message);
                            else exit = !analyseMessage(message);
                        }
                    }
                    //Si exit est à vrai, déconnexion du client
                    if (exit) break;
                    //Nettoyage du buffer
                    buffer.clear();
                }
                //Liaison du serveur aux serveurs proches ouverts après que le distant ferme
                if (isServerReceiver) {
                    linkServerAfterDown();
                    //Afichage d'un message pour constater une rupture avec un serveur distant
                    System.out.println("# Rupture liaison serveur connecté au port " + socketPort);
                }
                //Si le pseudo est définit et que le client n'est pas un lien vers un serveur
                if (socketUsername != null && !socketUsername.startsWith("#"))
                    broadcast(serverLogin, "# Déconnexion de " + socketUsername + ".", true);
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
            //Message code pour mettre à jour les liens
            if (message.startsWith("UPDATELINKS")) {
                //Récupération des ports
                message = message.substring(message.indexOf('[')+1, message.lastIndexOf(']'));
                //On vérifie qu'il y a des messages dans la liste
                if (message.length() > 0) {
                    //Explosion du string en tableau de string pour séparer les ports
                    String[] stringPorts = message.split(", ");
                    //Ajout des ports dans la liste des ports déjà connectés
                    for (String port : stringPorts)
                        linkedServersPorts.get(socketPort).add(Integer.parseInt(port));
                }
            }
            //Message code pour mettre à jour les liens
            else if (message.startsWith("UPDATEUSERNAMES")) {
                //Récupération des ports
                message = message.substring(message.indexOf('[')+1, message.lastIndexOf(']'));
                //On vérifie qu'il y a des messages dans la liste
                if (message.length() > 0) {
                    //Explosion du string en tableau de string pour séparer les ports
                    String[] pseudos = message.split(", ");
                    HashSet<String> hashPseudos = new HashSet<>();
                    Collections.addAll(hashPseudos, pseudos);
                    //Ajout des pseudos du serveur dans la liste
                    clientsUsernames.put(socketPort, hashPseudos);
                } else {
                    clientsUsernames.remove(socketPort);
                }
            }
            else {
                //Affichage du message
                //On utilise le pseudo du serveur distant afin que le message
                //ne lui soit pas renvoyé et ainsi éviter une boucle sans fin
                broadcast(socketUsername, message, true);
            }
        }

        private boolean analyseMessage(String message) {
            //Si le message est EXIT on sort de la boucle
            if (message.equals("EXIT")) {
                //Envoi d'un message de confirmation
                sendMessage("# Déconnexion du serveur.");
                return false;
            }
            //Si le message commence par LINKTO
            //on ajoute un backspace au début afin qu'un client ne puisse pas saisir la commande
            else if (message.startsWith("LINKTO ")) {
                isServerLink = true;
                String port = message.substring(7);
                socketUsername = "#" + port;
                //Liaison de ce serveur au serveur distant
                linkToServer(Integer.parseInt(port));
                //Mise à jour des liens entre chaque serveur connectés
                broadcastServerLinks();
                //Mise à jour des pseudos déjà utilisés pour le nouveau serveur
                broadcastUsernames();
            }
            //Si le message n'est pas vide
            else {
                //Si le pseudo n'est pas définit
                if (socketUsername == null) {
                    //Client se reconnectant au serveur
                    if (message.startsWith("RECONNECT ")) {
                        //Récupération du pseudo dans le message
                        socketUsername = message.substring(10);
                        //Affichage d'un message sur le serveur
                        System.out.println("# Reconnexion de " + socketUsername + " sur le serveur");
                    }
                    //Si le message commence par LOGIN
                    else if (message.startsWith("LOGIN ")) {
                        //Récupération du pseudo dans le message
                        socketUsername = message.substring(6);
                        //Vérification du pseudo, alphabétique, supérieur à 3 caractères
                        if (isPseudoValid(socketUsername)) {
                            //Si le pseudo est disponible
                            if (isPseudoAvailable(socketUsername)) {
                                //Ajout du pseudo à la liste des pseudos utilisés
                                clientsUsernames.get(serverPort).add(socketUsername);
                                //Notification de connexion du client au serveur
                                broadcast(serverLogin, "# Connexion de " + socketUsername + " au serveur", true);
                                //Envoi du pseudo au client
                                sendMessage("PSEUDO [" + socketUsername + "]");
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
                    } else {
                        sendMessage("# ERROR LOGIN aborting chatamu protocol.");
                        return false;
                    }
                } else {
                    //Si le message commence par MESSAGE
                    if (message.startsWith("MESSAGE "))
                        //Affichage du message
                        broadcast(socketUsername, socketUsername + " > " + message.substring(8), true);
                    else
                        sendMessage("# ERROR chatamu.");
                }
            }
            return true;
        }

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
