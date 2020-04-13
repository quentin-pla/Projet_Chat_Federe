package models;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Classe contenant des méthodes utilisées dans plusieurs classes
 */
public class ChatFunctions {

    /**
     * Insctance de la classe
     */
    private static ChatFunctions instance = null;

    /**
     * Méthode pour récupérer l'instance de la classe
     * @return instance de la classe
     */
    public static ChatFunctions getInstance() {
        if (instance == null) instance = new ChatFunctions();
        return instance;
    }

    /**
     * Extraire un message contenu dans un buffer
     * @param buffer buffer de bytes
     * @return message sous forme de string
     */
    public String extractMessage(ByteBuffer buffer) {
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

    /**
     * Sécuriser un message afin que l'utilisateur ne puisse pas le reproduire
     * @param message message à sécuriser
     * @return message sécurisé
     */
    public String secure(String message) {
        //Récupération de l'action à effectuer
        String operation = (!message.contains(" ")) ? message : message.substring(0, message.indexOf(" "));
        //Récupération du paramètre
        String argument = (!message.contains(" ")) ? "" : message.substring(message.indexOf(" ")+1);
        //Retour du message sécurisé
        return operation + "\b " + argument;
    }

    /**
     * Traduire un résultat en message français compréhensible
     * @param result résultat
     * @return traduction française
     */
    public String traductResult(String result) {
        switch (result) {
            case "[ERROR PORT]":
                return "Port invalide";
            case "[CONNECTION REFUSED]":
                return "Connexion refusée";
            case "[FAIL RECONNECT]":
                return "Reconnexion impossible";
            case "[SOCKET WRITE FAILED]":
                return "Écriture sur le serveur impossible";
            case "[DISCONNECT]":
                return "Déconnexion du serveur";
            case "[CONNECTION LOST]":
                return "Connexion au serveur perdue";
            case "[USERNAME ALREADY USED]":
                return "Pseudo déjà utilisé sur le serveur";
            case "[USERNAME INVALID]":
                return "Pseudo invalide (3 caractères minimum)";
            case "[FAIR NOT FOUND]":
                return "Salon introuvable";
            case "[INVALID FAIR NAME]":
                return "Nom de salon invalide";
            case "[FAIR ALREADY CREATED]":
                return "Nom de salon déjà utilisé";
            case "[SAME FAIR LOCATION]":
                return "Vous vous situez déjà dans le salon";
            case "[ERROR CHATAMU]":
                return "ERROR chatamu";
            default:
                return null;
        }
    }
}
