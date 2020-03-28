import java.io.IOException;
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

    //Selector pour des I/O multiplexées
    private static Selector selector;
    //Socket serveur TCP
    private static ServerSocketChannel ssc;
    //Executeur
    private static Executor executor;

    //Liste des clients connectés au serveur
    private static ArrayList<ClientHandler> clients = new ArrayList<>();

    public static void main(String[] args) {
        //Nombre d'arguments passés en paramètres
        int argc = args.length;
        //Serveur chatamu
        ChatamuCentral serveur;
        //Traitement des arguments
        if (argc == 1) {
            try {
                //Instanciation du serveur
                serveur = new ChatamuCentral();
                //Démarrage du serveur sur le port souhaité
                serveur.demarrer(Integer.parseInt(args[0]));
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
            ssc = ServerSocketChannel.open();
            //Assignation du port au serveur
            ssc.socket().bind(new InetSocketAddress(port));
            //Configuration canal en mode non bloquant
            ssc.configureBlocking(false);
            //Jeu d'opérations
            int ops = ssc.validOps();
            //Enregistrement du canal sur le sélecteur
            ssc.register(selector, ops, null);

            //Pool de threads voleurs de travail
            executor = Executors.newWorkStealingPool();

            while (true) {
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
                        //Accepte la connexion au serveur
                        SocketChannel csc = ssc.accept();
                        //Configuration en mode non-bloquant
                        csc.configureBlocking(false);
                        //Enregistrement du canal de la clé en mode lecture
                        csc.register(selector, SelectionKey.OP_READ);
                    }
                    //Si le canal de la clé est prêt à être lu
                    if (key.isReadable()) {
                        //Création d'un thread client
                        ClientHandler clientHandler = new ClientHandler(key);
                        //Execution du thread
                        executor.execute(clientHandler);
                        //Ajout du client à la liste des clients connectés
                        clients.add(clientHandler);
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
        if (author.equals("#"))
            //Couleur du message par défaut
            message = defaultColor + message;
        else
            //Couleur du message = celle des clients
            message = clientsColor + message;

        //Pour chaque client connecté
        for (ClientHandler client : clients)
            //Si le pseudo du client est définit
            if (client.pseudo != null)
                //Si le pseudo est différent de l'auteur du message,
                // ajout du message à la liste du client
                if (!client.pseudo.equals(author)) client.outgoingMessages.add(message);
        //Affichage du message sur le serveur
        System.out.println(defaultColor + message);
    }

    //Vérifier que le pseudo du nouveau client n'est pas déja pris
    public static boolean checkPseudo(ClientHandler newClient, String pseudo) {
        //Si des clients sont connectés au serveur
        if (clients.size() > 0)
            //Pour chaque client
            for (ClientHandler client : clients)
                //Si le pseudo du client est définit
                if (client.pseudo != null)
                    //Si le client n'est pas le nouveau client et que son pseudo vaut celui choisi
                    // retour faux car pseudo déjà pris
                    if (client != newClient && client.pseudo.equals(pseudo)) return false;
        //Retour vrai, pseudo disponible
        return true;
    }

    //Thread client
    private static class ClientHandler implements Runnable {
        //Nombre de messages groupés maximal à envoyer
        private final int MAX_OUTGOING = 10;
        //Taille maximale pour un message
        private final int MESSAGE_LENGTH = 128;
        //Socket client
        private SocketChannel csc;
        //Clé liée au client
        private SelectionKey key;
        //Pseudo du client
        private String pseudo = null;
        //Liste des messages à envoyer au client
        private ArrayBlockingQueue<String> outgoingMessages = new ArrayBlockingQueue<>(MAX_OUTGOING);

        //Constructeur
        public ClientHandler(SelectionKey key) {
            //Association de la clé passée en paramètre
            this.key = key;
            //Récupération du canal de la clé
            this.csc = (SocketChannel) key.channel();
        }

        //Envoyer un message au client depuis le serveur
        public void sendMessage(String message) {
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
        public void sendServerMessages() {
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
        public List<String> getMessagesToSend() {
            //Liste des messages à envoyer
            List<String> messagesToSend = new LinkedList<>();
            //Récupération des messages à envoyer
            outgoingMessages.drainTo(messagesToSend);
            return messagesToSend;
        }

        //Extraire le message contenu dans le buffer
        private String extractMessage(ByteBuffer buffer) {
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

        @Override
        public void run() {
            try {
                //Instanciation d'un buffer de bytes d'une taille MESSAGE_LENGTH
                ByteBuffer buffer = ByteBuffer.allocate(MESSAGE_LENGTH);
                //Tant qu'il est possible de lire depuis le client
                while (csc.read(buffer) != -1) {
                    //Envoi au client des messages serveur
                    sendServerMessages();
                    //Récupération du message
                    String message = extractMessage(buffer);
                    //Si le message est EXIT on sort de la boucle
                    if (message.equals("EXIT")) {
                        sendMessage(defaultColor + "# Déconnexion du serveur.");
                        break;
                    }
                    //Si le message n'est pas vide
                    else if (message.getBytes().length > 0){
                        //Si le pseudo n'est pas définit
                        if (pseudo == null) {
                            //Si le message commence par LOGIN
                            if (message.startsWith("LOGIN ")) {
                                //Récupération du pseudo dans le message
                                pseudo = message.substring(6);
                                //Vérification du pseudo, alphabétique, supérieur à 3 caractères
                                if (pseudo.matches("[a-zA-Z]+") && pseudo.length() >= 3) {
                                    //Si le pseudo est disponible
                                    if (checkPseudo(this, pseudo)) {
                                        //Notification de connexion du client au serveur
                                        broadcast("#", "# Connexion de " + pseudo + " au serveur.");
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
