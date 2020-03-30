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
    //Couleur attribuée pour les messages serveur envoyés aux clients
    private final static String clientsColor = "\u001B[32m";
    //Couleur attribuée aux messages du client
    private final static String userColor = "\u001B[34m";
    //Couleur par défaut du terminal
    private final static String defaultColor = "\u001B[0m";

    //Port du serveur
    private static int serverPort;

    //Pseudo du serveur
    private static String serverLogin;

    //Entrée clavier
    private static BufferedReader stdin;

    //Liste des clients connectés au serveur
    private static ArrayList<SocketHandler> clients = new ArrayList<>();

    private static ArrayList<String> linkedServersPorts = new ArrayList<>();

    //Sélecteur
    private static Selector selector;

    //Executeur
    private static Executor executor;

    //Nombre de messages groupés maximal à envoyer
    private static final int MAX_OUTGOING = 50;

    public static void main(String[] args) throws IOException {
        //Nombre d'arguments passés en paramètres
        int argc = args.length;
        //Serveur chatamu
        ChatamuCentral chatamu;

        //Traitement des arguments
        if (argc == 1) {
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
            System.out.println("Usage: java EchoServer port");
        }
    }

    //Démarrage du serveur
    public void demarrer(int port) {
        System.out.println("# Démarrage du chatamu central sur le port " + port);
        try {
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
    public static void broadcast(String author, String message) {
        //Si l'auteur du message est le serveur
        if (author.startsWith("#"))
            //Couleur du message par défaut
            message = defaultColor + message;
        else
            //Couleur du message = celle des clients
            message = clientsColor + message;

        //Pour chaque client connecté
        for (SocketHandler client : clients)
            //Si le pseudo du client est définit
            if (client.pseudo != null)
                //Si le pseudo est différent de l'auteur du message,
                // ajout du message à la liste du client
                if (!client.pseudo.equals(author)) client.outgoingMessages.add(message);

        //Affichage du message sur le serveur
        System.out.println(defaultColor + message);
    }

    //Vérification du pseudo syntaxiquement
    public static boolean isPseudoValid(String pseudo) {
        //Vérification du pseudo, alphabétique, supérieur à 3 caractères
        return pseudo.matches("[a-zA-Z0-9]+") && pseudo.length() >= 3;
    }

    //Vérifier qu'un pseudo n'est pas déja attribué
    public static boolean isPseudoAvailable(SocketHandler newClient, String pseudo) {
        //Si des clients sont connectés au serveur
        if (clients.size() > 0)
            //Pour chaque client
            for (SocketHandler client : clients)
                //Si le pseudo du client est définit
                if (client.pseudo != null)
                    //Si le client n'est pas le nouveau client et que son pseudo vaut celui choisi
                    // retour faux car pseudo déjà pris
                    if (client != newClient && client.pseudo.equals(pseudo)) return false;
        //Retour vrai, pseudo disponible
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
        return new String(conversion, StandardCharsets.UTF_8).replace("\n","");
    }

    //Connecter se serveur à un autre serveur
    private static void linkToServer(String port) {
        //Si le port n'est pas contenu dans la liste des serveurs déjà connectés
        if (!linkedServersPorts.contains(port) && Integer.parseInt(port) != serverPort) {
            //Ajout du port à la liste des ports déjà connectés
            linkedServersPorts.add(port);
            try {
                //Ouverture d'un canal du socket
                SocketChannel socket = SocketChannel.open();
                //Connexion du socket à l'adresse ip et au port passés en paramètre
                socket.connect(new InetSocketAddress("localhost", Integer.parseInt(port)));
                //Buffer de bytes
                ByteBuffer buffer = ByteBuffer.allocate(128);
                String message = "SERVERCONNECT " + serverPort;
                //Envoi du message
                buffer.put(message.getBytes());
                //Inversion du buffer
                buffer.flip();
                //Écriture du message dans le serveur
                socket.write(buffer);
                //Nettoyage du buffer
                buffer.clear();
                //Lancement d'un thread gérant la réception des messages depuis le serveur distant
                executor.execute(new LinkedServerMessageReceiver(socket, port));
            } catch (IOException e) {
                //Message d'erreur
                System.err.println("# Liaison impossible avec le serveur distant");
                e.printStackTrace();
            }
        }
    }

    ////################# LISTE DES THREADS ##################

    //Thread gérant la réception des messages provenants des autres serveurs
    public static class LinkedServerMessageReceiver implements Runnable {

        //Socket
        private SocketChannel socket;
        //Pseudo serveur distant
        private String linkedServerLogin;

        //Constructeur
        public LinkedServerMessageReceiver(SocketChannel socket, String port) {
            this.socket = socket;
            this.linkedServerLogin = "#" + port;
        }

        @Override
        public void run() {
            try {
                //Message au format string récupéré depuis le serveur
                String message = "";
                //Buffer de bytes contenant le message en bytes
                ByteBuffer buffer = ByteBuffer.allocate(128);
                //Tant qu'il est possible de lire depuis le socket
                while (socket.read(buffer) != -1) {
                    //Récupération du message
                    message = extractMessage(buffer);
                    //Affichage du message s'il n'est pas vide
                    if (message.getBytes().length > 0)
                        //Affichage du message
                        //On utilise le pseudo du serveur distant afin que le message
                        //ne lui soit pas renvoyé
                        broadcast(linkedServerLogin, message);
                    //Nettoyage du buffer
                    buffer.clear();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //Thread clavier
    public static class KeyboardInput implements Runnable {
        @Override
        public void run() {
            try {
                //Tant qu'il est autorisé d'utiliser le clavier
                while (selector.isOpen()) {
                    String message = "";
                    //Récupération du message au clavier
                    message = stdin.readLine();
                    //Si le message est SERVERCONNECT, établissement d'une liaison au serveur distant
                    if (message.startsWith("SERVERCONNECT ")) linkToServer(message.substring(14));
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
        private SocketChannel csc;
        //Pseudo du client
        private String pseudo = null;
        //Liste des messages à envoyer au client
        private ArrayBlockingQueue<String> outgoingMessages = new ArrayBlockingQueue<>(MAX_OUTGOING);
        //Propriété pour savoir si c'est un lien avec un serveur
        private boolean isServerLink = false;

        //Constructeur
        public SocketHandler(SelectionKey key) {
            //Récupération du canal de la clé
            this.csc = (SocketChannel) key.channel();
        }

        //Envoyer un message au client depuis le serveur
        private void sendMessage(String message) {
            try {
                //Ajout de la couleur en fonction de l'état de connexion
                message = (pseudo != null) ? message + userColor : message + defaultColor;
                //Tableau contenant le message en bytes
                byte[] backbuffer = (message + "\n").getBytes();
                //Envoi du message au client
                csc.write(ByteBuffer.wrap(backbuffer));
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
                //Tant qu'il est possible de lire depuis le client
                while (csc.read(buffer) != -1) {
                    //Envoi au client des messages serveur
                    sendServerMessages();
                    //Si c'est un lien de serveur on passe la suite du code
                    if (isServerLink) continue;
                    //Récupération du message
                    String message = extractMessage(buffer);
                    //Si le message est EXIT on sort de la boucle
                    if (message.equals("EXIT")) {
                        sendMessage(defaultColor + "# Déconnexion du serveur.");
                        break;
                    } else if (message.startsWith("SERVERCONNECT ")) {
                        isServerLink = true;
                        String port = message.substring(14);
                        pseudo = "#" + port;
                        //Envoi d'un message de confirmaton de liaison
                        sendMessage(defaultColor + "# Liaison établie au serveur connecté sur le port " + serverPort);
                        //Liaison de ce serveur au serveur distant
                        linkToServer(port);
                    }
                    //Si le message n'est pas vide
                    else if (message.getBytes().length > 0) {
                        //Si le pseudo n'est pas définit
                        if (pseudo == null) {
                            //Si le message commence par LOGIN
                            if (message.startsWith("LOGIN ")) {
                                //Récupération du pseudo dans le message
                                pseudo = message.substring(6);
                                //Vérification du pseudo, alphabétique, supérieur à 3 caractères
                                if (isPseudoValid(pseudo)) {
                                    //Si le pseudo est disponible
                                    if (isPseudoAvailable(this, pseudo)) {
                                        //Notification de connexion du client au serveur
                                        broadcast(serverLogin, "# Connexion de " + pseudo + " au serveur");
                                        //Envoi message instruction au client
                                        sendMessage(defaultColor + "# Pour envoyer un message saisir la commande : MESSAGE message");
                                    } else {
                                        pseudo = null;
                                        sendMessage(defaultColor + "# Pseudo déjà utilisé sur le serveur, veuillez réessayer.");
                                    }
                                } else {
                                    pseudo = null;
                                    sendMessage(defaultColor + "# Pseudo invalide, seuls les caractères de l'alphabet sont acceptés (3 caractères minimum).");
                                }
                            } else {
                                sendMessage(defaultColor + "# ERROR LOGIN aborting chatamu protocol.");
                                break;
                            }
                        } else {
                            //Si le message commence par MESSAGE
                            if (message.startsWith("MESSAGE "))
                                //Affichage du message
                                broadcast(pseudo, pseudo + " > " + message.substring(8));
                            else
                                sendMessage(defaultColor + "# ERROR chatamu.");
                        }
                    }
                    //Nettoyage du buffer
                    buffer.clear();
                }
                //Si le pseudo est définit
                if (pseudo != null)
                    if (pseudo.startsWith("#"))
                        broadcast(serverLogin, "# Rupture liaison serveur connecté au port " + pseudo.substring(1));
                    else
                        broadcast("#", "# Déconnexion de " + pseudo + ".");
                //Suppression du client dans la liste des clients
                clients.remove(this);
                //Fermeture du thread
                csc.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
