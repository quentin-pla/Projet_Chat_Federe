import java.io.CharArrayWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

class ChatamuCentralNIO {
    //Couleur attribuée pour les messages serveur envoyés aux clients
    private final static String clientsColor = "\u001B[32m";
    //Couleur attribuée aux messages du client
    private final static String userColor = "\u001B[34m";
    //Couleur par défaut du terminal
    private final static String defaultColor = "\u001B[0m";

    private static Selector selector;
    private static ServerSocketChannel ssc;
    private static Executor executor;

    //Liste des clients connectés au serveur
    private static ArrayList<ClientHandler> clients = new ArrayList<>();

    public static void main(String[] args) {
        int argc = args.length;
        ChatamuCentralNIO serveur;
        /* Traitement des arguments */
        if (argc == 1) {
            try {
                serveur = new ChatamuCentralNIO();
                serveur.demarrer(Integer.parseInt(args[0]));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Usage: java EchoServer port");
        }
    }

    //Démarrage du serveur
    public void demarrer(int port) {
        System.out.println("# Démarrage du chatamu central sur le port " + port);
        try {
            selector = Selector.open();
            ssc = ServerSocketChannel.open();
            ssc.socket().bind(new InetSocketAddress(port));
            ssc.configureBlocking(false);
            int ops = ssc.validOps();
            ssc.register(selector, ops, null);

            executor = Executors.newWorkStealingPool();

            new Thread(new MessagesBroadcaster()).start();

            while (true) {
                selector.select();
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> keys = selectionKeys.iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    if (key.isAcceptable()) {
                        SocketChannel csc = ssc.accept();
                        csc.configureBlocking(false);
                        csc.register(selector, SelectionKey.OP_READ);
                    }
                    if (key.isReadable()) {
                        executor.execute(new ClientHandler(key));
                        key.cancel();
                    }
                }
                keys.remove();
            }
        } catch (IOException ex) {
            System.out.println("# Arrêt anormal du serveur.");
            ex.printStackTrace();
        }
    }

    public static void broadcast(String author, String message) {
        for (ClientHandler client : clients) {
            if (!client.pseudo.equals(author)) client.outgoingMessages.add(message);
        }
        System.out.println(message);
    }

    //Thread gérant l'envoi des messages serveur aux clients connectés
    public static class MessagesBroadcaster extends Thread {
        public void run() {
            while (true) {
                for (ClientHandler client : clients) {
                    //Récupération des messages à envoyer
                    List<String> messages = client.getMessagesToSend();
                    //S'il y a des messages à envoyer
                    if (messages.size() > 0)
                        //Pour chaque message à envoyer
                        for (String message : messages)
                            //Écriture du message sur le flux sortant du client
                            client.sendMessage(message);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    throw new IllegalStateException(ex);
                }
            }
        }
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
        private String pseudo;
        //Liste des messages à envoyer au client
        private ArrayBlockingQueue<String> outgoingMessages = new ArrayBlockingQueue<>(MAX_OUTGOING);

        public ClientHandler(SelectionKey key) {
            this.key = key;
        }

        //Envoyer un message au client depuis le serveur
        public void sendMessage(String message) {
            try {
                //Tableau contenant le message en bytes
                byte[] backbuffer = message.getBytes();
                //Envoi du message au client
                csc.write(ByteBuffer.wrap(backbuffer));
            } catch (IOException e) {
                e.printStackTrace();
            }
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
            CharArrayWriter data = new CharArrayWriter();
            //Tant qu'il reste du contenu à consommer dans le buffer
            while(buffer.hasRemaining()) {
                //Consommation d'un caractère
                data.append((char)buffer.get());
            }
            //Retour du message au format chaine de caractères
            //Suppression du retour à la ligne
            return data.toString().replace("\n","");
        }

        @Override
        public void run() {
            try {
                //?
                csc = (SocketChannel) key.channel();
                //Instanciation d'un buffer de bytes d'une taille MESSAGE_LENGTH
                ByteBuffer buffer = ByteBuffer.allocate(MESSAGE_LENGTH);
                //Lecture du buffer
                csc.read(buffer);
                //Récupération du pseudo
                pseudo = extractMessage(buffer);
                //Notification de connexion du client au serveur
                broadcast(pseudo, defaultColor + "# Connexion de " + pseudo + " au serveur.");
                //Envoi d'un message de confirmation de connexion au serveur
                sendMessage(defaultColor + "# Vous êtes connecté au serveur chatamu central !\n" + userColor);
                //Nettoyage du buffer
                buffer.clear();
                //Tant que le client n'envoi pas un retour à la ligne
                while (true) {
                    //Lecture du buffer
                    csc.read(buffer);
                    //Récupération du message
                    String message = extractMessage(buffer);
                    //Si le message est un retour à la ligne
                    if (message.length() > 0 && message.getBytes()[0] == '\n') {
                        //Fermeture du thread
                        csc.close();
                        break;
                    }
                    else if (message.length() > 0 && message.getBytes()[0] != 0){
                        //Affichage du message
                        broadcast(pseudo, pseudo + " > " + message);
                    }
                    //Nettoyage du buffer
                    buffer.clear();
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        throw new IllegalStateException(ex);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
