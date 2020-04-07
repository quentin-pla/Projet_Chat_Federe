package models.chatamu;

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
}
